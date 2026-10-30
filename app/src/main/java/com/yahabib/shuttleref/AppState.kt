package com.yahabib.shuttleref

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yahabib.shuttleref.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ScreenState(
    val player: Player? = null, val sdkIdentity: SdkIdentity? = null,
    val homes: List<Home> = emptyList(), val selectedHome: Home? = null, val devices: List<ScoreHost> = emptyList(),
    val busy: Boolean = false, val message: String = "", val origin: String = "https://shuttleserver.wgine.com",
    val consent: Boolean = false, val initialized: Boolean = false,
    val invite: Home? = null, val inviteCode: String = "", val codeWait: Int = 0,
    val logoutPending: Boolean = false
)
class AppState(application: Application) : AndroidViewModel(application) {
    val integration = IntegrationFactory.create()
    val scoring = ScoreController(application, viewModelScope, integration.score)
    private val preferences = application.getSharedPreferences(if (integration.demo) "demo-account" else "account-v1", 0)
    private val fence = ContextFence()
    private val sessions = AccountSessionGate()
    private val mutable = MutableStateFlow(ScreenState(
        origin = preferences.getString("origin", null)?.let { runCatching { serverOrigin(it) }.getOrNull() } ?: "https://shuttleserver.wgine.com",
        consent = preferences.getBoolean("consent", false), logoutPending = preferences.getBoolean("logoutPending", false)
    ))
    val state = mutable.asStateFlow()
    private val cooldowns = mutableMapOf<String, Long>()
    private var activeCodeKey = ""
    private var devicesObservedAt = 0L
    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                mutable.update { it.copy(codeWait = remainingCodeWait(), devices = if (android.os.SystemClock.elapsedRealtime() - devicesObservedAt > 60_000) it.devices.map { d -> d.copy(connection = Connectivity.UNKNOWN) } else it.devices) }
            }
        }
        if (state.value.consent && !integration.demo) restore()
    }
    fun consent(value: Boolean) {
        if (state.value.busy || state.value.sdkIdentity != null) return
        preferences.edit().putBoolean("consent", value).apply()
        mutable.update { it.copy(consent = value) }
    }
    private fun ensureSdk() {
        check(state.value.consent) { "请先阅读并同意登录数据说明" }
        if (state.value.initialized) return
        integration.initialize(getApplication())
        mutable.update { it.copy(initialized = true) }
        integration.identity?.onSessionExpired {
            Handler(Looper.getMainLooper()).post {
                if (state.value.logoutPending) return@post
                fence.invalidate(); sessions.clear(); scoring.close("登录已失效，请重新登录")
                mutable.update { ScreenState(origin = it.origin, consent = it.consent, initialized = true, message = "登录已失效，请重新登录") }
            }
        }
    }
    private fun errorMessage(e: Exception): String = when (e) {
        is TimeoutCancellationException -> "请求超时，结果尚未确认。请刷新登录状态或家庭列表后再试。"
        is java.io.IOException -> "网络连接失败，请检查网络后重试"
        is ServiceFailure -> e.message.orEmpty()
        is IllegalStateException, is IllegalArgumentException -> e.message ?: "请求未完成，请重试"
        else -> "请求未完成（${e.javaClass.simpleName}），请重试"
    }
    private fun work(message: String, action: suspend (Long) -> Unit) {
        if (state.value.busy) return
        val generation = fence.capture()
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch {
            try { action(generation) }
            catch (e: CancellationException) {
                if (e is TimeoutCancellationException && fence.accepts(generation)) mutable.update { it.copy(message = errorMessage(e)) }
                else throw e
            } catch (e: Exception) {
                if (fence.accepts(generation)) mutable.update { it.copy(message = errorMessage(e)) }
            } finally {
                if (fence.accepts(generation)) mutable.update { it.copy(busy = false) }
            }
        }
    }
    fun initialize() = work("正在准备登录…") { ensureSdk(); mutable.update { it.copy(message = "登录服务已准备") } }
    fun restore() = work("正在恢复登录…") { generation ->
        ensureSdk()
        if (state.value.logoutPending) {
            finishLogout(generation)
        } else completeLogin(generation)
    }
    // Business bearer stays in memory. SDK owns its cached login; on process restart exchange a fresh ticket.
    private suspend fun completeLogin(generation: Long) {
        val identity = integration.identity?.currentIdentity()
        if (!fence.accepts(generation)) return
        mutable.update { it.copy(sdkIdentity = identity) }
        if (identity == null) {
            sessions.clear(); scoring.close()
            mutable.update { it.copy(player = null, homes = emptyList(), selectedHome = null, devices = emptyList(), invite = null, inviteCode = "", message = "请输入账号登录") }
            return
        }
        var message = "已登录羽小裁"
        val oldSession = sessions.session
        val attempt = sessions.begin(state.value.origin, identity.subject)
        val client = BusinessClient(state.value.origin)
        try {
            mutable.update { it.copy(player = null, message = "正在验证业务登录…") }
            val ticket = requireNotNull(integration.identity).loginTicket()
            check(integration.identity?.currentIdentity()?.subject == identity.subject) { "账号已变化，请重新登录" }
            if (!fence.accepts(generation)) return
            val session = client.exchange(ticket, identity)
            if (!fence.accepts(generation) || integration.identity?.currentIdentity()?.subject != identity.subject) {
                runCatching { client.logout(session.token) }; return
            }
            check(sessions.accept(attempt, session, System.currentTimeMillis() / 1000)) { "服务端未返回有效的涂鸦业务会话" }
            mutable.update { it.copy(player = session.player) }
            if (oldSession != null) runCatching { client.logout(oldSession.token) }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            message = "涂鸦账号已登录，业务连接未完成：${errorMessage(e)}"
        }
        if (!fence.accepts(generation)) return
        try { loadHomes(generation) } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            message += "；家庭加载失败：${errorMessage(e)}"
        }
        if (fence.accepts(generation)) mutable.update { it.copy(message = message) }
    }
    fun submit(method: LoginMethod, action: AccountAction, account: String, country: String, password: String, code: String) {
        val name = account.trim(); val region = country.trim()
        if (!method.valid(name, region)) { mutable.update { it.copy(message = "请检查手机号／邮箱及国家区号") }; return }
        val passwordRequired = action != AccountAction.LOGIN || method == LoginMethod.EMAIL_PASSWORD
        if (passwordRequired && password.isBlank()) { mutable.update { it.copy(message = "请输入密码") }; return }
        if ((action != AccountAction.LOGIN || method != LoginMethod.EMAIL_PASSWORD) && code.isBlank()) { mutable.update { it.copy(message = "请输入验证码") }; return }
        work("正在处理账号请求…") { generation ->
            ensureSdk()
            check(!state.value.logoutPending) { "请先完成退出登录" }
            val gateway = requireNotNull(integration.identity)
            when (action) {
                AccountAction.LOGIN -> {
                    gateway.login(method, name, region, if (method == LoginMethod.EMAIL_PASSWORD) password else code.trim())
                    completeLogin(generation)
                }
                AccountAction.REGISTER -> {
                    gateway.register(method, name, region, password, code.trim())
                    if (gateway.currentIdentity() != null) completeLogin(generation)
                    else if (fence.accepts(generation)) mutable.update { it.copy(message = "注册成功，请切换到登录") }
                }
                AccountAction.RESET_PASSWORD -> {
                    gateway.resetPassword(method, name, region, password, code.trim())
                    if (fence.accepts(generation)) mutable.update { it.copy(message = "密码已更新，请切换到登录") }
                }
            }
        }
    }
    fun codeContext(method: LoginMethod, action: AccountAction, account: String, country: String) {
        activeCodeKey = "${country.trim()}:${account.trim()}:${action.purpose}"
        mutable.update { it.copy(codeWait = remainingCodeWait()) }
    }
    private fun remainingCodeWait(): Int = (((cooldowns[activeCodeKey] ?: 0L) - android.os.SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0).toInt()
    fun sendCode(method: LoginMethod, action: AccountAction, account: String, country: String) {
        codeContext(method, action, account, country)
        if (!method.valid(account.trim(), country.trim())) { mutable.update { it.copy(message = "请先填写有效账号和国家区号") }; return }
        if (remainingCodeWait() > 0) return
        val key = activeCodeKey
        work("正在发送验证码…") { generation ->
            ensureSdk()
            // Reserve before sending: a timeout may still have delivered the code.
            cooldowns[key] = android.os.SystemClock.elapsedRealtime() + 60_000
            mutable.update { it.copy(codeWait = remainingCodeWait()) }
            requireNotNull(integration.identity).sendCode(account.trim(), country.trim(), action.purpose)
            if (fence.accepts(generation)) mutable.update { it.copy(message = "验证码已发送，请查收") }
        }
    }
    fun enterDemo() {
        if (!integration.demo) return
        fence.invalidate()
        mutable.update { it.copy(player = integration.previewPlayer(), homes = integration.previewHomes(), busy = false, message = "当前为模拟账号") }
    }
    fun logout() {
        if (state.value.busy || blockMatchContextChange()) return
        if (integration.demo) { fence.invalidate(); scoring.close(); mutable.value = ScreenState(origin = state.value.origin); return }
        work("正在退出登录…") { generation ->
            ensureSdk()
            preferences.edit().putBoolean("logoutPending", true).commit()
            mutable.update { it.copy(logoutPending = true) }
            finishLogout(generation)
        }
    }
    private suspend fun finishLogout(generation: Long) {
        scoring.close()
        val old = sessions.session
        sessions.clear()
        mutable.update { it.copy(player = null, homes = emptyList(), selectedHome = null, devices = emptyList(), invite = null, inviteCode = "") }
        val revoked = old == null || runCatching { BusinessClient(state.value.origin).logout(old.token) }.isSuccess
        requireNotNull(integration.identity).logout()
        if (!fence.accepts(generation)) return
        preferences.edit().putBoolean("logoutPending", false).apply()
        mutable.update { ScreenState(origin = it.origin, consent = it.consent, initialized = true, busy = true,
            message = if (revoked) "已退出登录" else "已在本机退出；服务器会话撤销未确认，将按有效期失效") }
    }
    private suspend fun loadHomes(generation: Long) {
        val homes = requireNotNull(integration.homes).listHomes()
        if (!fence.accepts(generation)) return
        val selected = homes.firstOrNull { it.id == state.value.selectedHome?.id } ?: homes.firstOrNull()
        if (selected?.id != state.value.selectedHome?.id) scoring.close()
        mutable.update { it.copy(homes = homes, selectedHome = selected, devices = emptyList()) }
        if (selected != null) loadDevices(generation, selected)
    }
    private suspend fun loadDevices(generation: Long, home: Home) {
        val devices = requireNotNull(integration.homes).listDevices(home.id)
        if (fence.accepts(generation) && state.value.selectedHome?.id == home.id) {
            devicesObservedAt = android.os.SystemClock.elapsedRealtime()
            mutable.update { it.copy(devices = devices) }
            scoring.updateDevices(devices)
        }
    }
    fun refreshHomes() = work("正在刷新家庭与设备…") { generation ->
        check(state.value.sdkIdentity != null && !state.value.logoutPending) { "请先登录" }
        mutable.update { it.copy(devices = it.devices.map { device -> device.copy(connection = Connectivity.UNKNOWN) }) }
        loadHomes(generation)
        if (fence.accepts(generation)) mutable.update { it.copy(message = "家庭与设备已更新") }
    }
    fun selectHome(home: Home) {
        if (state.value.busy || home !in state.value.homes) return
        if (home.id != state.value.selectedHome?.id) {
            if (blockMatchContextChange()) return
            scoring.close()
        }
        fence.invalidate()
        mutable.update { it.copy(selectedHome = home, devices = emptyList(), invite = null, inviteCode = "") }
        if (integration.demo) { mutable.update { it.copy(devices = integration.previewDevices(home.id)) }; return }
        work("正在加载设备…") { generation ->
            loadDevices(generation, home)
            if (fence.accepts(generation)) mutable.update { it.copy(message = "设备列表已更新") }
        }
    }
    fun clearInvitation() { if (!state.value.busy) mutable.update { it.copy(invite = null, inviteCode = "") } }
    fun previewInvitation(value: String) = work("正在读取家庭邀请…") { generation ->
        check(state.value.sdkIdentity != null && !state.value.logoutPending) { "请先登录" }
        val code = value.trim()
        require(code.matches(Regex("[a-zA-Z0-9]{1,128}"))) { "请仅输入家庭邀请码，不要粘贴整段邀请文字" }
        mutable.update { it.copy(invite = null, inviteCode = "") }
        val home = requireNotNull(integration.homes).previewInvitation(code)
        if (fence.accepts(generation)) mutable.update { it.copy(invite = home, inviteCode = code, message = "请核对家庭后确认加入") }
    }
    fun joinInvitation() = work("正在加入家庭…") { generation ->
        check(state.value.sdkIdentity != null && !state.value.logoutPending) { "请先登录" }
        check(scoring.changeBlockReason() == null) { scoring.changeBlockReason().orEmpty() }
        val home = requireNotNull(state.value.invite)
        requireNotNull(integration.homes).joinInvitation(state.value.inviteCode)
        if (!fence.accepts(generation)) return@work
        scoring.close()
        mutable.update { it.copy(invite = null, inviteCode = "", selectedHome = home, message = "已加入家庭，正在刷新设备…") }
        loadHomes(generation)
        if (fence.accepts(generation)) mutable.update { it.copy(message = "家庭与设备已更新") }
    }
    fun changeOrigin(value: String) {
        if (state.value.busy || blockMatchContextChange()) return
        val origin = runCatching { serverOrigin(value) }.getOrElse { mutable.update { it.copy(message = "请填写完整 HTTPS 域名") }; return }
        val oldToken = sessions.session?.token; val oldOrigin = state.value.origin
        fence.invalidate(); sessions.clear(); scoring.close()
        preferences.edit().putString("origin", origin).apply()
        mutable.update { ScreenState(origin = origin, consent = it.consent, initialized = it.initialized, sdkIdentity = it.sdkIdentity, logoutPending = it.logoutPending, message = "服务器已切换，请点击恢复登录连接") }
        if (oldToken != null) viewModelScope.launch { runCatching { BusinessClient(oldOrigin).logout(oldToken) } }
    }
    fun checkConnection() = work("正在检查业务连接…") { generation ->
        try {
            BusinessClient(state.value.origin).check(sessions.session?.token)
            if (fence.accepts(generation)) mutable.update { it.copy(message = if (sessions.session != null) "业务登录有效，服务器连接正常" else "服务有响应，尚未建立业务登录") }
        } catch (e: ServiceFailure) {
            if (e.status != 401) throw e
            if (!fence.accepts(generation)) return@work
            val hadSession = sessions.session != null
            sessions.clear()
            mutable.update { it.copy(player = null, message = if (hadSession) "业务会话已过期，请点击恢复登录连接" else "业务服务可达，尚未登录") }
        }
    }
    fun onForeground() {
        if (integration.demo) { scoring.foreground(); return }
        if (!state.value.initialized) return
        val actualIdentity = integration.identity?.currentIdentity()?.subject
        if (state.value.logoutPending || actualIdentity != state.value.sdkIdentity?.subject || actualIdentity == null) scoring.close()
        else scoring.foreground()
        if (state.value.busy) return
        mutable.update { it.copy(devices = it.devices.map { d -> d.copy(connection = Connectivity.UNKNOWN) }) }
        if (state.value.logoutPending) return
        if (integration.identity?.currentIdentity()?.subject != state.value.sdkIdentity?.subject) {
            fence.invalidate(); sessions.clear(); scoring.close()
            mutable.update { it.copy(player = null, sdkIdentity = null, homes = emptyList(), selectedHome = null, devices = emptyList()) }
            restore()
        } else if (state.value.sdkIdentity != null && (sessions.session?.expiresAt ?: 0) <= System.currentTimeMillis() / 1000) restore()
        else if (state.value.sdkIdentity != null) refreshHomes()
    }
    private fun blockMatchContextChange(): Boolean {
        val message = scoring.changeBlockReason() ?: return false
        mutable.update { it.copy(message = message) }
        return true
    }
    fun selectScoreDevice(device: ScoreHost) {
        if (state.value.busy || state.value.logoutPending || device !in state.value.devices) return
        val identity = state.value.sdkIdentity?.subject ?: if (integration.demo) "demo" else return
        val home = state.value.selectedHome ?: return
        scoring.select(device, "${state.value.origin}:$identity:${home.id}")
    }
    fun matchAction(action: MatchAction) {
        if (state.value.busy || state.value.logoutPending) return
        if (!integration.demo && (state.value.sdkIdentity == null || integration.identity?.currentIdentity()?.subject != state.value.sdkIdentity?.subject)) { scoring.close("登录状态变化，请重新登录"); return }
        scoring.send(action)
    }
    fun onBackground() { scoring.background() }
    override fun onCleared() { scoring.close(); integration.identity?.takeIf { state.value.initialized }?.onSessionExpired {}; super.onCleared() }
}

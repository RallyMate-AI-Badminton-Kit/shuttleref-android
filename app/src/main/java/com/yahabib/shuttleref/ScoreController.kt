package com.yahabib.shuttleref

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.yahabib.shuttleref.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest

data class ScoreState(
    val device: ScoreHost? = null, val snapshot: MatchSnapshot? = null, val saved: MatchSnapshot? = null,
    val online: Boolean = false, val fresh: Boolean = false, val pending: MatchCommand? = null,
    val message: String = "请先选择计分主机", val allowed: Set<MatchAction> = emptySet()
)
private class ScorePreferences(context: Context, namespace: String) : MatchPersistence {
    private val key = MessageDigest.getInstance("SHA-256").digest(namespace.toByteArray()).joinToString("") { "%02x".format(it) }
    private val preferences = context.getSharedPreferences("match-v1", Context.MODE_PRIVATE)
    override var sequence: Long
        get() = preferences.getLong("$key.seq", 0)
        set(value) { check(preferences.edit().putLong("$key.seq", value).commit()) { "指令序号保存失败" } }
    override var saved: String?
        get() = preferences.getString("$key.saved", null)
        set(value) { check(preferences.edit().putString("$key.saved", value).commit()) { "比分暂存失败" } }
}
class ScoreController(private val context: Context, private val scope: CoroutineScope, private val gateway: ScoreGateway?) {
    private val mutable = MutableStateFlow(ScoreState())
    val state = mutable.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var connection: ScoreConnection? = null
    private var session: MatchSession? = null
    private var timeout: Job? = null
    private var foreground = true
    fun changeBlockReason(): String? = when {
        state.value.pending != null -> "请等待主机确认当前操作后再切换"
        state.value.snapshot?.let { it.phase == 'a' && !it.finished } == true -> "请先暂停当前比赛，再切换账号、家庭或主机"
        else -> null
    }
    fun select(device: ScoreHost, namespace: String) {
        if (state.value.device?.id == device.id) { send(MatchAction.QUERY); return }
        val blocked = changeBlockReason()
        if (blocked != null) { mutable.update { it.copy(message = blocked) }; return }
        close()
        val version = generation
        mutable.value = ScoreState(device = device, message = "正在连接计分主机…")
        fun callback(action: () -> Unit) { main.post { if (version == generation) action() } }
        try {
            val link = requireNotNull(gateway) { "当前版本未接入计分主机" }.open(device.id, object : ScoreEvents {
                override fun snapshot(raw: String) = callback {
                    session?.receive(raw)
                    if (session?.pending == null) timeout?.cancel()
                    emit()
                }
                override fun online(value: Boolean) = callback {
                    val wasOnline = session?.online == true
                    session?.setOnline(value)
                    if (!value) timeout?.cancel()
                    emit()
                    if (value && !wasOnline && foreground) send(MatchAction.QUERY)
                }
                override fun removed() = callback { close("设备已移除，请刷新家庭列表") }
            })
            connection = link
            session = MatchSession(ScorePreferences(context, "$namespace:${device.id}"), link.online, link.cachedSnapshot)
            emit()
            if (foreground) send(MatchAction.QUERY)
        } catch (e: Exception) {
            close("计分主机连接失败，请刷新设备列表后重试")
        }
    }
    fun send(action: MatchAction) {
        if (!foreground) return
        val engine = session ?: return
        val link = connection ?: return
        val command = engine.begin(action)
        emit()
        if (command == null) return
        val version = generation
        timeout?.cancel()
        timeout = scope.launch {
            delay(12_000)
            if (version == generation && engine.pending == command) { engine.invalidate(); emit() }
        }
        try {
            link.publish(command.wire) { error ->
                main.post {
                    if (version != generation) return@post
                    engine.sent(command, error)
                    if (engine.pending == null) timeout?.cancel()
                    emit()
                }
            }
        } catch (e: Exception) { engine.sent(command, "SDK_EXCEPTION"); timeout?.cancel(); emit() }
    }
    private fun emit() {
        val s = session ?: return
        mutable.update { it.copy(snapshot = s.current, saved = s.saved, online = s.online,
            fresh = s.fresh, pending = s.pending, message = s.message,
            allowed = if (foreground) MatchAction.entries.filter(s::can).toSet() else emptySet()) }
    }
    fun background() { foreground = false; timeout?.cancel(); session?.invalidate("返回后需要同步主机比分"); emit() }
    fun foreground() { foreground = true; emit(); send(MatchAction.QUERY) }
    fun updateDevices(devices: List<ScoreHost>) {
        val selected = state.value.device ?: return
        val latest = devices.firstOrNull { it.id == selected.id }
        if (latest == null) { close("当前家庭已无法访问这台主机，请重新选择"); return }
        mutable.update { it.copy(device = latest) }
        session?.setOnline(latest.connection == Connectivity.ONLINE)
        emit()
    }
    fun close(message: String = "请先选择计分主机") {
        generation++; timeout?.cancel(); timeout = null
        session?.close(); session = null
        connection?.close(); connection = null
        mutable.value = ScoreState(message = message)
    }
}

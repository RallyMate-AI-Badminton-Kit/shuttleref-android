package com.yahabib.shuttleref

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.android.user.api.*
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.*
import com.thingclips.smart.sdk.api.*
import com.yahabib.shuttleref.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun sdkError(code: String?) = IllegalStateException("涂鸦请求失败（${code?.take(80) ?: "未知错误"}），请检查账号、验证码或网络后重试")
private suspend fun <T> sdkCall(block: (ok: (T) -> Unit, fail: (String?) -> Unit) -> Unit): T = withTimeout(30_000) {
    suspendCancellableCoroutine { c ->
        block({ if (c.isActive) c.resume(it) }, { if (c.isActive) c.resumeWithException(sdkError(it)) })
    }
}
private fun result(ok: (Unit) -> Unit, fail: (String?) -> Unit) = object : IResultCallback {
    override fun onSuccess() = ok(Unit)
    override fun onError(code: String?, error: String?) = fail(code)
}
private fun objectValue(value: Any?): JSONObject {
    val root = if (value is String) JSON.parseObject(value) else JSON.parseObject(JSON.toJSONString(value))
    return root ?: error("涂鸦返回了空数据")
}

class TuyaAccounts : IdentityGateway, HomeGateway {
    private val user get() = ThingHomeSdk.getUserInstance()
    // SDK auth calls mutate its global session. Keep them serial even if UI wait times out.
    private var authPending = false
    override fun currentIdentity(): SdkIdentity? {
        if (!user.isLogin) return null
        val u = user.user ?: return null
        return SdkIdentity(u.uid.orEmpty(), u.domain?.regionCode.orEmpty(), u.nickName.orEmpty().ifBlank { "羽小裁球友" })
    }
    override fun onSessionExpired(listener: () -> Unit) {
        ThingHomeSdk.setOnNeedLoginListener { listener() }
    }
    override suspend fun sendCode(account: String, countryCode: String, purpose: Int) = sdkCall<Unit> { ok, fail ->
        user.sendVerifyCodeWithUserName(account, "", countryCode, purpose, result(ok, fail))
    }
    override suspend fun login(method: LoginMethod, account: String, countryCode: String, credential: String): SdkIdentity {
        check(!authPending) { "上一笔登录尚未返回，请稍后再试" }
        return sdkCall { ok, fail ->
            authPending = true
            val callback = object : ILoginCallback {
                override fun onSuccess(value: User?) {
                    authPending = false
                    val identity = currentIdentity()
                    if (identity != null) ok(identity) else fail("SESSION_MISSING")
                }
                override fun onError(code: String?, error: String?) { authPending = false; fail(code) }
            }
            try {
                when (method) {
                    LoginMethod.PHONE_CODE -> user.loginWithPhone(countryCode, account, credential, callback)
                    LoginMethod.EMAIL_CODE -> user.loginWithEmailCode(countryCode, account, credential, callback)
                    LoginMethod.EMAIL_PASSWORD -> user.loginWithEmail(countryCode, account, credential, callback)
                }
            } catch (e: Exception) { authPending = false; throw e }
        }
    }
    override suspend fun register(method: LoginMethod, account: String, countryCode: String, password: String, code: String) {
        check(!authPending) { "上一笔登录尚未返回，请稍后再试" }
        sdkCall<Unit> { ok, fail ->
            authPending = true
            val callback = object : IRegisterCallback {
                override fun onSuccess(value: User?) { authPending = false; ok(Unit) }
                override fun onError(code: String?, error: String?) { authPending = false; fail(code) }
            }
            try {
                if (method == LoginMethod.PHONE_CODE) user.registerAccountWithPhone(countryCode, account, password, code, callback)
                else user.registerAccountWithEmail(countryCode, account, password, code, callback)
            } catch (e: Exception) { authPending = false; throw e }
        }
    }
    override suspend fun resetPassword(method: LoginMethod, account: String, countryCode: String, password: String, code: String) = sdkCall<Unit> { ok, fail ->
        val callback = object : IResetPasswordCallback {
            override fun onSuccess() = ok(Unit)
            override fun onError(code: String?, error: String?) = fail(code)
        }
        if (method == LoginMethod.PHONE_CODE) user.resetPhonePassword(countryCode, account, code, password, callback)
        else user.resetEmailPassword(countryCode, account, code, password, callback)
    }
    override suspend fun loginTicket(): String = sdkCall { ok, fail ->
        ThingHomeSdk.getRequestInstance().requestWithApiName("tuya.m.user.sso.ticket.getbysid", "1.0", emptyMap(), object : IRequestCallback {
            override fun onSuccess(value: Any?) {
                val ticket = runCatching {
                    val root = objectValue(value)
                    (root.getJSONObject("result") ?: root.getJSONObject("resultDO") ?: root).getString("ticket")
                }.getOrNull()
                if (!ticket.isNullOrBlank()) ok(ticket) else fail("TICKET_MISSING")
            }
            override fun onFailure(code: String?, error: String?) = fail(code)
        })
    }
    override suspend fun logout() {
        check(!authPending) { "上一笔登录尚未返回，请稍后退出" }
        sdkCall<Unit> { ok, fail ->
            authPending = true
            try {
                user.logout(object : ILogoutCallback {
                    override fun onSuccess() { authPending = false; ok(Unit) }
                    override fun onError(code: String?, error: String?) { authPending = false; fail(code) }
                })
            } catch (e: Exception) { authPending = false; throw e }
        }
    }
    override suspend fun listHomes(): List<Home> = sdkCall { ok, fail ->
        ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
            override fun onSuccess(homes: MutableList<HomeBean>?) = ok(homes.orEmpty().map {
                Home(it.homeId, it.name.orEmpty().ifBlank { "未命名家庭" }, when (it.role) { 2 -> HomeRole.OWNER; 1 -> HomeRole.ADMIN; else -> HomeRole.MEMBER })
            })
            override fun onError(code: String?, error: String?) = fail(code)
        })
    }
    override suspend fun listDevices(homeId: Long): List<ScoreHost> = sdkCall { ok, fail ->
        ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
            override fun onSuccess(home: HomeBean?) = ok(home?.deviceList.orEmpty().map {
                ScoreHost(it.getDevId(), it.getName().orEmpty().ifBlank { "未命名设备" }, if (it.getIsOnline() == true) Connectivity.ONLINE else Connectivity.OFFLINE, it.getSchemaMap()?.keys?.containsAll(listOf("101", "110")) == true)
            })
            override fun onError(code: String?, error: String?) = fail(code)
        })
    }
    override suspend fun previewInvitation(code: String): Home = sdkCall { ok, fail ->
        ThingHomeSdk.getMemberInstance().getInvitationFamilyInfo(code, object : IThingDataCallback<Any> {
            override fun onSuccess(value: Any?) {
                val home = runCatching {
                    val root = objectValue(value)
                    val id = root.getLongValue("groupId")
                    val name = root.getString("groupName")
                    require(id > 0 && !name.isNullOrBlank())
                    Home(id, name, HomeRole.MEMBER)
                }.getOrNull()
                if (home != null) ok(home) else fail("INVITATION_FORMAT")
            }
            override fun onError(code: String?, error: String?) = fail(code)
        })
    }
    override suspend fun joinInvitation(code: String) = sdkCall<Unit> { ok, fail ->
        ThingHomeSdk.getHomeManagerInstance().joinHomeByInviteCode(code, result(ok, fail))
    }
}

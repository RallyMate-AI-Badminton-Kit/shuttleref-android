package com.yahabib.shuttleref.core

import java.security.MessageDigest

enum class LoginMethod(val title: String) {
    PHONE_CODE("手机验证码"), EMAIL_CODE("邮箱验证码"), EMAIL_PASSWORD("邮箱密码");
    fun valid(account: String, country: String): Boolean {
        if (!country.matches(Regex("[0-9]{1,4}"))) return false
        return if (this == PHONE_CODE) {
            account.matches(if (country == "86") Regex("1[0-9]{10}") else Regex("[0-9]{5,15}"))
        } else account.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
    }
}
enum class AccountAction(val purpose: Int) { LOGIN(2), REGISTER(1), RESET_PASSWORD(3) }
data class SdkIdentity(val uid: String, val region: String, val name: String) {
    val subject get() = "$region:$uid"
    val fingerprint: String get() {
        require(uid.isNotBlank() && region.isNotBlank()) { "账号身份不完整，请重新登录" }
        return MessageDigest.getInstance("SHA-256").digest(subject.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
    }
}
data class BusinessSession(val player: Player, val token: String, val expiresAt: Long, val provider: String)
data class SessionAttempt(val generation: Long, val origin: String, val subject: String)
class AccountSessionGate {
    private var generation = 0L
    private var attempt: SessionAttempt? = null
    var session: BusinessSession? = null; private set
    fun begin(origin: String, subject: String): SessionAttempt = SessionAttempt(++generation, origin, subject).also { attempt = it; session = null }
    fun clear() { generation++; attempt = null; session = null }
    fun accept(request: SessionAttempt, result: BusinessSession, nowSeconds: Long): Boolean {
        if (request != attempt || result.provider != "tuya" || result.token.isBlank() || result.player.id.isBlank() || result.expiresAt <= nowSeconds) return false
        session = result
        return true
    }
}

package com.yahabib.shuttleref

import com.yahabib.shuttleref.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ServiceFailure(val status: Int, message: String) : Exception(message)
class BusinessClient(private val origin: String) {
    init { serverOrigin(origin) }
    private suspend fun request(path: String, body: JSONObject? = null, token: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("$origin/api/social$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 35_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            // Never follow redirects with tickets or bearer credentials.
            if (status in 300..399) throw ServiceFailure(status, "服务器发生重定向，请核对 HTTPS 地址")
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrNull()
            if (status !in 200..299) throw ServiceFailure(status, result?.optString("error")?.take(180)?.takeIf { it.isNotBlank() } ?: "业务服务返回 HTTP $status")
            result ?: throw ServiceFailure(status, "业务服务返回格式异常")
        } finally { connection.disconnect() }
    }
    suspend fun exchange(ticket: String, identity: SdkIdentity): BusinessSession {
        val result = request("/session/ticket", JSONObject().put("ticket", ticket).put("fingerprint", identity.fingerprint).put("name", identity.name.take(24).ifBlank { "羽小裁球友" }))
        val user = result.getJSONObject("user")
        return BusinessSession(Player(user.getString("id"), user.getString("name")), result.getString("token"), result.getDouble("expires_at").toLong(), result.optString("auth_provider"))
    }
    suspend fun check(token: String? = null) { request("/me", token = token) }
    suspend fun logout(token: String) { request("/session/logout", JSONObject(), token) }
}

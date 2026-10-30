package com.yahabib.shuttleref.core

import java.net.URI
import java.util.UUID

data class Player(val id: String, val name: String)
data class Home(val id: Long, val name: String, val role: HomeRole)
enum class HomeRole { OWNER, ADMIN, MEMBER }
enum class Connectivity { ONLINE, OFFLINE, UNKNOWN }
data class ScoreHost(val id: String, val name: String, val connection: Connectivity, val scoringSupported: Boolean = false)
data class Racket(val id: String, val name: String, val user: Player?, val hostId: String?, val connection: Connectivity)
data class Capabilities(val account: Boolean = false, val homeInvitation: Boolean = false,
    val score: Boolean = false, val ble: Boolean = false)

// SDK callbacks are converted to suspending functions at the adapter boundary, never in UI.
interface IdentityGateway {
    fun currentIdentity(): SdkIdentity?
    fun onSessionExpired(listener: () -> Unit)
    suspend fun sendCode(account: String, countryCode: String, purpose: Int)
    suspend fun login(method: LoginMethod, account: String, countryCode: String, credential: String): SdkIdentity
    suspend fun register(method: LoginMethod, account: String, countryCode: String, password: String, code: String)
    suspend fun resetPassword(method: LoginMethod, account: String, countryCode: String, password: String, code: String)
    suspend fun loginTicket(): String
    suspend fun logout()
}
interface HomeGateway {
    suspend fun listHomes(): List<Home>
    suspend fun listDevices(homeId: Long): List<ScoreHost>
    suspend fun previewInvitation(code: String): Home
    suspend fun joinInvitation(code: String)
}
interface RacketDiscovery {
    suspend fun discover(): List<Racket>
    suspend fun authenticate(deviceId: String): Racket
    fun cancel()
}
interface BusinessGateway {
    // No arbitrary target UID. Server resolves identity from the verified ticket.
    suspend fun exchangeTicket(ticket: String, fingerprint: String, name: String): BusinessSession
    suspend fun health(): Boolean
    fun clearSession()
}

/** Capture before async work; discard results after logout/server/home change. */
class ContextFence {
    private var generation = 0L
    @Synchronized fun capture(): Long = generation
    @Synchronized fun invalidate() { generation++ }
    @Synchronized fun accepts(value: Long): Boolean = value == generation
}

enum class OperationPhase { IDLE, SUBMITTING, WAITING_DEVICE, CONFIRMED, REJECTED, CONFLICT, EXPIRED, UNKNOWN }
class DeviceOperation {
    var id: String? = null; private set
    var phase = OperationPhase.IDLE; private set
    fun begin(): String {
        check(phase in setOf(OperationPhase.IDLE, OperationPhase.CONFIRMED, OperationPhase.REJECTED, OperationPhase.CONFLICT, OperationPhase.EXPIRED))
        return UUID.randomUUID().toString().also { id = it; phase = OperationPhase.SUBMITTING }
    }
    fun accepted(operationId: String) { if (id == operationId && phase == OperationPhase.SUBMITTING) phase = OperationPhase.WAITING_DEVICE }
    fun timeout(operationId: String) { if (id == operationId && phase in setOf(OperationPhase.SUBMITTING, OperationPhase.WAITING_DEVICE)) phase = OperationPhase.UNKNOWN }
    fun deviceResult(operationId: String, result: OperationPhase) {
        require(result in setOf(OperationPhase.CONFIRMED, OperationPhase.REJECTED, OperationPhase.CONFLICT, OperationPhase.EXPIRED))
        if (id == operationId && phase in setOf(OperationPhase.SUBMITTING, OperationPhase.WAITING_DEVICE, OperationPhase.UNKNOWN)) phase = result
    }
}
fun serverOrigin(input: String): String {
    val uri = URI(input.trim())
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path in listOf("", "/")) { "请输入 HTTPS 服务器域名，不包含路径或凭据" }
    return input.trim().trimEnd('/')
}

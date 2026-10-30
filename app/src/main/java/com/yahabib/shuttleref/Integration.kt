package com.yahabib.shuttleref
import android.app.Application
import com.yahabib.shuttleref.core.*
interface Integration {
    val demo: Boolean
    val description: String
    val capabilities: Capabilities
    val identity: IdentityGateway? get() = null
    val homes: HomeGateway? get() = null
    val score: ScoreGateway? get() = null
    fun initialize(application: Application): String
    fun previewPlayer(): Player? = null
    fun previewHomes(): List<Home> = emptyList()
    fun previewDevices(homeId: Long): List<ScoreHost> = emptyList()
}

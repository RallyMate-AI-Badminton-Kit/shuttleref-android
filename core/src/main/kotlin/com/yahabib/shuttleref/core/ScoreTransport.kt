package com.yahabib.shuttleref.core

interface ScoreEvents {
    fun snapshot(raw: String)
    fun online(value: Boolean)
    fun removed()
}
interface ScoreConnection {
    val cachedSnapshot: String?
    val online: Boolean
    fun publish(wire: String, completed: (String?) -> Unit)
    fun close()
}
interface ScoreGateway {
    fun open(deviceId: String, events: ScoreEvents): ScoreConnection
}

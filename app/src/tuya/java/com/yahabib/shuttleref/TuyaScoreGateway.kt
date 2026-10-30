package com.yahabib.shuttleref

import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.yahabib.shuttleref.core.*
import org.json.JSONObject

class TuyaScoreGateway : ScoreGateway {
    override fun open(deviceId: String, events: ScoreEvents): ScoreConnection {
        val bean = ThingHomeSdk.getDataInstance().getDeviceBean(deviceId)
            ?: error("主机信息尚未加载，请刷新设备列表")
        check(bean.getSchemaMap()?.keys?.containsAll(listOf("101", "110")) == true) { "此设备不支持羽小裁计分协议" }
        val device = ThingHomeSdk.newDeviceInstance(deviceId)
        return object : ScoreConnection {
            private var closed = false
            override val cachedSnapshot = bean.getDps()?.get("101") as? String
            override val online get() = !closed && ThingHomeSdk.getDataInstance().getDeviceBean(deviceId)?.getIsOnline() == true
            init {
                device.registerDevListener(object : IDevListener {
                    override fun onDpUpdate(id: String?, dps: String?) {
                        if (closed || id != deviceId) return
                        val raw = runCatching { JSONObject(dps.orEmpty()).opt("101") as? String }.getOrNull() ?: return
                        events.snapshot(raw)
                    }
                    override fun onRemoved(id: String?) { if (!closed && id == deviceId) events.removed() }
                    override fun onStatusChanged(id: String?, value: Boolean) { if (!closed && id == deviceId) events.online(value) }
                    override fun onNetworkStatusChanged(id: String?, value: Boolean) { if (!closed && id == deviceId) events.online(value && online) }
                    override fun onDevInfoUpdate(id: String?) { if (!closed && id == deviceId) events.online(online) }
                })
            }
            override fun publish(wire: String, completed: (String?) -> Unit) {
                check(!closed) { "设备连接已关闭" }
                device.publishDps(JSONObject().put("110", wire).toString(), object : IResultCallback {
                    override fun onSuccess() { if (!closed) completed(null) }
                    override fun onError(code: String?, error: String?) { if (!closed) completed(code ?: "SDK_ERROR") }
                })
            }
            override fun close() {
                if (closed) return
                closed = true
                device.unRegisterDevListener()
                device.onDestroy()
            }
        }
    }
}

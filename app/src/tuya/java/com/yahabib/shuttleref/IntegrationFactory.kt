package com.yahabib.shuttleref
import android.app.Application
import android.content.pm.PackageManager
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.yahabib.shuttleref.core.Capabilities
object IntegrationFactory {
    private var initialized = false
    private val accounts = TuyaAccounts()
    // SDK 7.11.0 OptimusProvider registers Activity callbacks before explicit init.
    // Verified 7.11.0 prepare: context, event bus and lifecycle only; full init remains explicit.
    fun prepareApplication(application: Application) {
        com.thingclips.smart.android.base.ThingSmartSdk.prepare(application)
    }

    fun create(): Integration = object : Integration {
        override val demo = false
        override val description = "安卓开发测试版"
        override val capabilities = Capabilities(account = true, homeInvitation = true, score = true)
        override val identity = accounts
        override val homes = accounts
        override val score = TuyaScoreGateway()
        override fun initialize(application: Application): String {
            if (initialized) return "SDK 已初始化"
            Diagnostics.begin()
            Diagnostics.mark("读取 SDK 配置")
            val info = application.packageManager.getApplicationInfo(application.packageName, PackageManager.GET_META_DATA)
            val key = info.metaData?.getString("THING_SMART_APPKEY").orEmpty()
            val secret = info.metaData?.getString("THING_SMART_SECRET").orEmpty()
            check(key.isNotBlank() && secret.isNotBlank()) { "尚未配置 Android AppKey / AppSecret" }
            Diagnostics.mark("配置 SDK 日志开关之前")
            ThingHomeSdk.setDebugMode(false)
            Diagnostics.mark("ThingHomeSdk.init 调用之前")
            ThingHomeSdk.init(application, key, secret)
            Diagnostics.mark("ThingHomeSdk.init 已返回")
            initialized = true
            return "SDK 已初始化；尚未验证云端授权和登录"
        }
    }
}

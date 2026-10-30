package com.yahabib.shuttleref

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.yahabib.shuttleref.core.safeCrash
import java.security.MessageDigest
import java.time.Instant

class ShuttleRefApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
        IntegrationFactory.prepareApplication(this)
    }
}

object Diagnostics {
    private const val PREF = "local_diagnostics"
    private lateinit var context: Context
    private var previousStage = "无"
    private var previousTime = "无"
    private var stage = "未开始"
    fun install(application: Application) {
        context = application.applicationContext
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        previousStage = prefs.getString("stage", "无") ?: "无"
        previousTime = prefs.getString("time", "无") ?: "无"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordException(error) }
            if (previous != null) previous.uncaughtException(thread, error)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
    fun mark(value: String) {
        stage = value
        // Synchronous persistence is intentional: native abort cannot be caught by Kotlin.
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("stage", value).putString("time", Instant.now().toString()).commit()
    }
    fun begin() {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("crash").commit()
        mark("初始化开始")
    }
    fun recordException(error: Throwable) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("crash", safeCrash(error)).commit()
    }
    fun report(): String = buildString {
        appendLine("羽小裁 Android 本机诊断 v1")
        appendLine("包名：${context.packageName}")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("版本：${packageInfo.versionName} (${packageInfo.versionCode})")
        appendLine("系统：Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABI：${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("签名 SHA256：${signature()}")
        appendLine("上次进程阶段：$previousStage / $previousTime")
        appendLine("本次阶段：$stage")
        appendLine("Java 异常（不含消息）：")
        appendLine(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("crash", "未记录到；不代表未发生 native 崩溃"))
        if (Build.VERSION.SDK_INT >= 30) {
            appendLine("系统最近退出记录：")
            runCatching {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 3)
                if (exits.isEmpty()) appendLine("无可用记录")
                exits.forEach { exit ->
                    appendLine("${Instant.ofEpochMilli(exit.timestamp)} reason=${exit.reason} status=${exit.status} importance=${exit.importance}")
                    // Only native stack frames; exclude registers, memory dumps, abort messages and logcat.
                    exit.traceInputStream?.bufferedReader()?.use { reader ->
                        reader.lineSequence().take(1200).filter { it.trim().matches(Regex("#[0-9]+ +pc +[0-9a-fA-F]+ +.*")) }
                            .take(32).forEach { appendLine(it.trim().take(400)) }
                    }
                }
            }.onFailure { appendLine("系统退出记录暂不可读取：${it.javaClass.simpleName}") }
        } else appendLine("Android 11 以下无系统退出记录 API；可记录 Java 异常和最后阶段")
        appendLine("未采集账号、密钥、ticket、HTTP 内容或 SDK 原始日志。")
    }
    private fun signature(): String = runCatching {
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        signatures.orEmpty().joinToString(" / ") { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString(":") { "%02X".format(it) }
        }
    }.getOrDefault("读取失败")
}

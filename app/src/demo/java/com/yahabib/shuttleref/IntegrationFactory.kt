package com.yahabib.shuttleref
import android.app.Application
import com.yahabib.shuttleref.core.*
object IntegrationFactory {
    fun prepareApplication(application: Application) = Unit
    fun create(): Integration = object : Integration {
        override val demo = true
        override val description = "演示版 · 模拟数据不会连接涂鸦或上传服务器"
        override val capabilities = Capabilities(score = true)
        override val score = DemoScoreGateway()
        override fun initialize(application: Application) = "演示环境已准备"
        override fun previewPlayer() = Player("demo-player", "演示球友")
        override fun previewHomes() = listOf(Home(1, "开发者测试家庭", HomeRole.OWNER), Home(2, "第二测试家庭", HomeRole.MEMBER))
        override fun previewDevices(homeId: Long) = if (homeId == 1L) listOf(ScoreHost("demo-host", "羽小裁计分主机", Connectivity.ONLINE, true)) else emptyList()
    }
}

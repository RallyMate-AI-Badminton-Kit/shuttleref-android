package com.yahabib.shuttleref
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ShuttleRef() } }
}
@Composable private fun ShuttleRef(model: AppState = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val score by model.scoring.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> when(event) { Lifecycle.Event.ON_RESUME -> model.onForeground(); Lifecycle.Event.ON_STOP -> model.onBackground(); else -> Unit } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("球局", "战绩", "组局", "设备", "我的")
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF245648), background = Color(0xFFF6F7F2), surface = Color.White)) {
        Scaffold(bottomBar = {
            NavigationBar { tabs.forEachIndexed { index, title -> NavigationBarItem(selected = index == tab, onClick = { tab = index }, icon = { Text(listOf("◉", "▤", "◎", "▣", "○")[index]) }, label = { Text(title) }) } }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(tabs[tab], style = MaterialTheme.typography.headlineLarge)
                Text(model.integration.description, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodyMedium)
                when (tab) {
                    0 -> MatchPanel(model, state, score) { tab = 3 }
                    1 -> {
                        var period by rememberSaveable { mutableStateOf("周") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("周", "月", "年").forEach { label -> FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label) }) } }
                        Info("按${period}回顾", "战绩接口待接入，当前未加载比赛记录。登录后将与 iPhone 共用同一份云端数据。")
                    }
                    2 -> {
                        Info("约朋友打球", "邀请、报名和赛程将复用现有业务服务。安卓端尚未开放创建和加入操作。")
                        Info("比赛模式", "自由约球 · 单双打循环 · 八人转 · 四人转 · 亲子双打 · 淘汰赛 · 瑞士制 · 小组循环淘汰")
                    }
                    3 -> DevicesPanel(model, state, toAccount = { tab = 4 }, selectedDevice = score.device?.id, selectDevice = { model.selectScoreDevice(it); tab = 0 })
                    4 -> {
                        if (model.integration.demo) {
                            Info(state.player?.name ?: "演示账号", "模拟数据，不连接真实服务")
                            if (state.player == null) Button(onClick = model::enterDemo) { Text("体验模拟账号") }
                            else OutlinedButton(onClick = model::logout) { Text("退出模拟账号") }
                        } else AccountPanel(model, state)
                        var origin by remember(state.origin) { mutableStateOf(state.origin) }
                        OutlinedTextField(value = origin, onValueChange = { origin = it }, label = { Text("业务服务器") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { model.changeOrigin(origin) }, enabled = !state.busy) { Text("应用地址并清空当前会话") }
                        if (!model.integration.demo) Button(onClick = model::checkConnection, enabled = !state.busy) { Text("检查业务连接") }
                    }
                }
                if (tab == 4) DiagnosticPanel()
            }
        }
    }
}
@Composable fun Info(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun DiagnosticPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    Text("初始化问题诊断", style = MaterialTheme.typography.titleMedium)
    Text("如果刚才闪退，请重新打开本页，先读取并复制诊断，再尝试初始化。信息只保存在手机上，不自动上传。")
    OutlinedButton(enabled = !loading, onClick = {
        loading = true; copied = false
        scope.launch {
            report = withContext(Dispatchers.IO) { runCatching { Diagnostics.report() }.getOrElse { "读取失败：${it.javaClass.simpleName}" } }
            loading = false
        }
    }) { Text(if (loading) "正在读取…" else "读取上次退出与初始化诊断") }
    if (report.isNotEmpty()) {
        OutlinedButton(onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("羽小裁本机诊断", report))
            copied = true
        }) { Text(if (copied) "已复制，请粘贴发给开发者" else "复制诊断信息") }
        Text(report, style = MaterialTheme.typography.bodySmall)
    }
}

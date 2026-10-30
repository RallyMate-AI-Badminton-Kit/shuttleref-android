package com.yahabib.shuttleref

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahabib.shuttleref.core.*

@Composable fun MatchPanel(model: AppState, account: ScreenState, score: ScoreState, toDevices: () -> Unit) {
    var confirmation by remember { mutableStateOf<MatchAction?>(null) }
    if (model.integration.demo) Text("模拟比赛 · 不连接真实主机", color = MaterialTheme.colorScheme.primary)
    if (score.device == null) {
        Info("每一分，都值得认真", "选择家庭中的计分主机，然后开始比赛。")
        Text(score.message)
        Button(onClick = toDevices) { Text("选择计分主机") }
        return
    }
    val device = score.device
    val snapshot = score.snapshot
    Text(device.name, style = MaterialTheme.typography.titleLarge)
    Text(if (score.online) "主机在线" else "主机或手机离线", color = MaterialTheme.colorScheme.primary)
    // Rules have their own row, never overlaid on player names or point totals.
    Text("21 分 · 三局两胜", style = MaterialTheme.typography.labelLarge)
    val phase = when { snapshot?.phase == 'i' -> "准备开赛"; snapshot?.finished == true -> "本场已结束"; snapshot?.phase == 'p' -> "比赛已暂停"; snapshot?.phase == 'a' -> "比赛进行中"; else -> "等待同步" }
    Text(phase, style = MaterialTheme.typography.headlineSmall)
    if (!score.fresh) Text("当前显示为上次收到的比分，请同步后操作。", style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScoreCard("红方", snapshot?.scores?.get(0), snapshot?.scores?.get(2), Color(0xFFAC413F), Modifier.weight(1f))
        ScoreCard("蓝方", snapshot?.scores?.get(1), snapshot?.scores?.get(3), Color(0xFF356AA3), Modifier.weight(1f))
    }
    if (snapshot?.phase != 'i' && snapshot != null) Text("${if (snapshot.scores[4] == 0) "红方" else "蓝方"}发球", style = MaterialTheme.typography.bodyMedium)
    if (score.pending != null) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(score.message, color = MaterialTheme.colorScheme.primary)
    fun enabled(action: MatchAction) = !account.busy && action in score.allowed
    if (snapshot?.phase == 'a' && !snapshot.finished) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { model.matchAction(MatchAction.RED) }, enabled = enabled(MatchAction.RED), modifier = Modifier.weight(1f).height(72.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAC413F))) { Text("红方 +1", fontSize = 22.sp) }
            Button(onClick = { model.matchAction(MatchAction.BLUE) }, enabled = enabled(MatchAction.BLUE), modifier = Modifier.weight(1f).height(72.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356AA3))) { Text("蓝方 +1", fontSize = 22.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { model.matchAction(MatchAction.UNDO) }, enabled = enabled(MatchAction.UNDO)) { Text("撤回上一分") }
            OutlinedButton(onClick = { model.matchAction(MatchAction.PAUSE) }, enabled = enabled(MatchAction.PAUSE)) { Text("暂停比赛") }
        }
    }
    if (snapshot?.phase == 'i' || snapshot == null) Button(onClick = { confirmation = MatchAction.BEGIN }, enabled = enabled(MatchAction.BEGIN), modifier = Modifier.fillMaxWidth()) { Text("开始比赛") }
    if (score.saved != null && snapshot?.phase != 'a') {
        Info("已暂存的球局", "${score.saved.scores[0]} : ${score.saved.scores[1]} · 局数 ${score.saved.scores[2]} : ${score.saved.scores[3]}")
        Button(onClick = { confirmation = MatchAction.RESUME }, enabled = enabled(MatchAction.RESUME), modifier = Modifier.fillMaxWidth()) { Text("继续比赛") }
    }
    if (snapshot?.finished == true) Info("${if (snapshot.scores[2] >= 2) "红方" else "蓝方"}获胜", "主机已确认本场比赛结束。当前安卓版本尚未接入战绩保存。")
    OutlinedButton(onClick = { model.matchAction(MatchAction.QUERY) }, enabled = enabled(MatchAction.QUERY)) { Text("同步主机比分") }
    if (snapshot?.hasId == true || score.saved != null || snapshot?.scores?.take(4)?.any { it != 0 } == true) {
        TextButton(onClick = { confirmation = MatchAction.CLEAR }, enabled = enabled(MatchAction.CLEAR)) { Text("结束并清除比分", color = MaterialTheme.colorScheme.error) }
    }
    TextButton(onClick = toDevices, enabled = score.pending == null) { Text("查看／更换主机") }
    val action = confirmation
    if (action != null) AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when(action) { MatchAction.BEGIN -> "开始一场比赛？"; MatchAction.RESUME -> "继续这场比赛？"; else -> "结束并清除比分？" }) }, text = {
        Text(when(action) {
            MatchAction.BEGIN -> "将在 ${device.name} 上开始 21 分、三局两胜的比赛。"
            MatchAction.RESUME -> "将暂存比分恢复到 ${device.name}。主机确认后才能继续计分。"
            else -> "这会清除主机当前比分和本机暂存。安卓端目前不会把本场保存到战绩，请先确认不再需要保留。"
        })
    }, confirmButton = { TextButton(onClick = { confirmation = null; model.matchAction(action) }, enabled = enabled(action)) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } })
}
@Composable private fun ScoreCard(name: String, points: Int?, games: Int?, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .07f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, color = color, style = MaterialTheme.typography.titleLarge)
            Text(points?.toString() ?: "—", color = color, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            Text("局数 ${games?.toString() ?: "—"}")
        }
    }
}

package com.yahabib.shuttleref

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yahabib.shuttleref.core.*

@Composable fun AccountPanel(model: AppState, state: ScreenState) {
    var explain by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    if (state.sdkIdentity != null || state.logoutPending) {
        Info(state.player?.name ?: state.sdkIdentity?.name ?: "退出处理中",
            when { state.logoutPending -> "退出尚未完成，请重试退出后再切换账号"
                state.player != null -> "已登录羽小裁 · 家庭、设备和业务身份已连接"
                else -> "涂鸦账号已登录，业务连接待恢复" })
        state.player?.let { Text("身份编号 · ${it.id.takeLast(8)}") }
        Button(onClick = model::restore, enabled = !state.busy && !state.logoutPending) { Text("恢复登录连接") }
        OutlinedButton(onClick = { logout = true }, enabled = !state.busy) { Text("退出登录／切换账号") }
    } else {
        Info("欢迎使用羽小裁", "使用与 iPhone 相同的羽小裁账号和国家区号登录。新成员可以先注册，再通过家庭邀请码加入。")
        var method by rememberSaveable { mutableStateOf(LoginMethod.EMAIL_PASSWORD) }
        var action by rememberSaveable { mutableStateOf(AccountAction.LOGIN) }
        var account by rememberSaveable { mutableStateOf("") }
        var country by rememberSaveable { mutableStateOf("86") }
        // Credentials are ephemeral: never saved in instance state, preferences or diagnostics.
        var password by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        LaunchedEffect(method, action) { password = ""; code = "" }
        LaunchedEffect(method, action, account, country) { model.codeContext(method, action, account, country) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AccountAction.LOGIN to "登录", AccountAction.REGISTER to "注册", AccountAction.RESET_PASSWORD to "找回密码").forEach { (value, title) ->
                FilterChip(selected = action == value, enabled = !state.busy, onClick = { action = value }, label = { Text(title) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LoginMethod.entries.forEach { value ->
                FilterChip(selected = method == value, enabled = !state.busy, onClick = { method = value }, label = { Text(value.title) })
            }
        }
        OutlinedTextField(country, { country = it }, enabled = !state.busy, label = { Text("国家区号（中国大陆 86）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(account, { account = it }, enabled = !state.busy, label = { Text(if (method == LoginMethod.PHONE_CODE) "手机号" else "邮箱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val needsCode = method != LoginMethod.EMAIL_PASSWORD || action != AccountAction.LOGIN
        val needsPassword = method == LoginMethod.EMAIL_PASSWORD || action != AccountAction.LOGIN
        if (needsCode) {
            OutlinedTextField(code, { code = it }, enabled = !state.busy, label = { Text("验证码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { model.sendCode(method, action, account, country) }, enabled = !state.busy && state.consent && state.codeWait == 0 && method.valid(account.trim(), country.trim())) {
                Text(if (state.codeWait > 0) "${state.codeWait} 秒后重发" else "获取验证码")
            }
        }
        if (needsPassword) OutlinedTextField(password, { password = it }, enabled = !state.busy, label = { Text(if (action == AccountAction.RESET_PASSWORD) "新密码" else "密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row {
            Checkbox(state.consent, model::consent, enabled = !state.busy)
            TextButton(onClick = { explain = true }) { Text("我已阅读并同意登录数据说明") }
        }
        Button(onClick = { model.submit(method, action, account, country, password, code) },
            enabled = !state.busy && state.consent, modifier = Modifier.fillMaxWidth()) {
            Text(when(action) { AccountAction.LOGIN -> "登录羽小裁"; AccountAction.REGISTER -> "注册账号"; AccountAction.RESET_PASSWORD -> "更新密码" })
        }
        TextButton(onClick = model::restore, enabled = state.consent && !state.busy) { Text("刷新登录状态") }
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false }, title = { Text("登录数据说明 · 开发测试版") }, text = {
        Text("手机号、邮箱、密码和验证码由涂鸦 SDK 处理，用于注册及登录。登录成功后，羽小裁会将短期登录票据及身份指纹发送到当前业务服务器，校验身份并连接战绩与组局账号。\n\n应用会读取你有权限访问的家庭和设备列表。密码与票据不会写入本机诊断。退出后会清除本机业务会话。\n\n这是开发者测试版本，请仅使用你同意用于联调的账号。")
    }, confirmButton = { TextButton(onClick = { model.consent(true); explain = false }) { Text("同意并继续") } }, dismissButton = { TextButton(onClick = { explain = false }) { Text("暂不") } })
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("退出当前账号？") }, text = { Text("退出后可登录其他账号。云端的家庭、设备和战绩会保留。") }, confirmButton = { TextButton(onClick = { logout = false; model.logout() }) { Text("退出登录") } }, dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
}

@Composable fun DevicesPanel(model: AppState, state: ScreenState, toAccount: () -> Unit, selectedDevice: String?, selectDevice: (ScoreHost) -> Unit) {
    if ((state.sdkIdentity == null && state.player == null) || state.logoutPending) {
        Info("登录后查看家庭与设备", "家庭成员可以共用家庭内的设备，无需重新绑定主机。")
        Button(onClick = toAccount) { Text("前往登录") }
        return
    }
    if (!model.integration.demo) OutlinedButton(onClick = model::refreshHomes, enabled = !state.busy) { Text("刷新家庭与设备") }
    if (state.homes.isEmpty()) Info("暂无家庭", "已有家庭可以通过邀请码加入；也可以由 iPhone 端创建家庭后邀请你。")
    state.homes.forEach { home ->
        OutlinedButton(onClick = { model.selectHome(home) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(home.name + if (home.id == state.selectedHome?.id) " ✓" else "")
        }
    }
    state.selectedHome?.let { home ->
        Text("${home.name} · ${when(home.role) { HomeRole.OWNER -> "拥有者"; HomeRole.ADMIN -> "管理员"; HomeRole.MEMBER -> "成员" }}")
        if (state.devices.isEmpty()) Text("没有设备或尚未加载完成，可点击刷新核对。")
    }
    state.devices.forEach { device ->
        Info(device.name, "${when(device.connection) { Connectivity.ONLINE -> "在线"; Connectivity.OFFLINE -> "离线"; Connectivity.UNKNOWN -> "状态未知" }} · ${device.id.takeLast(8)}")
        if (device.scoringSupported) Button(onClick = { selectDevice(device) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedDevice == device.id) "返回计分" else "选择此计分主机")
        } else Text("此设备尚未提供计分能力", style = MaterialTheme.typography.bodySmall)

    }
    if (!model.integration.demo) {
        var invitation by rememberSaveable { mutableStateOf("") }
        Text("加入朋友的家庭", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(invitation, { invitation = it; model.clearInvitation() }, enabled = !state.busy, label = { Text("家庭邀请码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { model.previewInvitation(invitation) }, enabled = !state.busy && invitation.isNotBlank()) { Text("查看家庭邀请") }
        state.invite?.let { home ->
            Info("受邀家庭：${home.name}", "确认后加入该家庭，具体权限以家庭设置为准。")
            Button(onClick = model::joinInvitation, enabled = !state.busy && invitation.trim() == state.inviteCode) { Text("确认加入家庭") }
        }
    }
    Info("球拍与拍底", "安卓蓝牙适配尚未接入。")
}

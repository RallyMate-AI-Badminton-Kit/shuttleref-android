# 羽小裁 Android 框架交接

> 当前进度已到 0.3.0 基础比赛计分接入；前面的框架及 0.1.x 小节是历史记录，最新范围和验证结果见文末。

## 目标和范围

先让其他开发者能打开、编译和接续开发原生安卓工程。用户随后提供 Android_SDK.zip；提取到私有 `config/local/android-sdk/`，不提交到 Git。

工程：`source/android`，Kotlin + Compose，JDK 17，AGP 8.9.2，Gradle 8.11.1，compile/target SDK 35，min SDK 26。包名已由用户确认 `com.yahabib.shuttleref`。

SDK 下载包明确指定 `com.thingclips.smart:thingsmart:7.11.0`、fastjson 1.1.67.android、okhttp-urlconnection 3.14.9。配套 `security-algorithm-1.0.0-beta.aar`，不要混用旧 `t_s.bmp`。

## 已实现

- core JVM 模块：稳定身份、家庭/主机/拍底模型、身份/家庭/业务/BLE 接口。
- 上下文代次拦截旧请求；设备操作 ID 及 submitting/waiting/unknown/confirmed 等状态。
- Compose 五页导航；战绩周期入口；家庭切换与空态；账号/服务器配置界面。
- demo 独立 source set 和 `.demo` 包名，只有模拟数据，无 SDK 与后端通信。
- tuya source set：真实 SDK 初始化入口（主动点击），私有密钥配置、无凭据 HTTPS 业务连接探测。
- 独立 GitHub Actions 工作流 `android.yml`，仅构建演示 APK，无需私有 SDK/凭据；尚未推送或执行云 CI。

## 未完成，不能作为产品功能宣称

验证码注册登录和 ticket 获取/服务端兑换；真实家庭列表和邀请码；设备配网、监听、计分协议；战绩/组局 API；BLE v2；生产凭据持久化；完整隐私协议与权限页面；团队发布签名；Android/iOS 双机同步。

core 接口是初始边界，真实 SDK 接入仍需补登录用途（注册/登录）、错误映射、设备订阅生命周期等参数。操作状态机只是基础组件，尚未接入业务端的持久操作恢复。UI 全部显示当前能力状态，不提供假的成功回执。

## 需要平台登记的本机测试签名

Android AppKey/AppSecret 已由用户写入 `config/local/android.properties`（不要提交）。

本机 debug 签名 SHA256：

```
AA:C7:B1:ED:CA:4B:3D:ED:63:50:B6:D0:09:57:31:3C:5D:A9:90:47:82:B2:59:1D:78:CB:F9:A0:DB:79:E4:65
```

在现有羽小裁 SDK 应用「获取密钥 → Android → SHA256」登记。本机证书位于默认 `~/.android/debug.keystore`。其他开发者自行构建的签名可能不同，需要另外登记；不要覆盖原 iOS 配置或另建 Schema。

## 开发与验证

详见 `source/android/README.md`。本次临时工具安装于 `/private/tmp/shuttleref-jdk`、`/private/tmp/shuttleref-android-sdk`，临时缓存 `/private/tmp/shuttleref-gradle`。这些路径可能被系统清除，其他开发者应安装自己的 Android Studio/SDK。

核心单元测试 8 项全部通过。测试覆盖旧回调、未知结果重提限制、设备回执、同名 ID、服务器地址限制。实际 APK 构建结果见本文后续记录。未连接安卓真机，未验证真实账号/设备，不影响现有 iOS/服务端/固件。

## 本次构建结果

2026-10-29：`:core:test` 成功（8 tests，0 failures）；`:app:assembleDemoDebug :app:assembleTuyaDebug :app:signingReport` 成功。

- 演示包：`source/android/app/build/outputs/apk/demo/debug/app-demo-debug.apk`
- SDK 联调框架包：`source/android/app/build/outputs/apk/tuya/debug/app-tuya-debug.apk`

联调包可检查 SDK 初始化和服务连接，但仍不包含真实登录或家庭加入流程。签名指纹输出与上述值一致。没有安卓真机安装/运行结果，不宣称跨端同步通过。

## 0.1.1 初始化闪退诊断版

真机反馈：UI 正常，业务连接返回未登录的可达状态，点击 SDK 初始化闪退。尚无崩溃堆栈，不能认定为授权或依赖故障。

诊断版：`source/android/dist/shuttleref-0.1.1-diagnostic.apk`，versionCode 2，同包名同签名覆盖安装。新增本机诊断读取/复制按钮、SDK 调用前后持久阶段、未捕获 Java 异常记录、Android 11+ ApplicationExitInfo 退出原因与有限 native 栈。只记录异常类型与栈，不记录 Throwable 消息、登录凭据、HTTP 内容或 SDK 原始日志；不会自动上传。

操作：覆盖安装 → 我的 → 读取上次退出与初始化诊断 → 复制。如果上次无记录，点击初始化复现一次，重新打开 App 后先读取诊断，不要再次点初始化覆盖阶段。

验证：核心测试共 9 项通过，含异常/嵌套原因消息脱敏测试；demoDebug 和 tuyaDebug 构建成功。没有真机复现堆栈，闪退根因与修复仍待诊断返回。诊断版不是修复完成声明。

## 模拟器与 0.1.2 初始化修复

本机安装 Android Emulator 37.2.12、Google APIs Android 15 ARM64 r9（镜像官方 SHA1 `16f5bceca236b2737008977c4aaf826e46a8de7d` 校验通过），创建 Pixel 7 AVD `ShuttleRef_API_35`。双击 `tools/android/start-emulator.command` 启动。工具、JDK、AVD 位于私有 `config/local/android-env/`，ADB 使用 5038 端口，虚拟设备 `emulator-5554`。

模拟器复现了 0.1.1 初始化闪退：`OptimusLifecycle.onActivityCreated` 在显式初始化前调用用户单例，`SimpleUserStorage` 使用空 `ThingSmartSdk.getApplication()`；单例静态初始化失败后，初始化按钮启动的 `ThingThread #1` 触发 `NoClassDefFoundError` 并退出。不能误判为类文件缺失或签名认证错误。仅设置 ThingBaseSdk 上下文不足；实际空上下文属于 ThingSmartSdk。

最终修正：Application.onCreate 中调用该 SDK 版本的公开 `ThingSmartSdk.prepare(application)`（核对字节码：准备 Context、事件总线和生命周期）；完整 `ThingHomeSdk.init` 仍由按钮触发。不删除 SDK Provider，不提前提交登录/云认证，不吞后台异常。升级 SDK 时重新验证此初始化顺序。

0.1.2（versionCode 3）构建通过，9 项核心测试通过。在模拟器冷启动、打开我的、点击初始化后显示「SDK 已初始化；尚未验证云端授权和登录」，进程仍存活、当轮 crash buffer 为空。真实手机仍需覆盖安装复测；这不代表登录/云授权完成。

修正版：`source/android/dist/shuttleref-0.1.2-init-fix.apk`。签名与旧版一致。

## 0.2.0 基础账号接入（2026-10-29）

已实现：手机/邮箱验证码登录、邮箱密码登录，手机/邮箱注册、找回密码；首次数据说明；自动 ticket 服务端兑换；退出/恢复；家庭列表、邀请码预览后确认加入、家庭切换与设备读取。

复用 iOS 已验证的账号链路，不新增账号系统、不改后端。Android UID 来自 SDK，区域来自 `User.domain.regionCode`，指纹与 iOS 算法相同。请求体仅 ticket/fingerprint/name；业务用户 ID 由现有服务器返回。凭据不打印/不加入诊断。SDK 初始化前仍须 `ThingSmartSdk.prepare`，本轮没有改变已修复的顺序。

接口实现：`TuyaAccounts.kt`（SDK 身份/家庭）；`BusinessClient.kt`（固定 HTTPS origin、禁止重定向、ticket 兑换、会话检查/撤销）；`AppState.kt`（上下文/中间失败态/退出恢复）；`AccountViews.kt`（表单/家庭入口）；core `Account.kt`（身份指纹与会话接收规则）。

业务 token 当前仅内存持有，不落盘；冷启动依靠 SDK 缓存登录重新获取 ticket，服务端仍映射同一个业务用户。验证码及密码只存在于表单内存。账号请求超时可能仍由 SDK 完成；适配器会阻止并发身份变更，页面提供“刷新登录状态”。注销失败保留 pending 标记，重启先完成退出。业务撤销失败会提示，不能声称服务器已撤销。

构建：`:core:test :app:assembleTuyaDebug :app:assembleDemoDebug` 通过。核心测试 15 项（新增账号校验、跨区域身份指纹、未验证会话拒绝、过期空 token、切换账号/服务器及退出后旧结果隔离）。

**真实账号验证仍待用户操作**：不掌握用户验证码或密码，未代建真实账号、未改动真实家庭成员。需要 Android 登录原 iPhone 账号核对身份编号，再用新账号通过 iPhone 家庭邀请码加入核对设备。验证码投递和 ticket 上游授权均须真账号确认。

尚未实现：Android 生成/撤销家庭邀请码、创建家庭、设备添加/配网/推送/计分、战绩/组局接口页面、BLE、正式隐私政策、团队发行签名。不要将账号接入误读为所有业务已完成。

新包：`source/android/dist/shuttleref-0.2.0-account.apk`，versionCode 4，同包名同签名覆盖安装；目前未上传公开分发或推送云 CI。

本机模拟器 UI 冒烟通过：首次数据说明确认、空账号拒绝、SDK 初始化后的匿名状态恢复、HTTPS 业务检查返回“业务服务可达，尚未登录”；该轮 crash buffer 为空。模拟器已安装 0.2.0 并停留在账号页，等待用户自行登录。自动化脚本 `/private/tmp/android-account-smoke.py` 只验证匿名路径，不代表真实注册、ticket 兑换和家庭加入成功。

## 0.3.0 基础比赛计分（2026-10-29）

用户已反馈安卓同账号登录正常，要求继续接入基本开赛/计分。本轮只改 Android 工程和交接说明，没有修改服务端、iOS、固件或平台 DP。

新增 core `Match.kt`，按 iOS `MatchWire.swift`/`MatchSession.swift` 及 C `match_session.c` 复用固定 32 字符 Base64 DP110 和 DP101 回执。操作 B/r/b/U/P/R/A/Q；序号、UUID、结果码、预期状态匹配；缓存不等于新鲜，SDK publish 成功不等于执行成功；超时只同步，不重发得分。恢复额外拒绝固件明确不接受的已结束单局比分。

新增 Android `ScoreController.kt`（监听作用域、超时、后台/重连同步、本机暂存隔离）、`TuyaScoreGateway.kt`（真实 SDK DP transport）、`MatchViews.kt`（计分页及清除二次确认）。比赛活动或等待 ACK 时切账号/家庭/主机受阻，SDK 掉线/移除设备强制失效。`DemoScoreGateway.kt` 仅存在于 demo 构建，无法进入正式包。

SDK 7.11.0 特别注意：`DeviceBean.schemaMap/dps/name` 是废弃公开字段；getter 才读取新缓存和产品 schema。本次全部改用 getter 判断 DP101/110、读取名称和当前 DP。

能力边界：固定 21 分三局两胜；支持选主机、开始、红蓝加分、撤回、暂停继续、同步、结束清除。球员身份/拍底归队、正式组局接管、战绩上传及 MVP 不在本轮中。清除弹窗明确提示当前没有安卓战绩保存，勿当成完成结算。

核心测试 27 项通过，包含 C 离线 harness 回放（本机已实际运行，不是跳过）：开始 → 红/蓝得分 → 撤回 → 暂停 → 恢复 → 红方打完两局 → 清除。其它测试覆盖固定编码向量、无效 DP、乱序回执、并发点击、发布成功非确认、超时/发送异常、双手机序号冲突、错误球局/状态 ACK、断线/关闭后晚回调、暂存重建、序号耗尽及存储失败。

包：`source/android/dist/shuttleref-0.3.0-scoring.apk`，versionCode 5，包名/签名不变，可覆盖安装。

最终 tuya/demo 构建通过，APK 签名验证通过。模拟器独立 demo 包已完成选主机、同步、开赛、红蓝加分、撤回、暂停/继续、后台返回同步、清除的页面操作验证；没有向真实主机发送比赛命令。APK SHA256：`7c3436cd9a67f0f4229cde913f7799ad5873997b6414680bb691a4b32385e06b`。

待真实主机验收：Android 选同一家庭主机 → 同步 → 开赛 → 红/蓝加分与撤回 → 在 iPhone 核对比分 → 暂停/继续 → 后台返回同步 → 测试球局清除。不要用离线回放或 demo 结果代替真实 SDK/云/主机链路通过。

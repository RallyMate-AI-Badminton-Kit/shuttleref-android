# 羽小裁 Android

Kotlin + Jetpack Compose 原生安卓框架。包名 `com.yahabib.shuttleref`，Android 8+。

## 当前范围

- 五个页面入口：球局、战绩（周/月/年入口）、组局、设备、我的。
- `demoDebug`：独立包名 `.demo`，仅内存模拟账号/家庭/设备，无涂鸦 SDK、无服务端请求。
- `tuyaDebug`：真实 SDK 7.11.0；注册、手机／邮箱验证码登录、邮箱密码登录、找回密码、自动 ticket 业务登录、退出／恢复登录、家庭列表、邀请码预览并加入、设备列表。
- 身份、家庭与邀请、业务服务、BLE 发现四类接口在 `core/Contracts.kt`。
- 核心测试覆盖旧上下文回调隔离、同名不同 ID、HTTPS 地址约束、HTTP 接受不等于设备确认、未知结果不能重复提交。

**当前是基础账号与计分测试版，不是完整安卓客户端。** 设备配网、战绩与组局页面 API、拍底 BLE v2 待接入。已接入选主机、DP 状态监听、开赛/加分/撤回/暂停/恢复/清除。真实账号和 iOS 家庭共享仍需双机联调；页面不会生成假的登录成功或设备 ACK。

业务会话仅保存在内存，SDK 管理自己的登录缓存。进程重启后，在已同意登录数据说明的前提下，自动从 SDK 获取新 ticket 交给服务端验证，保持服务端原有用户 ID。App 不保存密码、验证码、ticket 或 SID，不用 UID 拼接冒充认证。备份已关闭。后续若持久化业务凭据，应使用 Android Keystore。

## 本机配置

安装 Android Studio（JDK 17），安装 Android SDK Platform 35、Build Tools 35.0.0。
用 Android Studio 打开本目录；`local.properties` 配置本机 `sdk.dir`（不提交）。

私有文件位于仓库 `config/local/`（已忽略）：

- `android.properties`，包含 `TUYA_APP_KEY` 和 `TUYA_APP_SECRET`，使用 **Android SDK 应用**密钥。
- `android-sdk/security-algorithm-1.0.0-beta.aar`，来自用户的 Android_SDK.zip。

密钥会按 SDK 要求打包进真实 APK；不等同于后端密钥，后端云密钥禁止打包。新版安全组件明确禁止混入 `t_s.bmp`。SHA256、包名、安全组件和 Android 密钥必须属于平台同一配置。演示版不需要这些私有文件。

```sh
./gradlew :core:test :app:assembleDemoDebug
./gradlew :app:assembleTuyaDebug :app:signingReport
```

APK 输出：`app/build/outputs/apk/demo/debug/` 或 `app/build/outputs/apk/tuya/debug/`。
初次构建需要访问 Google Maven、Maven Central 和涂鸦 Maven。自动生成的本机 debug 签名仅用于开发；多开发者各自编译时须登记各自签名 SHA256。统一分发 APK 后续应配置团队受控签名，不在 Git 中保存私钥。此阶段不自动上传 APK 或开放生产写入。

## 模块边界

- `core/`：纯 Kotlin 模型、接口、上下文代次、设备操作状态机，不引用 Android/SDK。
- `app/src/main/`：Compose UI、账号 ViewModel、HTTPS 业务会话客户端。
- `app/src/demo/`：模拟数据，只存在于 demo 构建。
- `app/src/tuya/`：ThingHomeSdk 初始化、TuyaAccounts 身份及家庭 gateway；私有密钥只从本机配置读取。

服务器默认为 `https://shuttleserver.wgine.com`。换地址清除上下文；只接受 HTTPS origin，不携带旧身份发请求。完整 SDK 初始化由首次同意后的账号操作触发，后续启动自动恢复。当前有开发测试数据说明；正式发行仍需完整隐私政策。蓝牙/位置能力未开放，不申请相应运行时权限。

## 接下来依次验证

1. 构建 tuyaDebug，将 `signingReport` 的 SHA256 登记平台。
2. Android 验证码注册/登录，确认与 iOS 使用同一 Schema、数据中心，验证同一账号身份。
3. 验证已接入的 Android ticket 获取与现有 `POST /api/social/session/ticket`，使用服务端验证身份。参照 iOS，不凭 UID/SID 拼接认定身份；不降级绕过验证。
4. 双手机验证家庭列表、邀请码预览/确认加入及设备读取；DP 监听另行实现。
5. 计分必须复用现有序号和设备确认协议；再接业务战绩/组局。
6. 拍底 BLE 以 `docs/handoff/2026-10-28-racket-ble-v2-firmware-interface.md` 为准，单独处理认证/关联/主机就绪，不凭扫描或连接判定成功。

现有 iOS、服务端、固件未因安卓框架修改。

## 来源

- 下载包 `7.11.0/thingsmart_home_sdk/dependencies.txt`（版本及依赖的首要依据）
- 下载包 `security-algorithm/README.md`（安全组件说明）
- https://developer.tuya.com/cn/docs/app-development/integrated?id=Ka69nt96cw0uj

## 本机模拟器

安装完成后，双击 `tools/android/start-emulator.command` 启动 `ShuttleRef_API_35`（Pixel 7 / Android 15 / ARM64）。工具和镜像位于 `config/local/android-env/sdk`，虚拟机数据位于同目录 `avd`，均不提交 Git。脚本固定模拟器端口 5554；若已有其他实例占用，请先关闭该实例。

终端安装 APK（模拟器开机后）：

```sh
config/local/android-env/sdk/platform-tools/adb -P 5038 -s emulator-5554 install -r source/android/dist/shuttleref-0.3.0-scoring.apk
```

模拟器适合页面、HTTP、账号和 SDK 初始化诊断；不能替代真实拍底 BLE 或特定手机系统兼容验证。

## 0.2.0 基础登录实现

- 普通用户路径：我的 → 选择登录／注册／找回密码 → 手机或邮箱 → 数据说明 → 提交。账号登录成功自动换取业务会话，无第二个网页授权步骤。
- ticket：SDK `tuya.m.user.sso.ticket.getbysid` v1.0 → HTTPS `POST /api/social/session/ticket`；region 使用 Android `User.domain.regionCode`，身份指纹与 iOS 一样为 SHA256(`region:uid`) 前 16 位十六进制。
- 业务登录失败保留 SDK 登录，可重试连接；不退回 UID/SID 自报身份或模拟数据。返回 401 清除业务会话；SDK 会话失效清除账号及家庭页面。
- 业务 token 不跨服务器使用；切换地址清空页面上下文。当前地址切换需等待正在提交的请求完成。
- 退出先清除业务状态并请求服务端撤销，再退出 SDK；退出未完成标记持久化，重启继续退出，不偷偷恢复旧账号。
- 家庭邀请码使用真实 `getInvitationFamilyInfo`（`groupId/groupName`）预览，经确认后 `joinHomeByInviteCode`，刷新真实家庭/设备列表。生成邀请码暂用 iPhone。
- 设备状态为读取时的快照，60 秒后或回前台先标记未知；刷新获取实际状态。尚不支持设备控制。
- 验证码请求预留 60 秒冷却（包含超时），按账号、区号及用途分别管理。SDK 登录/注册/退出串行，超时后不重叠发出另一笔身份变更。

SDK 方法依据：本机 7.11.0 JAR；参数核对官方 [邮箱账号文档](https://developer.tuya.com/en/docs/app-development/useremail?id=Ka6a99luv3tr1)、[手机账号文档](https://developer.tuya.com/en/docs/app-development/usermobile?id=Ka69pu4myrrws)。

## 0.3.0 基础比赛计分

入口：登录 → 设备 → 选择家庭 → 选择此计分主机 → 球局。SDK schema 具有 DP101/110 的设备才显示计分选择按钮；获取能力/名称/缓存比分使用 SDK 7.11.0 getter，避免读取已废弃公开字段。

- 支持固定 21 分、三局两胜：开始比赛、红/蓝 +1、撤回上一分、暂停、继续、结束并清除。
- DP110 与 iOS 完全相同：24 字节结构编码为 32 字符 Base64，完整 UUID、32 位大端序号；恢复附带比分/局数/发球方。DP101 `@1` 原子快照是唯一确认来源。
- 发送成功只代表提交；匹配操作序号、球局 ID、结果码和状态的主机回执才确认。等待期间禁止第二笔指令。12 秒无回执或发送异常显示结果未知，只能同步，不自动重发加分。
- 多手机序号冲突 code 3 读取主机新水位，再由用户同步；不抢占另一场比赛。不使用旧字符串 `point:red` 或额外 DP。
- 暂停比分、指令序号按服务器/SDK 身份/家庭/设备隔离保存；发指令前同步持久序号。启动缓存不表示新鲜状态，恢复/加分前必须收到查询确认。
- App 后台使比分失效，回前台查询；断线禁写、重连查询；换主机或退出时注销 SDK 监听。账号/主机上下文代次隔离旧回调。
- 等待确认或比赛进行中，切换账号/家庭/主机/服务器先要求暂停。设备被移除、家庭权限消失或 SDK 登录失效时强制断开旧会话。
- **当前没有自动保存战绩、MVP 结算或组局比赛接管**。清除前会明确提示。比赛规则仍由既有固件实现，安卓不自行计算真实比分。

真实 transport 只编译进 tuya 包；demo 包模拟主机有显著标识，仅供 UI 操作验证。核心状态机测试复用现有 C 计分核心做离线协议回放：

```sh
cc -std=c99 -I shuttleref/core/include source/linux/main.c shuttleref/core/src/score_engine.c shuttleref/core/src/match_session.c shuttleref/core/src/phone_command.c -o /tmp/shuttleref-match-harness
SHUTTLEREF_MATCH_HARNESS=/tmp/shuttleref-match-harness source/android/gradlew -p source/android :core:test
```

未设置 harness 路径时仅跳过 C 回放测试，其余 JVM 测试照常运行。SDK 对接参考 [官方设备管理](https://developer.tuya.com/en/docs/app-development/devicemanage?id=Ka6ki8r2rfiuu) 与本地 SDK JAR。

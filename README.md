# ShuttleRef Android · 羽小裁

<!-- team-handbook:start -->
> 团队协作：[成员入口](https://github.com/RallyMate-AI-Badminton-Kit/.github-private/blob/main/profile/README.md) · [开发与合并规范](https://github.com/RallyMate-AI-Badminton-Kit/.github-private/blob/main/handbook/workflow.md) · [负责人认领](https://github.com/RallyMate-AI-Badminton-Kit/.github-private/blob/main/handbook/ownership.md)
<!-- team-handbook:end -->


Kotlin + Jetpack Compose 原生 Android 基础客户端，独立维护账号、家庭、设备和基础计分链路。包名 `com.yahabib.shuttleref`，Android 8+。它目前不是 iOS 全功能移植版。

## 模块与功能

| 路径 / 构建 | 内容 |
| --- | --- |
| `core/` | 纯 Kotlin 账号、上下文代次、协议、比赛状态机和测试，不依赖 Android/Tuya SDK |
| `app/src/main/` | Compose 页面、账号状态、HTTPS 业务会话和计分控制 |
| `app/src/demo/` / `demoDebug` | 独立 `.demo` 包名的内存演示版本，无 Tuya SDK 或真实账号请求 |
| `app/src/tuya/` / `tuyaDebug` | Tuya SDK 7.11.0：账号、家庭、邀请、设备、DP 状态与控制 |
| `tests/support/` | 固定版本的 C 规则与 DP110/DP101 回放 oracle |
| `scripts/` | 独立验收和既有模拟器启动入口 |
| `config/examples/` | SDK 路径与私有配置占位模板 |

当前提供球局、战绩、组局、设备、我的五个入口；其中账号和基础设备计分已接入，完整战绩/MVP、组局 API、设备配网和拍底 BLE v2 尚未完成。源码迁移不扩大功能或对外宣称这些入口已可用。

真实计分流程为登录 → 设备 → 家庭 → 选择计分主机 → 球局。支持固定 21 分、三局两胜、红蓝加分、撤回、暂停、继续和清除。DP110 使用既有 Base64 协议，匹配 DP101 球局、序号、结果和状态后才确认；接口发送成功不能当成设备已执行。超时或断连后先同步原结果，不自动重发加分。

## 环境与独立验收

固定工程版本：JDK 17、Gradle 8.11.1（带 SHA256 校验的 wrapper）、AGP 8.9.2、Kotlin 2.1.20、compile/target SDK 35、Build Tools 35.0.0。用 Android Studio 打开本仓库根目录。

```sh
# 使用自己的 JDK 17 / Android SDK；也可由 Android Studio 配置。
export JAVA_HOME=/absolute/path/to/jdk-17
export ANDROID_HOME=/absolute/path/to/Android/sdk
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
bash scripts/check.sh
```

也可在根目录配置忽略的 `local.properties`，字段模板见 `config/examples/local.properties.example`。首次构建需能访问 Gradle、Google Maven、Maven Central；Tuya 构建还需要工程中声明的 SDK 仓库。

`check.sh` 会编译本仓 C harness，执行全新 JVM 测试和真实离线协议回放，再构建 Demo APK；结果检查禁止跳过 C 回放。全程不连接生产主机、安装 App、登录账号或操作拍底。

```sh
./gradlew :core:test :app:assembleDemoDebug
# 上述直接 Gradle 命令若没设置 SHUTTLEREF_MATCH_HARNESS，会跳过可选 C 回放；
# 完整验收请使用 scripts/check.sh。
```

Demo APK 位于 `app/build/outputs/apk/demo/debug/`。GitHub CI 运行相同完整验收，并仅保留短期 Demo 产物，不注入 Tuya 密钥。

## Tuya 私有配置

默认私有目录为本仓 `config/local/`，不再读取原单仓的 `../../config/local/`：

```text
config/local/
  android.properties
  android-sdk/
    security-algorithm-1.0.0-beta.aar
```

`android.properties` 字段见 `config/examples/android.properties.example`；实际 AppKey/AppSecret 必须来自现有 Android SDK 应用，AAR 由团队私下提供。本仓没有包含真实密钥、安全 AAR、debug/release 私钥或现场签名。

```sh
./gradlew :app:assembleTuyaDebug
# 也可复用另一个已维护的私有目录，无需把密钥搬到仓库：
./gradlew :app:assembleTuyaDebug -PshuttlerefPrivateDir=/absolute/private/config
```

覆盖目录应包含同样的 `android.properties` 与 `android-sdk/` 布局。SDK 按要求将 Android 应用凭据打包到 Tuya APK；后端云密钥不能打包。不要混入旧 `t_s.bmp`。包名、安全组件、Android 应用身份和平台登记的签名 SHA256 必须对应同一配置，不为迁移重新签发身份或修改 iOS Schema。

`tuyaDebug` 输出在 `app/build/outputs/apk/tuya/debug/`。自动 debug 签名只是开发签名；各开发者的证书可能不同，需要独立登记。正式分发签名由团队私下管理，不放入 Git。CI Demo 包不代表真实 SDK 登录或平台认可的签名已验收。

## 账号、服务和回执边界

服务器默认仍为 `https://shuttleserver.wgine.com`。更换 origin 会清空业务上下文，不跨服务器携带旧身份。SDK ticket 交由现有 `/api/social/session/ticket` 可信验证；不以 UID/SID 拼接伪造业务登录。

业务会话在内存中，SDK 管理其登录缓存。App 不保存密码、验证码、ticket 或 SID；备份已关闭。退出未完成会保留退出标记，重启继续退出。设备和比赛状态按账号、家庭、主机及服务器隔离；切换上下文、后台、断线时使旧结果失效，收到新快照后才能继续写入。

当前没有自动保存战绩、MVP 结算或完整组局比赛接管。真实注册、验证码、与 iOS 共享家庭、DP 往返和拍底 BLE 仍需要各自现场验收；演示 UI、离线回放和编译不代替这些能力。

## 模拟器与设备

已有命名 AVD `ShuttleRef_API_35` 时，可运行 `scripts/start-emulator.command`。它使用 `ANDROID_HOME`，兼容已有本地 `config/local/android-env/` 布局；否则使用 Android Studio 的默认 AVD 目录。脚本保留既有 ADB 5038、模拟器 5554 端口，运行前核对端口占用。

本次迁移没有启动模拟器、安装 APK、读取真实登录会话或覆盖现有签名。模拟器可验证 UI/账号适配，不能代替实体拍底 BLE。

## 来源

由原单仓 `source/android/` 拆至根目录，保留该组件唯一一次提交历史；应用与核心源码、包名、版本号和依赖版本不变。C oracle 是固定快照，正式规则源归 [shuttleref-edge](https://github.com/RallyMate-AI-Badminton-Kit/shuttleref-edge)，后续与其协调协议更新。

详见 [迁移记录](docs/migration/2026-11-04-status.md)、[来源哈希](docs/migration/provenance.json) 和 [DP 协议](docs/dp-protocol.md)。旧 README 与实机记录保留在 `docs/history/`，其中原单仓路径、临时工具目录和部署结论属于历史环境。

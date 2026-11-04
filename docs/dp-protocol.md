# 羽小裁 ShuttleRef · DP 协议规范

> 产品 PID：`sbim7kat6jrmwxyr`（12 条自定义 DP，101-112）
> 配套实现：`shuttleref/core/src/score_engine.c`（se_announce 为 score_realtime 的唯一生成源）

## 比分与状态（上行 ro）

### `score_realtime` (101, string, maxlen 64)

**= `se_announce()` 输出原文**，UTF-8，格式：

```
{红分}比{蓝分}，{红|蓝}方发球，{左|右}区[，平分|，局点|，场点]
```

- 示例：`5比3，红方发球，右区` / `20比20，蓝方发球，左区，平分` / `21比20，红方发球，右区，场点`
- 数字消费方请用 108/109，本 DP 为人眼展示（基础面板/分享卡片），**机器不解析**
- 最长情形 ≈ 51 字节，maxlen 64 安全

### `score_red` (108) / `score_blue` (109)

value 0-30，随每次得分更新；撤销后回退。

### `match_state` (102, enum)

| 值 | 语义 |
|---|---|
| `standby` | 待命（开机未开局） |
| `refereeing` | 裁判中（首次报到/开局口令后） |
| `replay_paused` | 回放暂停（三击/回放指令后） |
| `finished` | 已结束（比赛终局） |

### `ring_status` (112, string, maxlen 128)

```
{ring_id4hex}:{电量百分数}[,{ring_id}:{batt}...]   # 仅活跃戒指，按红蓝顺序
```

示例：`1111:90,2222:85,3333:80,4444:95`

## 下行指令（wr）

### `referee_cmd` (110, string) —— 统一命令入口

| 值 | 语义 | 对应端侧动作 |
|---|---|---|
| `undo` | 撤销上一分 | 同戒指长按（se_undo） |
| `resume` | 回放后继续 | 状态机恢复 |
| `reset_match` | 重置比赛 | se_init（比分及报到状态清零） |
| `replay` | 请求最近回合回放 | 同戒指三击 |
| `point:red:<seq>` | 手机临时为红方记一分 | 调用 `se_score_side`；`seq` 为 1–32 位字母、数字、`-` 或 `_`，近期重复序号不重复计分 |
| `point:blue:<seq>` | 手机临时为蓝方记一分 | 同上；戒指到位后仍由同一比分内核计算 |

手机记分命令只用于戒指到位前的联调。面板每次点击应生成新的 `seq`，连续给同一方记分也不能重用；收到同一 `seq` 的重传不再计分。最终比分以设备上报的 108/109 为准。

### `checkin_cmd` (111, string, maxlen 128) —— 小程序点选报到

```
red:{id},{id};blue:{id},{id}    # id 为 4 位 hex，不带 0x
```

示例：`red:1111,2222;blue:3333,4444`。整组下发（替换语义，非增量）。

### 其余

- `replay_cmd` (106)：**兼容预留**，统一入口为 110
- `voice_broadcast` (103, bool) / `volume` (104, 0-100) / `referee_mode` (105, `scorekeeper|cloud_referee`) / `team_bind` (107, 小程序生成的球队空间码)：自明

## BLE 侧（不经 DP，见需求文档 KD9）

戒指事件（双击/长按/三击/碰区报到）在 T5AI 端本地消化；DP 只承载结果与下行指令。
广播包结构见 `shuttleref/core/include/ring_protocol.h`（8B：ring_id+event+seq+batt+0xA5+crc8）。

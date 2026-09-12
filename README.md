# PureArena

PureArena 是一个面向 **comboPVP** 玩法的 Minecraft 竞技场子服插件，适用于 Paper 1.21.11，使用 Java 21 构建。

## 玩法定位

本插件的核心玩法为 **comboPVP**：玩家在竞技场内进行连续近战连击、走位和击退对抗。插件提供轻量、可配置的竞技场基础能力，不强制发放固定武器，玩家使用服务器已有的战斗物品和规则进行对战。

## 主要功能

- comboPVP 竞技场基础流程。
- 可配置水平、垂直击退和垂直速度上限。
- 玩家进入服务器或复活后传送到竞技场出生点。
- 玩家进入竞技场时自动选中快捷栏第一格。
- Y 坐标低于 70 格时自动判定死亡。
- CPS 动作栏显示当前 CPS 和当前连续攻击动作最高 CPS。
- 停止攻击 1 秒后动作栏清除，连续动作最高 CPS 清零。
- 每次挥动重置攻击冷却，适合 comboPVP 连击节奏。
- 挖方块挥臂不会计入 CPS，攻击实体和空气挥动按规则统计。
- CPS 超阈值检测与基础飞行、Reach 反作弊。
- 击杀后击杀者恢复满血并播放击杀音效。
- 每日 MVP 统计，每天服务器本地时间 0 点刷新。
- 右侧计分板显示当天 MVP、今日全服累计击杀和剩余玩家。
- 每日击杀数据异步写入 SQLite，服务器重启后恢复当天统计。
- 返回大厅按钮，支持冷却、回血、清空竞技场物资和 BungeeCord/Velocity 连接。
- 玩家死亡后自动复活，可配置复活延迟。

## 计分板占位符

在 `plugins/PureArena/config.yml` 的 `scoreboard.lines` 中可以使用：

| 占位符 | 含义 |
| --- | --- |
| `%date%` | 当前服务器本地日期 |
| `%mvp_name%` | 今日击杀最高的玩家 |
| `%mvp_amount%` | 今日全服累计击杀数 |
| `%kill_amount%` | 今日全服累计击杀数（与 `%mvp_amount%` 相同） |
| `%remain_player%` | 当前未淘汰玩家数 |
| `%cps%` | 当前 CPS |
| `%max_cps%` | 本次会话最高 CPS |

## 指令与权限

```text
/purearena help
/purearena reload
```

- `purearena.use`：基础帮助权限，默认所有玩家拥有。
- `purearena.admin`：重载配置权限，默认管理员拥有。
- `purearena.anticheat.bypass`：绕过基础反作弊，默认管理员拥有。

## 配置和数据

配置文件位于 `plugins/PureArena/config.yml` 和 `plugins/PureArena/messages.yml`。每日击杀数据库位于 `plugins/PureArena/stats.db`。

击杀统计按服务器本地日期保存。每天 0 点开始新的一天，旧日期数据不会参与当天 MVP 和计分板显示。SQLite 数据库使用参数化 SQL，数据库 I/O 在异步任务中执行。

## 安装

1. 将构建得到的 `PureArena-x.y.z.jar` 放入服务器的 `plugins` 目录。
2. 启动服务器生成默认配置。
3. 修改竞技场世界和出生点配置。
4. 使用 `/purearena reload` 应用可热重载配置。
5. 如需跨服返回大厅，请确认代理端已配置对应服务器名。

## 构建与测试

需要 Java 21 和 Gradle：

```shell
gradle clean test build
```

构建会运行单元测试，并在 Gradle 构建目录的 `libs` 子目录生成插件 JAR。

## 目标环境

- Paper 1.21.11
- Java 21
- SQLite JDBC 3.49.1.0（通过 `plugin.yml` 的 `libraries` 自动加载）

插件源码保留了 Paper/Folia 调度抽象，但当前项目交付以 Paper 1.21.11 的 comboPVP 服务器为主要测试目标；尚未进行真实 Folia 服务器运行验证。

## 许可证

GPL-3.0，详见 [LICENSE](LICENSE)。

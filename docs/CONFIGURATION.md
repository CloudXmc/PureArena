# 配置说明

配置文件位于服务器的 `plugins/PureArena/config.yml`，修改后使用 `/purearena reload`。

## 每日统计

每日击杀与 MVP 数据保存到 `plugins/PureArena/stats.db`。统计按服务器本地日期划分，每天 0 点开始新的一天。

## 计分板

- `%mvp_name%`：当天击杀最高玩家。
- `%mvp_amount%`：今日全服累计击杀数。
- `%kill_amount%`：今日全服累计击杀数。
- `%remain_player%`：当前剩余玩家数。

## 调度与兼容

插件使用 Paper 1.21.11 API 和统一调度器。数据库 I/O 在异步任务执行，玩家背包、计分板和实体状态更新回到对应实体上下文。


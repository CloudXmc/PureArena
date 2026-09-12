# PureArena

适用于 Paper 1.21.11 的纯净竞技场插件，使用 Java 21 与 Gradle 构建。

主要功能包括自定义击退、竞技场出生点、右侧计分板、每日 MVP 与 SQLite 击杀统计、CPS 显示、基础反作弊、自动复活、击杀回血和返回大厅按钮。

## 构建

```shell
gradle clean test build
```

构建产物位于 Gradle 构建目录的 `libs` 子目录。

## 运行依赖

- Paper 1.21.11
- Java 21
- SQLite JDBC（由 `plugin.yml` 的 `libraries` 自动加载）

## 许可证

GPL-3.0，详见 [LICENSE](LICENSE)。

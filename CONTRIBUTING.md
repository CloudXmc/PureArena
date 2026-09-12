# 贡献指南

## 开发环境

- Java 21
- Paper API 1.21.11
- Gradle

## 提交前检查

运行完整测试与构建：

```shell
gradle clean test build
```

请保持玩家可见文本位于资源文件中，并通过 `TaskScheduler` 调度涉及 Bukkit 实体的任务。


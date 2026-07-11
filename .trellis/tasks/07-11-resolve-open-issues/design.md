# Design

## Task Boundary

父任务只负责需求映射、跨子任务质量门和最终开放 issue 审计，不直接承载功能实现。

## Child Map

1. `07-11-issue-68-upstream-sync-audit`：CI/上游同步完成性。
2. `07-11-issue-102-markdown-table-gesture`：聊天 UI 手势冲突。
3. `07-11-issue-104-subagent-context-reuse`：子代理运行时缓存与复用。

## Integration Contract

- 子任务按可独立验证的边界启动、检查、提交和归档。
- 只有最后一个检查阶段可以运行全量 Gradle/安装，避免并行构建耗尽内存。
- #68 可先独立完成；#102 与 #104 的代码实现可分派，但最终统一做 app 安装验收。

## Rollback

- CI 验证失败不关闭 #68，保留 run URL 和错误证据。
- UI 或运行时回归时只回退对应子任务提交，不影响其他 issue。


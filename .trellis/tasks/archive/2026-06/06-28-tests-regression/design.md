# Design: Tests & Regression D

## 时序

```
A/B/C 编码中 ──> 代码合入 ──> D 跑针对性回归 ──> D 跑全量 test + lint + compile ──> 父任务合并
       │
       └──（并行）D 先写测试骨架 / mock
```

## 测试矩阵

| 子任务 | 测试文件 | 关键断言 |
|--------|----------|----------|
| A | `SubagentStreamingConsistencyTest.kt` | 清理后 `text` JSON `streaming=false` 且 `metadata.subagent_streaming=false` |
| B | `WorkspaceShellPolicyTest.kt`（扩充） | 6+ 拒绝、4+ 允许（含 `grep shutdown log.txt` 反例） |
| C-3 | `ConversationRepositorySyncTest.kt` 或 `*SyncOpsTest.kt` | 同 id 覆盖、删孤儿、新增 |

## 全量回归命令

```bash
.\gradlew test --no-daemon                    # 所有模块 JVM 单测
.\gradlew lint --no-daemon                    # Android Lint
.\gradlew :app:compileDebugKotlin --no-daemon # 最终编译门
```

失败处理：
- 单测失败 → 报告给对应子任务（A/B/C），D 不自行修业务代码。
- lint 新增 error → 报告；warning 记录但放行。
- 编译失败 → 报告，**不**继续装机。

## 回归报告

`.trellis/tasks/06-28-tests-regression/research/regression-report.md`：
- 跑了哪些 `./gradlew` 任务。
- 每个 `*Test` 类的通过/失败数。
- lint 结论（error 数、warning 数）。
- 编译结论。
- 结论：是否可进入父任务合并阶段。

## 与父任务的衔接

D 通过后，父任务执行：
1. `git diff --stat release/rikka-arsucar...HEAD` 审查。
2. `.\gradlew :app:installDebug --no-daemon` 装机。
3. 提醒用户 commit。

## 风险

- **中**：Room 集成测试基建可能缺失 → C-3 走降级路径，D 需对应跑降级后的纯函数测试。
- **低**：lint 历史 warning 不阻塞。

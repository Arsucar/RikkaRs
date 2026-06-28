# PRD: Tests & Regression D — Cover A/B/C and Final Gate

## 目标

为 Blocker A / B / C 的修复补测试、跑全量回归、确认编译与 lint 通过；**不**直接改业务代码（除非测试本身需要可见性调整）。

## 依赖

- **依赖** A 代码就绪 → 才能跑 `SubagentStreamingConsistencyTest`。
- **依赖** B 代码就绪 → 才能跑扩展后的 `WorkspaceShellPolicyTest`。
- **依赖** C 代码就绪 → 才能跑 `*Sync*` 测试与 LogPage 编译。
- 测试**编写**可与 A/B/C 并行（先写骨架，mock 行为）；**运行**必须在 A/B/C 代码合入后。

## 验收标准

1. **AC-1**：Blocker A 的新单测存在并通过（`SubagentStreamingConsistencyTest` 或同名）。
2. **AC-2**：Blocker B 的扩展测试存在并通过（`WorkspaceShellPolicyTest` 新增 ≥10 条用例）。
3. **AC-3**：Blocker C 的 `syncMessageNodes` 测试存在并通过（Room 集成或降级纯函数）。
4. **AC-4**：`.\gradlew test --no-daemon` 全量通过（所有模块）。
5. **AC-5**：`.\gradlew lint --no-daemon` 无新增 error（warning 可接受）。
6. **AC-6**：`.\gradlew :app:compileDebugKotlin --no-daemon` 通过（D 作为最终编译门）。
7. **AC-7**：输出回归报告到 `.trellis/tasks/06-28-tests-regression/research/regression-report.md`：列明跑了哪些测试、通过/失败、lint 结论。

## 约束

- 不改业务代码（A/B/C 的实现）；仅允许为测试可见性做最小调整（如 `internal` 可见性）。
- 不引入新测试框架（用项目现有 JUnit / AndroidX Test）。
- 测试命名遵循 `*Test.kt`（AGENTS.md）。
- `--no-daemon` 必须带。

## 不在本任务范围

- 修复 A/B/C 实现缺陷（那是 A/B/C 子代理的职责；D 只报告）。
- 新增 e2e / instrumented 测试（除非现有测试基建已支持且成本合理）。
- 性能基准测试。

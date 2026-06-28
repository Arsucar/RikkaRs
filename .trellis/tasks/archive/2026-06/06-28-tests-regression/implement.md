# Implement: Tests & Regression D

> 子代理派发时 prompt 必须以 `Active task: .trellis/tasks/06-28-tests-regression` 开头。

## 阶段 1：并行编写测试骨架（A/B/C 编码时可并行）

- [ ] **1.1** 确认 A/B/C 子代理已开始；D 不阻塞，先准备回归报告模板。
- [ ] **1.2** 若 A/B/C 测试由其各自子代理编写（推荐），D 仅准备**运行**与**报告**；若 A/B/C 未自带测试，D 负责补。
- [ ] **1.3** 准备 `.trellis/tasks/06-28-tests-regression/research/regression-report.md` 模板。

## 阶段 2：针对性回归（A/B/C 代码就绪后）

- [ ] **2.1** `.\gradlew :app:testDebugUnitTest --tests "*SubagentStreaming*" --no-daemon`（A 的测试）。
- [ ] **2.2** `.\gradlew :workspace:test --no-daemon`（B 的测试）。
- [ ] **2.3** `.\gradlew :app:testDebugUnitTest --tests "*Sync*" --no-daemon`（C-3 的测试）。
- [ ] **2.4** 失败 → 报告给对应子任务，不自行修业务代码。

## 阶段 3：全量回归与编译门

- [ ] **3.1** `.\gradlew test --no-daemon`（所有模块）。
- [ ] **3.2** `.\gradlew lint --no-daemon`（仅记录新增 error）。
- [ ] **3.3** `.\gradlew :app:compileDebugKotlin --no-daemon`（最终编译门；D 是最后一个子代理，负责完整编译）。

## 阶段 4：回归报告

- [ ] **4.1** 填写 `regression-report.md`：每个测试类通过/失败、lint 结论、编译结论。
- [ ] **4.2** 结论：可合并 / 需 A/B/C 修复后重跑。
- [ ] **4.3** 通知父任务（本会话主代理）进入合并阶段。

## 回滚点

- D 不改业务代码，无独立回滚。
- 若回归全失败 → 父任务决定整体 revert 或逐子任务修。

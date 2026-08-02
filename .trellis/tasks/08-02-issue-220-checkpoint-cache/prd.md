# issue-220: 生成中检查点缓存（实验性）

## Goal

实验性功能（默认关）：主对话长任务生成期间，每 N 步工具调用后将当前 `MessageNode` 树落盘检查点；进程被杀重启后从最近检查点恢复，最多丢 N 步工具结果。设置项：开关 + 间隔（4/8/16/32，默认 8）+ 恢复提示。

对应 GitHub issue: #220。与 #219 互补（保活降被杀概率 / 检查点兜底恢复），无硬依赖。

## Requirements

### R1 默认关闭零行为变化
- `enableCheckpointCache=false` 时落盘时点与现状完全一致（发送/审批/完成兜底/取消/成功等既有点）。

### R2 周期性检查点
- 开启后，`(stepIndex - lastCheckpointStep) >= N` 触发一次检查点落盘。
- 落盘复用或精简 `saveConversation`；评估 FTS 全量重建开销，过高则检查点路径跳过/延迟 FTS。
- 异步 + 现有 `persistenceMutex` 串行；失败静默降级（仅日志），不阻断生成。

### R3 步数来源
- `GenerationHandler` 已有 `stepIndex`（`for (stepIndex in 0 until maxSteps)`）；`GenerationChunk.Messages` 目前不带 step 号。
- 实现任选：chunk 暴露 `stepIndex`，或 ChatService 侧按工具步计数。须可单测。

### R4 恢复
- `hydrateConversationFromDb` 读最近快照；若为检查点（非完成态）进入对话时提示「已从检查点恢复（第 X 步），后续 Y 步结果已丢失」。
- 完成态快照正常打开不提示。

### R5 设置 UI
- 设置页实验开关 + 间隔选择；关时间隔置灰（`enabled=false` + alpha 0.38，仿 AssistantMemoryPage）。
- 字段：`enableCheckpointCache: Boolean = false`，`checkpointStepInterval: Int = 8`。
- PreferencesStore **transform 原子写**（#202）。

### R6 元数据
- 记录检查点步数 / 是否完成态，供恢复提示区分。

## Constraints

- 不每步写（成本：nodes JSON + syncMessageNodes + FTS）。
- 与审批暂停/继续、取消、流式、多对话并发共存。
- 新字符串中英本地化。
- 本开关不依赖 #215；独立设置项即可（可放设置→实验/高级区；无实验区则新建轻量区块）。

## Acceptance Criteria

- [ ] AC1 默认关；关时落盘行为与现状一致。
- [ ] AC2 开且 N=8，≥8 步工具任务有周期性落盘（日志/DB 次数 ≈ 步数/N）。
- [ ] AC3 模拟杀进程后恢复到最近检查点，提示丢失步数，数据不损坏。
- [ ] AC4 N∈{4,8,16,32}；关时间隔置灰。
- [ ] AC5 写入失败不影响生成。
- [ ] AC6 与审批/取消/流式共存，无 revision 冲突损坏。
- [ ] AC7 恢复提示仅检查点恢复时出现。
- [ ] AC8 新字符串中/英本地化。
- [ ] AC9 最终 installDebug 真机验收（含杀进程矩阵抽样）。

## Out of Scope

- 增量只追加工具结果的新表方案（列为后续）。
- 世界书/变量无关。
- 不实现 #219 FGS。

## Notes

- 现状：`ChatService.saveConversation` ~L2659；落盘点含 L602/728/878/911/931 等；`hydrateConversationFromDb` ~L3325；`GenerationHandler` stepIndex L167+；`SubagentContextCache` 可参考异步+revision 范式。

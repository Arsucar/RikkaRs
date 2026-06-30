# feat: GenerationHandler finish_work 元工具

## Goal

为子代理（explore/coder/reviewer）的工具循环引入显式收敛机制 `finish_work`，让模型在自认为任务完成时主动终止循环，避免不必要的额外工具调用浪费 token 和延迟。

## Background

当前 `GenerationHandler.generateText`（`app/.../GenerationHandler.kt`）以 `for (stepIndex in 0 until maxSteps)` 循环：模型生成 → 执行工具 → 结果写回 → 下一轮。循环仅在以下情况终止：
1. 模型无工具调用（自然结束）
2. 工具需审批 → break 等用户
3. stepIndex == maxSteps → 硬截断

子代理 maxSteps：explore=48, coder=64, reviewer=24。模型在简单任务上倾向"做得更多以确保完成"，导致 3-4 步即可完成的任务实际执行 10+ 步。

## Confirmed Facts

- `GenerationHandler.kt` 循环：`for (stepIndex in 0 until maxSteps)`，无提前收敛信号
- `spawn_subagent` → `SubagentHost.runToCompletion` → 嵌套 `generateText(maxSteps = profile.maxSteps)`
- 子代理 systemPrompt 要求"不要未完成"，但未定义"什么时候算完成"
- 工具定义在 `ai/Tool.kt`：`name`、`execute`、`systemPrompt`、`needsApproval`
- 子代理工具由 `SubagentPermissionBuilder.buildSubagentTools` 组装
- 子代理 maxSteps 来自 `SubagentProfile`（内置 profile 在 `SubagentRegistry.kt`）

## Requirements

1. **引入 `finish_work` 元工具**：无参数，调用后立即终止当前工具循环并返回当前 assistant 文本
2. **Tool output**：`finish_work` 返回固定文本 `"Task completed."`
3. **System Prompt 注入**：子代理 systemPrompt 明确告知模型应在任务完成时调用 `finish_work`
4. **循环检测**：`GenerationHandler` 执行工具后检查 `toolName == "finish_work"`，若是则 break
5. **主代理可选**：主代理也可使用 `finish_work`，但不强制启用
6. **倒计时机制暂不实现**：架构上预留扩展空间，v1 先观察 `finish_work` 效果

## Acceptance Criteria

- [ ] 子代理生成时 `finish_work` 工具可用且无需审批
- [ ] 模型调用 `finish_work` 后循环立即终止，返回当前 assistant 文本作为总结
- [ ] `finish_work` tool output 为 `"Task completed."`
- [ ] 子代理 systemPrompt 包含 finish_work 使用指引
- [ ] `finish_work` 未被调用时，现有行为不变（自然结束或 maxSteps 截断）
- [ ] 主代理调用 `finish_work` 也正常终止循环
- [ ] 现有测试通过

## Out of Scope

- 不修改 `maxSteps` 默认值
- 不引入"完成标准"契约式设计
- 不改动主代理默认行为（仅新增可选工具）
- v1 不实现倒计时机制

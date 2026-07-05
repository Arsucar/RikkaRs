# Simplify subagent fields

## Goal

精简 subagent 配置、主代理管理工具 schema、spawn 返回 payload 中暴露的字段，降低用户设置页和主代理上下文里的概念噪音。

核心方向：

- 用户可见配置只保留常用、可解释、可操作的字段。
- 主代理可写字段只保留创建/调整子代理所需的最小集合。
- spawn 返回给主代理的 JSON 只保留决策所需字段，内部审计字段放入 metadata 或仅在 UI 内部使用。
- 精简底层数据模型，删除不再需要的 `displayName` 与 `maxSteps` 字段；预算只由工具调用次数控制。

## Confirmed Facts

- `SubagentProfile` 当前内部字段较多，包括模型参数、工具继承、本地工具、MCP、技能、记忆、输出、预算等配置。
- `manage_subagent_profile` 当前暴露给主代理的字段包括：
  `action`, `name`, `display_name`, `description`, `system_prompt`, `model_id`, `inherit_tools`, `local_tools`,
  `enabled_skills`, `mcp_server_ids`, `excluded_tools`, `max_tool_calls`, `disable_tool_budget_stop`,
  `stream_output`, `enable_memory`, `temperature`, `top_p`, `max_tokens`。
- `spawn_subagent` 当前 slim payload 暴露：
  `profile_name`, `summary`, `succeeded`, `error`, `max_steps`, `max_tool_calls`, `tool_loop_steps`,
  `truncated`, `transcript_size`, `usage`。
- 近期决策：用户不希望直接暴露“最大步数”概念；预算语义应以“最大工具调用次数”为主。
- 近期决策：保留“禁用工具预算截断”功能。
- 近期决策：底层删除 `displayName` 和 `maxSteps`，不再保留两套预算字段。
- 近期决策：主代理管理 schema 不暴露模型采样参数、工具继承、本地工具、技能、MCP、记忆和输出等高级字段。

## Requirements

### R1: 精简用户可见字段

设置页默认可见字段应聚焦：

- 基本信息：名称 / 描述
- 行为提示：system prompt
- 模型选择：model
- 预算：最大工具调用次数
- 预算策略：禁用工具预算截断
- 权限：工作区访问权限

其他高级字段需要隐藏、移入高级区，或暂不在当前任务处理，避免默认界面过载。

### R2: 精简主代理可写 schema

`manage_subagent_profile` 暴露字段应优先收敛到：

- `action`
- `name`
- `description`
- `system_prompt`
- `model_id`
- `max_tool_calls`
- `disable_tool_budget_stop`

候选移除或隐藏字段：

- `display_name`
- `temperature`
- `top_p`
- `max_tokens`
- `stream_output`
- `enable_memory`
- `inherit_tools`
- `local_tools`
- `enabled_skills`
- `mcp_server_ids`
- `excluded_tools`

移除仅指从主代理工具 schema 中移除；内部模型和 UI 高级能力可保留。

### R3: 精简 spawn 返回 payload

`spawn_subagent` 返回给主代理的 slim payload 应优先保留：

- `profile_name`
- `summary`
- `succeeded`
- `error`（仅失败时）
- `max_tool_calls`
- `truncated`

候选从 slim payload 移除、保留在 metadata 或仅内部使用：

- `max_steps`
- `tool_loop_steps`
- `transcript_size`
- `usage`

UI 需要的 transcript、steps、tool calls、streaming 状态可继续放 metadata，不直接污染主代理可读文本。

### R4: 底层字段删除与预算收敛

- 删除 `SubagentProfile.displayName`，UI 展示使用 `name` 或 `description`。
- 删除 `SubagentProfile.maxSteps`，不再用“最大步数”控制预算。
- 删除 `SubagentResult.maxSteps` 及相关 `max_steps` 输出。
- 子代理循环预算只由 `maxToolCalls` / `max_tool_calls` 控制。
- 保留 `disableToolBudgetStop`，用于允许工具次数达到上限后继续执行。

### R5: 兼容性

- 旧数据中多余的 `displayName` / `maxSteps` 字段应被序列化层忽略，不阻塞读取。
- 不破坏已有 subagent 内置 profile 默认值。
- 不破坏 UI 展示 subagent transcript / progress 的 metadata 读取。

## Acceptance Criteria

- [ ] 设置页默认路径不再同时暴露多个预算概念。
- [ ] 底层 `SubagentProfile` / `SubagentResult` 不再定义 `displayName` 和 `maxSteps`。
- [ ] `manage_subagent_profile` schema 字段数量明显减少，且不再暴露低频高级字段。
- [ ] `spawn_subagent` slim payload 不再包含主代理无需决策的审计字段。
- [ ] subagent UI transcript/progress 仍可正常显示。
- [ ] 旧 profile 数据仍可反序列化。
- [ ] `./gradlew :app:compileDebugKotlin --no-daemon` 通过。
- [ ] subagent 相关单测通过。

## Out of Scope

- 重做 subagent 设置页整体信息架构。
- 改动非 subagent 的工具系统。
- 改动模型 provider 参数语义。

## Open Questions

- None.

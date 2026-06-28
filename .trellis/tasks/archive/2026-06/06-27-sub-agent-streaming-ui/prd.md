# 子代理流式输出与think/summary块UI

## Goal

子代理执行期间，用户可在聊天界面实时看到子代理的思考（reasoning）、工具调用（tool call）、文本输出（text）的增量更新，而非等待整个子代理完成后才看到结果。借鉴 rikkahub-sub 分支的 metadata 方案实现。

## Confirmed Facts

- 本地 `SubagentHost.spawn` 已支持 `onProgress` 回调（L45, L198–200），但 `ChatService.buildSubagentToolsForChat` 未传入
- 本地 `SpawnSubagentToolUI` 仅解析完成后的 `SubagentResult` JSON，无流式状态
- sub 分支方案：将子代理中间状态写入 `UIMessagePart.Text.metadata`（key: `subagent_transcript`, `subagent_streaming`, `subagent_profile`, `subagent_steps`, `subagent_succeeded`），UI 从 metadata 读取实时渲染
- 已有 `ThinkTagTransformer` 仅支持单 `考量` 块提取，sub 分支支持多块
- 当前无 `ToolCallId` 上下文隔离，并行子代理会互相覆写 progress

## Requirements

### R1: ChatService 传入 onProgress 回调

- `buildSubagentToolsForChat` 在 `spawn` 调用时传入 `onProgress`，回调内调用 `updateSubagentProgress`
- 新增 `updateSubagentProgress(conversationId, toolCallId?, profileName, subMessages)` 私有方法
- `updateSubagentProgress` 构建 metadata 并更新父对话中对应 tool part 的 output

### R2: onProgress 节流

- 对 `onProgress` 做时间节流（最小间隔 120ms）+ 结构签名跳过（assistant parts 数量变化时立即发射）
- 避免 flow 背压：使用 `progressScope.launch` 异步发射

### R3: 并行子代理 toolCallId 隔离

- `GenerationHandler` 增加 `ToolCallIdElement` + `currentToolCallId()` / `withToolCallId`
- `executeSingleTool` 包裹 `withToolCallId(tool.toolCallId)`
- `updateSubagentProgress` 按 `toolCallId` 匹配目标 tool part

### R4: SpawnSubagentToolUI 流式渲染

- 卡片读取 `metadata["subagent_transcript"]` 渲染 `ChainOfThought` 风格时间线（Thinking 步骤带 Sparkles icon）
- 流式期间：标题显示 profile name + loading 动画；步骤计数实时更新
- 完成后：回落到现有 `SubagentResult` JSON 解析，显示 summary + 展开式 transcript
- 流式 → 完成的状态切换平滑过渡（无闪烁）

### R5: ThinkTagTransformer 多块支持

- 单个 Text part 中存在多个 `考量...考量` 块时，全部提取为独立 `UIMessagePart.Reasoning`
- 流式期间 `finishedAt = null`，闭合标签出现时设置 `finishedAt`

### R6: 数据模型适配

- `SubagentTranscriptStep.ToolCall` 增加 `executed: Boolean` 字段（区分 in-flight vs done）
- `SubagentTranscriptStep.ToolCall` 增加 `childTranscript` 字段（嵌套子代理 transcript）
- `SubagentTranscriptStep.Reasoning` 增加 `createdAt: Long?` 字段
- `UIMessagePart.Text` 现有 `metadata: Map<String, String>` 已够用，无需新增 subtype

## Acceptance Criteria

- [ ] AC1: 子代理执行中，聊天界面卡片实时展示 thinking / tool call / text 步骤（每 120ms–250ms 可见更新）
- [ ] AC2: 两个并行子代理同时执行时，各自卡片独立更新，互不干扰
- [ ] AC3: 子代理完成后，卡片从流式状态平滑切换到完成态（summary + 可展开 transcript）
- [ ] AC4: 包含多个 `考量` 块的模型回复，每个块独立显示为 Reasoning 步骤
- [ ] AC5: 无子代理或 `streamOutput=false` 时，行为与当前完全一致（无回归）
- [ ] AC6: metadata 中的 transcript 不被发送给 AI 模型（仅 UI 用途）

## Out of Scope

- 子代理 summary 生成逻辑变更（沿用现有 summary continuation）
- 子代理 AI 日志系统（独立任务）
- `ask_btw` 工具的流式改造

## Dependencies

- 无外部子任务依赖，优先实施
- 其他子任务依赖本任务的 metadata schema 稳定

## Open Questions

- 无

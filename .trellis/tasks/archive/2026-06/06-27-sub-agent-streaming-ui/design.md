# 子代理流式输出与think/summary块UI — 技术设计

## Architecture & Boundaries

### 核心机制

子代理流式输出复用父对话消息流。实现分三层：

1. **运行时层** — `SubagentHost` 已有 `onProgress` 回调；需在 `ChatService` 传入并实现 `updateSubagentProgress`
2. **数据层** — 进度信息写入 `UIMessagePart.Text.metadata`（`JsonObject?`），不复用 `text` 字段（保留给 model-facing JSON）
3. **UI层** — `SpawnSubagentToolUI` 优先读 `metadata["subagent_transcript"]`；无 metadata 时回落到现有 `SubagentResult` JSON 解析

### 不新增 UIMessagePart 子类型

流式状态完全通过 tool output `Text` 的 `metadata` 表达，不引入新的 sealed variant。这保证：
- 数据库 schema 不变
- 序列化/反序列化逻辑不变
- provider 层不受影响（metadata 不发送给 AI）

---

## Data Flow

### 流式进度流

```
SubagentHost.runToCompletion
  → onProgress(throttled, List<UIMessage>)
    → ChatService.updateSubagentProgress(conversationId, toolCallId, profileName, subMessages)
      → SubagentHost.buildTranscript(subMessages, truncateToolOutput = 2000)
      → build metadata JsonObject
        { "subagent_transcript": [...steps...],
          "subagent_streaming": true,
          "subagent_profile": "Researcher",
          "subagent_steps": N,
          "subagent_succeeded": false }
      → updateConversationState(conversationId) { conv ->
          patch last ASSISTANT message's Tool part
          where toolName == "spawn_subagent" && toolCallId matches
          → part.copy(output = listOf(Text(text = partialJson, metadata = transcriptMetadata)))
        }
      → UI recompose from StateFlow
```

### 完成时替换

```
SubagentTools.execute returns
  → listOf(UIMessagePart.Text(text = fullSubagentResultJson, metadata = finalMetadata))
    where finalMetadata = {
      "subagent_transcript": [...full steps...],
      "subagent_streaming": false,
      "subagent_profile": "Researcher",
      "subagent_steps": N,
      "subagent_succeeded": true
    }
```

完成后 UI 从 `metadata` 读 transcript 展示，`text` 中的 JSON 仍然供父模型消费。

---

## Contracts

### Metadata Schema

| Key | Type | Stream | Final | UI 用途 |
|-----|------|--------|-------|---------|
| `subagent_transcript` | `JsonArray` (serialized `List<SubagentTranscriptStep>`) | 截断版（`truncateToolOutput=2000`） | 完整版 | 渲染 ChainOfThought 时间线 |
| `subagent_streaming` | `JsonPrimitive(boolean)` | `true` | `false` | 标题动画/状态切换 |
| `subagent_profile` | `JsonPrimitive(string)` | profile name | profile name | 卡片标题 |
| `subagent_steps` | `JsonPrimitive(int)` | 当前步数 | 最终步数 | 标题/副标题 |
| `subagent_succeeded` | `JsonPrimitive(boolean)` | `false` | `true/false` | 状态指示 |

### SubagentTranscriptStep 扩展

```kotlin
sealed class SubagentTranscriptStep {
    data class Reasoning(
        val text: String,
        val createdAt: Long? = null  // 新增
    ) : SubagentTranscriptStep()

    data class ToolCall(
        val toolName: String,       // 本地当前字段名
        val input: String,
        val output: String,
        val executed: Boolean = true,    // 新增：区分 in-flight vs done
        val childTranscript: List<SubagentTranscriptStep>? = null  // 新增：嵌套
    ) : SubagentTranscriptStep()

    data class Text(
        val content: String         // 本地当前字段名（sub 用 text）
    ) : SubagentTranscriptStep()
}
```

**序列化兼容性**：新字段均有默认值，旧数据反序列化无破坏。

### ToolCallIdElement

```kotlin
private class ToolCallIdElement(val toolCallId: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ToolCallIdElement>
}
suspend fun currentToolCallId(): String? = coroutineContext[ToolCallIdElement]?.toolCallId
private suspend fun <T> withToolCallId(toolCallId: String, block: suspend () -> T): T =
    withContext(ToolCallIdElement(toolCallId)) { block() }
```

---

## Throttle Design

`SubagentHost.runToCompletion` 内 onProgress 节流：

```kotlin
var lastSignature = -1
var lastEmitTime = 0L
val minIntervalMs = 120L

val throttledOnProgress: ((List<UIMessage>) -> Unit)? = onProgress?.let { cb ->
    { messages ->
        val signature = messages.sumOf { msg ->
            if (msg.role == MessageRole.ASSISTANT) msg.parts.size else 0
        }
        val now = System.currentTimeMillis()
        if (signature != lastSignature || now - lastEmitTime >= minIntervalMs) {
            lastSignature = signature
            lastEmitTime = now
            progressScope.launch { cb(messages) }
        }
    }
}
```

- 结构变化（新 part 出现）→ 立即发射
- 纯文本追加 → 最少 120ms 间隔
- 异步 `launch` 避免 flow 背压

---

## UI Changes

### SpawnSubagentToolUI 改造

**现有流程**：
1. `loading && result == null` → spinner
2. `result != null` → 解析 `SubagentResult` JSON → summary + transcript

**目标流程**：
1. 读 `context.tool.output` 中首个 `Text` 的 `metadata`
2. `metadata != null && metadata["subagent_streaming"]?.jsonPrimitive?.boolean == true` → **流式态**
   - 标题：profile name + 脉冲动画 + `(${steps}步)`
   - 内容：从 `metadata["subagent_transcript"]` 反序列化 → `ChainOfThought` 风格步骤列表
   - Reasoning 步骤 → Sparkles icon + "Thinking"
   - ToolCall 步骤 → executed=false 时显示 pending indicator
3. `metadata != null && streaming == false` → **完成态（metadata 路径）**
   - 标题：profile name + 完成状态 + token 使用
   - Summary 块（从 `SubagentResult.summary` 或 text 字段解析）
   - 可展开 transcript
4. `metadata == null` → **回落到现有 JSON 解析**（向后兼容旧消息）

### ChainOfThought 复用

现有聊天消息的 `ChainOfThought` 组件（`ChatMessage.kt`）用于主消息流。子代理卡片内部可采用类似但简化的时间线布局，不直接复用同一 composable（避免嵌套过深），复用相同视觉风格（Sparkles icon、Reasoning 颜色等）。

---

## Compatibility & Migration

- **旧消息兼容**：`metadata == null` 的旧 `spawn_subagent` tool parts → 走现有 `SubagentResult` JSON 解析路径，无变化
- **SubagentTranscriptStep 新字段**：均有默认值，旧数据反序列化兼容
- **Provider 影响**：metadata 不进入 API 请求 payload（`SubagentTools.buildToolMessage` 或 provider 序列化不读 metadata）
- **并行策略**：保留现有 `size > 1 && subagentCount > 1` 规则；仅增加 `ToolCallIdElement` 隔离不影响调度策略

---

## Trade-offs

| Decision | Choice | Alternative | Why |
|----------|--------|-------------|-----|
| 进度写入位置 | tool output `Text.metadata` | 新增 `UIMessagePart.StreamingProgress` | 无 schema 变更，provider 层零改动 |
| 节流策略 | 120ms + signature bypass | 固定间隔 | 结构变化（新步骤）需即时可见 |
| UI 样式 | 简化 ChainOfThought 风格 | 完全复用 ChatMessage ChainOfThought | 子代理卡片嵌套深度可控 |
| ToolCall 字段名 | 保留 `toolName` | 改为 `name` 对齐 sub | 避免序列化破坏 + 全量 grep 替换风险 |

---

## Rollback

- `updateSubagentProgress` 仅被 `onProgress` 回调触发；移除回调即可回到现有非流式行为
- metadata key 是追加式的，旧代码不读新 key
- `SpawnSubagentToolUI` 保留 `metadata == null` 的 fallback 路径

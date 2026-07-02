# Research: ChatService.kt

- **Query**: `compressConversation`、`GenerationChunk.Messages`、`updateCurrentMessages` 调用链
- **Scope**: internal
- **Date**: 2026-07-02

## File

`app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

## `compressConversation` (L1045–1151)

```kotlin
suspend fun compressConversation(
    conversationId: Uuid,
    conversation: Conversation,
    additionalPrompt: String,
    targetTokens: Int,
    keepRecentMessages: Int = 32
): Result<Unit> = runCatching {
    ...
    val allMessages = conversation.currentMessages
    ...
    val keepMessageIds = messagesToKeep.map { it.id }.toSet()
    var hiddenCount = 0
    val nodesWithHidden = conversation.messageNodes.map { node ->
        val messageId = node.currentMessage.id
        if (messageId !in keepMessageIds && !node.hidden) {
            hiddenCount++
            node.copy(hidden = true)
        } else {
            node
        }
    }

    val summaryNodes = compressedSummaries.map { summary ->
        UIMessage.user(summary).toMessageNode().copy(
            compressHiddenCount = hiddenCount.takeIf { it > 0 },
        )
    }

    val insertAt = nodesWithHidden.indexOfFirst { node ->
        !node.hidden && node.currentMessage.id in keepMessageIds
    }.let { if (it < 0) nodesWithHidden.size else it }

    val newMessageNodes = buildList {
        addAll(nodesWithHidden)
        summaryNodes.forEachIndexed { offset, summaryNode ->
            add(insertAt + offset, summaryNode)
        }
    }
    val newConversation = conversation.copy(
        messageNodes = newMessageNodes,
        chatSuggestions = emptyList(),
    )

    saveConversation(conversationId, newConversation)
}
```

要点：

1. 用 **`currentMessages`** 决定压缩/保留哪些 `UIMessage`（L1062–1080）。
2. 对 **`messageNodes` 全量** 打 `hidden = true`（不删除节点）（L1119–1127）。
3. 在第一个「保留且可见」节点物理位置 **`insertAt`** 插入若干摘要 `MessageNode`（L1135–1144）。
4. 保存后 **`messageNodes` 长度与可见消息数不一致**，且前导区段常为 hidden。

## 生成入参：仍用 `currentMessages` (L651–657)

```kotlin
messages = conversation.currentMessages.let {
    if (messageRange != null) {
        it.subList(messageRange.start, messageRange.endInclusive + 1)
    } else {
        it
    }
},
```

发给模型的顺序在压缩后仍是正确的可见序列；问题出在**写回存储**。

## `GenerationChunk.Messages` → `updateCurrentMessages` (L779–784)

```kotlin
}.collect { chunk ->
    when (chunk) {
        is GenerationChunk.Messages -> {
            updateConversationState(conversationId) { prev ->
                prev.updateCurrentMessages(chunk.messages)
            }
            ...
        }
    }
}
```

- `GenerationHandler` 发出的 `chunk.messages` 是**工具循环内的完整可见消息列表**（在 `generateText` 里维护的 `messages` 变量），与 `conversation.currentMessages` 同语义，**不是**按 `messageNodes` 物理下标排列。
- 每次流式 chunk、工具执行后合并、生成结束 emit（见 `GenerationHandler.kt` L196–238、L278、L336）都会走此路径。

## 其它 `updateCurrentMessages` 调用

| 位置 | 上下文 |
|------|--------|
| L203 | `Conversation.cleanStaleSubagentStreaming`：基于 `currentMessages` 改 assistant 后写回 |
| L430 | 新会话预设消息（无 hidden，安全） |
| L1360 | `updateSubagentProgress`：读 `currentMessages`，改最后一条 assistant，再 `updateCurrentMessages` |

`updateSubagentProgress` (L1337–1361) 与主流式路径相同，压缩后并行子代理时同样会错位写节点。

## `onCompletion` 兜底 (L766–773)

直接 `prev.messageNodes.map { ... finishReasoning() }`，**不经过** `updateCurrentMessages`，不会单独引入下标错位；但若此前 chunk 已写错节点，兜底只改 reasoning 状态，不能修复顺序。

## 状态更新 API (L1267–1290)

`updateConversationState` 在 `stateLock` 下整表替换 `session.state.value`；与 `compressConversation` 的 `saveConversation` 串行取决于用户操作时机。压缩后若立即继续生成，内存中的 `messageNodes` 已是「hidden + 摘要插入」布局，下一帧 chunk 即触发错位更新。
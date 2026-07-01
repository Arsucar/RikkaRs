# Research: compress hidden count + message count stats (Issues #24 #25)

- **Query**: Code paths for compress hidden-count prefix, FilesPicker message count, `currentMessages` filter, ChatSizeChecker thresholds, related strings
- **Scope**: internal
- **Date**: 2026-07-01

## Findings

### 1. Hidden count prepended to summary text (`ChatService.kt`)

Compression uses **visible** messages (`currentMessages`) to decide what to summarize and keep, then marks non-kept nodes `hidden = true`, and inserts summary **user** nodes whose **text body** includes the localized prefix.

```1011:1116:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> = runCatching {
        // ...
        val allMessages = conversation.currentMessages
        // ... messagesToCompress / messagesToKeep from allMessages ...
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
            val prefix = if (hiddenCount > 0) {
                context.getString(R.string.compress_hidden_count, hiddenCount) + "\n\n"
            } else {
                ""
            }
            UIMessage.user(prefix + summary).toMessageNode()
        }
        // ... insert summaryNodes into messageNodes, saveConversation ...
    }
```

**Implications for #24 (hidden count “leak”)**:

- Prefix is stored as **plain text** on `UIMessage` user parts (`prefix + summary`), not as structured metadata.
- That summary node is **not** `hidden`; it appears in chat UI like any user message (`ChatList` → `ChatMessage` renders all `messageNodes`).
- Anything that reads message **text** (copy, export, send-to-model via `currentMessages`, notifications using `toText()`, etc.) can include `[N messages hidden]` / `[已隐藏 N 条消息]` unless separately stripped.
- `hiddenCount` only increments nodes that were **not already** `hidden` (`!node.hidden`), so re-compress behavior depends on prior hidden state.

### 2. Compress UI message count (`FilesPicker.kt`)

Trailing count on “Compress History” uses **total** `messageNodes.size`, including hidden nodes and inserted summary nodes.

```215:233:app/src/main/java/me/rerere/rikkahub/ui/components/ai/FilesPicker.kt
        // Compress History Button
        ListItem(
            // ...
            trailingContent = {
                if (conversation.messageNodes.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_page_message_count, conversation.messageNodes.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
```

**Implications for #25 (message count stats)**:

- Display count ≠ “messages in context” (`currentMessages.size`).
- After compress: count includes hidden nodes + summary nodes; user may expect visible/context count or non-hidden count only.

### 3. `currentMessages` and hidden filter (`Conversation.kt`)

```44:52:app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt
    /**
     *  当前选中的 message
     */
    val currentMessages
        get(): List<UIMessage> {
            return messageNodes
                .filter { !it.hidden }
                .map { node -> node.messages[node.selectIndex] }
        }
```

`MessageNode.hidden` default:

```111:117:app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
    val hidden: Boolean = false,
```

- Hidden nodes are **excluded** from LLM context assembly paths that use `currentMessages`.
- Summary nodes inserted by compress are **visible** (`hidden = false`), so their full text (including prefix) **is** in `currentMessages`.

### 4. `ChatSizeChecker.kt` — `messageNodes.size` threshold

```17:55:app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatSizeChecker.kt
const val MESSAGE_NODE_WARNING_THRESHOLD = 768
const val LAST_ASSISTANT_INPUT_TOKEN_WARNING_THRESHOLD = 300_000
// ...
fun rememberConversationSizeInfo(conversation: Conversation): ConversationSizeInfo {
    return remember(conversation.messageNodes) {
        val nodeCount = conversation.messageNodes.size
        val lastAssistantInputTokens = conversation.messageNodes.asReversed()
            .map { it.currentMessage }
            .firstOrNull { it.role == MessageRole.ASSISTANT }
            ?.usage
            ?.promptTokens
            ?: 0
        val exceedNodeCountThreshold = nodeCount > MESSAGE_NODE_WARNING_THRESHOLD
        // showWarning when BOTH node count AND last assistant input tokens exceed thresholds
```

Dialog copy uses `sizeInfo.nodeCount` → `R.string.chat_size_dialog_content`.

**Implications for #25**:

- Warning uses **all** nodes (hidden + summaries), not `currentMessages.size`.
- `lastAssistantInputTokens` scans **all** nodes in reverse (includes hidden assistants).

### 5. Related UI: list vs context

| Location | Iterates | Hidden handling |
|----------|----------|-----------------|
| `ChatList.kt` | `conversation.messageNodes` | Shows hidden with bar + 0.4 alpha |
| `ChatMessage.kt` | per `node` | Label `message_hidden_label` when `node.hidden` |
| `ChatPage.kt` scroll | `currentMessages.size` for scroll index | Context-aligned |
| `FilesPicker.kt` count | `messageNodes.size` | Includes hidden |
| `ChatService` compress input | `currentMessages` | Excludes hidden |
| `ChatService` notification | `currentMessages.lastOrNull()?.toText()` | Excludes hidden bodies |

### 6. String resources

**`compress_hidden_count`** (only en + zh in repo; not in ja/ko/ru/zh-TW):

| File | Value |
|------|--------|
| `app/src/main/res/values/strings.xml` | `[%1$d messages hidden]` |
| `app/src/main/res/values-zh/strings.xml` | `[已隐藏 %1$d 条消息]` |

**`chat_page_message_count`** (multiple locales):

| File | Value |
|------|--------|
| `values/strings.xml` | `%d messages` |
| `values-zh/strings.xml` | `%d 条消息` |
| `values-zh-rTW`, `values-ja`, `values-ko-rKR`, `values-ru` | localized `%d …` variants |

Related:

- `message_hidden_label`: en `Hidden · not in context` / zh `已隐藏 · 不在上下文中`
- `chat_size_dialog_content`: en uses “Current message count: %1$d” (fed `nodeCount` from checker)

### 7. Product changelog (intent)

`CHANGELOG.md` documents: compress hides old messages instead of deleting, and injects `[已隐藏 N 条消息]` / `[N messages hidden]` **before summary**. Confirms prefix is intentional in message content today.

## Caveats / Not Found

- GitHub issue bodies for #24 / #25 were not in-repo; bug mapping above is inferred from task title + code behavior.
- No unit tests found specifically for `compressConversation` hidden prefix or FilesPicker count.
- `compress_hidden_count` missing from non-zh secondary locales (falls back to English via `values/`).

## Related Specs / Tasks

- `.trellis/tasks/07-01-bug-compress-hidden-stats/prd.md` — placeholder PRD for this fix
- `CHANGELOG.md` — compress + hide feature description
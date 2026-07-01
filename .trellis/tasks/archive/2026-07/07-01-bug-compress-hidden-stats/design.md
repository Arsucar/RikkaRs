# Design: Fix compress hidden count leak + message count stats

## #24 — Hidden count in model text

### Option chosen: Metadata on MessageNode

Store `hiddenCount` as a field on the **summary MessageNode** rather than in UIMessage text.

**Rationale**: UIMessage text is the unit sent to model; MessageNode metadata is UI-only. This avoids any stripping logic at generation time.

### Data model change

Add `compressHiddenCount: Int? = null` to `MessageNode`:

```kotlin
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
    val hidden: Boolean = false,
    val compressHiddenCount: Int? = null,
)
```

- `null` (default) = not a compress summary or old data
- `> 0` = compress summary with hidden count context

### ChatService.kt change

Replace:
```kotlin
val prefix = if (hiddenCount > 0) {
    context.getString(R.string.compress_hidden_count, hiddenCount) + "\n\n"
} else ""
UIMessage.user(prefix + summary).toMessageNode()
```

With:
```kotlin
UIMessage.user(summary).toMessageNode().copy(
    compressHiddenCount = hiddenCount.takeIf { it > 0 }
)
```

No prefix in text body. Hidden count stored on node.

### Chat UI change

In `ChatMessage.kt` where the message bubble renders: if `node.compressHiddenCount != null`, show a small label above/below the message content:

- Use existing string `R.string.compress_hidden_count` 
- Style: small text, muted color, optional icon (same as `message_hidden_label` pattern)
- This label is UI-only, never sent to model

### Generation-time guarantee

`currentMessages` maps `messageNodes.filter { !it.hidden }.map { it.messages[it.selectIndex] }` — it only extracts `UIMessage`, not `MessageNode.compressHiddenCount`. So the hidden count metadata is **never** reachable from the model context path. No stripping needed.

### Backward compatibility

- Old conversations: summary nodes have no `compressHiddenCount` field → `null` by default → no label shown, old prefix text remains in `UIMessage.text` (harmless, model already saw it)
- New compressions: clean text, metadata on node
- No migration needed

## #25 — Message count stats

### FilesPicker.kt

Replace:
```kotlin
conversation.messageNodes.size
```

With:
```kotlin
conversation.messageNodes.count { !it.hidden }
```

This aligns with `currentMessages.size` semantics.

### ChatSizeChecker.kt

Replace:
```kotlin
val nodeCount = conversation.messageNodes.size
```

With:
```kotlin
val nodeCount = conversation.messageNodes.count { !it.hidden }
```

Also fix `lastAssistantInputTokens` scan — change `conversation.messageNodes.asReversed()` to `conversation.messageNodes.filter { !it.hidden }.asReversed()`.

### Optional enhancement

Add secondary label in FilesPicker: `"X hidden"` alongside visible count, e.g. "42 messages · 5 hidden". Use `R.string.chat_page_message_count` for visible count and a new string for hidden.

## Summary of changes

| File | Change | Issue |
|------|--------|-------|
| `Conversation.kt` | Add `compressHiddenCount: Int? = null` to `MessageNode` | #24 |
| `ChatService.kt` | Remove prefix from text; store on node | #24 |
| `ChatMessage.kt` | Render `compressHiddenCount` label (UI-only) | #24 |
| `FilesPicker.kt` | `count { !it.hidden }` instead of `size` | #25 |
| `ChatSizeChecker.kt` | `count { !it.hidden }` + filtered token scan | #25 |

## Risks

- `MessageNode` is serialized in `Conversation` JSON → `compressHiddenCount` default `null` ensures backward compat
- Multiple summary nodes from multi-round compress: each gets its own `compressHiddenCount` (delta for that round, not cumulative) — matches current behavior where each summary shows its own N
- `compress_hidden_count` string only in en/zh → other locales fall back to English (acceptable, same as current)
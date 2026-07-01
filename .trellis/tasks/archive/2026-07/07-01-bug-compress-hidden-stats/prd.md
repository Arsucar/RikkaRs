# PRD: Fix compress hidden count leak + message count stats

## Issues

- #24: Compress context summary writes `[已隐藏 N 条消息]` / `[N messages hidden]` prefix into model-visible UIMessage text body
- #25: "Compress History" trailing count uses `messageNodes.size` (includes hidden), diverges from actual context size

## Problem

### #24 — Hidden count in model text

After compression, `ChatService.compressConversation()` prepends `compress_hidden_count` string to the summary UIMessage's text body:

```kotlin
val prefix = if (hiddenCount > 0) {
    context.getString(R.string.compress_hidden_count, hiddenCount) + "\n\n"
} else { "" }
UIMessage.user(prefix + summary).toMessageNode()
```

This prefix:
1. Is sent to the model as part of `currentMessages` — model may treat it as conversational fact
2. Is displayed to the user in the chat message bubble
3. Stales silently if user unhides nodes (N becomes wrong)
4. Persists across re-compressions

### #25 — Message count includes hidden

Two locations use `messageNodes.size` instead of visible-node count:

1. `FilesPicker.kt` ~227: `stringResource(R.string.chat_page_message_count, conversation.messageNodes.size)`
2. `ChatSizeChecker.kt` ~40: `val nodeCount = conversation.messageNodes.size` (threshold 768)

This inflates the count relative to what the model actually sees (`currentMessages`).

## Acceptance Criteria

### #24 Fix

- `compressConversation()` does NOT write `compress_hidden_count` prefix into UIMessage text
- Hidden count is stored as structured metadata (e.g. on MessageNode or as a separate UIMessagePart) that:
  - Is visible in chat UI (label/badge on summary node)
  - Is NOT included in text sent to model via `currentMessages`
  - Updates if user unhides/re-hides nodes (or can be recalculated from `messageNodes.count { it.hidden }`)
- Existing conversations with old-style prefix-in-text degrade gracefully (prefix still shows old text; new compressions use new format)

### #25 Fix

- `FilesPicker.kt` trailing count shows `currentMessages.size` or `messageNodes.count { !it.hidden }`
- `ChatSizeChecker.kt` uses visible-node count for both `nodeCount` and `lastAssistantInputTokens` scan
- Optional: secondary label "X hidden" shown alongside visible count

## Constraints

- No database migration needed (Conversation is JSON-serialized)
- Must not break existing compress/uncompress cycle
- Backward-compatible with old conversations that have prefix in text

## Related Code

| File | Lines | Issue |
|------|-------|-------|
| `ChatService.kt` | 1092–1098 | Prefix concatenation |
| `FilesPicker.kt` | 227–229 | `messageNodes.size` |
| `ChatSizeChecker.kt` | 40–47 | `messageNodes.size` threshold |
| `Conversation.kt` | 44–51 | `currentMessages` filter |
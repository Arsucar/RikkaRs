# Research: ChatVM.kt

- **Query**: `handleCompressContext`
- **Scope**: internal
- **Date**: 2026-07-02

## File

`app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt`

## `handleCompressContext` (L190–201)

```kotlin
fun handleCompressContext(additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int): Job {
    return viewModelScope.launch {
        chatService.compressConversation(
            _conversationId,
            conversation.value,
            additionalPrompt,
            targetTokens,
            keepRecentMessages
        ).onFailure {
            chatService.addError(it, title = context.getString(R.string.error_title_compress_conversation))
        }
    }
}
```

## Data flow

- `ChatPage` → `CompressContextDialog.onConfirm` → `vm.handleCompressContext(...)`（见 `ChatPage.kt` L681）。
- 使用当前 `conversation.value` 快照调用 `ChatService.compressConversation`；成功后通过 `saveConversation` 更新仓库与会话 state。
- **不**参与流式 `updateCurrentMessages`；压缩本身逻辑正确，缺陷在压缩后的**后续生成写回**。

## Related: `toggleMessageHidden` (L214–216)

```kotlin
fun toggleMessageHidden(messageId: Uuid) = viewModelScope.launch {
    chatService.toggleMessageHidden(_conversationId, messageId)
}
```

用户手动 hidden 与压缩 hidden 共用 `MessageNode.hidden`，同样会使 `currentMessages` 与物理下标脱节，放大 `updateCurrentMessages` 下标假设问题（非 #28 独有，但同类风险）。
# Research: Pending approval UI + ChatVM/ChatService flow

- **Query**: ChatMessageTools pending approval UI; ChatVM/ChatService handleToolApproval
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTools.kt` | Pending approve/deny UI |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt` | Forwards `onToolApproval` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | Wires VM handlers |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt` | `handleToolApproval` / `handleToolAnswer` |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | Persists approval state + resumes generation |
| `ai/src/main/java/me/rerere/ai/ui/Message.kt` | `ToolApprovalState` + `isPending` / `canResumeExecution` |
| `app/src/main/java/me/rerere/rikkahub/web/routes/ConversationRoutes.kt` | Web API also calls `handleToolApproval` |

### Code Patterns

#### ToolApprovalState model

```365:384:ai/src/main/java/me/rerere/ai/ui/Message.kt
sealed class ToolApprovalState {
    data object Auto : ToolApprovalState()
    data object Pending : ToolApprovalState()
    data object Approved : ToolApprovalState()
    data class Denied(val reason: String = "") : ToolApprovalState()
    data class Answered(val answer: String) : ToolApprovalState()
}
```

- Default on tool parts: `Auto`.
- `isPending` ⇒ show approve/deny buttons.
- Resume when Approved / Denied / Answered (`canResumeToolExecution`).

#### Pending UI (entry point for “始终允许此目录”)

```103:165:app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTools.kt
val isPending = tool.approvalState is ToolApprovalState.Pending
// ...
extra = if (isPending && onToolApproval != null) {
    {
        Row(...) {
            FilledTonalIconButton(onClick = { showDenyDialog = true }) // deny
            FilledTonalIconButton(onClick = { onToolApproval(tool.toolCallId, true, "") }) // approve once
        }
    }
} else null
```

Current signature:

```kotlin
onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null
```

- **No secondary action** for “always allow this directory”.
- Issue #258: show secondary only when  
  `toolName in {workspace_write_file, workspace_edit_file}` **and** path is outside writable roots.
- Path is available via `tool.inputAsJson()` → `path` field (same as tool args).

#### ChatPage → ChatVM → ChatService

```815:819:app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt
onToolApproval = { toolCallId, approved, reason ->
    vm.handleToolApproval(toolCallId, approved, reason)
},
```

```519:531:app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt
fun handleToolApproval(toolCallId: String, approved: Boolean, reason: String = "") {
    chatService.handleToolApproval(_conversationId, toolCallId, approved, reason)
}
fun handleToolAnswer(toolCallId: String, answer: String) {
    chatService.handleToolApproval(_conversationId, toolCallId, approved = true, answer = answer)
}
```

```720:777:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
fun handleToolApproval(
    conversationId: Uuid,
    toolCallId: String,
    approved: Boolean,
    reason: String = "",
    answer: String? = null,
) {
    // map toolCallId part → Approved | Denied | Answered
    // saveConversation
    // if no remaining Pending → handleMessageComplete(..., ToolContinuation)
}
```

- Does **not** currently accept/persist a trusted root.
- Web route (`ConversationRoutes.kt:363`) uses same API: `toolCallId, approved, reason, answer`.

#### Issue-required approval UI hook shape

1. In `ChatMessageToolStep` extra row: tertiary control “始终允许此目录”.
2. Confirm dialog (risk: executable code + cross-assistant shared for `/skills`).
3. On confirm:
   - Persist prefix to workspace trusted roots (repo API).
   - Approve **this** toolCallId (reuse existing Approved path so generation resumes).
4. Tools that must **never** show the control: `workspace_shell`, `skill_tool` (and non-write tools).

### Related Specs

- None found for chat approval UX.

## Caveats / Not Found

- Extending `handleToolApproval` signature affects ChatVM, ChatPage, ConversationRoutes.
- Alternative: separate `trustWriteRootAndApprove(toolCallId, root)` on ChatService/VM that (a) writes Room then (b) calls existing approval — keeps web API simpler if web does not need “always allow” yet.
- Workspace id for persistence is **not** on the tool part; must come from conversation/assistant binding (`assistant.workspaceId`) inside ChatService/VM.

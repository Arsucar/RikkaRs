# Technical Design

## Boundaries and Data Flow

ChatPage's existing no-argument callback remains unchanged. ChatVM captures the current composer text before clearing it, keeps the exact original separately for recovery, and passes the trimmed instruction to `ChatService.generateInputDraft`. A pure prompt builder renders the optional block before provider streaming.

## Contracts

- `userInstruction` is transient prompt input only; it is never persisted into `Conversation`.
- Blank/whitespace-only instructions render the same template as before, without unresolved placeholders.
- Existing generation number and content comparison remain the authority for cancellation, late chunks, and user/ASR edits.

## Compatibility and Risks

The service parameter is optional/defaulted for compatibility. Prompt insertion is deliberately user-authored text inside explicit tags; delimiter escaping is outside this issue's scope. The exact pre-trigger text remains separate from the trimmed prompt value to preserve restoration semantics.

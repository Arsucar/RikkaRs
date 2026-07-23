# Implementation Plan

1. Add an internal pure input-draft prompt builder and tests for nonblank trimming and blank omission.
2. Extend ChatService and ChatVM call chain while preserving restoration and generation guards.
3. Wire trigger-time capture through the existing ChatPage callback without changing ChatInput UI.
4. Run focused prompt/policy tests, app compile, and `git diff --check`.

Risky files: `Suggestion.kt`, `ChatService.kt`, `ChatVM.kt`, `ChatPage.kt`.

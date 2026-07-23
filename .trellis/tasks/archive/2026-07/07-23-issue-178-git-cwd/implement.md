# Implementation Plan

1. Add relative-cwd conversion coverage and extend WorkspaceManager/WorkspaceRepository validated-path execution with cwd.
2. Thread effective cwd through Git repository, use cases, ChatVM, and ChatPage callbacks; include cwd in request identity.
3. Add focused tests for nested cwd execution and path validation.
4. Run workspace tests, app Git/CWD tests, app compile, and `git diff --check`.

Risky files: `WorkspaceManager.kt`, `WorkspaceGitRepository.kt`, `GitStatusUseCases.kt`, `ChatVM.kt`, `ChatPage.kt`.

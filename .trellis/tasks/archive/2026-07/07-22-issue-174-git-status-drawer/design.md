# Design: Assistant-bound Git status drawer

## Architecture

Data flow:

`Assistant.workspaceId` -> `GetAssistantGitStatusUseCase` -> `WorkspaceGitRepository` -> `WorkspaceRepository` -> `WorkspaceManager.executeProgram(argv)` -> `ProotShellRunner` -> Git porcelain/diff parser -> `GitStatusUiState` in `ChatVM` -> right drawer UI.

The UI receives only typed domain state. Raw stdout/stderr never reaches Compose, logs, conversation messages, or model request construction.

## Workspace Execution Boundary

- Add a non-Shell execution mode to the workspace runner context. `WorkspaceManager.executeProgram` accepts a non-empty argv list, validates NUL-free arguments, resolves cwd canonically, and invokes the runner without `bash -c` or `eval`.
- `HostShellRunner` uses `ProcessBuilder(argv)`; `ProotShellRunner` appends argv directly after the sanitized `env -i` prefix while retaining the existing PRoot working directory.
- Existing `executeCommand` behavior and shell policy remain unchanged. The new app-facing Git repository calls only argv mode with executable `git` and fixed option lists.
- Add a manager/repository path-validation entry point backed by `WorkspaceFileSystem.resolve`, so diff paths are rejected before Git receives them. The validated relative path, including spaces or metacharacters, remains one argv element.

## Domain And Data Contracts

- `GitRepositoryStatus` contains workspace display name, branch name or detached short OID, distinct change count, grouped `GitFileChange` lists, and `truncated`.
- A change stores current path, optional original path, index/worktree status codes and a stable change kind. It never stores file contents.
- `GitStatusUiState` distinguishes idle/loading, unbound, missing Workspace, not-ready Workspace, directory unavailable, permission denied, not a repository, timeout, command unavailable/failure, and success. Clean is a success with no changes.
- `GitDiffUiState` distinguishes idle/loading, text, binary, too-large/truncated, invalid path, timeout and failure.
- Porcelain v2 NUL parsing is centralized and tested. Type `1`, `2`, `u`, and `?` records are supported; ignored records are excluded. Rename records consume the following NUL record as the original path.
- Summary change count uses distinct current paths. Group membership derives from XY: index changes -> staged, worktree changes/conflicts -> unstaged, `?` -> untracked.
- Status retains at most 300 distinct files. Diff text retains at most 64 KiB. The runner's existing 128 KiB output cap remains the outer process bound.

## Status And Diff Commands

- Status: `git status --porcelain=v2 --branch -z --untracked-files=all` in Workspace root.
- Text/binary probe: scoped `git diff --numstat` (or `git diff --no-index --numstat /dev/null <path>` for untracked).
- Patch: scoped `git diff --no-color --no-ext-diff --no-textconv --unified=3` with `--cached` for staged or `--no-index /dev/null` for untracked.
- `--` terminates Git options before every repository path. Exit code 1 is accepted only for `git diff --no-index` differences.

## Presentation And State Ownership

- `ChatVM` owns status/diff state and request jobs because the existing drawer's context and Hook async features follow this pattern.
- Each request carries the requested workspace ID and a monotonically increasing generation. Starting a request immediately replaces visible data with loading; cancelled or stale jobs cannot publish results.
- `ConversationDrawerContent` receives the current assistant workspace ID plus state/callbacks. When the drawer opens or the assistant binding changes while open, it triggers one status read. Closing the drawer clears diff selection and returns the internal screen to menu; no polling is scheduled.
- Menu subtitle projects the status into project/branch/count or a concise state label. Details use a scrollable list with stable keys. File click opens an in-drawer diff view for the selected section.
- Unbound/missing CTA closes the drawer and navigates to `Screen.AssistantDetail(currentAssistantId)` where the existing Workspace picker lives.

## Compatibility And Security

- No schema, backup, migration, Assistant, Conversation, or model-request changes.
- Existing Shell commands still use the current heuristic policy; argv execution is internal application infrastructure and is not exposed as a new AI tool.
- The feature reads only the explicitly bound Workspace root and never falls back to list order or effective conversation cwd.
- UI errors are stable localized categories. Raw Git stderr and absolute paths are neither logged nor displayed.

## Rollback

- UI/data feature can be removed without migration.
- The argv runner mode is additive. Reverting it restores the prior shell-only API without changing persisted data.


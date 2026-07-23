# Technical Design

## Boundaries and Data Flow

`Conversation.workspaceCwd` / `Assistant.defaultWorkspaceCwd` -> `resolveEffectiveWorkspaceCwd` -> ChatPage Git callbacks -> ChatVM -> Git use cases -> `WorkspaceGitRepository` -> `WorkspaceRepository.executeProgram*` -> `WorkspaceManager`.

The UI continues to expose absolute `/workspace` paths. The repository converts them to manager-relative CWD values, resolves the Git repository root with `git rev-parse --show-prefix`, and runs both status and diff from that root. Porcelain-v2 `-z` paths are repository-root-relative, so they remain valid for diff even when the selected CWD is a subdirectory.

## Contracts

- `WorkspaceManager.executeProgramWithValidatedPath` gains an optional `cwd` while preserving the existing default. Missing or non-directory CWD failures use a typed `WorkspaceWorkingDirectoryException` so Git path validation errors remain distinguishable.
- Validation checks the combined `cwd/path` against the workspace root and rejects symlinked intermediate components; the argv path remains relative to cwd.
- Git use cases accept optional effective cwd and preserve all existing error mapping.
- Loading identity includes workspace id and cwd so a changed cwd cannot be mistaken for the in-flight request.

## Compatibility and Risks

Default/root callers pass empty cwd and retain current behavior. The only new UI input is already normalized by `resolveEffectiveWorkspaceCwd`. No persistence schema changes are needed. The main risk is validating a nested path against the wrong base; tests must assert both runner cwd and argv path.

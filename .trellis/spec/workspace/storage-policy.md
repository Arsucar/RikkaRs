# Workspace Storage Policy Spec

> Code-spec for workspace project files storage modes and shell-visible behavior.

## Scenario: Git Pack Writes on Workspace Storage

### 1. Scope / Trigger

- Trigger: Changing workspace files storage behavior, storage settings UI, migration behavior, or Git support messaging.
- Applies to project files mounted at `/workspace` for workspace shell commands.

### 2. Signatures

- `WorkspaceFilesStorage.PRIVATE`
- `WorkspaceFilesStorage.EXTERNAL`
- `fun WorkspaceFilesStorage.supportsWorkspaceGitPackWrites(): Boolean`

### 3. Contracts

- `PRIVATE` returns `true` from `supportsWorkspaceGitPackWrites()`.
- `EXTERNAL` returns `false` from `supportsWorkspaceGitPackWrites()`.
- UI that lets users select or view `EXTERNAL` storage must surface that Git clone/fetch/pull may fail on pack writes.
- Storage migration must remain data-copy behavior only; do not silently migrate users back to `PRIVATE` just because Git is safer there.

### 4. Validation & Error Matrix

- `PRIVATE` selected -> no Git pack warning required.
- `EXTERNAL` selected -> show warning and recommend `PRIVATE` for Git-heavy workspaces.
- Migration between storage modes -> preserve existing copy/delete semantics and existing failure reporting.

### 5. Good/Base/Bad Cases

- Good: User selects `EXTERNAL`; settings dialog warns that Git pack writes may fail.
- Base: User stays on `PRIVATE`; Git-heavy workspace has the supported storage mode.
- Bad: UI describes `EXTERNAL` as fully Git-compatible through the workspace shell.

### 6. Tests Required

- Unit test asserts `PRIVATE.supportsWorkspaceGitPackWrites()` is true.
- Unit test asserts `EXTERNAL.supportsWorkspaceGitPackWrites()` is false.
- Migration tests continue to pass for existing storage movement behavior.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val gitSupported = true
```

#### Correct

```kotlin
val gitSupported = storage.supportsWorkspaceGitPackWrites()
```

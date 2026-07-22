# Workspace Program Execution Contract

## Scenario: Parameterized App-Internal Programs

### 1. Scope / Trigger

- Trigger: application code must run a workspace program with a repository path, user-derived value, or other argument that must not be interpreted as Shell syntax.
- This API is internal infrastructure. Adding a new AI tool or bypassing tool approval requires a separate security review.

### 2. Signatures

```kotlin
fun WorkspaceManager.executeProgram(
    root: String,
    arguments: List<String>,
    cwd: String = "",
    timeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MS,
    stdin: ByteArray? = null,
    extraBindMounts: List<WorkspaceBindMount> = emptyList(),
): WorkspaceCommandResult

fun WorkspaceManager.validateRelativePath(root: String, path: String): String

fun WorkspaceManager.executeProgramWithValidatedPath(
    root: String,
    path: String,
    buildArguments: (String) -> List<String>,
    timeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MS,
): WorkspaceCommandResult
```

`WorkspaceRepository.executeProgram` and `validateRelativePath` resolve the bound `WorkspaceEntity` before delegating.

### 3. Contracts

- `arguments[0]` is the executable and every following list item is one argv value. `HostShellRunner` uses `ProcessBuilder(arguments)`; `ProotShellRunner` appends the same list after its sanitized `env -i` prefix.
- Program execution does not use `bash -c`, `eval`, command-string quoting, or `evaluateShellCommand`.
- Existing `executeCommand` remains the Shell API and retains heuristic policy plus changed-file scanning.
- Read-only path resolution never creates a missing Workspace files root. Directory creation belongs to `ensureWorkspace` and explicit write operations.
- `validateRelativePath` rejects NUL, backslash, Unix/Windows absolute paths, empty paths and any `..` segment, then applies canonical containment under the Workspace files root. The Workspace root and every existing intermediate path component must not be a symbolic link. Call it immediately before passing repository paths to argv.
- App-internal repository commands use `executeProgramWithValidatedPath` so canonical/symlink validation, argv construction and process startup stay in one synchronous execution boundary.
- A validated path remains one argv element. Use a program-native `--` terminator where the program supports it.

### 4. Validation & Error Matrix

- Empty argv -> `IllegalArgumentException`.
- Any argv value containing NUL -> `IllegalArgumentException`.
- Missing/non-directory cwd -> `IllegalArgumentException` before process start, without creating the missing Workspace root.
- Global migration lock -> structured exit code 1 result, same as Shell execution.
- Relative path with `..`, absolute prefix or backslash -> `IllegalArgumentException`.
- Symlink resolving outside the files root, or a symlinked intermediate repository directory -> `IllegalArgumentException`.
- Timeout/cancellation -> existing process runner forcibly destroys the process and returns timeout or rethrows interruption.

### 5. Good / Base / Bad Cases

- Good: `executeProgram(root, listOf("git", "diff", "--", validatedPath))`; a path containing `;` stays data.
- Base: a fixed no-argument probe uses `executeProgram(root, listOf("git", "--version"))`.
- Bad: `executeCommand(root, "git diff -- $path")`; Shell metacharacters in `path` become executable syntax.
- Bad: relying on `WorkspaceShellPolicy` to make interpolated arguments safe; the policy is heuristic only.

### 6. Tests Required

- Host and PRoot command construction preserve metacharacter-containing values as one argv item and omit Shell `eval` in program mode.
- Empty/NUL argv and invalid cwd fail before runner invocation.
- Traversal, absolute paths, symlink escape and symlinked repository parents are rejected; a normal relative path is accepted unchanged after normalization.
- Validation and program execution against a missing files root leave that root absent and never invoke the process runner.
- Existing Shell command construction and policy tests continue to pass.

### 7. Wrong vs Correct

#### Wrong

```kotlin
workspaceRepository.executeCommand(id, "git diff -- $path")
```

#### Correct

```kotlin
val safePath = workspaceRepository.validateRelativePath(id, path)
workspaceRepository.executeProgram(id, listOf("git", "diff", "--", safePath))
```

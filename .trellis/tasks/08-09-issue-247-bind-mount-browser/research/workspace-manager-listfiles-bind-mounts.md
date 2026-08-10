# Research: WorkspaceManager listFiles / resolveRootfsPath / bind mounts

- **Query**: How WorkspaceManager lists files vs resolves Rootfs bind-mount paths
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `workspace/src/main/java/me/rerere/workspace/WorkspaceManager.kt` | Core list/resolve/export; constructor `bindMounts` |
| `workspace/src/main/java/me/rerere/workspace/WorkspaceFileSystem.kt` | Host FS list/read/export under a root dir |
| `workspace/src/main/java/me/rerere/workspace/Workspace.kt` | `WorkspaceStorageArea` (`FILES`/`LINUX`), `WorkspaceFileEntry` |
| `workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt` | `WorkspaceBindMount(source, target)` data class |
| `workspace/src/test/java/me/rerere/workspace/RootfsPathResolutionTest.kt` | Bind-mount path resolution tests |

### Code Patterns

#### Constructor bind-mount table

```kotlin
// WorkspaceManager.kt:14-26
class WorkspaceManager(
    ...
    private val bindMounts: List<WorkspaceBindMount> = emptyList(),
) {
    // target 长度降序, 保证 /a/b 优先于 /a
    private val sortedBindMounts = bindMounts.sortedByDescending { it.target.trimEnd('/').length }
```

- `bindMounts` is **private**; issue #247 asks for a public `bindMounts()` exposure.
- Same list is passed into PRoot via `executeCommand` / `executeProgram` as `extraBindMounts = bindMounts + extraBindMounts` (lines 246–247, 292).

#### `listFiles` — area-relative only (no mount redirect today)

```kotlin
// WorkspaceManager.kt:70-75
fun listFiles(
    root: String,
    path: String = "",
    area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
): List<WorkspaceFileEntry> =
    fileSystem.list(areaDir(root, area), path)
```

- `areaDir(FILES)` → `filesDir(root)` (`filesBaseDir/<root>/files`)
- `areaDir(LINUX)` → `linuxDir(root)` (`baseDir/<root>/linux`)
- Path is **relative to the area root**, not Rootfs absolute.
- Under LINUX, browsing `skills`, `upload`, etc. enumerates the empty placeholder dirs inside the rootfs tree (or empty if placeholders are empty dirs).

#### `resolveRootfsPath` — absolute Rootfs path → host location

```kotlin
// WorkspaceManager.kt:135-160
fun resolveRootfsPath(root: String, path: String): RootfsLocation {
    // 1) sortedBindMounts: exact target or "$target/" prefix → mount.source + relative
    // 2) /workspace → filesDir(root) + relative
    // 3) KERNEL_FS_MOUNTS (/dev,/proc,/sys) → error(... use workspace_shell)
    // 4) else → linuxDir(root) + trimmed absolute without leading /
}
```

- Comment at 128–133: bind-mount sources are normal Android dirs; reading via LINUX area path “必然落空”.
- Used by `rootfsFileSize` / `exportRootfsFile` only (tools path), **not** by browser `listFiles` / `readText` / `fileSize` / `exportFile`.

#### Area-based IO still ignores mounts

| API | Uses |
|---|---|
| `listFiles` | `areaDir` + `fileSystem.list` |
| `readText` / `fileSize` / `exportFile` | `areaDir` + `fileSystem` |
| `rootfsFileSize` / `exportRootfsFile` | `resolveRootfsPath` → host file |

#### `WorkspaceFileSystem.list` behavior

- Resolves path under root with canonical escape checks (`..` rejected).
- Filters `.l2s.*` names; sorts dirs first; caps at `config.maxListEntries` (500).
- Entry `path` is relative to the **list root** (`File.relativePath(root)`), so redirect must keep relative paths consistent with browser navigation (`entry.path` used as next `state.path`).

#### Constants

```kotlin
ROOTFS_WORKSPACE_DIR = "/workspace"
KERNEL_FS_MOUNTS = listOf("/dev", "/proc", "/sys")
LINUX_DIR = "linux", FILES_DIR = "files"
```

### External References

- None (in-repo only).

### Related Specs

- No dedicated `.trellis/spec` hits for bind-mount browser.

## Caveats / Not Found

- No existing `listRootfs` / LINUX path redirect in `listFiles`.
- No public getter for constructor `bindMounts`.
- `skills_private` is **not** part of constructor `bindMounts` (session-only extra mount; see skills research).
- If LINUX root listing does not create placeholder dirs for mounts, UI may not show mount names until synthetic entries or rootfs install creates them — issue assumes placeholders exist and appear empty.

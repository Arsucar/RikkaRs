# Research: RepositoryModule global mounts

- **Query**: Where global PRoot/file-tool bind mounts are wired
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/di/RepositoryModule.kt` | Koin `WorkspaceManager` single + global bindMounts |
| `app/src/main/java/me/rerere/rikkahub/data/files/FilesManager.kt` | `FileFolders` constants |
| `app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceRepository.kt` | Thin IO facade over manager |

### Code Patterns

#### Global mounts (constructor table)

```kotlin
// RepositoryModule.kt:70-101
WorkspaceManager(
    baseDir = File(context.filesDir, "workspaces"),
    filesBaseDirProvider = { resolveWorkspaceFilesBaseDir(...) },
    globalLock = globalLock,
    shellRunner = ProotShellRunner(nativeLibraryDir = ...),
    // 同一份挂载表既用于 PRoot 的 -b 参数, 也用于文件工具的路径解析
    bindMounts = listOf(
        WorkspaceBindMount(
            source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
            target = "/skills",
        ),
        WorkspaceBindMount(
            source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
            target = "/tool_outputs",
        ),
        WorkspaceBindMount(
            source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
            target = "/upload",
        ),
    ),
)
```

| Target | Host source | FileFolders |
|---|---|---|
| `/skills` | `context.filesDir/skills` | `SKILLS = "skills"` |
| `/tool_outputs` | `context.filesDir/tool_outputs` | `TOOL_OUTPUTS = "tool_outputs"` |
| `/upload` | `context.filesDir/upload` | `UPLOAD = "upload"` |

**Not in global table:**

| Target | Where | Host source |
|---|---|---|
| `/workspace` | Built into `resolveRootfsPath` + PRoot workspace files bind | per-workspace `filesDir(root)` |
| `/skills_private` | ChatService session `extraBindMounts` | `assistant_skills/<assistantId>` |

#### FileFolders

```kotlin
// FilesManager.kt:575-582
object FileFolders {
    const val UPLOAD = "upload"
    const val SKILLS = "skills"
    const val ASSISTANT_SKILLS = "assistant_skills"
    const val SKILL_SHARED = "skill_shared"
    const val FONTS = "fonts"
    const val TOOL_OUTPUTS = "tool_outputs"
}
```

#### WorkspaceRepository browser path

```kotlin
// WorkspaceRepository.kt:148-156
suspend fun listFiles(id, area, path): List<WorkspaceFileEntry> {
    val workspace = dao.getById(id) ?: return emptyList()
    manager.ensureWorkspace(workspace.root)
    manager.listFiles(workspace.root, path, area)
}
```

Also:

- `readText` / `fileSize` / `exportFile` — area-based (browser uses these).
- `rootfsFileSize` / `exportRootfsFile` — absolute Rootfs (tools use these; already mount-aware).
- `readTextForPreview` — LINUX still uses area `fileSize`/`exportFile` (not rootfs APIs).
- `requireWritableArea` blocks write/delete/import on LINUX (AC5 stay).

Constructor deps: `WorkspaceDAO`, `WorkspaceManager`, `RootfsInstaller`, `SettingsStore` — Settings already available for assistant binding lookup.

### Related Specs

- None under `.trellis/spec` for this topic.

## Caveats / Not Found

- Terminal Proot args (`WorkspaceTerminalSession.buildWorkspaceTerminalProotArgs`) currently only bind `/skills` (not full global table + skills_private); out of #247 browser scope but shows mount tables can diverge by call site.
- Global mounts are fixed at WorkspaceManager construction; skills_private cannot be a fixed global mount without assistant context.

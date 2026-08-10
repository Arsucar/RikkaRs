# Research: WorkspaceDetailVM / Page LINUX zone browser

- **Query**: How Rootfs (LINUX) file browser works and where mount redirect plugs in
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/.../ui/pages/extensions/workspace/WorkspaceDetailVM.kt` | Area/path state, refresh via `repository.listFiles` |
| `app/.../ui/pages/extensions/workspace/WorkspaceDetailPage.kt` | UI: area selector, path bar, file cards, open/export/share |
| `app/.../ui/pages/extensions/workspace/WorkspaceFileEditorPage.kt` | LINUX read-only editor |
| `app/.../ui/components/ai/WorkspaceCwdPicker.kt` | FILES-only browser (cwd), separate from detail |

### Code Patterns

#### State model

```kotlin
// WorkspaceDetailVM.kt:293-301
data class WorkspaceDetailState(
    val workspace: WorkspaceEntity? = null,
    val filesPath: String? = null,
    val area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    val path: String = "",          // relative to area root; blank = /
    val entries: List<WorkspaceFileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)
```

- No mount metadata, no assistant selection field for skills_private yet.

#### Refresh / navigation

```kotlin
fun refresh() {
    repository.listFiles(id = id, area = state.value.area, path = state.value.path)
}
fun open(entry) { path = entry.path; refresh() }
fun goUp() { path = path.substringBeforeLast('/'); refresh() }
fun selectArea(area) { path = ""; refresh() }
```

- LINUX path semantics today: relative under `linuxDir` (e.g. `skills/foo`, not `/skills/foo`).
- Path bar already shows `path.ifBlank { "/" }` (`WorkspacePathBar`).

#### Read-only LINUX safety net (keep for AC5)

| Action | Guard |
|---|---|
| Delete | `if (area == LINUX) return` in VM |
| Import | close stream + return if LINUX |
| Save text | `if (target.area == LINUX) return` in Page |
| Import FAB | only when `area != LINUX` |
| File card | `readOnlyArea = area == LINUX` hides edit/delete |

Export / share / view text still allowed on LINUX via `repository.exportFile` / `readText` with area — **these must redirect to mount sources** for AC3/AC4.

#### Text open path (AC3)

```kotlin
// WorkspaceDetailPage openTextFile
workspaceRepository.readText(id = id, path = entry.path, area = area)
```

- Uses area-based `readText`, not `exportRootfsFile`.
- Alternative already on repository: `rootfsFileSize` / `exportRootfsFile` with absolute path `"/" + path`.

#### Export / share / media (AC4)

```kotlin
// WorkspaceDetailVM.prepareFile / exportFile
repository.exportFile(id, area, path = entry.path, outputStream)
```

- Same area-based path; needs mount redirect for bind targets.

#### UI surface for AC1–AC2

- `WorkspaceFilesPage` + `WorkspaceFileCard` — no mount badge today (optional).
- No assistant picker for multi-bind `/skills_private`.
- Empty state: `EmptyDirectoryState` when `!loading && entries.isEmpty && error == null`.
- Error: `ErrorCard(state.error)`.

#### Pager

- Page 0: basic/settings; Page 1: files browser.
- BackHandler: goUp when on files page and path non-blank.

### Related Specs

- None.

## Caveats / Not Found

- No existing UI tests for WorkspaceDetail file browser.
- `readTextForPreview` on LINUX still area-based with size cap; if editor uses area path after mount redirect, either redirect `readText`/`exportFile` or switch Page/VM to rootfs APIs for LINUX.
- Entry paths returned by `list` are relative to the **filesystem root used for list**. After redirect, list root becomes mount source; relative paths under mount must still form navigable `state.path` that maps back to the same mount (e.g. `skills/demo/SKILL.md` under LINUX).

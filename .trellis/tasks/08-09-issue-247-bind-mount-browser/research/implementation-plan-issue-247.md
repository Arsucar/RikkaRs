# Research: Implementation plan for feat #247 bind-mount browser

- **Query**: Files to touch and end-to-end data flow for showing real bind-mount contents in workspace Rootfs browser
- **Scope**: mixed (internal inventory + issue AC mapping)
- **Date**: 2026-08-09

## Findings

### Problem (issue #247)

Rootfs browser uses `WorkspaceStorageArea.LINUX` + `listFiles` → host `linuxDir`. Bind targets (`/skills`, `/upload`, `/tool_outputs`, `/workspace`, and session `/skills_private`) are empty placeholders under `linux/`; real data lives on Android host dirs. Tools already resolve absolute Rootfs paths via `resolveRootfsPath` / known mounts; browser does not.

### Authoritative acceptance (from issue body)

| ID | Criterion |
|---|---|
| AC1 | Rootfs `/` shows mount entries; enter → real host contents |
| AC2 | `/skills_private` by entry assistant (1 bound / 0 → current / many → choose) |
| AC3 | Text view via redirected `readText` |
| AC4 | Export/share via redirected `exportFile` |
| AC5 | LINUX still read-only (no edit/delete/import) |
| AC6 | `/dev` `/proc` `/sys` unchanged |
| AC7 | Missing/empty mount source → empty state, no crash |
| AC8 | Unit tests: listFiles redirect + skills_private assistant resolve |

### Data flow (target)

```text
WorkspaceDetailVM.refresh
  → WorkspaceRepository.listFiles(id, LINUX, path)
  → WorkspaceManager.listFiles(root, path, LINUX, extraMounts?)
       map path to Rootfs absolute: "/" + path.trim('/')
       if matches bind target (constructor + extra):
         fileSystem.list(mount.source, relative)
       else if /workspace: fileSystem.list(filesDir, relative)
       else: fileSystem.list(linuxDir, path)  // today
  → entries with area-relative paths for navigation
```

Same redirect for `readText` / `fileSize` / `exportFile` when `area == LINUX` (or switch LINUX browser IO to rootfs APIs after listing).

### Mount sources

| Rootfs target | Source | Wired today |
|---|---|---|
| `/skills` | `filesDir/skills` | `RepositoryModule` bindMounts |
| `/tool_outputs` | `filesDir/tool_outputs` | `RepositoryModule` bindMounts |
| `/upload` | `filesDir/upload` | `RepositoryModule` bindMounts |
| `/workspace` | per-workspace files area | `resolveRootfsPath` special case |
| `/skills_private` | `filesDir/assistant_skills/<assistantId>` | ChatService extraBindMounts only |

### Files to touch

#### Data / workspace module

| File | Change |
|---|---|
| `workspace/.../WorkspaceManager.kt` | Expose `bindMounts()`; redirect LINUX `listFiles`/`readText`/`fileSize`/`exportFile` via `resolveRootfsPath` (or shared helper); optional empty-source → empty list |
| `workspace/.../RootfsPathResolutionTest.kt` | listFiles redirect cases; empty source; workspace mapping; kernel paths unchanged |
| (optional new) `workspace/.../BindMountListFilesTest.kt` | If suite grows large |

#### App repository / skills / settings

| File | Change |
|---|---|
| `app/.../data/repository/WorkspaceRepository.kt` | Pass skills_private extra mount into manager list/read/export for LINUX; resolve entry assistant from Settings; surface multi-bind choice if needed |
| `app/.../data/files/SkillManager.kt` | Reuse `getAssistantSkillsDir` (likely no API change) |
| `app/.../di/RepositoryModule.kt` | Only if DI needs SkillManager on repository / new collaborator — global mounts already correct |
| `app/.../data/datastore/PreferencesStore.kt` or small pure helper | `assistantsBoundTo(workspaceId)` + fallback current assistant pure function (easy to unit test) |

#### Presentation

| File | Change |
|---|---|
| `app/.../ui/.../WorkspaceDetailVM.kt` | Load bound assistants; hold selected skills_private assistant; refresh after selection; optional mount badge state |
| `app/.../ui/.../WorkspaceDetailPage.kt` | Optional mount badge; path bar Rootfs `/` prefix on LINUX; multi-assistant picker / “当前助手” label for skills_private |
| `app/.../res/values*/strings.xml` | Mount badge / current-assistant / picker strings (issue asks CN+EN) |

#### Tests

| File | Change |
|---|---|
| `RootfsPathResolutionTest.kt` | Manager list/read redirect (AC8) |
| New app test e.g. `SkillsPrivateEntryAssistantTest.kt` | 0/1/many binding resolution pure function |
| Optional | Repository integration-style test with temp dirs |

#### Do not need for AC5

- Leave delete/import/write guards as-is; no write mapping for mounts.

### Suggested implementation order

1. **WorkspaceManager**: `bindMounts()` + LINUX path → `resolveRootfsPath` for list/read/size/export; unit tests in RootfsPathResolutionTest style.
2. **skills_private extra mount**: pure assistant resolution helper + repository injects `WorkspaceBindMount` for private dir when listing/reading LINUX paths under `skills_private`.
3. **WorkspaceDetailVM/Page**: wire SettingsStore/SkillManager or repository API for assistant selection UI when many binds; labels for fallback.
4. **Optional polish**: mount badge, path bar prefix.
5. **Verify AC5–AC7** manually / tests.

### Reusable existing APIs

- `resolveRootfsPath` / `rootfsFileSize` / `exportRootfsFile` — already mount-aware for absolute paths.
- `SkillManager.getAssistantSkillsDir` — private skill host root.
- `Settings.getCurrentAssistant()` — 0-bind fallback.
- `ChatService.assistantPrivateSkillMounts` — shape to mirror for browser extra mount.
- `WorkspaceFileSystem.list` — reuse after choosing correct host rootDir.

### Related research files

- `research/workspace-manager-listfiles-bind-mounts.md`
- `research/repository-module-global-mounts.md`
- `research/skills-private-and-assistant-binding.md`
- `research/workspace-detail-ui-linux-browser.md`
- `research/rootfs-path-resolution-test-pattern.md`

## Caveats / Not Found

- PRD in task dir is still TBD; issue body is the requirements source.
- Whether Rootfs `/` listing **synthesizes** mount dir entries when placeholders are missing is unspecified; issue assumes placeholders exist. If empty rootfs lacks `skills/` dir, AC1 may require synthetic entries from `bindMounts()`.
- Terminal session only binds `/skills` today — out of browser scope.
- Research agent does not implement; this plan is inventory for implement phase.

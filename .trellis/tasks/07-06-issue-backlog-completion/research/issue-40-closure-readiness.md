# Research: issue-40-closure-readiness

- Query: Audit GitHub issue #40 closure readiness from task artifacts, code, tests, and issue state.
- Scope: mixed
- Date: 2026-07-06

## Findings

### Issue State

- `gh issue view 40 --json number,title,state,body,comments` returned `state: OPEN`, title `feat: 文件菜单增加全屏查看/编辑（文字类与工作区文件）`, and no comments.
- Task matrix says #40 has commit/code evidence plus final `lint`, `test`, and `:app:installDebug`, but still lacks device UI validation for `.md/.json/.kt/.log` plus binary/unknown files: `.trellis/tasks/07-06-issue-backlog-completion/issue-closure-matrix.md:13`.
- PRD acceptance requires manual validation before closure: `.trellis/tasks/07-06-issue-backlog-completion/prd.md:49`.
- Implementation plan still has #40 device validation unchecked: `.trellis/tasks/07-06-issue-backlog-completion/implement.md:13`.

### Files Found

- `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceDetailPage.kt` - workspace Files tab menu/view/edit dialog integration.
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageEditedFiles.kt` - AI edited-file chip bottom sheet view/edit integration.
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt` - `UIMessagePart.Document` text attachment in-app read-only viewer.
- `app/src/main/java/me/rerere/rikkahub/ui/components/ui/TextArea.kt` - shared `FullScreenTextEditor` composable.
- `app/src/main/java/me/rerere/rikkahub/utils/TextFileUtil.kt` - text extension/MIME detection and 5 MB view cap.
- `app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceRepository.kt` - workspace text read/write repository API used by UI.
- `workspace/src/main/java/me/rerere/workspace/WorkspaceFileSystem.kt` and `workspace/src/main/java/me/rerere/workspace/Workspace.kt` - underlying 5 MB read/write caps.
- `workspace/src/test/java/me/rerere/workspace/ExampleUnitTest.kt` - basic workspace read/write/path-safety unit coverage.
- `app/src/main/res/values*/strings.xml` - localized `View` and file read/save/too-large error strings.

### Acceptance Criteria Confirmed Statically

- Workspace file rows classify non-directory text-like files and add View/Edit menu actions only for those files: `WorkspaceDetailPage.kt:748`, `WorkspaceDetailPage.kt:800-827`.
- Workspace file View/Edit both open `FullScreenTextEditor`; read-only mode omits save, edit mode writes back through `workspaceRepository.writeText(... overwrite = true, area = target.area)` and refreshes: `WorkspaceDetailPage.kt:136-191`, `WorkspaceDetailPage.kt:273-304`.
- Edited-file chips classify text-like paths, show View/Edit cards for text files, resolve `/workspace/...` to `WorkspaceStorageArea.FILES` and other absolute paths to `WorkspaceStorageArea.LINUX`, and save edits back to the resolved area: `ChatMessageEditedFiles.kt:96-150`, `ChatMessageEditedFiles.kt:238-283`, `ChatMessageEditedFiles.kt:359-368`, `ChatMessageEditedFiles.kt:393-399`.
- Document attachment chips use text extension/MIME detection and prefer an in-app read-only fullscreen viewer for allowed text files; non-text, missing, oversized, or invalid files fall back to `ACTION_VIEW`: `ChatMessage.kt:330-360`, `ChatMessage.kt:660-668`.
- Shared fullscreen editor supports read-only versus editable modes, disables dismissal/save while saving, displays localized error text, and uses a full-height dialog surface: `TextArea.kt:192-290`.
- Required file classes are covered by detection: `.json`, `.kt`, `.log`, and `.md` are in `TEXT_FILE_EXTENSIONS`; text MIME and `+json`/`+xml` are accepted; size cap is `5L * 1024L * 1024L`: `TextFileUtil.kt:5`, `TextFileUtil.kt:29-35`, `TextFileUtil.kt:56-90`.
- Workspace repository exposes the UI read/write contract and delegates to workspace manager under `Dispatchers.IO`: `WorkspaceRepository.kt:153-173`.
- Workspace backend read/write cap is aligned to 5 MB: `Workspace.kt:39-41`; `WorkspaceFileSystem.kt:27-47`.
- Error strings are localized in default, zh, zh-rTW, ja, ko-rKR, and ru resources; default lines are `strings.xml:1617-1620`.

### Tests / Validation Evidence Supporting It

- `workspace/src/test/java/me/rerere/workspace/ExampleUnitTest.kt:15-28` proves basic `WorkspaceFileSystem.writeText` then `readText` round-trip inside workspace root.
- `workspace/src/test/java/me/rerere/workspace/ExampleUnitTest.kt:30-41` proves path escape rejection for workspace writes.
- No focused Compose/UI unit test for #40 view/edit menus or `FullScreenTextEditor` behavior was found by static search.
- Task matrix records final `git diff --check`, `lint`, full `test`, and `:app:installDebug` passed, but those are artifact evidence only; they do not replace the missing manual UI matrix.

### Manual UI Validation Matrix Still Needed On Device

Run on an unlocked installed debug app with a workspace containing these files: `notes.md`, `config.json`, `Main.kt`, `app.log`, and a binary/unknown file such as `blob.bin` or `README` with non-text content. Include at least one file under `/workspace/...` and one under rootfs/Linux path so `FILES` and `LINUX` area resolution are both exercised.

| Surface | Files | Required device checks before closing #40 |
|---|---|---|
| Workspace detail -> Files tab -> file row dropdown | `.md`, `.json`, `.kt`, `.log` | Each text file shows `View` and `Edit`; `View` opens fullscreen read-only with no save; `Edit` opens fullscreen with Save; changing text, saving, reopening, and/or listing file content confirms persistence. |
| Workspace detail -> Files tab -> file row dropdown | binary/unknown | No text `View`/`Edit` actions are offered; export/share/delete/open behavior remains usable and app does not crash. |
| Message edited-files chip -> bottom sheet | `.md`, `.json`, `.kt`, `.log` across `/workspace/...` and non-`/workspace` rootfs paths | Each text edited-file chip opens bottom sheet with `View` and `Edit`; `View` is read-only; `Edit` saves back to the correct `FILES` or `LINUX` area and persists. |
| Message edited-files chip -> bottom sheet | binary/unknown | Bottom sheet does not offer text `View`/`Edit`; export/share remain usable and app does not crash. |
| Message `UIMessagePart.Document` attachment chip | `.md`, `.json`, `.kt`, `.log` | Tapping opens in-app fullscreen read-only viewer instead of immediately launching external `ACTION_VIEW`; content is readable, dismissible, and no save action is present. |
| Message `UIMessagePart.Document` attachment chip | binary/unknown or oversized >5 MB text | App falls back to external viewer/no in-app text editor path, or shows the too-large/read error where applicable; no crash. |

### Related Specs

- `.trellis/spec/app/index.md` - app-layer quality expectations; no #40-specific UI spec exists.
- `.trellis/spec/guides/kotlin-concurrency-and-compose.md` - Compose state/`remember` cautions relevant to editor dialog state.
- `.trellis/spec/guides/cross-layer-thinking-guide.md` - useful boundary guide for workspace repository -> UI round-trip validation.

## Caveats / Not Found

- Follow-up device UI validation completed on 2026-07-06 after the device was unlocked: workspace `897d1ef7-ce1e-4aaf-8e11-47a1e44408e9` Files tab showed temporary `issue40-ui` files; `.log`, `.json`, `.kt`, and `.md` menus exposed `查看` and `编辑`; `.log` view opened read-only fullscreen with only `确定`; `.log` edit exposed `取消` / `保存`, saved marker `issue40-edited`, and adb readback confirmed persistence; binary `blob.bin` exposed only `导出` / `分享` / `删除`. Temporary validation files were removed.
- Therefore #40 is ready for an evidence comment and closure.
- No direct automated test was found for text-file type detection, `FullScreenTextEditor`, workspace file dropdown actions, edited-file chip actions, or document attachment in-app viewer behavior.
- Static code confirms implementation presence; the workspace-file manual device matrix above is now complete. Edited-file chip and document attachment surfaces remain covered by static audit rather than a separate manual smoke.

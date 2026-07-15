# Implementation Plan

## Ordered work

1. Inspect current Assistant serialization, SettingsStore migrations/update
   helpers, ChatService request preparation, Assistant detail sections, ChatVM/
   ChatPage search toggle, WorkspaceDetailPage/VM/repository export behavior,
   FileProvider paths, and existing image preview/test utilities.
2. Add the Assistant-level search field and one-time DataStore migration; update
   `SettingsJsonMigrator` for old backup payloads and add migration/backup and
   targeted-update tests before rewiring consumers.
3. Move generation warning/tool gating and Android chat toggle to the current
   Assistant while preserving `PreparedGenerationRequest` and
   `buildGenerationTools`; add the Assistant creation/detail switch, Assistant
   tool summary state, and remove the global Android/Web settings mutation path.
4. Add a testable Workspace file classifier/MIME helper and media-open intent
   helper using the existing repository export/cache path and FileProvider.
5. Integrate image preview and external open into WorkspaceDetailPage, preserve
   text/Markdown flows, and enforce read-only actions for the LINUX area in both
   Compose and ViewModel/repository boundaries.
6. Add English/Simplified Chinese resources through `locale-tui`; verify key and
   placeholder parity in affected resource files.
7. Run focused JVM tests and static checks; the designated final check agent
   runs the Gradle compile/lint/test commands with `--no-daemon`.
8. Verify `git diff --check`, inspect all affected packages, and if a device is
   available run the repository-required `adb devices` / `:app:installDebug`
   acceptance flow.

## Validation commands

- `./gradlew --no-daemon :app:testDebugUnitTest`
- `./gradlew --no-daemon :app:compileDebugKotlin`
- `./gradlew --no-daemon :app:processDebugResources`
- `./gradlew --no-daemon lint`
- `git diff --check`
- Device acceptance when available: `adb devices`, then
  `./gradlew --no-daemon :app:installDebug`.

## Risk and rollback points

- Before changing any `enableWebSearch` consumer, search the full repository and
  update Android/web/API and backup clients consistently.
- Migration edits are the highest-risk boundary; keep the transform pure and
  atomic, and test version gating before wiring it into DataStore.
- Workspace LINUX policy must be enforced both in UI callbacks and in the
  ViewModel/repository call sites so hidden UI cannot accidentally write.
- Before committing, confirm no duplicate `WorkspaceFileEditorPage` or
  `readTextForPreview` was introduced and no temporary device artifacts exist.

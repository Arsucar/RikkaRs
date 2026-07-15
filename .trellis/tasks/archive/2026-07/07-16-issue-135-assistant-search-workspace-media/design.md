# Technical Design

## Scope and boundaries

This task spans the `app` data/store, generation service, Assistant Compose UI,
Workspace Compose UI/repository boundary, Android resources, and focused JVM
tests. The `workspace` module's storage contracts remain unchanged. Existing
`PreparedGenerationRequest`, `buildGenerationTools`, `TextFileUtil`,
`FullScreenTextEditor`, and `FullScreenMarkdownViewer` remain the owners of
their current responsibilities.

## Network-search data flow

```text
legacy DataStore enable_web_search
        │ one-time PreferenceStore migration (atomic DataStore edit)
        ▼
Settings.assistants[*].enableWebSearch
        │ SettingsStore.updateAssistantConfig / selected Assistant projection
        ├── Assistant detail UI switch
        ├── Chat input quick toggle (updates current Assistant only)
        └── ChatService.prepareGenerationRequest
                └── buildGenerationTools(assistant.enableWebSearch)
```

- Add a serializable `enableWebSearch` field to `Assistant` with the existing
  product default (`false`) so old JSON remains decodable.
- Add the next `PreferenceStore` migration. It reads the legacy boolean and
  assistant JSON, injects the value into every existing assistant that lacks
  the field, writes the transformed JSON and a completed version marker in the
  same DataStore transaction, and never re-runs after the version advances.
  Malformed/unknown assistant records keep compatibility defaults; the
  migration must not expose a partially updated list.
- The legacy preference may remain as migration input for backup/import
  compatibility but is no longer a runtime source. `Settings.enableWebSearch`
  and its write/read path are removed or reduced to migration-only compatibility
  after all consumers move to the Assistant field.
- `ChatService` warning and tool assembly use the selected `assistant` value;
  MCP/local/memory/workspace tool behavior is unchanged.
- Android ChatVM exposes the current conversation Assistant's value and writes a
  targeted Assistant update. Assistant detail UI exposes the same field in the
  existing configuration section. Any web endpoint/type that still mutates a
  global search flag is either removed or changed to target an Assistant
  explicitly so no client can reintroduce global runtime state.
- Backup import/restore through `SettingsJsonMigrator` applies the same legacy
  root-field transform, so old backups do not silently lose the setting.

## Workspace media data flow

```text
WorkspaceFileEntry + selected WorkspaceStorageArea
        │ type/extension classification
        ├── text/Markdown → existing TextFileUtil + read/edit dialogs
        ├── image → repository export to cache → ImagePreviewDialog(file URI)
        └── other → repository export to cache → FileProvider content URI
                                      → ACTION_VIEW + MIME + read grant
```

- Reuse the existing Workspace repository export/share path to materialize a
  cache copy; do not expose workspace-root `file://` paths.
- Centralize file classification and MIME inference in a small testable helper
  (or extend an existing utility) so the card menu and open handlers agree.
- Extend `WorkspaceDetailPage` with image preview state and safe external-open
  handling (`resolveActivity`, `SecurityException`/`ActivityNotFoundException`,
  and export/decode failures become localized feedback). Reuse the existing
  `ImagePreviewDialog` contract and preserve current text/Markdown dialogs.
- In `WorkspaceStorageArea.LINUX`, hide/disable import, edit, delete, and other
  write actions; view/export/share/media-open remain read-only operations.
- Enforce the LINUX write prohibition in ViewModel/repository entry points too,
  not only by hiding Compose menu items.
- Keep the existing `FileProvider` manifest/path configuration and grant only
  temporary read permission. Unknown MIME uses `application/octet-stream`.

## Testing strategy

- Pure migration tests cover true/false legacy values, multiple assistants,
  empty assistant lists, existing assistant field preservation, one-time version
  gating, malformed/legacy JSON compatibility, and targeted Assistant updates.
- Pure media helper tests cover text/Markdown/image/known video/unknown MIME,
  query/fragment/case normalization, and LINUX write-action policy.
- Existing `TextFileUtilTest` and Workspace repository tests remain unchanged and
  must pass. Add Compose/UI tests only where the project has stable harnesses;
  otherwise keep rendering state and intent construction in testable functions.

## Compatibility and rollback

- DataStore migration is forward-only and atomic; no Room schema change is
  required because assistants are stored as serialized DataStore JSON.
- If implementation verification finds a migration or intent regression, revert
  the focused app/resource/test changes without touching existing Workspace
  storage data or the legacy preference until a corrected migration is ready.
- No new analytics, deep links, video player, or duplicate text editor/read
  helper is introduced.

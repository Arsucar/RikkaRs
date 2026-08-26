# Research: Import UI location and trigger flow

- **Query**: Where the "import tavern character card" button is located and how it triggers import.
- **Scope**: internal
- **Date**: 2026-08-16

## Import button location

### `AssistantImporter.kt` L48-60
```kotlin
@Composable
fun AssistantImporter(
    modifier: Modifier = Modifier,
    onUpdate: (Assistant) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SillyTavernImporter(onImport = onUpdate)
    }
}
```
Renders only `SillyTavernImporter`.

### `SillyTavernImporter` (L62-147) — two OutlinedButtons
- PNG button (L127-135): icon `AutoAIIcon(name = "tavern")`; text from `R.string.assistant_importer_import_tavern_png` (or `assistant_importer_importing` while loading). Launches `pngPickerLauncher` with `arrayOf("image/png")`.
- JSON button (L137-145): same icon; text `R.string.assistant_importer_import_tavern_json`. Launches `jsonPickerLauncher` with `arrayOf("application/json")`.

Both launchers are `ActivityResultContracts.OpenDocument()` and route to the same `importAssistantFromUri` suspend function (L251-291).

### Host page: `AssistantPage.kt` L419-425
```kotlin
AssistantImporter(
    onUpdate = {
        update(it)
        state.confirm()
    },
    modifier = Modifier.fillMaxWidth(),
)
```
`update` mutates the assistant state held by `AssistantDetailVM`; `state.confirm()` persists the change. So the imported Assistant is written to DataStore immediately after parse — there is no confirmation step today.

The `AssistantImporter` sits inside a `Column` on the assistant detail/setting page, between the name `OutlinedTextField` (L408-416) and the `enableWebSearch` `ListItem` (L427-439).

## How the import button triggers import (control flow)

1. User taps PNG or JSON button → launcher launches.
2. `ActivityResultContracts.OpenDocument()` returns a `Uri?`.
3. `uri?.let { ... }` handler (L75-96 / L101-122):
   - Sets `isLoading = true`.
   - Launches coroutine in `rememberCoroutineScope()`.
   - Calls `importAssistantFromUri(context, uri, onImport, toaster, filesManager)`.
   - On failure: prints stack trace and shows toast with `exception.message ?: R.string.assistant_importer_import_failed`.
   - In `finally`: `isLoading = false`.
4. `importAssistantFromUri` (L251-291):
   - Resolves MIME via `filesManager.getFileMimeType(uri)`.
   - PNG branch: `ImageUtils.getTavernCharacterMeta` → Base64 decode → JSON string; also `createChatFilesByContents([uri])` → background URI.
   - JSON branch: read file text directly.
   - Parses JSON → `parseAssistantFromJson` → `Assistant`.
   - Calls `onImport(assistant)` on success.
   - Any exception → toast with `ToastType.Error`.
5. `onImport` (in `AssistantPage.kt`): `update(it); state.confirm()` — writes the Assistant to DataStore.

## Where to inject a confirmation dialog for #302

The natural insertion point is **between step 4 (parse) and step 5 (persist)**. Currently `onImport(assistant)` is called immediately; to add a dialog:
1. Change `AssistantImporter` to expose parsed-but-not-yet-persisted state (e.g. a `StateFlow<PendingImport?>` or a callback `onParsed(assistant, bindings: List<DetectedBinding>)`).
2. Hoist dialog state up to `AssistantPage.kt` alongside `AssistantImporter` so the dialog can render above the page.
3. When user confirms, run a new "apply bindings" step that updates `Settings.lorebooks`/`Settings.presets` and attaches IDs to the new Assistant before calling `update(it); state.confirm()`.

`AssistantDetailVM` already has the needed hooks:
- `updateSettings(transform: (Settings) -> Settings)` (L598-602) — for appending lorebooks/presets.
- `update(assistant)` (via `state.confirm()`) — for persisting the new assistant.

## Existing confirm dialog component

`RikkaConfirmDialog` (imported in `AssistantExtensionsPage.kt` L56) is the standard confirm dialog in this codebase — see usage L302-314 of that file. It takes `show`, `title`, `confirmText`, `dismissText`, `onConfirm`, `onDismiss`, and a content slot.

## String resources

| Key | en (values) | zh (values-zh) |
|---|---|---|
| `assistant_importer_import_tavern_png` | Import Tavern Character Card (PNG) | 导入酒馆角色卡 (PNG) |
| `assistant_importer_import_tavern_json` | Import Tavern Character Card (JSON) | 导入酒馆角色卡 (JSON) |
| `assistant_importer_importing` | Importing... | 导入中... |
| `assistant_importer_import_failed` | (failed) | (失败) |
| `assistant_importer_missing_data_field` | — | — |
| `assistant_importer_missing_name_field` | — | — |
| `assistant_importer_missing_spec_field` | — | — |
| `assistant_importer_unsupported_spec` | — | — |
| `assistant_importer_unsupported_file_type` | — | — |
| `assistant_importer_read_json_failed` | — | — |
| `export_import_success` | Imported successfully | 导入成功 |

New strings for the dialog (e.g. "Detected bindings", "Import all", "Skip") would need to be added to `values/strings.xml` and `values-zh/strings.xml` (and ideally ja/ko/ru-zhTW for parity).

## Related files

| File | Role |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt` | Import buttons + parse pipeline |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantPage.kt` L419 | Hosts `AssistantImporter`; wires `onUpdate` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt` L598 | `updateSettings { ... }` (append lorebook/preset) |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantExtensionsPage.kt` L302 | `RikkaConfirmDialog` usage example |
| `app/src/main/res/values/strings.xml` L213-214 | Import button strings |

## Caveats / Not found

- The import is currently fire-and-forget: parse → onImport → persist. No staging/preview mechanism exists in `AssistantDetailVM` for partial assistant edits.
- `AssistantImporter` does not know about `SettingsStore` directly; it talks only through `onUpdate: (Assistant) -> Unit`. Adding binding import will require either widening this callback signature or injecting `SettingsStore`/`AssistantDetailVM` into the importer.
- The two buttons (PNG / JSON) share a single `importAssistantFromUri` — any binding detection added there automatically covers both entry points.

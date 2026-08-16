# Implement Plan: Issue #302

## Execution Checklist

### 1. Lift visibility (ExportSerializer.kt)
- [ ] `SillyTavernLorebook` → `internal`
- [ ] `SillyTavernEntry` → `internal`
- [ ] `LorebookSerializer.tryImportSillyTavern` → `internal`

### 2. Add binding detection (AssistantImporter.kt)
- [ ] Add `detectWorldBook(json: JsonObject): List<Lorebook>?` function
  - Probe `data.character_book`, `data.extensions.world`, `data.extensions.character_book`
  - Use `ExportSerializer.DefaultJson` to decode `SillyTavernLorebook`
  - Map to `Lorebook` via existing `mapSillyTavernPosition` + entry mapping
  - Return null if no entries or all entries invalid
- [ ] Add `PendingImport` data class
- [ ] Change `importAssistantFromUri` to return `PendingImport` instead of calling `onImport` directly

### 3. Add confirmation dialog (AssistantImporter.kt)
- [ ] Add `pendingImport` state to `SillyTavernImporter`
- [ ] Render `RikkaConfirmDialog` when `pendingImport != null`
- [ ] Dialog shows: world book entry count
- [ ] Confirm button → persist lorebooks + assistant
- [ ] Skip button → persist assistant only
- [ ] Dismiss (back press) → equivalent to skip

### 4. Wire persistence (AssistantPage.kt)
- [ ] Update `onUpdate` to accept `(Assistant, List<Lorebook>)`
- [ ] On import: `settingsStore.update { it.copy(lorebooks = it.lorebooks + newLorebooks) }`
- [ ] Associate: `assistant.copy(lorebookIds = assistant.lorebookIds + newLorebookIds)`

### 5. Strings (values/strings.xml + values-zh/strings.xml)
- [ ] `assistant_importer_detected_bindings` — "Detected additional content" / "检测到附加内容"
- [ ] `assistant_importer_world_book_count` — "World book: %1$d entries" / "世界书：%1$d 条"
- [ ] `assistant_importer_import_all` — "Import" / "导入"
- [ ] `assistant_importer_skip_bindings` — "Skip" / "跳过"

### 6. Unit tests
- [ ] `detectWorldBook` with v2 card containing `data.character_book`
- [ ] `detectWorldBook` with v3 card containing `data.extensions.world`
- [ ] `detectWorldBook` with no bindings → null
- [ ] `detectWorldBook` with empty character_book → null
- [ ] `detectWorldBook` with invalid entries → partial result + skip count

## Validation
- `./gradlew --no-daemon :app:compileDebugKotlin` after steps 1-4
- `./gradlew --no-daemon test` after step 6
- Install to device for manual verification

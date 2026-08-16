# Design: Issue #302 — Tavern card bindings import

## Architecture

### Data Flow
```
User picks PNG/JSON file
  → importAssistantFromUri: parse JSON → JsonObject
  → NEW: detectBindings(json) → DetectedBindings?
  → If bindings: show dialog (hoisted state in AssistantPage)
    → User confirms: createLorebooks(bindings) + associate to assistant
    → User skips: just import assistant
  → If no bindings: direct import (unchanged)
```

### Key Decisions

1. **Binding detection** — A pure function `detectWorldBook(json: JsonObject): List<Lorebook>?` that probes:
   - `data.character_book` (canonical v2/v3)
   - `data.extensions.world`
   - `data.extensions.character_book`
   
   Returns `null` if no valid entries found.

2. **Reuse existing mapper** — Lift `LorebookSerializer.tryImportSillyTavern` from `private` to `internal`, or extract a pure `mapSillyTavernLorebook(json: String): Lorebook?` function that both the standalone import and card import can call.

3. **Dialog state** — Hoist a `PendingImport` state to `SillyTavernImporter` composable:
   ```kotlin
   data class PendingImport(
       val assistant: Assistant,
       val lorebooks: List<Lorebook>,  // detected, not yet persisted
   )
   ```
   When non-null, render `RikkaConfirmDialog` with binding summary.

4. **Persistence on confirm** — The `onImport` callback signature changes to:
   ```kotlin
   onImport: (assistant: Assistant, lorebooks: List<Lorebook>) -> Unit
   ```
   `AssistantPage.kt` wires this to: persist lorebooks via `settingsStore.update`, then `update(assistant.copy(lorebookIds = assistant.lorebookIds + newLorebookIds)); state.confirm()`.

5. **No new Assistant fields** — `Assistant.lorebookIds` already exists. No model change needed.

6. **No new data model classes** — Reuse `SillyTavernLorebook`/`SillyTavernEntry` (lift visibility) and existing `LorebookSerializer` mapper.

## Files to Modify

| File | Change |
|---|---|
| `ExportSerializer.kt` | Lift `SillyTavernLorebook`, `SillyTavernEntry`, `tryImportSillyTavern` visibility to `internal` |
| `AssistantImporter.kt` | Add binding detection; change `importAssistantFromUri` to return `PendingImport`; add dialog composable |
| `AssistantPage.kt` | Update `onUpdate` callback to handle lorebook persistence |
| `strings.xml` (values + values-zh) | New strings for dialog title, body, buttons |

## Edge Cases
- Empty `character_book` object → treated as no bindings
- Entries missing `key` or `content` → skipped, counted in toast
- Large world book (100+ entries) → dialog shows count, not full list
- User cancels dialog (back press) → equivalent to skip
- Duplicate import → each import creates new Lorebook with fresh UUID (consistent with standalone import)

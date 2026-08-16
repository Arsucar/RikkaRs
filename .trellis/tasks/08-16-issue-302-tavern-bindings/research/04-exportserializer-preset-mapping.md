# Research: ExportSerializer — SillyTavern preset & lorebook import mapping

- **Query**: Show the existing SillyTavern preset JSON import mapping logic (referenced as #188).
- **Scope**: internal
- **Date**: 2026-08-16

## File

`app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt` (450 lines)

## Top-level types

- `ExportData` (L23-28) — wrapper `{version, type, data}` for RikkaHub's native serialized payloads.
- `ExportSerializer<T>` interface (L30-68) — `export/import` contract plus utilities `readUri`, `getUriFileName`, `getExportFileName`, `exportToJson`.
- `ExportSerializer.DefaultJson` (L62-66) — shared Json instance:
  ```kotlin
  Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      prettyPrint = false
  }
  ```

## PresetSerializer (L71-269)

`object PresetSerializer : ExportSerializer<Preset>`, type = `"preset"`.

### import (L88-97)
1. `readUri(context, uri)` → JSON string
2. `tryImportNative(json)` — RikkaHub native format `{type: "preset", data: Preset}` (L99-118)
   - Re-randomizes `Preset.id` and each `PresetEntry.id` to avoid duplicates when the same file is imported twice.
3. Falls back to `tryImportSillyTavernPreset(json, fileName)` (L126-199).

### tryImportSillyTavernPreset (L126-199) — the #188 logic
Decodes `SillyTavernPreset` (see file 03). Pipeline:
1. `rawPrompts = stPreset.prompts.orEmpty()`; returns null if empty.
2. Builds `promptsByKey: LinkedHashMap<String, SillyTavernPrompt>`:
   - key = `prompt.identifier` (or `"#index"` when identifier is blank)
   - first occurrence wins on duplicate identifier
3. Resolves order list from `promptOrder`:
   - Filter to groups with non-null `order`
   - Pick group with `characterId == ST_DEFAULT_CHARACTER_ID (100001)`; else first group
   - Missing `prompt_order` → fall back to `promptsByKey.keys.map { it to true }`
4. For each ordered key, look up prompt:
   - Skip if `marker == true` (system placeholders like `chatHistory`, `worldInfoBefore`, `charDescription`) — counted in `skippedMarkers`
   - Skip if `content` blank
   - Build `PresetEntry.Custom`:
     - `id = Uuid.random()`
     - `enabled = (prompt.enabled ?: true) && orderEntry.enabled` (AND of both flags)
     - `order = entries.size` (positional index)
     - `position = mapSillyTavernPresetPosition(prompt.injectionPosition, stRole)` (L211-218)
     - `injectDepth = prompt.injectionDepth ?: 4`
     - `role = normalizeSillyTavernEntryRole(stRole)` (L238-239) — SYSTEM folds to USER, ASSISTANT stays ASSISTANT
     - `name = prompt.name ?: prompt.identifier ?: ""` (never exposes synthetic `"#index"` keys)
     - `content = content`
5. Returns null if `entries.isEmpty()`.
6. Builds `Preset` with:
   - `id = Uuid.random()`
   - `name = fileName ?: LocalDateTime.now().toLocalString()`
   - `description = buildSillyTavernPresetDescription(stPreset, skippedMarkers)` (L245-268)
   - `entriesVersion = PRESET_ENTRIES_VERSION`

### Position mapping (L211-218) — distinct from lorebook mapping
```kotlin
internal fun mapSillyTavernPresetPosition(injectionPosition: Int?, role: MessageRole): InjectionPosition = when {
    injectionPosition == 1 -> InjectionPosition.AT_DEPTH
    role == MessageRole.SYSTEM -> InjectionPosition.AFTER_SYSTEM_PROMPT
    else -> InjectionPosition.TOP_OF_CHAT
}
```
Comment at L201-210 explicitly warns: **do NOT reuse `LorebookSerializer`'s world-book position mapping** — preset `injection_position` only has 0=relative / 1=absolute, semantics entirely different from lorebook's 0..4 values.

### Role normalization (L238-239)
`SYSTEM` is folded into `USER` because:
- `PresetEntry` contract only allows USER/ASSISTANT (UI selector only has these two).
- `createMergedInjectionMessages` already downgrades SYSTEM to user at injection time, but would split same-depth SYSTEM/USER into separate messages and break ST's order-sensitive @Depth prompts.

### Description builder (L245-268)
Lists unmapped ST top-level settings (`impersonation_prompt`, `new_chat_prompt`, `new_group_chat_prompt`, `new_example_chat_prompt`, `continue_nudge_prompt`, `scenario_format`, `personality_format`, `group_nudge_prompt`, `wi_format`) and skipped marker count into `Preset.description` so the user can see what was dropped.

## LorebookSerializer (L271-355)

`object LorebookSerializer : ExportSerializer<Lorebook>`, type = `"lorebook"`.

### import (L285-293)
1. `readUri(context, uri)` → JSON
2. `tryImportNative(json)` — RikkaHub native `{type: "lorebook", data: Lorebook}` (L296-312)
   - Re-randomizes `Lorebook.id` and each `RegexInjection.id`
3. Falls back to `tryImportSillyTavern(json, fileName)` (L314-343).

### tryImportSillyTavern (L314-343)
Decodes `SillyTavernLorebook` (see file 03). For each `SillyTavernEntry`:
- Builds `PromptInjection.RegexInjection` with:
  - `id = Uuid.random()`
  - `name = entry.comment.orEmpty().ifEmpty { entry.key.firstOrNull().orEmpty() }`
  - `enabled = !entry.disable`
  - `priority = entry.order` (note: ST `order` is inverted priority — higher = earlier — maps directly to RikkaHub `priority`)
  - `position = mapSillyTavernPosition(entry.position)` (L345-354)
  - `injectDepth = entry.depth`
  - `content = entry.content`
  - `keywords = entry.key`
  - `useRegex = false` (ST format does not expose regex flag in this model — see caveat)
  - `caseSensitive = entry.caseSensitive ?: false`
  - `scanDepth = entry.scanDepth ?: 4`
  - `constantActive = entry.constant`

Returns `Lorebook(id, name, description="", enabled=true, entries)`.

### Lorebook position mapping (L345-354)
```kotlin
private fun mapSillyTavernPosition(position: Int): InjectionPosition = when (position) {
    0 -> InjectionPosition.BEFORE_SYSTEM_PROMPT
    1 -> InjectionPosition.AFTER_SYSTEM_PROMPT
    2 -> InjectionPosition.TOP_OF_CHAT
    3 -> InjectionPosition.TOP_OF_CHAT // After Examples -> chat history top
    4 -> InjectionPosition.AT_DEPTH
    else -> InjectionPosition.AFTER_SYSTEM_PROMPT
}
```
Note: newer ST positions (5,6,...) all collapse to `AFTER_SYSTEM_PROMPT`. Also `BOTTOM_OF_CHAT` is never produced — ST has no direct equivalent.

## Reuse hooks for issue #302

- `LorebookSerializer.tryImportSillyTavern(json, fileName)` is `private` — must be lifted to `internal` or `public` if the importer wants to feed it an embedded `data.character_book` JSON string.
- `PresetSerializer.tryImportSillyTavernPreset(json, fileName)` is `internal` — already accessible within the `data.export` package, but the importer is in `ui.pages.assistant.detail`; same lift needed.
- Alternative: factor the pure mapping functions into a shared `internal` object (e.g. `SillyTavernMappers`) so both the serializer UI flow and the importer can call them.
- `ExportSerializer.DefaultJson` is already `public` via the companion; importer can reuse it for parsing.

## Caveats / Not found

- ST `SillyTavernEntry` model does NOT capture `selective` / `selective_logic` / `vectorized` / `extensions` — ST world info entries are richer than what RikkaHub imports. Any `constant: true` selective entries with secondary keys will be flattened to keyword-only matching.
- ST preset `SillyTavernPrompt` does NOT capture `role` values beyond user/assistant/system; `marker` placeholders are skipped (counted) but not surfaced beyond the description string.
- Tests live in `app/src/test/java/me/rerere/rikkahub/data/export/SillyTavernPresetImportTest.kt` — also exercises `LorebookSerializer` round-trips (L645).

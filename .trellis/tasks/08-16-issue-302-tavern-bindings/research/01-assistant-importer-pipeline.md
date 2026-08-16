# Research: AssistantImporter — full tavern card import pipeline

- **Query**: Show the full import flow: how PNG/JSON character cards are parsed, what fields are extracted, how the Assistant is created.
- **Scope**: internal
- **Date**: 2026-08-16

## File

`app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt` (291 lines)

## UI entry point

`AssistantImporter` (L48-60) is a thin wrapper Composable rendering `SillyTavernImporter` in a Row.
`SillyTavernImporter` (L62-147) renders two `OutlinedButton`s:
- PNG button (L127-135): launches `pngPickerLauncher` with `arrayOf("image/png")`. String key `R.string.assistant_importer_import_tavern_png`.
- JSON button (L137-145): launches `jsonPickerLauncher` with `arrayOf("application/json")`. String key `R.string.assistant_importer_import_tavern_json`.

Both launchers share `ActivityResultContracts.OpenDocument()` and the same handler `importAssistantFromUri`. `isLoading` gates the buttons; on success it calls `onImport(assistant)` which is wired through `AssistantPage.kt` L419-425 to `update(it)` + `state.confirm()`.

## Core parsing strategy (L149-249)

### TavernCardParser interface (L151-154)
```kotlin
private interface TavernCardParser {
    val specName: String
    fun parse(context: Context, json: JsonObject, background: String?): Assistant
}
```

### TAVERN_PARSERS registry (L233-236)
A `Map<String, TavernCardParser>` keyed by `specName`, populated with `CharaCardV2Parser` (spec = `"chara_card_v2"`) and `CharaCardV3Parser` (spec = `"chara_card_v3"`).

### CharaCardV2Parser (L156-193) — reads `json["data"]` sub-object
Fields extracted from `data`:
- `name` (required, errors with `R.string.assistant_importer_missing_name_field`)
- `first_mes` → `presetMessages` (wrapped in `UIMessage.assistant(firstMessage)`; empty list if null)
- `system_prompt` → `system` (folded into prompt)
- `description` → `description`
- `personality` → `personality`
- `scenario` → `scenario`

Builds a single `systemPrompt` string:
```
You are roleplaying as $name.

<system_prompt if non-blank>

## Description of the character
<description ?: "Empty">

## Personality of the character
<personality ?: "Empty">

## Scenario
<scenario ?: "Empty">
```

Returns:
```kotlin
Assistant(
    name = name,
    presetMessages = if (firstMessage != null) listOf(UIMessage.assistant(firstMessage)) else emptyList(),
    systemPrompt = prompt,
    background = background
)
```
**Note**: only `name`, `presetMessages`, `systemPrompt`, `background` are set; all other Assistant fields default. No `presetIds`, `lorebookIds`, `tags`, etc. are populated from the card.

### CharaCardV3Parser (L195-231)
Identical to v2 in behavior — extracts the same six fields from `data` and builds the same prompt. v3-specific extensions are NOT read.

### parseAssistantFromJson (L238-247)
1. Reads `json["spec"]` (required → errors `R.string.assistant_importer_missing_spec_field`).
2. Looks up parser in `TAVERN_PARSERS`; unknown spec → errors `R.string.assistant_importer_unsupported_spec`.
3. Delegates to `parser.parse(...)`.

## importAssistantFromUri (L251-291) — file-type dispatch

```kotlin
private suspend fun importAssistantFromUri(context, uri, onImport, toaster, filesManager)
```

1. `mime = filesManager.getFileMimeType(uri)` (Dispatchers.IO)
2. Branch on mime:
   - `"image/png"`:
     - `ImageUtils.getTavernCharacterMeta(context, uri)` → `Result<String>` of Base64 character data
     - `Base64.decode(base64Data, Base64.DEFAULT)` → JSON string
     - `filesManager.createChatFilesByContents(listOf(uri)).first().toString()` → background URI (uploaded to upload folder as managed file)
     - Returns `(jsonString, backgroundStr)`
   - `"application/json"`:
     - Reads file text via `contentResolver.openInputStream(uri).bufferedReader().readText()`
     - Returns `(jsonString, null)` — no background
   - else: errors `R.string.assistant_importer_unsupported_file_type`
3. `Json.parseToJsonElement(jsonString).jsonObject` → parse to JsonObject
4. `parseAssistantFromJson(context, json, background)` → Assistant
5. `onImport(assistant)` — succeeds silently; errors go to `toaster.show(..., type = ToastType.Error)`

## Pipeline summary (as requested)

```
PNG file URI
  → filesManager.getFileMimeType → "image/png"
  → ImageUtils.getTavernCharacterMeta(uri)    [reads PNG tEXt chunk, extracts [chara:...] payload]
  → Base64.decode → JSON string
  → filesManager.createChatFilesByContents([uri])   [copies PNG into upload folder, returns file URI for background]
  → Json.parseToJsonElement → JsonObject
  → parseAssistantFromJson:
      read json["spec"] → "chara_card_v2" | "chara_card_v3"
      TAVERN_PARSERS[spec].parse(json, background):
        json["data"] → {name, first_mes, system_prompt, description, personality, scenario}
        → build single systemPrompt string
        → Assistant(name, presetMessages=[first_mes], systemPrompt, background)
  → onImport(assistant)
```

JSON path skips the PNG/background steps; everything from `Json.parseToJsonElement` onward is identical.

## What is NOT parsed (gap for issue #302)

- `data.extensions` — **not read at all** (this is where `world`/`character_book`/presets live per the issue)
- `data.character_book` (v2/v3 inline world book)
- `data.extensions.world` (world_info entries)
- `data.extensions.depth_prompt`
- `data.tags`, `data.creator`, `data.character_version`, `data.alternate_greetings`, `data.post_history_instructions` — none are read
- No assets / character book export file references

The parsers are pure functions of `JsonObject` → `Assistant`, so adding binding extraction is low-friction; the parsers just need to return extra structured data alongside the Assistant.

## Related files

| File | Role |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantPage.kt` L419 | Hosts `AssistantImporter` and wires `onImport = { update(it); state.confirm() }` |
| `app/src/main/java/me/rerere/rikkahub/utils/ImageUtils.kt` L301 | `getTavernCharacterMeta` |
| `app/src/main/java/me/rerere/rikkahub/data/files/FilesManager.kt` L133 | `createChatFilesByContents` |
| `app/src/main/res/values/strings.xml` L213-214 | `assistant_importer_import_tavern_png/json` |
| `app/src/main/res/values/strings.xml` L584 | `export_import_success` |

## Caveats / Not Found

- Both v2 and v3 parsers are byte-identical in behavior; v3's `extensions.character_book` / `extensions.world` are silently dropped today.
- `presetMessages` only ever holds the first greeting; `alternate_greetings` is ignored.
- No confirmation dialog exists anywhere in the current import path — `onImport` is called directly after parse, and `AssistantPage` immediately writes the assistant via `update(it); state.confirm()`.

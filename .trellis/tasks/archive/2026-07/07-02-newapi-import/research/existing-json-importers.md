# Research: Existing JSON import patterns (Cherry Studio, Chatbox)

- **Query**: JSON parsing / format detection logic reusable for NewAPI import
- **Scope**: internal
- **Date**: 2026-07-02

## Findings

### Importer modules

Only two provider-focused importers under `app/src/main/java/me/rerere/rikkahub/data/sync/importer/`:

1. `CherryStudioProviderImporter.kt`
2. `ChatboxImporter.kt`

### Cherry Studio pattern

- Input: `.zip` backup, entry `data.json`
- Detection: structural — missing `data.json` or `persist:cherry-studio` / `llm` throws `IllegalArgumentException`
- Maps `type` string → `Claude` / `Google` / default `OpenAI`
- Fields: `apiKey`, `name`, `apiHost` → `normalizeBaseUrl(..., suffix="/v1" or "/v1beta")`, `models[]` via `parseModels`
- Dedup: `distinctBy { importedProviderKey }` where key is `openai|baseUrl|apiKey` (or google/claude variants)
- Skips providers with blank `apiKey`

### Chatbox pattern

- Input: UTF-8 JSON file
- `importProviders(file)`: `readSettings(reader)` then `importProviders(JsonObject(mapOf("settings" to settings)))`
- Reads `settings.providers.openai|claude|gemini` objects with `apiHost`, `apiKey`, optional `models`
- OpenAI: builds `Model` list from `capabilities` (vision, tool_use, reasoning)
- Skips entries with blank `apiKey`

### UI integration

| Importer | VM method | UI |
|----------|-----------|-----|
| Cherry Studio | `BackupVM.restoreFromCherryStudio` | `ImportExportTab` |
| Chatbox | `BackupVM.restoreFromChatBox` / `importStreaming` | `ImportExportTab` |

Merges imported providers **ahead of** existing: `importProviders + settings.value.providers`.

### Contrast with QR import

| Aspect | QR (`decodeProviderSetting`) | File importers |
|--------|------------------------------|----------------|
| Format | Single prefixed string | File type + JSON shape |
| Output | One `ProviderSetting` | `List<ProviderSetting>` |
| UI surface | `SettingProviderPage` | Backup tab |
| Model list | Empty in share encode; decode restores whatever is in JSON | Cherry parses models; Chatbox partial |

## Suggested integration points (factual only)

- NewAPI JSON importer would logically live beside `CherryStudioProviderImporter.kt` as `NewApiChannelImporter` (or similar) **or** extend import dialog to parse pasted/scanned text before/alongside `decodeProviderSetting`.
- Reuse patterns: `JsonInstant.parseToJsonElement`, `jsonObjectOrNull`, blank key skip, `normalizeBaseUrl`, `ProviderSetting.OpenAI` as default gateway type.

## Caveats / Not Found

- No shared `ProviderImportRouter` abstraction; each format is separate.
- NewAPI/OneAPI channel JSON field names not documented in this repo — need external spec or issue #19 description when implementing.
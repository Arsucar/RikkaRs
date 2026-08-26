# Research: SillyTavern data model classes (chara_card / preset / lorebook)

- **Query**: Find existing JSON model classes for tavern cards (chara_card_v2/v3, world_info/character_book, presets).
- **Scope**: internal
- **Date**: 2026-08-16

## Summary

There is **NO** typed `chara_card_v2`/`chara_card_v3` data class. The character card parser works on raw `JsonObject` and plucks fields by key. There ARE typed SillyTavern preset and lorebook data classes (private to `ExportSerializer.kt`) used by the standalone import flows.

## Character card model

`AssistantImporter.kt` L156-231 uses `kotlinx.serialization.json.JsonObject` + `jsonPrimitiveOrNull?.contentOrNull` — see file `01-assistant-importer-pipeline.md`. No `@Serializable data class CharaCardV2` / `CharaCardV3` exists.

Fields currently read from `data`:
- `name`, `first_mes`, `system_prompt`, `description`, `personality`, `scenario`

Fields NOT modeled (relevant to #302):
- `spec`, `spec_version`, `data` (container)
- `data.extensions.*` — including `world`, `depth_prompt`, `character_book`, preset references
- `data.character_book` — direct inline world info (v2/v3)
- `data.tags`, `data.creator`, `data.character_version`, `data.alternate_greetings`, `data.post_history_instructions`, `data.group_only_greetings`

## SillyTavern preset models (issue #188)

`app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt` L383-450 — `internal` to that file:

### `SillyTavernPreset` (L383-409)
```kotlin
@Serializable
internal data class SillyTavernPreset(
    val prompts: List<SillyTavernPrompt>? = null,
    @SerialName("prompt_order") val promptOrder: List<SillyTavernPromptOrder>? = null,
    @SerialName("impersonation_prompt") val impersonationPrompt: String? = null,
    @SerialName("new_chat_prompt") val newChatPrompt: String? = null,
    @SerialName("new_group_chat_prompt") val newGroupChatPrompt: String? = null,
    @SerialName("new_example_chat_prompt") val newExampleChatPrompt: String? = null,
    @SerialName("continue_nudge_prompt") val continueNudgePrompt: String? = null,
    @SerialName("scenario_format") val scenarioFormat: String? = null,
    @SerialName("personality_format") val personalityFormat: String? = null,
    @SerialName("group_nudge_prompt") val groupNudgePrompt: String? = null,
    @SerialName("wi_format") val wiFormat: String? = null,
)
```

### `SillyTavernPrompt` (L418-433)
```kotlin
@Serializable
internal data class SillyTavernPrompt(
    val identifier: String? = null,
    val name: String? = null,
    val role: String? = null,
    val content: String? = null,
    val enabled: Boolean? = null,
    val marker: Boolean? = null,
    @SerialName("system_prompt") val systemPrompt: Boolean? = null,
    @SerialName("injection_position") val injectionPosition: Int? = null,
    @SerialName("injection_depth") val injectionDepth: Int? = null,
    @SerialName("forbid_overrides") val forbidOverrides: Boolean? = null,
)
```

### `SillyTavernPromptOrder` (L440-444), `SillyTavernPromptOrderEntry` (L447-450)
```kotlin
@Serializable
internal data class SillyTavernPromptOrder(
    @SerialName("character_id") val characterId: Int? = null,
    val order: List<SillyTavernPromptOrderEntry>? = null,
)
@Serializable
internal data class SillyTavernPromptOrderEntry(
    val identifier: String? = null,
    val enabled: Boolean? = null,
)
```

## SillyTavern lorebook models

`ExportSerializer.kt` L357-374 — `private` to that file:

### `SillyTavernLorebook` (L357-360)
```kotlin
@Serializable
private data class SillyTavernLorebook(
    val entries: Map<String, SillyTavernEntry> = emptyMap(),
)
```

### `SillyTavernEntry` (L362-374)
```kotlin
@Serializable
private data class SillyTavernEntry(
    val key: List<String> = emptyList(),
    val content: String = "",
    val comment: String? = null,
    val constant: Boolean = false,
    val position: Int = 0,
    val order: Int = 100,
    val disable: Boolean = false,
    val depth: Int = 4,
    val scanDepth: Int? = null,
    val caseSensitive: Boolean? = null,
)
```

These map to RikkaHub `PromptInjection.RegexInjection` via `LorebookSerializer.tryImportSillyTavern` (L314-343). **Note**: position mapping only handles 0..4; ST actually has more positions (e.g. `5=before char description`, `6=after char description`, etc. in newer versions) — the `else` branch falls back to `AFTER_SYSTEM_PROMPT`.

## World info / character_book inside character cards — NOT modeled

SillyTavern v2/v3 cards embed world info under `data.character_book` (a `SillyTavernLorebook`-shaped object) and/or `data.extensions.world` / `data.extensions.character_book`. There is **no** existing code path that reads these embedded bindings from a character card JSON — only the standalone lorebook JSON import (`LorebookSerializer.import`) handles world info.

The `SillyTavernLorebook` / `SillyTavernEntry` model is currently `private` to `ExportSerializer.kt`, so reusing it from `AssistantImporter.kt` requires either:
1. Promoting visibility to `internal`/`public`, or
2. Re-declaring an equivalent model in the importer module, or
3. Reusing the import logic by routing embedded world info JSON through `LorebookSerializer.tryImportSillyTavern` (currently `private`; would need to be lifted).

## Files

| File | Line range | Contents |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt` | L357-374 | `SillyTavernLorebook`, `SillyTavernEntry` (private) |
| `app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt` | L383-450 | `SillyTavernPreset`, `SillyTavernPrompt`, `SillyTavernPromptOrder`, `SillyTavernPromptOrderEntry` (internal) |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt` | L156-231 | CharaCard v2/v3 parsers (use raw JsonObject, no model class) |

## Caveats / Not found

- No v3-specific extensions model (no `assets`, `group_only_greetings`, `alternate_greetings`).
- No tests for character card parsing (only `SillyTavernPresetImportTest.kt` for the preset serializer).
- `LorebookSerializer.tryImportSillyTavern` is `private` — reusing it from the importer requires a visibility change.

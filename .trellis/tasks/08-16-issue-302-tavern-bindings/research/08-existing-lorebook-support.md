# Research: Existing world info / lorebook support in the codebase

- **Query**: Search for any existing world info / lorebook data models and runtime support.
- **Scope**: internal
- **Date**: 2026-08-16

## Summary

RikkaHub already has a full **Lorebook** subsystem (the local equivalent of SillyTavern world info). It is NOT inside character cards — it lives in `Settings.lorebooks` and is bound to an Assistant via `Assistant.lorebookIds` (or per-conversation via `Conversation.lorebookIds`).

## Data model

### `Lorebook` — `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` L275-282
```kotlin
@Serializable
data class Lorebook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val entries: List<PromptInjection.RegexInjection> = emptyList(),
)
```

### `PromptInjection.RegexInjection` — same file L238-252
Keyword-triggered injection entry. Fields: `keywords`, `useRegex`, `caseSensitive`, `scanDepth`, `constantActive`, plus inherited `priority`, `position`, `injectDepth`, `role`, `content`, `enabled`, `name`, `id`.

### `InjectionPosition` — same file L178-194
5 positions: `BEFORE_SYSTEM_PROMPT`, `AFTER_SYSTEM_PROMPT`, `TOP_OF_CHAT`, `BOTTOM_OF_CHAT`, `AT_DEPTH`.

## Binding surfaces

### Assistant binding
`Assistant.lorebookIds: Set<Uuid>` (L58) — IDs into `Settings.lorebooks`.

### Conversation override
`Conversation.lorebookIds: Set<Uuid>` (`Conversation.kt` L29) — when non-empty, overrides the assistant-level set at runtime.

### Settings persistence
`Settings.lorebooks: List<Lorebook>` (`PreferencesStore.kt` L1694) — JSON-serialized into DataStore key `LOREBOOKS`.

## Runtime injection — `PromptInjectionTransformer`

`app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt`

### `collectInjections` (L72-124)
1. `effectiveLorebookIds = conversationLorebookIds.ifEmpty { assistant.lorebookIds }` (L82)
2. `enabledLorebooks = lorebooks.filter { it.enabled && it.id in effectiveLorebookIds }` (L107-109)
3. For each enabled lorebook:
   - Filters out SYSTEM messages from the conversation
   - For each entry: builds context via `extractContextForMatching(nonSystemMessages, entry.scanDepth)` and checks `entry.isTriggered(context)`
   - Triggered entries are appended to the injection list
4. Preset entries are also expanded in the same pass (L88-104) — produces `PromptInjection.ResolvedInjection` per enabled `PresetEntry`.

### `transformMessages` (L43-67)
Sorts all injections by `priority DESC`, groups by `position`, then calls `applyInjections`.

### `applyInjections` (L181-284)
Inserts injections into the message list at the correct position depending on `InjectionPosition`:
- `BEFORE/AFTER_SYSTEM_PROMPT` — merged into the system message text (or a new system message if none)
- `TOP_OF_CHAT` — before the first USER message
- `BOTTOM_OF_CHAT` — before the last message
- `AT_DEPTH` — N messages back from the end, grouped by `injectDepth`

### Trigger evaluation — `Assistant.kt` L416-437
```kotlin
fun PromptInjection.RegexInjection.isTriggered(context: String): Boolean {
    if (!enabled) return false
    if (constantActive) return true
    if (keywords.isEmpty()) return false
    return keywords.any { keyword ->
        if (useRegex) {
            try { Regex(keyword, options).containsMatchIn(context) } catch (e: Exception) { false }
        } else {
            if (caseSensitive) context.contains(keyword)
            else context.contains(keyword, ignoreCase = true)
        }
    }
}
```

### Context extraction — `Assistant.kt` L446-453
```kotlin
fun extractContextForMatching(messages: List<UIMessage>, scanDepth: Int): String =
    messages.takeLast(scanDepth).joinToString("\n") { it.toText() }
```

## Import path — standalone lorebook JSON

`LorebookSerializer` in `ExportSerializer.kt` L271-355 handles standalone `.json` files (RikkaHub native format OR SillyTavern world info format). Already wired into the Extensions page UI:

### `PromptPage.kt` L417-424
```kotlin
val importer = rememberImporter(LorebookSerializer) { result ->
    result.onSuccess { imported ->
        onUpdate(currentLorebooks + imported)
        toaster.show(importSuccessMsg)
    }.onFailure { error -> toaster.show(importFailedMsg.format(error.message)) }
}
```

So importing a SillyTavern world info JSON as a standalone file already works end-to-end via the Extensions → Lorebooks tab.

## What's missing for #302

The gap is **ONLY** reading embedded `data.character_book` / `data.extensions.world` from inside a character card and routing it through `LorebookSerializer.tryImportSillyTavern` (currently `private`).

There is NO:
- Character-card-level field on `Assistant` for world info (by design — bindings are ID-based).
- Field on the card parsers (`CharaCardV2Parser`/`CharaCardV3Parser`) that reads `data.character_book` or `data.extensions.*`.
- Typed model for `SillyTavernLorebook` outside `ExportSerializer.kt` (it's `private`).

## SillyTavern world_info entry model (existing, private)

`ExportSerializer.kt` L357-374:
```kotlin
@Serializable
private data class SillyTavernLorebook(val entries: Map<String, SillyTavernEntry> = emptyMap())

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
This shape matches the SillyTavern `character_book.entries[]` structure embedded in v2/v3 cards. The position mapping (`mapSillyTavernPosition`, L345-354) handles positions 0..4; newer ST positions (5,6,...) fall back to `AFTER_SYSTEM_PROMPT`.

## Related files

| File | Role |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` L275-282 | `Lorebook` model |
| `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` L238-252 | `PromptInjection.RegexInjection` |
| `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` L416-469 | Trigger & context extraction helpers |
| `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt` | Runtime injection transformer |
| `app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt` L271-355 | `LorebookSerializer` (standalone JSON import) |
| `app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt` L357-374 | `SillyTavernLorebook`/`SillyTavernEntry` (private) |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/PromptPage.kt` L417 | Lorebook import button |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantExtensionsPage.kt` L228 | Lorebook binding UI per assistant |
| `app/src/test/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformerTest.kt` | Lorebook runtime tests |

## Caveats / Not found

- No existing code reads `data.extensions.*` from a character card. The issue's reference to `data.extensions.world` matches the SillyTavern spec where world info can be embedded either as `data.character_book` (canonical v2/v3) or as `data.extensions.character_book` / `data.extensions.world` (some exporters). Both shapes should be probed when implementing #302.
- ST world info entries also support `selective` (secondary keywords) and `selective_logic` (AND/OR/NOT) — these are NOT captured by `SillyTavernEntry` today and will be flattened to keyword-OR matching on import.
- ST world info `vectorized` / `extensions` sub-objects are ignored.
- No standalone "world info file" import (`.json` with top-level `entries: {...}`) beyond `LorebookSerializer` — which already handles that shape via `tryImportSillyTavern`.

# Research: Assistant data model — all fields, lorebook/preset bindings

- **Query**: Show the Assistant model structure, especially world info / preset / lorebook fields.
- **Scope**: internal
- **Date**: 2026-08-16

## File

`app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` (469 lines)

## Assistant data class (L16-81)

```kotlin
@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val chatModelId: Uuid? = null,                              // null = global default model
    val name: String = "",
    val isArchived: Boolean = false,
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false,
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    val contextMessageLimit: Int = 0,                           // 0 = unlimited; stepped truncation otherwise
    val autoCompressEnabled: Boolean = false,                   // #59 fork
    val autoCompressThresholdTokens: Int = 8000,
    val autoCompressKeepRecentMessages: Int = 32,
    val streamOutput: Boolean = true,
    val enableWebSearch: Boolean = false,
    val enableMemory: Boolean = false,
    val useGlobalMemory: Boolean = false,
    val enableMemoryTable: Boolean = false,
    val enableSemanticMemory: Boolean = false,                  // SemanticMemory Plugin
    val enableRecentChatsReference: Boolean = false,
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val regexes: List<AssistantRegex> = emptyList(),
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo),
    val workspaceId: Uuid? = null,
    val defaultWorkspaceCwd: String? = null,
    val background: String? = null,                             // local file URI or network URL for chat bg
    val backgroundOpacity: Float = 1.0f,
    val useGradientBackground: Boolean = false,
    val presetIds: Set<Uuid> = emptySet(),                      // associated preset IDs (issue #65)
    val lorebookIds: Set<Uuid> = emptySet(),                   // associated Lorebook IDs
    val enabledSkills: Set<String> = emptySet(),
    val enableTimeReminder: Boolean = false,
    val allowConversationSystemPrompt: Boolean = false,
    val enableSubagents: Boolean = false,
    val subagentMaxDepth: Int = 2,
    val subagentMaxConcurrent: Int = 3,
    val parallelToolExecution: Boolean = false,
    val subagentDelegateOnly: Boolean = false,
    val subagentProfiles: List<SubagentProfile> = emptyList(),
    val disabledBuiltinSubagents: Set<String> = emptySet(),
    val disabledGlobalSubagents: Set<String> = emptySet(),
    val stepsCountdownThreshold: Int? = null,
    val hooks: List<ConversationHook> = emptyList(),
    val toolPermissions: Map<String, ToolPermission> = emptyMap(),
    val enableVariableSystem: Boolean = false,                  // #217/#216 macros + MVU
    val experimentalFeatureOverrides: Map<String, Boolean> = emptyMap(), // #215
)
```

## Extension binding fields (relevant to #302)

The Assistant already has the fields needed to associate imported bindings:

| Field | Type | Purpose |
|---|---|---|
| `presetIds` | `Set<Uuid>` | IDs into `Settings.presets: List<Preset>` |
| `lorebookIds` | `Set<Uuid>` | IDs into `Settings.lorebooks: List<Lorebook>` |
| `quickMessageIds` | `Set<Uuid>` | IDs into `Settings.quickMessages` |
| `enabledSkills` | `Set<String>` | skill names |
| `mcpServers` | `Set<Uuid>` | MCP server config IDs |
| `tags` | `List<Uuid>` | tag IDs |

**No new Assistant field is required for #302.** World info imported from a character card becomes a new `Lorebook` entry in `Settings.lorebooks`, and its ID is added to `Assistant.lorebookIds`. Same for presets → `Settings.presets` + `Assistant.presetIds`.

## Companion types in the same file

### `Lorebook` (L275-282)
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
This is the RikkaHub world book container. Each entry is a `PromptInjection.RegexInjection` (keyword-triggered injection).

### `Preset` (L290-309)
```kotlin
@Serializable
data class Preset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val modeInjectionIds: Set<Uuid> = emptySet(),   // legacy migration-only
    val disabledEntryIds: Set<Uuid> = emptySet(),   // legacy migration-only
    val entries: List<PresetEntry> = emptyList(),   // edit/toggle/sort entries (#182)
    val entriesVersion: Int = 0,                    // 0=legacy; 1=entries model
    val draftContext: DraftContextConfig? = null,   // #196 reply-draft context
)
```

### `PresetEntry` (in `PresetEntry.kt`)
Sealed class with `Custom` and `Builtin` variants — see file 06 for full schema. SillyTavern preset imports produce only `Custom` entries.

### `PromptInjection` sealed class (L205-253)
- `ResolvedInjection` (`@SerialName("mode")`) — runtime-resolved, produced from preset entries; NOT persisted.
- `RegexInjection` (`@SerialName("regex")`) — keyword-triggered; this is what Lorebook stores.

### `RegexInjection` fields (L238-252)
```kotlin
data class RegexInjection(
    override val id: Uuid = Uuid.random(),
    override val name: String = "",
    override val enabled: Boolean = true,
    override val priority: Int = 0,
    override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
    override val content: String = "",
    override val injectDepth: Int = 4,
    override val role: MessageRole = MessageRole.USER,
    val keywords: List<String> = emptyList(),
    val useRegex: Boolean = false,
    val caseSensitive: Boolean = false,
    val scanDepth: Int = 4,
    val constantActive: Boolean = false,
) : PromptInjection()
```

### `InjectionPosition` enum (L178-194)
5 values: `BEFORE_SYSTEM_PROMPT`, `AFTER_SYSTEM_PROMPT`, `TOP_OF_CHAT`, `BOTTOM_OF_CHAT`, `AT_DEPTH`. Serialized via `@SerialName` snake_case.

### `AssistantRegex` (L125-134)
```kotlin
data class AssistantRegex(
    val id: Uuid,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "",
    val replaceString: String = "",
    val affectingScope: Set<AssistantAffectScope> = setOf(),
    val visualOnly: Boolean = false,
)
```

### `ToolPermission` enum (L91)
`INHERIT`, `ALLOW`, `ASK`, `DENY`.

### `QuickMessage` (L93-98)
```kotlin
data class QuickMessage(val id: Uuid = Uuid.random(), val title: String = "", val content: String = "")
```

### `LegacyModeInjection` (L259-270)
Migration-only type for old global `mode_injections` DataStore key. Not used for new imports.

## Persistence path for #302

`Settings` (in `PreferencesStore.kt` L1636) holds the canonical lists:
```kotlin
val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
val presets: List<Preset> = emptyList(),
val lorebooks: List<Lorebook> = emptyList(),
val quickMessages: List<QuickMessage> = emptyList(),
```
These are JSON-serialized into DataStore keys `ASSISTANTS`, `PRESETS`, `LOREBOOKS`, `QUICK_MESSAGES` (see `writeFullSettings` L1293+).

To persist imported bindings:
1. `settingsStore.update { settings -> settings.copy(lorebooks = settings.lorebooks + newLorebook) }`
2. Update the assistant: `settings.assistants.map { if (it.id == targetId) it.copy(lorebookIds = it.lorebookIds + newLorebook.id) else it }`

`SettingsStore.update` (L665-683) takes a transform, applies `writeFullSettings`, and re-syncs the flow. For partial updates avoiding full-snapshot race, use `updateAssistantConfig(assistant)` (L744) or the targeted helpers like `toggleAssistantPreset` (L937-995) — the latter writes only the assistant field, not the full Settings.

## Related files

| File | Role |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/model/PresetEntry.kt` | `PresetEntry.Custom/Builtin` schema |
| `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt` L29 | `Conversation.lorebookIds` — conversation-level override |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` L1636 | `Settings` data class; persistence root |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` L744 | `updateAssistantConfig` (partial write) |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` L937 | `toggleAssistantPreset` (partial preset toggle, #218) |

## Caveats / Not found

- `Assistant.background` is the only file:// reference stored on the assistant itself. Lorebooks/presets are NOT stored as files — they live in Settings JSON.
- `Conversation.lorebookIds` (Conversation.kt L29) overrides `Assistant.lorebookIds` at runtime when non-empty — see `PromptInjectionTransformer.collectInjections` L82: `val effectiveLorebookIds = conversationLorebookIds.ifEmpty { assistant.lorebookIds }`. Imported lorebooks attached to an Assistant will apply to all new conversations of that assistant unless the conversation overrides.
- No `worldInfo`/`characterBook` field exists on Assistant — by design; bindings are ID references, not inline content.

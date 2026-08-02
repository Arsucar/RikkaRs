# Assistant Preset Toggle Contract

## Scenario: Chat / assistant extension Presets Switch (#218 / #202)

### 1. Scope / Trigger

- Trigger: enabling or disabling a preset binding on one Assistant from:
  - chat extension selector (`ExtensionSelector` / `PresetsContent`)
  - assistant extensions page (`AssistantExtensionsPage`)
- Applies to `Assistant.presetIds` in Preferences DataStore only.
- Does **not** cover ModeInjection / Lorebook / Skills / QuickMessages switches (still may use full assistant rewrite until their own partial APIs exist).

### 2. Signatures

- `SettingsStore.toggleAssistantPreset(assistantId: Uuid, presetId: Uuid, enabled: Boolean): Boolean`
- `MutablePreferences.writeAssistantPresetToggle(assistantId, presetId, enabled, fallbackAssistants): Boolean`
- `ChatVM.toggleAssistantPreset(assistantId, presetId, enabled): Job` (optional wrapper)
- `AssistantDetailVM.toggleAssistantPreset(presetId, enabled)` (uses current assistant id)

### 3. Contracts

- **Partial write only**: under `updateMutex`, `dataStore.edit` mutates **only** the `ASSISTANTS` key for the target assistant's `presetIds`. Never `writeFullSettings` / full `Settings` snapshot rewrite for this path.
- **Enable is exclusive**: `enabled == true` → `presetIds = setOf(presetId)` (matches chat selector one-active-preset UX). Disable removes only `presetId`.
- **Optimistic UI**: assign `settingsFlow.value` with the optimistic assistants list **before** the disk edit so Switch flips immediately.
- **Latest stored wins**: `writeAssistantPresetToggle` decodes current `ASSISTANTS` JSON when present; uses `fallbackAssistants` only if the key is missing. Stale flow snapshots must not clobber sibling fields (`name`, `enableWebSearch`, etc.).
- **Idempotent no-op**: if computed `presetIds` equals current, return success without rewriting JSON.
- **Failure rollback**: on edit failure, prefer `syncSettingsFlowFromStore()`; if sync also fails, restore the pre-optimistic `fallbackSettings` so UI does not stick on a wrong optimistic state.
- **Missing assistant**: log and return `false`; do not rewrite other assistants.
- **Dummy init settings**: if `settingsFlow.value.init`, refuse the toggle (`false`).

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Dummy / init settings | `false`; no edit |
| Assistant id not in list (flow or store) | `false`; store JSON unchanged |
| Disk edit throws | log; sync or restore fallback; return `false` |
| Enable preset B while A selected | target ends with only B; other assistants untouched |
| Disable selected preset | target loses that id only |
| Concurrent sibling field edit on same assistant | reread store JSON inside edit; preserve sibling fields |

### 5. Good / Base / Bad Cases

- Good: rapid Switch flips serialize on `updateMutex`; final `presetIds` match the last operation; unrelated DataStore keys unchanged.
- Base: assistant with empty `presetIds`; enable once → singleton set; disable → empty.
- Bad: `settings.copy(assistants = …)` then `updateSettings` / `writeFullSettings` for preset Switch (full encode + disk wait → jank; lost concurrent updates).
- Bad: optimistic flow update without rollback path (UI permanently wrong after IO failure).

### 6. Tests Required

- `writeAssistantPresetToggle`: exclusive enable, disable target only, missing assistant no-write, latest-stored-not-stale-fallback, unrelated keys preserved.
- Store-level (when harness exists): mutex serializes concurrent toggles; failure path restores flow.
- Manual: chat + assistant extensions Switch instant flip; kill process; binding persists.

### 7. Wrong vs Correct

#### Wrong

```kotlin
onToggle = { id, checked ->
    val next = if (checked) setOf(id) else assistant.presetIds - id
    onUpdate(assistant.copy(presetIds = next)) // full Settings rewrite
}
```

#### Correct

```kotlin
onToggle = { id, checked ->
    scope.launch {
        settingsStore.toggleAssistantPreset(
            assistantId = assistant.id,
            presetId = id,
            enabled = checked,
        )
    }
}
```

**Related**: [Preset Entries](./preset-entries.md) (`updatePreset` partial pattern), [Assistant Web Search](./assistant-web-search.md) (per-assistant targeted update).

# Experimental Features Registry Contract

## Scenario: Pluggable experimental feature page (#215)

### 1. Scope / Trigger

- Trigger: adding experimental toggles, reading feature gates, rendering global/assistant experiment UIs.
- Migrates: `chat_keepalive`, `checkpoint_cache` (Global), `variable_system` (Assistant).

### 2. Signatures

- `FEATURE_CHAT_KEEPALIVE` / `FEATURE_CHECKPOINT_CACHE` / `FEATURE_VARIABLE_SYSTEM`
- `ExperimentalFeatureScope`, `ExperimentalFeatureStatus`, `FeatureSpec`
- `ExperimentalFeatureRegistry.all|get|byScope`
- `resolveExperimentalFeature(id, settings, assistant?): Boolean`
- `Settings.experimentalFeatures: Map<String, Boolean>`
- `Assistant.experimentalFeatureOverrides: Map<String, Boolean>`
- `SettingsStore.updateExperimentalFeature` / `updateAssistantExperimentalFeature` / `updateAllAssistantsExperimentalFeature`
- `Screen.Experiments` / `Screen.AssistantExperiments(id)`

### 3. Contracts

- **Default off**: empty maps + `defaultEnabled=false`.
- **Resolve order**: explicit map value → legacy bridge boolean → `FeatureSpec.defaultEnabled` → false.
- **Scope isolation**: Global only reads `experimentalFeatures` (+ global legacy); Assistant only reads overrides (+ `enableVariableSystem`).
- **Write**: UI writes maps under mutex; dual-writes legacy booleans for the three bridged ids for one compatibility generation.
- **Hot-plug**: new feature = register `FeatureSpec` + consumer `resolve`; pages list from registry.
- **Scattered UI removed**: notification page and assistant memory page no longer host these switches.
- **Checkpoint interval**: remains Settings field; shown on Global experiments page when checkpoint enabled.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Corrupt experimental_features JSON | emptyMap |
| Unknown id, no map key | false |
| Map missing, legacy true | true (bridge) |
| Map false, legacy true | false (map wins) |
| Cross-scope map pollution | ignored by resolve for wrong scope |

### 5. Tests Required

- `ExperimentalFeatureResolveTest`: isolation, defaults, map-over-legacy, bridge, unknown id.

### 6. Wrong vs Correct

#### Wrong

```kotlin
if (settings.enableKeepAliveNotification) startFgs() // bypasses registry
```

#### Correct

```kotlin
if (resolveExperimentalFeature(FEATURE_CHAT_KEEPALIVE, settings)) startFgs()
```

**Related**: chat-generation-keepalive, conversation-checkpoint-cache, conversation-variables.

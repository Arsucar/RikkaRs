# Assistant Web Search Contract

## Scenario: Assistant-level network search persistence

### 1. Scope / Trigger

- Trigger: adding, migrating, reading, or mutating the network-search enabled state.
- Applies to Assistant JSON in Preferences DataStore, backup JSON migration, Android/Web controls, and generation tool assembly.

### 2. Signatures

- `Assistant.enableWebSearch: Boolean`
- `SettingsStore.updateAssistantWebSearch(assistantId: Uuid, enabled: Boolean)`
- `PreferenceStoreV4Migration : DataMigration<Preferences>`
- Web request: `UpdateSearchEnabledRequest(assistantId: String, enabled: Boolean)`
- Tool gate: `buildGenerationTools(..., assistant: Assistant, ...)`

### 3. Contracts

- Runtime search state comes from the Assistant attached to the conversation, not a global selected Assistant or legacy preference.
- The legacy `enable_web_search` preference is migration input only. One DataStore migration atomically rewrites every missing Assistant field, advances `data_version`, and removes the old key.
- Existing Assistant fields are not overwritten during migration; newly decoded old Assistant JSON defaults to `false` when no legacy migration context exists.
- `SettingsJsonMigrator` performs the equivalent root-field-to-Assistant transform for restored old backups.
- Android and Web mutations carry the target Assistant ID and update only that Assistant.
- `PreparedGenerationRequest` and `buildGenerationTools` retain their existing boundaries; only the gate uses `assistant.enableWebSearch`.

### 4. Validation & Error Matrix

- Malformed Assistant JSON during DataStore migration -> throw; do not write the new version, so startup can retry safely.
- Missing Assistant ID in Android/Web mutation -> no unrelated Assistant update; Web returns not found.
- Assistant search enabled but model has no tool ability -> existing tool-unavailable error path.
- Old backup with root `enableWebSearch` -> copy to Assistants missing the field and remove the root field.

### 5. Good/Base/Bad Cases

- Good: Conversation A uses Assistant A=true and receives search tools while Assistant B=false remains unchanged.
- Base: Fresh Assistant defaults to search disabled until explicitly enabled.
- Bad: `settings.copy(enableWebSearch = enabled)` recreates global runtime state or overwrites unrelated settings.

### 6. Tests Required

- Pure migration tests for legacy true/false, multiple/empty Assistants, existing field preservation, and malformed JSON.
- Actual `DataMigration.migrate()` test asserting Assistants, version, and legacy-key removal are one result.
- Backup migrator test asserting root-field removal and per-Assistant inheritance.
- Targeted update test asserting the non-target Assistant is byte-for-byte equivalent after decoding.
- App compile/unit tests and Web typecheck after request/type changes.

### 7. Wrong vs Correct

#### Wrong

```kotlin
if (settings.enableWebSearch) addAll(createSearchTools(settings))
```

#### Correct

```kotlin
if (assistant.enableWebSearch) addAll(createSearchTools(settings))
```

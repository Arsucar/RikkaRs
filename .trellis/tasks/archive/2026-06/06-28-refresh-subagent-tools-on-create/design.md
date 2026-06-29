# Design: Refresh subagent tools after manageSubagentProfile within same generation round

## Approach

Instead of rebuilding the entire tool list after every `manage_subagent_profile` call (which would be architecturally invasive), make the `spawn_subagent` tool **lazily resolve** profiles at execution time instead of eagerly freezing them at construction time.

Two changes are needed:

### 1. Dynamic profile_name enum → deferred validation

The `profile_name` parameter in `spawn_subagent` currently uses a hardcoded `enum` list built from the frozen `profiles` snapshot. We change it to `"type": "string"` without `enum`, and add validation + helpful error messages at execution time.

**Why not keep enum?** The `enum` constraint is a schema-level constraint — the API provider validates it server-side. Rebuilding the Tool object mid-generation is complex because `tools` is passed as a `List<Tool>` and the loop never re-fetches it. Removing `enum` and adding runtime validation is simpler and allows the model to type any profile name.

**Tradeoff:** The model loses the explicit enum list in the schema. Mitigation: keep the profile list in the tool **description** text (which is already there as `Available subagent profiles:` block), and add runtime validation that returns a clear error listing available profiles if the name doesn't match.

### 2. Live settings resolution in spawn closure

The `spawn` closure inside `createSubagentTools` currently captures `assistant` and `settings` from the frozen snapshot. Change it to read the latest `Settings` from `settingsStore.settingsFlow.first()` at spawn time, then re-derive the `Assistant` from it by ID.

This requires passing `settingsStore` (or a light lambda that fetches current settings + assistant by ID) into the `createSubagentTools` / `ChatService.buildSubagentToolsForChat` closure chain.

### 3. Refresh manage tool's profile list

The `manage_subagent_profile` tool also receives a frozen `profiles` list (used for the "update" base lookup at line 246-251 of SubagentTools.kt). Change `applyPatch` to look up the base profile lazily from live settings at execution time rather than from the frozen `profiles` list.

## Detailed Changes

### A. `SubagentTools.kt` — `createSubagentTools`

**Before:** `profiles: List<SubagentProfile>` is used to:
1. Build `profileNames` → hardcoded `enum` in `profile_name` parameter
2. Build `profileListText` → description text listing available profiles
3. Pass to `spawn` closure (indirectly, via `resolveProfile` at call site in ChatService)

**After:**
```kotlin
fun createSubagentTools(
    json: Json,
    spawn: suspend (profileName: String, task: String, description: String) -> SubagentResult,
    askBtw: suspend (question: String) -> String,
    includeAskBtw: Boolean = true,
): List<Tool>
```

- Remove `profiles` parameter from `createSubagentTools`.
- Change `profile_name` parameter from `enum`-constrained to plain `string`.
- Remove `profileListText` from the static description (the system prompt at line 60-77 already lists profiles dynamically — see change C below).
- In `execute`, if `profileName` doesn't resolve, return error listing currently available profiles.

### B. `SubagentTools.kt` — `createManageSubagentTool`

**Before:** `profiles: List<SubagentProfile>` used for `update` base lookup.

**After:**
- Remove `profiles` parameter (or keep for backward compat but ignore for update).
- In `execute` for "update" action, the `manage` callback already calls `ChatService.manageSubagentProfile` which reads live settings. So the profiles list is only needed for the `applyPatch` base lookup at line 246-251. Move that lookup into the `manage` callback.

### C. `ChatService.buildSubagentToolsForChat`

**Before:** Captures frozen `assistant`, `settings` in closures.

**After:**
- Pass a settings-fetching lambda instead of frozen values:
  ```kotlin
  val getLiveSettings: () -> Settings = { settingsStore.settingsFlow.value }
  val getLiveAssistant: (Uuid) -> Assistant? = { id -> getLiveSettings().getAssistantById(id) }
  ```
- The `spawn` closure calls `getLiveSettings()` and `getLiveAssistant(assistantId)` to resolve current profile.
- The `systemPrompt` callback on the Tool (line 60-77 of SubagentTools.kt) also reads live profiles for the description listing.

### D. `GenerationHandler.generateText` — system prompt refresh

Currently at line 336-339, `tools.forEach { tool -> append(tool.systemPrompt(model, messages)) }` is called once per step inside `generateInternal`. Since `Tool.systemPrompt` is a lambda `(Model, List<UIMessage>) -> String?`, if the tool's `systemPrompt` lambda reads live settings, the system prompt will automatically reflect the latest profiles on every step. No change needed in GenerationHandler.

### E. SubagentHost.spawn — already fine

`SubagentHost.spawn` receives `settings` and `parentAssistant` as parameters each time it's called. If the caller (the spawn closure) passes live values, spawn works correctly. No change needed.

## Data Flow After Fix

```
GenerationHandler.generateText (loop)
  → step N: manage_subagent_profile(create "foo")
    → ChatService.manageSubagentProfile → settingsStore.update → DataStore persisted
  → step N+1: generateInternal builds system prompt
    → spawn_subagent.systemPrompt reads getLiveSettings() → includes "foo"
  → step N+1: model calls spawn_subagent(profile_name="foo")
    → spawn closure reads getLiveSettings() → resolveProfile("foo") succeeds
    → SubagentHost.spawn executes with new profile
```

## Edge Cases

- **Race condition:** `settingsStore.settingsFlow.value` is a `MutableStateFlow`, so `.value` is always the last emitted value. Since `manageSubagentProfile` calls `settingsStore.update` which sets `settingsFlow.value` first then writes to DataStore, the value is immediately visible. No race.
- **Nested subagent (depth > 0):** Already excluded from `manage_subagent_profile` (depth check in `createManageSubagentTool`). No fix needed.
- **Model sends invalid profile name:** Runtime validation returns error with available profile list. Same experience as before when enum rejected the value, just deferred to execution time instead of schema-validation time.

## Rollback

- Revert `SubagentTools.kt` signature changes and `ChatService.buildSubagentToolsForChat` closure changes.
- No database or schema migrations involved.

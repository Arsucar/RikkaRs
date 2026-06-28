# Implementation Plan

## Ordered Checklist

1. **Refactor `createSubagentTools` signature** — `SubagentTools.kt`
   - [ ] Remove `profiles: List<SubagentProfile>` parameter
   - [ ] Change `profile_name` parameter: remove `enum` constraint, keep as plain `string`
   - [ ] Update tool description to note that profile names are listed in system prompt, not enum
   - [ ] Update `systemPrompt` lambda to accept a profiles-fetching callback (or read from a provided lambda)
   - [ ] Add runtime validation in `execute` block: if profile not found, return error listing available profiles

2. **Refactor `createManageSubagentTool`** — `SubagentTools.kt`
   - [ ] Remove or deprioritize `profiles: List<SubagentProfile>` for the "update" base lookup
   - [ ] Change so `manage` callback is called with `(action, name, rawProfileOrNull)` where "update" sends `null` base, letting the manage function in ChatService look up the current profile from live settings
   - [ ] Alternatively: pass a `resolveProfile: (String) -> SubagentProfile?` lambda

3. **Update `ChatService.buildSubagentToolsForChat`** — `ChatService.kt`
   - [ ] Create `getLiveSettings` and `getLiveAssistant` lambdas that read from `settingsStore.settingsFlow.value`
   - [ ] Update `spawn` closure: call `getLiveSettings()` + `getLiveAssistant(assistant.id)` instead of using frozen `assistant`/`settings`
   - [ ] Update `createSubagentTools` call: remove `profiles` arg, pass `systemPrompt` profile-fetching logic
   - [ ] Update `createManageSubagentTool` call: remove or adapt `profiles` arg
   - [ ] Update `toolsForSubagentProfile`: nested spawn closure also needs live resolution (or keep frozen for nested since nested can't manage profiles — decide based on impact analysis)

4. **Update `manageSubagentProfile` in ChatService** — `ChatService.kt`
   - [ ] For "update" action: if the base profile is not found in the frozen list passed from SubagentTools, look it up from live `settingsStore.settingsFlow.first()` instead of erroring
   - [ ] This ensures update works for profiles created earlier in the same round

5. **Update system prompt to list live profiles** — `SubagentTools.kt`
   - [ ] The `spawn_subagent` tool's `systemPrompt` lambda must call `getLiveSettings()` to list current profiles each step
   - [ ] Verify this already works: `GenerationHandler.generateInternal` calls `tool.systemPrompt(model, messages)` on every step

6. **Update existing tests** — `SubagentModelTest.kt` and `SubagentPermissionTest.kt`
   - [ ] Fix any tests that relied on `createSubagentTools(profiles=...)` signature
   - [ ] Add test for: manage creates profile → subsequent spawn in same "round" resolves it

7. **Manual verification**
   - [ ] Build: `.\gradlew :app:compileDebugKotlin --no-daemon`
   - [ ] Install: `.\gradlew :app:installDebug --no-daemon` (if device available)
   - [ ] Manual test: in a chat, have the AI create a subagent profile via `manage_subagent_profile`, then immediately spawn it

## Validation Commands

```bash
.\gradlew :app:compileDebugKotlin --no-daemon
```

## Review Gates

- After step 1-2: compile check to verify SubagentTools.kt changes compile
- After step 3-4: compile check for ChatService.kt
- After step 6: full test run if applicable

## Rollback Points

- After each numbered step, the codebase should compile (partial changes may not compile between steps, but steps 1+2 together and 3+4+5 together should be compilable units)

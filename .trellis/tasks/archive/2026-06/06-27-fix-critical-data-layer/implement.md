# Implement: Fix critical data layer

## Checklist

- [ ] **1. PreferencesStore.kt**: Add `GLOBAL_SUBAGENT_PROFILES` key, read, write
  - Add key to `Keys` object
  - Add read in `settingsFlowRaw.map` → `Settings(globalSubagentProfiles = ...)`
  - Add write in `update` lambda → `preferences[GLOBAL_SUBAGENT_PROFILES] = ...`

- [ ] **2. ChatService.kt**: CAS retry limit
  - Change `while(true)` to `repeat(50)` in `updateConversationState`
  - Add `Log.w` on limit exceeded

- [ ] **3. ChatService.kt**: Streaming metadata cleanup
  - Add `cleanupStreamingSubagentMetadata(conversationId)` private fun
  - Call it at end of `handleMessageComplete`
  - Implementation: scan last assistant message, find `spawn_subagent` tool parts with `subagent_streaming: true`, replace with `false`

- [ ] **4. Build verification**: `.\gradlew :app:compileDebugKotlin`
  - No runtime test needed for this phase (persistence test requires emulator)

- [ ] **5. Install verification**: `adb devices` + `.\gradlew :app:installDebug`

## Validation Commands

```bash
.\gradlew :app:compileDebugKotlin
.\gradlew :app:installDebug
```

## Rollback

All changes are in-app data layer only. If serialization breaks, old data fallback is `"[]"` → empty list. No migration needed.

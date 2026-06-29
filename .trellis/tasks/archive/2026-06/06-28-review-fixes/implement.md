# Implementation Plan

## Parallel Execution (5 independent work items)

These can be dispatched to sub-agents in parallel:

### Worker A: R1 - disabledBuiltinSubagents migration
- **File**: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
- **Steps**:
  1. Read current `migrateSubagentBuiltinsIfNeeded` function
  2. After the `toAdd` logic, add: iterate `settings.assistants`, for each with non-empty `disabledBuiltinSubagents`, compute `disabledGlobalSubagents = (existing disabledGlobalSubagents) union (disabledBuiltinSubagents filter name exists in new globalSubagentProfiles)`
  3. Return updated settings with both `globalSubagentProfiles` and modified `assistants`
- **Validation**: `.\gradlew :app:compileDebugKotlin`
- **Rollback**: Revert PreferencesStore.kt changes only

### Worker B: R2 - i18n strings
- **Files**: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`
- **Steps**:
  1. In `values/strings.xml`:
     - `setting_provider_detail_tags` -> "Tags"
     - `setting_provider_detail_add_tag` -> "Add tag"
     - `filter_all` -> "All"
     - `provider_suggested_tags` items -> English: "Frequently Used", "Budget", "Long Context", "High Quality", "Free Tier", "Domestic"
  2. In `values-zh/strings.xml` and `values-zh-rTW/strings.xml`: add Chinese originals for these keys
- **Validation**: `.\gradlew :app:compileDebugKotlin`
- **Rollback**: Revert strings.xml changes

### Worker C: R3 - Stale streaming cleanup on load
- **File**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- **Steps**:
  1. Extract a pure function: `fun Conversation.cleanStaleStreamingMetadata(): Conversation` that scans messages for `spawn_subagent` tool parts with `subagent_streaming=true` and sets it to `false`
  2. Call this function in `getOrCreateSession` when creating the initial `ConversationSession` state (apply it to the loaded conversation)
  3. Keep existing `cleanupStreamingSubagentMetadata` as-is for live paths
- **Validation**: `.\gradlew :app:compileDebugKotlin`
- **Rollback**: Revert ChatService.kt changes

### Worker D: R4 - Remove childTranscript dead code
- **File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt`
- **Steps**:
  1. Remove `val childTranscript: List<SubagentTranscriptStep>? = null` from `SubagentTranscriptStep.ToolCall`
  2. Verify no other file references `childTranscript` (grep confirms only declaration)
- **Validation**: `.\gradlew :app:compileDebugKotlin`
- **Rollback**: Revert SubagentProfile.kt changes

### Worker E: R5 - Migration tests
- **File**: `app/src/test/java/me/rerere/rikkahub/data/ai/subagent/SubagentModelTest.kt`
- **Steps**:
  1. Import `migrateSubagentBuiltinsIfNeeded` from PreferencesStore (or make it package-internal/testable)
  2. Add 5 test functions as described in design.md
  3. Note: `migrateSubagentBuiltinsIfNeeded` is private in PreferencesStore - may need to extract to a top-level function or make internal for testing
- **Validation**: `.\gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.subagent.SubagentModelTest"`
- **Rollback**: Revert test file changes

## Final Verification (after all workers complete)

1. `.\gradlew :app:compileDebugKotlin` - full compilation check
2. `.\gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.subagent.SubagentModelTest"` - unit tests
3. `.\gradlew lint` - lint check
4. Install to device: `.\gradlew :app:installDebug`

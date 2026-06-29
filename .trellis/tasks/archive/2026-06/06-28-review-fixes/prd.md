# Fix review findings: builtin migration, i18n, stale streaming, dead code, tests

## Goal

Fix 5 concrete issues found during code-review of the subagent-global refactor and i18n extraction changes (37 files, +1496/-322 lines).

## Background

The workspace has a large uncommitted diff that:
- Refactors subagent management from hardcoded "builtin" to a user-editable "global" store.
- Adds streaming subagent progress UI with ChainOfThought rendering.
- Adds a Provider tag system for model list filtering.
- Extracts many hardcoded UI strings into string resources.

The review identified 1 blocker, 4 non-blockers, and 1 nit.

## Requirements

### R1 - Migrate `disabledBuiltinSubagents` -> `disabledGlobalSubagents` (blocker)
- **File**: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
- **Problem**: `migrateSubagentBuiltinsIfNeeded` copies BUILTIN_PROFILES into `globalSubagentProfiles` but never migrates existing `assistant.disabledBuiltinSubagents` values into `assistant.disabledGlobalSubagents`. After upgrade, previously-disabled builtin subagents re-appear for all existing users.
- **Fix**: In the migration path, for each assistant that has non-empty `disabledBuiltinSubagents`, merge those names into `disabledGlobalSubagents` (only for names that exist in the global profile list after migration). Keep the old field for serialization compat.

### R2 - Move Chinese strings out of default `values/strings.xml` (non-blocker)
- **File**: `app/src/main/res/values/strings.xml`
- **Problem**: `setting_provider_detail_tags`, `setting_provider_detail_add_tag`, `filter_all`, and `provider_suggested_tags` array contain Chinese text in the default (en) resource file. Non-CJK locale users will see Chinese UI labels.
- **Fix**: Replace the Chinese values in `values/strings.xml` with English equivalents. Add the Chinese versions to `values-zh/strings.xml` (and `values-zh-rTW/strings.xml` for Traditional Chinese).

### R3 - Clean stale `subagent_streaming=true` metadata on conversation load (non-blocker)
- **File**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- **Problem**: `cleanupStreamingSubagentMetadata` runs after generation success/failure, but not when a conversation is loaded. If the process is killed mid-generation, stale `subagent_streaming=true` persists, showing a permanent loading spinner.
- **Fix**: Add a cleanup pass when a conversation session is created/resumed.

### R4 - Remove unused `childTranscript` field from `SubagentTranscriptStep.ToolCall` (non-blocker)
- **File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt:125`
- **Problem**: `childTranscript: List<SubagentTranscriptStep>? = null` is declared but never assigned or consumed. Dead code.
- **Fix**: Remove the field. Backwards-compatible (defaults to null, never serialized in practice).

### R5 - Add tests for global subagent migration logic (non-blocker)
- **File**: `app/src/test/java/me/rerere/rikkahub/data/ai/subagent/SubagentModelTest.kt`
- **Problem**: No tests cover `migrateSubagentBuiltinsIfNeeded` migration in PreferencesStore.
- **Fix**: Add tests for: (a) migration triggers when `subagentBuiltinMigrated == false`, (b) builtin profiles copied to global, (c) `disabledBuiltinSubagents` names migrated to `disabledGlobalSubagents`, (d) migration is idempotent, (e) already-migrated settings untouched.

## Acceptance Criteria

- [ ] R1: Assistants with `disabledBuiltinSubagents = setOf("coder")` have those names in `disabledGlobalSubagents` after migration
- [ ] R1: Migration is idempotent
- [ ] R1: Existing `disabledGlobalSubagents` entries are merged, not replaced
- [ ] R2: `values/strings.xml` has English text for affected keys; Chinese moved to `values-zh/strings.xml`
- [ ] R3: Opening a conversation with stale `subagent_streaming=true` metadata cleans it up
- [ ] R4: `SubagentTranscriptStep.ToolCall` no longer has `childTranscript` field; code compiles
- [ ] R5: New migration tests pass: `.\gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.subagent.SubagentModelTest"`

## Out of Scope

- Fixing throttled progress callback low-risk race - current throttle + CAS is sufficient
- Adding readOnly feedback for SubagentProfileForm - UX polish, not a bug
- Removing `disabledBuiltinSubagents` field from Assistant - kept for serialization backward compatibility

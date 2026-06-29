# Design: Review Fixes

## R1 — disabledBuiltinSubagents migration

**Current flow** (broken):
1. `migrateSubagentBuiltinsIfNeeded` checks `settings.init || settings.subagentBuiltinMigrated`
2. If not migrated: copies `BUILTIN_PROFILES` → `globalSubagentProfiles`, sets `subagentBuiltinMigrated = true`
3. Does NOT touch `assistants[*].disabledBuiltinSubagents`

**New flow**:
In `migrateSubagentBuiltinsIfNeeded`, after adding builtin profiles to `globalSubagentProfiles`, iterate `settings.assistants` and for each assistant with `disabledBuiltinSubagents`:
- Filter to only names that exist in `globalSubagentProfiles`
- Merge into `disabledGlobalSubagents` (union, not replace)
- Keep `disabledBuiltinSubagents` unchanged for serialization compat

**Compatibility**: No schema change. Old `disabledBuiltinSubagents` values are preserved. New `disabledGlobalSubagents` accumulates the migrated names. Both fields coexist.

**Rollback**: N/A (one-way migration; if it runs twice, result is idempotent due to set union).

## R2 — i18n string fix

**Approach**: 
- Replace Chinese values in `values/strings.xml` with English: `setting_provider_detail_tags` → "Tags", `setting_provider_detail_add_tag` → "Add tag", `filter_all` → "All".
- Replace `provider_suggested_tags` array items with English equivalents: "Frequently Used", "Budget", "Long Context", "High Quality", "Free Tier", "Domestic".
- Add Chinese original values to `values-zh/strings.xml` and `values-zh-rTW/strings.xml`.

**No code changes needed** — only string resource files.

## R3 — Stale streaming cleanup on load

**Current**: `cleanupStreamingSubagentMetadata(conversationId)` is called from `handleMessageComplete` success/failure paths only.

**New**: Add a cleanup call in `getOrCreateSession` — when a session is first created, scan the conversation's messages for any stale `subagent_streaming=true` and clean them. This handles process-death scenarios.

**Implementation**: Extract a `cleanStaleStreamingMetadata(conversation: Conversation): Conversation` pure function that can be called both from `getOrCreateSession` and from the existing path. Keep the existing path as-is (it uses `updateConversationState` which is the right API for live updates).

## R4 — Remove childTranscript dead code

**Change**: Remove `val childTranscript: List<SubagentTranscriptStep>? = null` from `SubagentTranscriptStep.ToolCall`.

**Compatibility**: Field defaulted to null, never serialized in practice. Deserialization of old data with `childTranscript` key will use `@SerialName` matching; since the field is removed, unknown keys are ignored by default (kotlinx.serialization `ignoreUnknownKeys` is typically enabled). Need to verify Json config in SubagentHost/ChatService — if `ignoreUnknownKeys = true` (which it is for the JSON instances used here), removing the field is safe.

## R5 — Migration tests

Add tests to `SubagentModelTest.kt` covering the migration logic in PreferencesStore. Since `migrateSubagentBuiltinsIfNeeded` is a pure function on `Settings`, tests can call it directly without DataStore machinery.

Tests needed:
1. `migration_copiesBuiltinToGlobal` — fresh settings get all builtins in `globalSubagentProfiles`
2. `migration_migratesDisabledBuiltinToGlobal` — assistant with `disabledBuiltinSubagents=setOf("coder")` gets "coder" in `disabledGlobalSubagents`
3. `migration_isIdempotent` — calling twice produces same result
4. `migration_preservesExistingGlobalDisabled` — existing `disabledGlobalSubagents` are not lost
5. `migration_skipsWhenAlreadyMigrated` — `subagentBuiltinMigrated=true` → no-op

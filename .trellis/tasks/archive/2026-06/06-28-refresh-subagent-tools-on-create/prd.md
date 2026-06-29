# Refresh subagent tools after manageSubagentProfile within same generation round

## Goal

When the AI calls `manage_subagent_profile` to create/update a subagent profile during a generation round, the newly created profile should be immediately available for `spawn_subagent` within the **same** generation round — not only the next one.

## Problem

1. `ChatService.handleMessageComplete` calls `settingsStore.settingsFlow.first()` once (line 592) and passes the frozen `settings` + `assistant` snapshot to `GenerationHandler.generateText`.
2. `buildSubagentToolsForChat` (line 1564) builds the `spawn_subagent` tool with a hardcoded `profile_name` enum (from `mergeSubagentProfiles` on the frozen snapshot) and a closure that resolves profiles from the same frozen `assistant`/`settings`.
3. `manageSubagentProfile` (line 1768) persists the new profile to DataStore via `settingsStore.update`, but the running generation loop continues using the frozen tool list.
4. Result: `spawn_subagent` schema enum and `resolveProfile` closure don't see the new profile → "profile not found" error or model can't select the new name.

## Requirements

- After `manage_subagent_profile` create/update succeeds, the `spawn_subagent` tool must be refreshed so that:
  - Its `profile_name` enum includes the newly created/updated profile name.
  - Its `resolveProfile` call and `spawn` closure read the latest `assistant` and `settings` from DataStore.
- The refresh must work within the **same** `GenerationHandler.generateText` tool loop — no need to restart the generation.
- Delete action should also remove the profile from the enum and resolve path.
- The refresh must not break existing tool execution semantics (tool approval flow, parallel subagent spawn, etc.).
- Nested subagent spawning (depth > 0) does NOT have `manage_subagent_profile` tool (depth check at line 176 of SubagentTools.kt), so only depth-0 (main agent) needs this fix.

## Acceptance Criteria

- [ ] During a single generation round, the AI can call `manage_subagent_profile` (action=create) and then `spawn_subagent` with the newly created profile name in the same or subsequent tool step, and the spawn succeeds.
- [ ] After `manage_subagent_profile` (action=delete), the deleted profile no longer appears in subsequent `spawn_subagent` profile_name enum within the same generation round.
- [ ] After `manage_subagent_profile` (action=update), the updated profile fields (system_prompt, model_id, etc.) take effect on subsequent spawn calls within the same generation round.
- [ ] Existing subagent spawn behavior (parallel execution, depth limit, nested spawn) is unaffected.
- [ ] Tool approval flows are unaffected.

## Constraints

- The `generateText` loop passes `tools: List<Tool>` as an immutable list. The fix must work within or adapt this architecture.
- Only depth-0 (main agent) has `manage_subagent_profile`; nested subagents don't need this fix.
- Must not introduce excessive latency on every tool step (avoid re-reading DataStore on every step if possible).

## Notes

- See `GenerationHandler.kt:110-296` for the tool loop: each step builds `toolsInternal` from `tools` + memory tools, but `tools` itself is never refreshed.
- The `Tool` class is `me.rerere.ai.core.Tool` — check if it can be made dynamic or if we need a wrapper pattern.
- Alternative: make `spawn_subagent`'s execute closure always read live settings + assistant, and make the enum dynamic (via a lookup callback instead of a static list).

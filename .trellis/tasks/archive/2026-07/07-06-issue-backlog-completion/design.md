# Design

## Work Structure

This task is a parent-level closure task with three implementation lanes:

1. Evidence and closure lane: re-validate already implemented issues and close only when evidence is strong.
2. Flat memory scope lane: implement `#39` over the existing flat memory system.
3. Memory table lane: implement `#41` in staged, gated slices so disabled mode remains identical to today.

The parent task owns the issue closure table and final integration check. Large code slices may become child tasks if concurrent implementation becomes practical.

## Existing Feature Closure

For `#31`, `#34/#36/#37/#38`, `#35`, `#40`, and `#42`, do not rewrite working code unless validation reveals gaps. The closure path is:

- map issue expectations to current files and commits;
- add missing tests or UI refinements only where evidence is weak;
- run focused validation;
- update evidence table;
- comment and close the GitHub issue.

## #39 Flat Memory Scope

Data model:

- Add a per-row memory scope field, using stable string values such as `ASSISTANT` and `GLOBAL`.
- Keep `assistant_id` as the owner/bucket for assistant-local rows.
- Migrate existing rows:
  - `assistant_id == MemoryRepository.GLOBAL_MEMORY_ID` becomes `scope = GLOBAL`;
  - all other rows become `scope = ASSISTANT`.

Repository and generation:

- Generation reads all `scope = GLOBAL` rows plus `scope = ASSISTANT AND assistant_id == currentAssistant.id` when memory is enabled.
- Assistant-level `useGlobalMemory` becomes a compatibility/default-new-memory setting rather than a whole-pool switch.
- `memory_tool` creates local memories by default unless a scope is specified; edit operations can change scope where authorized.

UI:

- `AssistantMemoryPage` shows memory scope per item.
- Users can switch a memory between local/global.
- The old global memory switch is relabeled or treated as default scope for new memories, not as the only memory source selector.

## #41 Memory Tables

Implement in gates:

- P0: disabled-by-default global/assistant switches, Room entities, repository interfaces, and zero-intrusion tests. No prompt/tool/job activity while disabled.
- P1: template/document CRUD and basic UI for viewing/editing table documents.
- P2: injection transformer behind gates with token/row limits.
- P3: `memory_table_tool` and optional manual sync. Auto sync remains disabled by default.

The flat memory feature and table memory feature must not share storage tables or migration assumptions. Table memory introduces separate entities and repository methods.

## Verification and Closure

Verification uses one final check flow with `--no-daemon`:

- lint;
- test;
- install to device;
- manual UI/device checks for features whose acceptance depends on runtime behavior.

GitHub closure is the final step, after validation evidence exists.

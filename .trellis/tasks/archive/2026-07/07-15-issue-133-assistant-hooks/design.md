# Technical Design

## Configuration

- Add `hooks: List<ConversationHook> = emptyList()` to `Assistant`; existing JSON remains compatible.
- Hook configuration contains Uuid ID/modelId, name, enabled, trigger, independent prompt, sealed action config, and monotonic configVersion.
- List order is execution order. New triggers/actions register handlers without adding action-specific logic to the dispatcher.

## Final-Success Gate

- Persist `GenerationLogicalTurnEntity` before initial generation and reuse its ID across approval pauses and ToolContinuation.
- After each generation flow returns, evaluate failure/cancellation, pending resumable tools, active source message, and non-empty final assistant text.
- Unique `(logicalTurnId, trigger)` prevents duplicate runs; no matching enabled Hook closes the turn without run rows.

## Execution Pipeline

`Final gate → persist run/executions → appScope frozen payload → dispatcher → provider executor → strict parser → action registry`

- Frozen message text, prompt, config, and raw output remain only in the appScope task.
- Executor uses model/provider lookup, background parameters, rate limiter, and direct `generateText`.
- Parser uses an isolated strict JSON path and explicit key-set validation.
- Add-tag handler validates execution token, conversation/source activity, allowed tag IDs, and current vocabulary before calling #132.

## Persistence and State

- Add logical-turn, hook-run, and hook-execution Room entities/DAO/migration.
- Execution statuses retain ordered terminal results; run aggregation is `FAILED > INTERRUPTED > CANCELLED > SUCCESS > SKIPPED`, with SKIPPED only when all executions skip.
- Conditional lease/token transitions protect timeout and cancellation races.
- On startup, QUEUED/RUNNING become INTERRUPTED; do not reconstruct or retry payloads.
- Retain at most 100 runs per conversation and 30 days, deleting whole runs with executions.

## UI

- Extend assistant detail state and pages with Hook list/editor, enabled state, ordering, model and allowed-tag selection.
- Add `HookHistory` to the existing right-drawer screen enum/menu/content.
- `ChatVM` exposes Room Flow for history; current names resolve dynamically and deleted references get explicit fallback labels.

## Privacy and Compatibility

- Persist only IDs, version/hash, timestamps/status, truncated reason, stable error code, and sanitized error.
- Never persist message snapshots, full prompts/config payloads, raw provider output, headers, or credentials.
- Complete backup may contain terminal audit metadata and Assistant configuration; restored running states are interrupted.

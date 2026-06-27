# Subagent Runtime Engine

## Goal

Provide the runtime that spawns subagents from configured profiles, runs nested generation and tool loops, and returns structured results to the parent agent. Expose three parent-callable tools (`spawn_subagent`, `ask_btw`, `manage_subagent_profile`) when subagents are enabled on an assistant.

## Context & Dependencies

- Depends on **subagent-model**: `SubagentProfile`, `SubagentResult`, and assistant-level extensions (e.g. `enableSubagents`, profile storage) must exist before this runtime can integrate.
- Tool visibility and permission filtering for child runs are owned by a separate **subagent-permissions** child; this task accepts a **tool-set builder** supplied by the integrator rather than defining permission rules here.

## Requirements

### SubagentHost (orchestrator)

1. **Invocation**: Accept a profile name and task description; resolve the matching profile from the assistant configuration. Fail clearly when the profile is missing or invalid.
2. **Depth limit**: If current nesting depth is greater than or equal to the configured maximum depth, the spawn must fail with an explicit error (no silent downgrade).
3. **Model resolution**: Use the profile’s chat model when set; otherwise inherit the parent conversation’s model.
4. **Child assistant**: Construct a synthetic child assistant from the resolved profile (system behavior, limits, and profile-bound settings).
5. **Child tools**: Build the child tool set via an injected tool-set builder function (permissions child provides the concrete builder at integration time).
6. **Generation**: Run child text generation with child messages, child tools, and child `maxSteps`, reusing the existing generation handler’s tool loop semantics.
7. **Summary extraction**: Derive the subagent’s **summary** from the final assistant message text in the child run.
8. **Continuation**: When the summary length is below the configured minimum (default expectation: 200 characters), append a continuation prompt and perform **one** additional generation step **without tools**, then use the updated final text as the summary.
9. **Result packaging**: Produce a `SubagentResult` that includes transcript steps suitable for UI cards and downstream inspection.
10. **Parent handoff**: Return the result as tool output to the parent agent in a stable, machine-readable shape.

### `spawn_subagent` tool

1. **Parameters**: `profile_name` (enum of configured profile names), `task` (string), optional `description` (string).
2. **Return payload**: JSON including at least `profile_name`, `succeeded`, `summary`, `depth`, `steps`, plus transcript metadata needed for display or debugging.
3. **System prompt guidance**: When subagents are enabled, the parent system prompt must include delegation guidance, the list of available profiles, and when to use each pattern.
4. **Parallelism**: Multiple `spawn_subagent` invocations in a single parent model response must execute concurrently (same wall-clock benefit as existing parallel tool handling in generation).
5. **Registration**: The tool is included in `ChatService.buildTools` only when `assistant.enableSubagents` is true.

### `ask_btw` tool

1. **Parameters**: `question` — a self-contained string that carries all context needed for a one-shot answer.
2. **Return payload**: JSON `{ answer }`.
3. **Behavior**: Single LLM call only — no tools, no multi-step tool loop.
4. **Purpose**: Lightweight second opinion using the same resolved model as the parent context.

### `manage_subagent_profile` tool

1. **Parameters**: `action` (`list` | `create` | `update` | `delete`) plus fields required for the chosen action.
2. **Persistence**: Changes must be written back to the assistant’s subagent profile configuration and survive reload.
3. **Depth gate**: Callable only from depth 0 (main agent); nested subagents must not mutate profile definitions.

### Cancellation

1. Stopping or cancelling the parent conversation must cancel in-flight subagent generation.
2. Cancelled subagent runs must not leave the parent in an inconsistent state; the parent tool result must reflect failure/cancellation clearly.

### Token usage

1. Subagent token usage must be merged into the parent message’s `totalUsage`.
2. The same usage breakdown must also be stored on `SubagentResult` for subagent card display.

## Constraints

- Do not redefine profile schema or permission matrices in this task; consume model types and the injected tool-set builder.
- Continuation is exactly one no-tools step after a short summary — not an open-ended retry loop.
- `manage_subagent_profile` is a main-agent-only capability.

## Acceptance Criteria

- [ ] `spawn_subagent` runs a subagent for up to N steps (per profile/`maxSteps`) and returns a non-empty summary on success.
- [ ] `ask_btw` returns a text `answer` without invoking tools or a tool loop.
- [ ] `manage_subagent_profile` can list, create, update, and delete profiles with changes persisted on the assistant.
- [ ] Continuation runs when summary length is below 200 characters (or configured minimum) and improves or replaces the summary via one no-tools step.
- [ ] `SubagentResult` includes transcript steps and per-subagent token usage.
- [ ] Parent conversation cancellation stops active subagent runs and surfaces cancellation in tool output.
- [ ] Parallel `spawn_subagent` calls in one parent turn complete concurrently and reduce wall-clock time versus strictly serial execution.
- [ ] Depth at or above `maxDepth` rejects spawn with a clear error.
- [ ] `spawn_subagent`, `ask_btw`, and `manage_subagent_profile` are absent from the parent tool list when `enableSubagents` is false; present when true (with `manage_subagent_profile` still depth-gated at runtime).

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Technical design belongs in `design.md`; execution checklist in `implement.md` before `task.py start` if treated as a complex child task.
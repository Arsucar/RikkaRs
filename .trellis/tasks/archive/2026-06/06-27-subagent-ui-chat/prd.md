# Subagent Chat UI

## Goal

Render subagent-related tool outcomes in the chat message stream as first-class cards: rich display for `spawn_subagent` results (summary, transcript, usage, errors, streaming) and a lightweight card for `ask_btw`. Ensure rendered state survives conversation save and reload.

## Parent

- Parent task: `06-27-subagent-mvp` (Subagent System MVP)

## Dependencies

- **subagent-model**: `SubagentResult`, `SubagentTranscriptStep` variants, profile fields such as `displayName` and `streamOutput`, and stable JSON serialization contracts.
- **subagent-runtime**: Tool execution must return structured payloads for `spawn_subagent` and `ask_btw` (including `SubagentResult`, transcript steps, token usage, and success/failure).

This task does not define runtime behavior, profile schema, or permission rules.

## Requirements

### 1. Subagent tool card (`spawn_subagent` result)

When the parent message contains a completed or in-progress `spawn_subagent` tool result, the chat UI must show a dedicated card bound to that tool invocation.

**Header (always visible when collapsed)**

- Profile identity: icon and `displayName` from the resolved profile (or a clear fallback when the profile name is unknown).
- Run metrics: step count and token count derived from the result (usage on `SubagentResult` and/or equivalent fields in the tool payload).
- Token usage must be visible without expanding the card.

**Summary (always visible)**

- The subagent summary text must be readable in the collapsed card; the user must not need to expand the card to read the summary.

**Expandable transcript**

- The card must support expand/collapse for a chronological transcript built from `SubagentTranscriptStep` (or equivalent serialized steps).
- **Reasoning** steps: collapsed by default within the expanded transcript; visually distinct (e.g. indented, secondary/italic styling per app conventions).
- **ToolCall** steps: show tool name plus truncated input and truncated output; full detail may require interaction only if product limits require truncation.
- **Text** steps: shown inline in reading order.

**Error and failure**

- When `succeeded` is false (or equivalent failure status), the card must use a clearly error-styled treatment (e.g. error tint) and show the error message prominently alongside any available summary context.

**Loading / streaming**

- While a subagent run is active, the card must indicate in-progress state.
- When the profile has `streamOutput` enabled, the card must update in near real time as new transcript content and summary-related text arrive, without requiring the user to leave and re-enter the conversation.
- Update cadence must feel continuous; product expectation aligns with the existing ~120ms throttle pattern used on the sub branch for streamed tool/subagent UI (exact mechanism is out of scope for this PRD).

### 2. Ask-between card (`ask_btw` result)

When the message contains an `ask_btw` tool result, show a simpler card:

- The question (from tool input or payload) and the answer (from tool output).
- Token count for that call, visible on the card.
- No expandable transcript section is required for this tool.

### 3. Tool UI registry integration

- Chat tool rendering must resolve custom renderers for tool names **`spawn_subagent`** and **`ask_btw`** via the existing tool UI registry pattern used for other tools in chat messages.
- When subagents are disabled or a tool name is unknown, behavior must fall back to existing generic tool presentation without breaking the message list.

### 4. Persistence and reload

- Subagent and ask_btw outcomes must remain renderable after the conversation is saved and reopened.
- The persisted representation must retain enough structured data to rebuild the same cards (summary, transcript steps, usage, success/failure, question/answer for ask_btw).
- A design choice is required in a downstream design artifact: dedicated `UIMessagePart` subtype versus JSON embedded in standard tool output—but the **requirement** is round-trip fidelity through the app’s conversation persistence path, consistent with other tool results in `UIMessage`.

### 5. Non-goals (this child)

- Subagent settings screens, profile editor UI, and permission configuration UI.
- Implementing spawn, cancellation, or depth limits (runtime child).
- Defining `SubagentResult` schema (model child).

## Constraints

- Cards must fit existing chat message layout and theming (light/dark, accessibility: readable contrast for error state and transcript).
- Do not require network or re-execution to render historical tool results from stored messages.
- Streaming updates apply only when the runtime exposes incremental result/stream events for `streamOutput` profiles.

## Acceptance Criteria

- [ ] A `spawn_subagent` tool result renders as a card with header (profile label, step count, token count), summary visible without expand, and an expandable transcript.
- [ ] Expanded transcript shows Reasoning (collapsed by default), ToolCall (name + truncated input/output), and Text steps in order.
- [ ] Failed subagent results (`succeeded=false` or equivalent) show error styling and a visible error message.
- [ ] `ask_btw` tool results render as a Q&A card with token count and no transcript expander.
- [ ] `spawn_subagent` and `ask_btw` are registered in the tool UI registry under those exact tool names.
- [ ] After save and reload of a conversation containing these tool results, cards render the same summary, transcript, usage, and error/Q&A content as before reload.
- [ ] For profiles with `streamOutput=true`, an in-flight subagent card updates incrementally until completion (near-real-time, throttled updates acceptable).

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Add `design.md` and `implement.md` before `task.py start` if this child is executed as a complex task (persistence shape, streaming subscription, registry wiring).
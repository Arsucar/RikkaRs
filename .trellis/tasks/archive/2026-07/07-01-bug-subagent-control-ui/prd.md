# PRD: Fix subagent control flow, cancellation, and transcript UI

## Issues

- #21: `tool_loop_steps` vs `tool_calls` semantics confused; no cumulative tool-call count limit
- #18: Force-stop leaves subagent tools running/spinning in background
- #22: Subagent transcript tool calls use generic rendering instead of tool-specific ToolUIRegistry

## Problem

### #21 — Semantics & no tool-call cap

- `tool_loop_steps` = number of **assistant messages** (LLM↔tool loop iterations), NOT tool invocations
- `tool_calls` = count of **UIMessagePart.Tool** parts across all assistant messages
- Single step can execute **multiple tools in parallel** → `tool_calls` can far exceed `tool_loop_steps`
- Only `maxSteps` (loop iteration limit) exists; no `maxToolCalls` budget
- Metadata confusion: `SubagentTools.kt` sets both `subagent_steps` and `subagent_tool_loop_steps` to `result.toolLoopSteps` — `subagent_steps` should be `result.steps`
- Streaming progress uses `transcript.size` for "steps" label, which is step count not loop count

**Impact**: Subagent with `maxSteps=48` (explore) can call 128+ tools; user/parent-agent misled by small "steps" number; no early stop for runaway tool usage.

### #18 — Cancellation chain incomplete

Current chain: `ChatService.stopGeneration()` → `job.cancel()` → `finishInterruptedPendingTools()`

Gaps:
1. `finishInterruptedPendingTools` only handles tools with `!part.isExecuted` — streaming `spawn_subagent` sets `isExecuted=true` when tool execute completes, so it's **missed**
2. `SubagentHost` has no Job registry or explicit cancel mechanism
3. No first-class "subagent cancelled" result payload
4. In-flight HTTP requests (OkHttp) not interrupted — rely on coroutine cancel which can't break TCP reads
5. `progressScope` uses `SupervisorJob()` — not tied to user stop

**Impact**: User clicks stop → subagent tools keep spinning until natural completion or timeout.

### #22 — Transcript tool rendering

Main agent tool steps use `ToolUIRegistry.resolve(toolName)` for:
- Tool-specific icon & readable title
- Summary composable
- Click-through Preview (BottomSheet with structured content)

Subagent transcript tool calls use `SubagentStreamingStepView`/`SubagentTranscriptStepRow`:
- Generic icon from `subagentToolStepIcon(toolName)` hardcoded map
- Plain `stringResource(R.string.subagent_tool_ui_tool_call, toolName, truncatedInput)`
- No click-through Preview
- No tool-specific rendering

**Impact**: Same tool (e.g. `search_web`) renders differently depending on whether called by main agent or subagent; subagent details are opaque.

## Acceptance Criteria

### #21 Fix

- `SubagentTools.kt` metadata: `subagent_steps` = `result.steps`, `subagent_tool_loop_steps` = `result.toolLoopSteps`, `tool_calls` = `result.toolCallCount` (already correct in slim JSON)
- Add `maxToolCalls` to `SubagentProfile` (default: no limit; optional per-profile override)
- When `maxToolCalls` is set, `SubagentHost.runToCompletion` checks `countToolCalls(messages)` after each step and terminates early if exceeded
- UI label for subagent card distinguishes "loop steps" vs "tool calls"

### #18 Fix

- `finishInterruptedPendingTools` also handles `isExecuted=true` + `subagent_streaming=true` tools — marks them as cancelled
- `SubagentHost.spawn` registers its Job in a cancellable scope; `ChatService.stopGeneration` propagates cancel to active subagent sessions
- In-flight subagent tools get a "cancelled" result payload (not just CancellationException)
- UI stops showing spinner for cancelled subagent steps

### #22 Fix

- `SubagentStreamingStepView` / transcript tool-call rows use `ToolUIRegistry.resolve(toolName)` for:
  - Icon and readable title (same as main agent)
  - Compact summary (one-line, not full expansion)
- Clicking a transcript tool step opens the same Preview BottomSheet as main agent (via `ToolUIRegistry.resolve(toolName).Preview`)
- Keep compact layout inside ChainOfThought card; don't default-expand summaries

## Constraints

- #18 fix must not break normal completion flow
- #22 fix must keep subagent card compact — tool registry rendering is for click-through detail, not inline expansion
- #21 `maxToolCalls` default must not change existing behavior for users who don't configure it
- Backward-compatible: old `SubagentResult` without `maxToolCalls` works fine

## Related Code

| File | Issue | Key |
|------|-------|-----|
| `SubagentHost.kt` | #21, #18 | `runToCompletion`, `countToolCalls`, `toolLoopSteps` |
| `SubagentTools.kt` | #21 | metadata fields, slim JSON |
| `SubagentProfile.kt` | #21 | `maxSteps` default 32; `SubagentResult` |
| `SubagentRegistry.kt` | #21 | `explore` 48, `coder` 64, `reviewer` 24 |
| `GenerationHandler.kt` | #21 | `maxSteps` 256, parallel tool execution |
| `ChatService.kt` | #18 | `stopGeneration`, `finishInterruptedPendingTools` |
| `ChatMessageTools.kt` | #22 | `ChatMessageToolStep` + `ToolUIRegistry` |
| `SubagentToolUIs.kt` | #22 | `SubagentStreamingStepView`, transcript rendering |
| `Message.kt` (ai) | #18 | `finishPendingTools` only `!isExecuted` |
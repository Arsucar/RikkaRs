# Design: Fix subagent control flow, cancellation, and transcript UI

## #21 — Fix metadata semantics + add maxToolCalls

### Fix `SubagentTools.kt` metadata

Current (buggy):
```kotlin
put("subagent_steps", JsonPrimitive(result.toolLoopSteps))
put("subagent_tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
```

Fix:
```kotlin
put("subagent_steps", JsonPrimitive(result.steps))
put("subagent_tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
put("subagent_tool_calls", JsonPrimitive(result.toolCallCount))
```

### Fix streaming progress `subagent_steps`

Current: `ChatService.kt` ~1275 sets `subagent_steps = transcript.size`

Fix: use `transcript.count { it is SubagentTranscriptStep.Assistant }` for loop-step count, or carry a separate counter in SubagentHost that tracks assistant-message deltas.

### Add `maxToolCalls` to SubagentProfile

```kotlin
data class SubagentProfile(
    val name: String,
    val maxSteps: Int = 32,
    val maxToolCalls: Int? = null,  // null = no limit (backward compat)
    // ...
)
```

### Enforce in SubagentHost.runToCompletion

After each `generateText` call, check:
```kotlin
val maxToolCalls = profile.maxToolCalls
if (maxToolCalls != null && countToolCalls(allMessages) >= maxToolCalls) {
    // Terminate early, return partial result with flag
    return RunCompletion(messages = allMessages, ..., truncated = true)
}
```

Add `truncated: Boolean = false` to `RunCompletion` / `SubagentResult` to signal early termination.

### UI: distinguish steps vs tool-calls in subagent card

In `SpawnSubagentToolUI.Summary`, show both:
- "3 steps · 12 tool calls" (or localized format)

Use separate string resources for each.

## #18 — Fix cancellation chain

### Extend `finishInterruptedPendingTools`

Current logic only processes `!part.isExecuted`. Fix: also process tools where:
- `part.isExecuted == true` AND `part.metadata?.get("subagent_streaming") == "true"`

These are streaming subagent tools whose `spawn_subagent` execute has returned (setting `isExecuted=true`) but whose progress UI is still showing running state.

Action: set `subagent_streaming = "false"` and add `subagent_cancelled = "true"` to metadata; set tool output to cancellation JSON.

### Add subagent cancel signal in SubagentHost

Add an internal `cancelReason` field:

```kotlin
private var cancelReason: String? = null

fun requestCancel(reason: String = "cancelled by user") {
    cancelReason = reason
}
```

Check `cancelReason` at the start of each `while` iteration in `spawn()` (the summary-continuation loop). If set, break out with a cancelled `SubagentResult`.

### Wire ChatService.stopGeneration → SubagentHost

When `stopGeneration` is called:
1. Cancel `generationJob` (existing)
2. For each active subagent session, call `subagentHost.requestCancel()`
3. Then call `finishInterruptedPendingTools` (enhanced)

Need a registry of active `SubagentHost` instances per conversation. Add to `ChatService`:

```kotlin
private val activeSubagents = mutableMapOf<Uuid, MutableList<SubagentHost>>()
```

Register on spawn, unregister on completion/failure/cancel.

### Cancelled subagent result

When cancelled, `SubagentResult` should have:
- `succeeded = false`
- `error = cancelReason`
- `summary = "Task cancelled by user"` (localized)
- `toolCallCount` and `toolLoopSteps` filled from what ran so far
- `transcript` included (what completed before cancel)

## #22 — Transcript tool UI uses ToolUIRegistry

### Approach: Make SubagentStreamingStepView resolve from ToolUIRegistry

In `SubagentStreamingStepView` / transcript rendering, replace:

```kotlin
// Current: generic
Text(text = stringResource(R.string.subagent_tool_ui_tool_call, step.toolName, truncatedInput))
```

With:

```kotlin
val renderer = remember(step.toolName) { ToolUIRegistry.resolve(step.toolName) }
// Use renderer.title for the step label
// Use renderer.icon for the step icon
```

### Click-through Preview

Add `onClick` to `SubagentStreamingStepView` that opens a `ModalBottomSheet` with:

- `renderer.Preview(toolInput, toolOutput)` if renderer has non-null Preview
- Fallback: raw JSON display if no registered Preview

### Keep compact layout

- Step row: icon + renderer.title (one line, not expanded Summary)
- Summary is only in the click-through sheet, not inline
- Loading state preserved (spinner when `!executed`)

### Limitations

- Transcript `SubagentTranscriptStep.ToolCall` has `input: String` and `output: String?` (text), not structured `UIMessagePart.Tool.input` (JSON). ToolUIRegistry renderers expect structured input.
- For Preview, parse `input` as JsonObject where possible; fall back to raw string display
- This is a known limitation — future improvement could store structured input in transcript

## Summary of changes

| File | Change | Issue |
|------|--------|-------|
| `SubagentTools.kt` | Fix `subagent_steps` = `result.steps`; add `subagent_tool_calls` | #21 |
| `ChatService.kt` | Fix streaming `subagent_steps` | #21 |
| `SubagentProfile.kt` | Add `maxToolCalls: Int? = null` | #21 |
| `SubagentHost.kt` | Check `maxToolCalls`; add `requestCancel`; active registry | #21, #18 |
| `SubagentResult.kt` / `RunCompletion` | Add `truncated: Boolean` | #21 |
| `ChatService.kt` | `activeSubagents` registry; enhanced stopGeneration | #18 |
| `ChatService.kt` | Enhanced `finishInterruptedPendingTools` for streaming subagents | #18 |
| `SpawnSubagentToolUI` | Show steps + tool_calls; cancelled state | #21, #18 |
| `SubagentToolUIs.kt` | Use `ToolUIRegistry.resolve()` for icon/title/Preview | #22 |
| Strings | `subagent_steps_tool_calls`, `subagent_cancelled` | #21, #18 |

## Risks

- `MessageNode` / `UIMessagePart.Tool` metadata changes affect serialization — use nullable/optional fields
- `SubagentHost.requestCancel` is cooperative, not immediate — long HTTP calls may still delay
- `maxToolCalls` default `null` preserves existing behavior; non-null changes enumeration — profile migration not needed (new field with default)
- ToolUIRegistry in transcript: input is text, not structured JSON — preview may be limited for some tools
- `activeSubagents` registry must be cleaned up on session end to avoid leaks

## Execution order

1. #21 metadata fix (low risk, high visibility — fix `subagent_steps` confusion)
2. #21 maxToolCalls + truncated flag
3. #18 cancellation chain (registry + requestCancel + enhanced finish)
4. #22 ToolUIRegistry in transcript (UI-only, can ship independently)
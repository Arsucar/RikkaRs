# Research: Subagent UI — tool call count, loops label, transcript truncation

- **Query**: Three subagent UI bugs (final title tool count, "loops" string, transcript tool output truncation/expand)
- **Scope**: internal
- **Date**: 2026-07-01

## Findings

### Bug 1 — Final card title shows 0 tool calls (streaming title OK)

**Behavior**

| Phase | Data source | Tool call count |
|--------|-------------|-----------------|
| Streaming | `UIMessagePart.Text.metadata` (`subagent_tool_calls`) | Correct — set in `ChatService.updateSubagentProgress` |
| Completed | `SpawnSubagentToolUI.title()` when `parsed.result != null` | Uses `result.toolCallCount` from `parseSubagentResult()` |

**Streaming title** (`SubagentToolUIs.kt` ~109–128): reads metadata when `subagent_streaming == "true"`:

```kotlin
val toolCalls = meta["subagent_tool_calls"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
```

**Completed title** (~131–156): after streaming, uses decoded `SubagentResult`:

```kotlin
val toolCalls = result.toolCallCount
append(stringResource(R.string.subagent_tool_ui_steps_and_tool_calls, loopSteps, toolCalls))
```

**Root cause (field name mismatch)**

`SubagentProfile.kt` — `SubagentResult.toolCallCount` is serialized as **`tool_call_count`**:

```kotlin
@SerialName("tool_call_count") val toolCallCount: Int = 0,
```

`SubagentTools.kt` — slim JSON body uses **`tool_calls`** (not `tool_call_count`):

```kotlin
put("tool_calls", JsonPrimitive(result.toolCallCount))
```

`parseSubagentResult()` (`SubagentToolUIs.kt` ~811–836):

1. Tries `JsonInstant.decodeFromString(SubagentResult.serializer(), raw)` — if this **succeeds**, `tool_call_count` is absent → **`toolCallCount` defaults to 0**.
2. `recoverCatching` fallback reads `obj["tool_calls"]` — only runs when step 1 **throws**.

Metadata on the same `Text` part still has correct `subagent_tool_calls` (`SubagentTools.kt` ~117–124), but completed title **does not** fall back to metadata when `result != null`.

**Related**: Final metadata also sets `subagent_steps` = `result.steps` and `subagent_tool_loop_steps` = `result.toolLoopSteps`; streaming sets both `subagent_steps` and `subagent_tool_loop_steps` to the same assistant-count (`ChatService.kt` ~1310–1323).

---

### Bug 2 — "loops" label

**String resource** (English only in default `values`; no `subagent_tool_ui_steps_and_tool_calls` in `values-zh`):

| File | Line | Value |
|------|------|--------|
| `app/src/main/res/values/strings.xml` | 112–113 | `subagent_tool_ui_steps` = `%1$d steps`; `subagent_tool_ui_steps_and_tool_calls` = **`%1$d loops · %2$d tool calls`** |
| `app/src/main/res/values-zh/strings.xml` | 111+ | Has `subagent_tool_ui_steps` (`%1$d 步`) but **no** `subagent_tool_ui_steps_and_tool_calls` → locale falls back to English "loops" string |

**Usage** (`SubagentToolUIs.kt`):

- Streaming: line ~125 — `R.string.subagent_tool_ui_steps_and_tool_calls` with `loopSteps`, `toolCalls`
- Completed: line ~152 — same string with `result.toolLoopSteps`, `result.toolCallCount`

First argument is **`toolLoopSteps`** (assistant/tool-loop iterations), not `result.steps` (continuation/summary step counter).

There is **no** separate string key named `subagent_tool_ui_loops`; the word "loops" is hardcoded inside `subagent_tool_ui_steps_and_tool_calls`.

---

### Bug 3 — Transcript tool output truncation / no inline expand

#### `SubagentTranscriptStep.ToolCall` fields (`SubagentProfile.kt` ~126–132)

```kotlin
data class ToolCall(
    val toolName: String,
    val input: String,
    val output: String,
    val executed: Boolean = true,
)
```

#### Where output is truncated — `SubagentHost.buildTranscript` (~515–567)

Default call sites use `truncateChars = 200`, `truncateToolOutput = 0` (then output limit falls back to `truncateChars`):

```kotlin
val toolOutputLimit = when {
    truncateToolOutput > 0 -> truncateToolOutput
    truncateChars > 0 -> truncateChars
    else -> 0
}
val output = when {
    toolOutputLimit > 0 -> truncate(outputText, toolOutputLimit)
    else -> outputText
}
steps.add(
    SubagentTranscriptStep.ToolCall(
        toolName = part.toolName,
        input = truncate(part.input, truncateChars),
        output = output,
        executed = part.isExecuted,
    ),
)
```

**Streaming progress** (`ChatService.kt` ~1302–1306): `truncateChars = 200`, **`truncateToolOutput = 2000`** (longer tool output in live transcript).

#### UI — `SubagentToolUIs.kt`

| Component | Tool output in UI |
|-----------|-------------------|
| `SubagentTranscriptToolCallStep` (ChainOfThought, streaming) | `content = null`; **only** label + `onClick` → `ModalBottomSheet` + `renderer.Preview` |
| `SubagentTranscriptToolCallCompactRow` (expanded details section) | One-line `renderer.title(context)`; click opens same Preview sheet — **no** inline output text |
| `SubagentTranscriptStepRow` → `ToolCall` | Delegates to compact row |
| Reasoning step | Inline preview truncated to **`TRUNCATE_LEN = 120`** (~507–514) |
| `truncate(text)` private (~843–847) | Defined with `TRUNCATE_LEN`; **not** used for `ToolCall` rows (truncation already in `buildTranscript`) |

`SubagentTranscriptSection` (~366–389) provides expand/collapse for **list of steps**, not per-tool output body.

#### Main agent comparison — `ChatMessageTools.kt` (~64–218)

- `ControlledChainOfThoughtStep` with **`expanded = true`** by default
- **`content`** block renders `renderer.Summary(context)` inline (expand/collapse on step)
- **`onClick`** opens `ModalBottomSheet` with `renderer.Preview` for full result

Subagent transcript tool steps mirror **click → Preview sheet** but omit **inline Summary/content** and **ControlledChainOfThoughtStep** expand.

#### Metadata / result wiring (`SubagentTools.kt` ~114–135)

```kotlin
put("subagent_tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
put("subagent_tool_calls", JsonPrimitive(result.toolCallCount))
put("subagent_streaming", JsonPrimitive(false))
// slim body:
put("tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
put("tool_calls", JsonPrimitive(result.toolCallCount))
```

---

## Files Found

| File Path | Description |
|-----------|-------------|
| `app/.../tools/SubagentToolUIs.kt` | `SpawnSubagentToolUI.title/Summary`, streaming vs final counts, transcript rows, `parseSubagentResult` |
| `app/.../subagent/SubagentProfile.kt` | `SubagentResult`, `SubagentTranscriptStep` |
| `app/.../subagent/SubagentTools.kt` | `spawn_subagent` execute, metadata + slim JSON |
| `app/.../subagent/SubagentHost.kt` | `buildTranscript`, `countToolCalls` / result assembly |
| `app/.../service/ChatService.kt` | `updateSubagentProgress` streaming metadata |
| `app/.../message/ChatMessageTools.kt` | Main-agent `ChatMessageToolStep` expand + Summary + Preview |
| `app/src/main/res/values/strings.xml` | `subagent_tool_ui_steps_and_tool_calls` ("loops") |
| `app/src/main/res/values-zh/strings.xml` | Missing `subagent_tool_ui_steps_and_tool_calls` |

## Related Specs / Task Notes

- `.trellis/tasks/07-01-bug-subagent-control-ui/prd.md` — documents `subagent_steps` vs `tool_loop_steps` metadata intent
- `.trellis/tasks/draft-subagent-prd/research/subagent-control-cancel-ui-bugs.md` — prior notes on metadata fields

## Caveats / Not Found

- `subagent_tool_ui_loops` as a string **name** does not exist; only the English phrase inside `subagent_tool_ui_steps_and_tool_calls`.
- Whether `decodeFromString(SubagentResult.serializer(), slimPayload)` always succeeds or always fails in production was not runtime-verified; static analysis shows mismatch `tool_calls` vs `tool_call_count` explains 0 count when decode succeeds without that field.
- `Summary()` composable does not render the title line tool counts; counts appear on the **collapsed tool step title** via `ToolUIRenderer.title()`.
# Design: `finish_work` meta-tool

## Overview

Add a zero-parameter meta-tool `finish_work` that gives the model an explicit “I am done” signal. `GenerationHandler` stops the step loop after executing it. Subagents always receive the tool; the root chat session receives it only when `assistant.enableSubagents` is true (optional for the main agent without changing default assistants that keep subagents off).

## Components

| Piece | Location | Responsibility |
|-------|----------|----------------|
| Tool factory | `app/.../data/ai/tools/FinishWorkTool.kt` (new) | Single `Tool` definition + name constant |
| Loop break | `GenerationHandler.generateText` | After tool execution, break if `finish_work` ran |
| Subagent injection | `SubagentPermissionBuilder.buildSubagentTools` | Append `finish_work` after permission assembly |
| Root injection | `ChatService` tool `buildList` (same block as subagent tools) | `+ createFinishWorkTool()` when `enableSubagents` |
| Prompt guidance | `SubagentRegistry` builtin `systemPrompt` + `Tool.systemPrompt` | Tell the model when to call `finish_work` |

`finish_work` is **not** a member of `SUBAGENT_TOOL_NAMES` (`SubagentTools.kt`). It must never be inherited from `parentTools` as the sole source; it is **always appended** in `buildSubagentTools` so `profile.excludedTools` and parent inheritance cannot drop it.

## Tool contract

```kotlin
const val FINISH_WORK_TOOL_NAME = "finish_work"

fun createFinishWorkTool(): Tool = Tool(
    name = FINISH_WORK_TOOL_NAME,
    description = """
        Signal that your assigned work is complete and stop further tool use.
        Call this only after you have finished the task and written a concise summary
        in your assistant message (before or alongside this call).
        No parameters.
    """.trimIndent(),
    parameters = { null },
    needsApproval = { false },
    systemPrompt = { _, _ ->
        """
        When you have fully completed the task, call the `finish_work` tool to end your
        tool loop. Your final assistant text (summary, findings, or change report) should
        already be present in the same turn; do not rely on the tool output as the summary.
        """.trimIndent()
    },
    execute = {
        listOf(UIMessagePart.Text("Task completed."))
    },
)
```

- **Input**: empty object `{}` (no schema).
- **Output**: exactly `"Task completed."` as a text part (PRD AC).
- **Approval**: always auto-execute.
- **UI**: default tool rendering; no custom Compose UI in v1.

## Loop termination (`GenerationHandler`)

### Insertion point

After the existing block that merges `executedTools` into the last assistant message and `emit(GenerationChunk.Messages(...))` (currently ~L292–310), **before** the `for` loop advances to the next `stepIndex`:

```kotlin
if (executedTools.any { it.toolName == FINISH_WORK_TOOL_NAME }) {
    Log.i(TAG, "generateText: finish_work executed, terminating tool loop")
    break
}
```

No change to the “no unexecuted tools → break” path (L205–208). `finish_work` is only relevant when the model emitted tool calls in that step.

### What the user/subagent “summary” is

- The loop stops **without** an extra model turn after `finish_work`.
- **Subagent** `SubagentHost.runToCompletion` already sets `summary = lastAssistantText(finalMessages)` — text parts on the **last assistant message**, not tool output.
- The model should place the real summary in **assistant text parts** in the same turn as the `finish_work` tool call (standard parallel text + tools pattern). `finish_work` output is only an ACK string.
- If the model calls `finish_work` with **no** text parts, summary may be empty; that is model misbehavior, not handled specially in v1.

### Optional refactor (testability)

Extract a package-visible helper:

```kotlin
internal fun shouldBreakAfterToolExecution(executed: List<UIMessagePart.Tool>): Boolean =
    executed.any { it.toolName == FINISH_WORK_TOOL_NAME }
```

Unit-test this without spinning up `GenerationHandler`.

### Continuation rounds (`SubagentHost`)

- `runToCompletion` uses one `generateText(..., maxSteps = profile.maxSteps)` call; early `break` on `finish_work` is sufficient.
- Post-run **summary continuation** (if any, driven by `summaryMinLength` / `summaryContinuationAttempts` on profile) runs **after** `generateText` returns; it does not re-enter the tool loop unless implemented as a separate `generateText` with `tools = emptyList()`. No change required for v1: finishing via `finish_work` yields the same message list shape as natural stop.

## Subagent integration

### `buildSubagentTools`

At the end of the function, after `withSpawn` and `distinctBy { it.name }`:

```kotlin
return (withSpawn + createFinishWorkTool()).distinctBy { it.name }
```

Order: workspace / inherited / spawn / **finish_work**. `distinctBy` keeps one `finish_work` if parent ever carried a duplicate.

### System prompts (`SubagentRegistry`)

Append a short, identical stanza to each builtin profile `systemPrompt` (explore / coder / reviewer), e.g.:

> When the task is complete, write your final summary in the assistant message, then call `finish_work` to stop.

Keep the stanza aligned with `Tool.systemPrompt` on `createFinishWorkTool()` so custom/global profiles that override `systemPrompt` still get tool-level hints via `GenerationHandler` tool prompt aggregation.

**Custom profiles**: persisted `SubagentProfile.systemPrompt` is not auto-patched in v1; `Tool.systemPrompt` covers them. Document in release notes that builtin text was updated.

### `buildChildAssistant`

No change: `systemPrompt = profile.systemPrompt` already flows from registry/merged profile.

## Main agent integration

In `ChatService` where tools are assembled (~L730–743), when `assistant.enableSubagents`:

```kotlin
add(createFinishWorkTool()) // or addAll(listOf(...)) once
addAll(buildSubagentToolsForChat(...))
```

When `enableSubagents == false`, do **not** register `finish_work` (unchanged default behavior).

Root session does not use `buildSubagentTools`; injection is explicit in the chat tool list only.

## Edge cases

| Case | Behavior (v1) |
|------|----------------|
| `finish_work` + other tools same turn | All tools in `toolsToProcess` execute (parallel if configured); message updated with all outputs; then **break** if any executed name is `finish_work`. Sibling tools may still run in that step — acceptable. |
| `finish_work` only, no text summary | Loop stops; `lastAssistantText` may be blank. |
| Model never calls `finish_work` | Unchanged: natural stop (no pending tools) or `maxSteps`. |
| `needsApproval` / Pending | Not applicable (`needsApproval` false). |
| Denied (hypothetical) | Treated like any denied tool in `executeSingleTool`; no `finish_work` in `executedTools` with success output → **no** finish break. |
| Resume after approval | Unchanged approval path; if `finish_work` never executes, no break. |
| Nested subagent | Each nested `generateText` has its own `finish_work` in child tool list via `toolsForSubagentProfile` → `buildSubagentTools`. |
| `finish_work` in `excludedTools` | Ignored for subagents because tool is appended after exclusions. |
| Countdown / step budget hint | **Out of scope v1**; loop break hook is the extension point for future “N steps left” system injections. |

## Data flow

```
Model (child assistant)
  → assistant message [Text summary?, Tool finish_work, Tool ...]
  → GenerationHandler executes tools
  → last assistant message parts updated (tool outputs)
  → if finish_work executed → break for-loop
  → SubagentHost.lastAssistantText(messages) → SubagentResult.summary
```

## Rollback

1. Remove `createFinishWorkTool()` usage from `buildSubagentTools` and `ChatService`.
2. Remove break check in `GenerationHandler`.
3. Revert `SubagentRegistry` prompt edits.
4. Delete `FinishWorkTool.kt`.

No schema migrations, flags, or API surface.

## Testing

| Test | Type | Assert |
|------|------|--------|
| `createFinishWorkTool().execute({})` | JVM unit | Output text `"Task completed."`; `needsApproval` false |
| `buildSubagentTools(...)` | Extend `SubagentPermissionTest` | Result names contain `finish_work` even with `excludedTools` / `inheritTools` false (workspace-only base) |
| `shouldBreakAfterToolExecution` | JVM unit | true iff list contains executed `finish_work` |
| `GenerationHandler` + `finish_work` | Optional integration | Mock provider returning one assistant message with tool call; verify flow completes in one step and does not invoke step 2 — only if existing test harness for `GenerationHandler` exists; otherwise rely on helper + manual QA |
| Regression | `./gradlew test` | Existing suites green |

Manual QA: spawn `explore` on a trivial task; confirm subagent stops after `finish_work`, parent receives `SubagentResult.summary` from assistant text, tool output shows `"Task completed."`.

## Files to touch (implementation checklist)

- `app/.../data/ai/tools/FinishWorkTool.kt` — new
- `app/.../data/ai/GenerationHandler.kt` — break after execution
- `app/.../data/ai/subagent/SubagentPermissionBuilder.kt` — append tool
- `app/.../data/ai/subagent/SubagentRegistry.kt` — builtin prompts
- `app/.../service/ChatService.kt` — root optional tool
- `app/.../subagent/SubagentPermissionTest.kt` — coverage

## Non-goals (confirm PRD)

- No `maxSteps` default changes.
- No countdown / soft step budget in v1.
- No new assistant settings flag (gating = `enableSubagents` for root).
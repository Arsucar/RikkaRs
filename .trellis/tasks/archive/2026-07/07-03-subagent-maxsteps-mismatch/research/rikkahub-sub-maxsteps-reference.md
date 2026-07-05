# Research: linklink256/rikkahub-sub — subagent `maxSteps` behavior

- **Query**: How does reference fork handle sub-agent `max_steps`, UI, loop termination, summary, architecture
- **Scope**: External (GitHub `linklink256/rikkahub-sub`, ref `870fe28e96ab4a0280b02df297c3a794c5c2352d`)
- **Date**: 2026-07-05

## Relevant files (repo paths)

| Area | Path |
|------|------|
| Profile model + builtin defaults | `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt` |
| Spawn + `runToCompletion` | `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt` |
| Tools (`spawn_subagent`, `manage_subagent_profile`) | `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt` |
| Tool-loop engine | `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` |
| Assistant-level subagent flags | `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` |
| Wiring + spawn callback | `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` |
| List / depth / parallel UI | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentPage.kt` |
| Per-profile editor (incl. maxSteps) | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` |
| Chat tool UI | `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUI.kt` |

## Two different “step limit” surfaces

1. **Main chat agent** — `GenerationHandler.generateText(..., maxSteps: Int = 256)`. Root `ChatService.handleMessageComplete` calls `generateText` **without** `maxSteps`, so the parent uses **256** by default.

2. **Subagent** — `SubagentProfile.maxSteps: Int = 32` (data-class default). Builtin profiles override: `explore` **16**, `coder` **20**, `reviewer` **12**. Passed into subagent runs only via `SubagentHost.runToCompletion`.

There is **no** assistant-level “max steps for all subagents” slider in the reference UI; only **per-profile** `maxSteps` plus assistant-level **`subagentMaxDepth`** (1–5) and **`parallelToolExecution`**.

## How `maxSteps` is enforced (GenerationHandler)

The loop is a bounded `for (stepIndex in 0 until maxSteps)`:

```kotlin
for (stepIndex in 0 until maxSteps) {
    // ... generateInternal (one LLM turn) ...
    val tools = messages.last().getTools().filter { !it.isExecuted }
    if (tools.isEmpty()) {
        break  // no tool calls → done
    }
    // approval pending → break
    // execute tools → if executedTools.isEmpty() break
}
```

Termination paths:

- **Normal**: last assistant message has no unexecuted tool parts → `break`.
- **HITL**: tool needs approval and YOLO off → `break` (wait for user).
- **Implicit cap**: loop index reaches `maxSteps - 1` and model keeps requesting tools → loop ends after last iteration (no dedicated `maxStepsReached` flag in this reference tree).

Subagent injection site:

```kotlin
generationHandler.generateText(
    // ...
    maxSteps = profile.maxSteps.coerceIn(1, 256),
    memories = emptyList(),
)
```

(`SubagentHost.kt` — `runToCompletion`.)

## SubagentHost: run to completion + summary

1. **`spawn`**: depth guard → `buildChildAssistant` → `buildChildTools` + `excludedTools` filter.
2. **First run**: `runToCompletion` with full `childTools`.
3. **Optional continuation** (if `summary.length < summaryMinLength` and attempts left): extra `runToCompletion` with **`selectContinuationTools(childTools) = emptyList()`** so continuation is a **single** text generation, not another full tool loop (avoids re-consuming `maxSteps`).

Summary extraction:

```kotlin
private fun lastAssistantText(messages: List<UIMessage>): String {
    for (message in messages.asReversed()) {
        if (message.role != MessageRole.ASSISTANT) continue
        val text = message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
        if (text.isNotBlank()) return text.trim()
    }
    return ""
}
```

`SubagentResult` fields returned to parent:

- `summary`, `succeeded`, `error`, `depth`
- `usage`, `steps` (**count of `runToCompletion` invocations**, including continuation rounds — **not** inner `GenerationHandler` step index)
- `toolCallCount` (count of `UIMessagePart.Tool` on assistant messages)
- `transcript` (reasoning / tool / text steps for UI)

If summary blank: `"(subagent produced no textual summary)"`.

## Tool result payload to parent model

`createSubagentTools` → `spawn_subagent` `execute` builds JSON text with:

`profile_name`, `succeeded`, `error`, `summary`, `depth`, `steps`, `tool_calls`

Full `transcript` is stored in **`UIMessagePart.Text.metadata`** (`subagent_transcript`, `subagent_profile`, `subagent_steps`, `subagent_succeeded`) for UI only — not emphasized as model-facing.

`manage_subagent_profile` documents `max_steps` (1–256) and `applyPatch` sets `maxSteps = int("max_steps") ?: maxSteps`.

## UI: subagent step settings

| UI | What it configures |
|----|-------------------|
| `AssistantSubagentPage` | `enableSubagents`, `subagentMaxDepth` (Slider 1–5), `parallelToolExecution`, profile list |
| `AssistantSubagentProfilePage` | **Per-profile `maxSteps`** — Slider **1..256**, label `subagent_profile_max_steps`, value string `subagent_profile_max_steps_value` |
| `SubagentToolUI` | Shows profile name; transcript steps; metadata `subagent_steps` (generation-round count from host, not maxSteps cap) |

**Single UI control for subagent tool-loop limit**: per-profile max steps on `AssistantSubagentProfilePage`. Parent chat has no separate “agent max steps” setting in this reference (fixed 256 default in code).

## Architectural notes (avoid duplication)

- **One loop implementation**: subagents reuse `GenerationHandler.generateText`; `SubagentHost` orchestrates profile → child `Assistant` → tools → flow collect.
- **Tool building delegated**: `buildChildTools: suspend (Assistant, depth) -> List<Tool>` supplied by `ChatService.buildSubagentTools` (base tools, recursive spawn, sandbox).
- **Profile merge**: `mergeSubagentProfiles(custom, disabledBuiltin)` — builtin + custom override by name; `disabledBuiltinSubagents` removes builtins without deleting custom rows.
- **Depth vs steps**: `maxDepth` / `depth` limit nesting; `maxSteps` limits tool rounds **per spawn** only.
- **Continuation decoupled**: empty tools on continuation prevents doubling wall-clock / step budget.

## Caveats / not found in reference repo

- No `maxStepsReached` / `truncated` on `SubagentResult` tied to exhausting `maxSteps` (unlike some forks/issues discussing `maxToolCalls`).
- No `mergeInheritedFrom` on sparse assistant overrides — custom `SubagentProfile` rows replace builtin by name via `mergeSubagentProfiles`; fields not edited in UI keep data-class defaults (e.g. `maxSteps = 32`) unless builtin defaults apply before override.
- GitHub code search API returned empty/incomplete for this repo; analysis used tree listing + raw file fetch.
- Parent `maxSteps=256` is implicit in `ChatService`; not exposed in assistant settings UI in files reviewed.

## Code snippets (reference)

**Profile default + builtin maxSteps** (`SubagentProfile.kt`):

```kotlin
val maxSteps: Int = 32,
// BUILTIN explore: maxSteps = 16, coder: 20, reviewer: 12
```

**Assistant-level (not maxSteps)** (`Assistant.kt`):

```kotlin
val enableSubagents: Boolean = true,
val subagentMaxDepth: Int = 2,
val subagentProfiles: List<SubagentProfile> = emptyList(),
val parallelToolExecution: Boolean = false,
```

**UI slider** (`AssistantSubagentProfilePage.kt`):

```kotlin
Slider(
    value = current.maxSteps.toFloat(),
    onValueChange = { v ->
        saveProfile { it.copy(maxSteps = v.roundToInt().coerceIn(1, 256)) }
    },
    valueRange = 1f..256f,
)
```
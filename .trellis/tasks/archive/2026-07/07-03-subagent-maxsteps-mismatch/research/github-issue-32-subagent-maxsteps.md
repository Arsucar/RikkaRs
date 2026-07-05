# Research: GitHub issue #32 — subagent max_steps / metadata / summary

- **Query**: Verify four reported problems against current `app/` subagent code
- **Scope**: internal
- **Date**: 2026-07-03

## Findings

### Problem 1: `maxSteps` not inherited via `mergeInheritedFrom()`

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt`

`SubagentProfile.maxSteps` default:

```69:69:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt
    val maxSteps: Int = 32,
```

`mergeInheritedFrom()` only merges `displayName`, `description`, `systemPrompt` when local name matches global/builtin base:

```189:195:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt
internal fun SubagentProfile.mergeInheritedFrom(base: SubagentProfile): SubagentProfile {
    if (name != base.name) return this
    return copy(
        displayName = displayName.takeIf { it.isNotBlank() && it != name } ?: base.displayName,
        description = description.ifBlank { base.description },
        systemPrompt = systemPrompt.ifBlank { base.systemPrompt },
    )
}
```

**Does not merge**: `maxSteps`, `maxToolCalls`, `workspaceAccess`, tools/skills/MCP, model params, etc.

**Resolution path**: `SubagentRegistry.resolveProfile()` applies merge only for assistant-local profiles that share a name with `effectiveGlobalProfiles(global)`:

```85:89:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentRegistry.kt
        assistant.subagentProfiles.firstOrNull { it.name == name }?.let { local ->
            effectiveGlobalProfiles(globalProfiles)
                .firstOrNull { it.name == name }
                ?.let { global -> return local.mergeInheritedFrom(global) }
            return local
```

**Builtin `maxSteps`** (`SubagentRegistry.kt`):

| Profile   | maxSteps |
|-----------|----------|
| explore   | 48       |
| coder     | 64       |
| reviewer  | 24       |

**Issue accuracy**: **Accurate** for sparse assistant-local overrides: e.g. assistant stores `SubagentProfile(name = "coder")` only → runtime `maxSteps` stays **32** (data class default), not builtin **64**, unless global DataStore entry overrides the builtin name.

---

### Problem 2a: `slimPayload` JSON missing fields

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt` (lines 125–135)

`slimPayload` includes:

- `profile_name`, `summary`, `succeeded`, optional `error`
- `steps`, `tool_loop_steps`, `transcript_size`, `tool_call_count`
- optional `usage`

**Missing from slim JSON** (present on `SubagentResult` / metadata):

- `truncated` — **not** in `slimPayload`
- `max_steps` — **never** exposed on tool result (profile limit not serialized)
- Snake_case `tool_loop_steps` yes; no `max_steps` / `truncated`

`finalMetadata` also omits `subagent_truncated` / profile `maxSteps`.

**Issue accuracy**: **Accurate** for `truncated` (and any expectation of `max_steps` on parent-visible JSON). `parseSubagentResult` fallback in UI (`SubagentToolUIs.kt` ~888–899) never reads `truncated` from slim JSON.

---

### Problem 2b: `SubagentResult` field semantics

**File**: `SubagentProfile.kt` lines 100–113

| Field | Meaning in model | Set in `SubagentHost.spawnBody` |
|-------|------------------|----------------------------------|
| `steps` | Comment absent; used as **count of `runToCompletion` runs** (initial + each summary continuation) | `steps += 1` per run (~195, 237); initial `var steps = 0` |
| `toolLoopSteps` | KDoc: tool loop steps under `maxSteps`; each step = LLM + tools | `totalToolLoopSteps += (assistantCount after run - preAssistantCount)` (~196, 238) |
| `toolCallCount` | KDoc: total tool parts on assistant messages | `countToolCalls(messages)` (~254) |

`truncated` on result: OR of `run.truncated` across runs (~202–243, 256). **`run.truncated` is only set when `maxToolCalls` budget trips** (`ToolCallBudgetStop` ~383–385), **not** when `GenerationHandler` exhausts `maxSteps`.

**Issue accuracy**: Partially accurate — slim payload omits `truncated`; semantic confusion between `steps` vs `tool_loop_steps` is real for consumers expecting “LLM rounds” vs “spawn runs”.

---

### Problem 2c: `generateText` / `runToCompletion` / `spawn`

**File**: `SubagentHost.kt`

`maxSteps` into generation:

```363:370:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt
            generationHandler.generateText(
                ...
                maxSteps = profile.maxSteps.coerceIn(1, 256),
                stepsCountdownThreshold = assistant.stepsCountdownThreshold,
```

`SubagentResult` success path (~247–258): `summary = summary.ifBlank { buildFallbackSummary(transcript) }`, `steps`, `toolCallCount`, `toolLoopSteps`, `truncated`, `transcript`.

Failure `getOrElse` (~286–294): sets `steps` but **omits** `toolLoopSteps`, `toolCallCount`, `truncated` (defaults).

**Issue accuracy**: Accurate for missing `truncated` in tool output; accurate that profile `maxSteps` drives `generateText` only after `resolveProfile` (inheritance gap affects this).

---

### Problem 3: `buildFallbackSummary` false “max steps” message

**File**: `SubagentHost.kt` lines 572–599

Fixed copy when transcript non-empty:

```572:576:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt
        fun buildFallbackSummary(transcript: List<SubagentTranscriptStep>): String {
            if (transcript.isEmpty()) return "(subagent ran out of steps with no output)"
            val sb = StringBuilder()
            sb.appendLine("(Max steps reached — auto-generated summary from transcript)")
```

Called only when primary summary blank but run **succeeded**:

```249:249:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt
                summary = summary.ifBlank { buildFallbackSummary(transcript) },
```

**No guard** on `truncated`, `profile.maxSteps`, or whether generation ended due to step limit vs empty assistant text after tools.

**Issue accuracy**: **Accurate** — wording implies max-steps exhaustion even when `truncated == false` and/or summary empty for other reasons (tools-only transcript, short continuation, etc.). Empty transcript branch also blames “ran out of steps” unconditionally.

---

### Problem 4: `subagent_steps` semantic inconsistency

**Streaming** — `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` ~1314–1326:

```kotlin
val loopSteps = subMessages.count { it.role == MessageRole.ASSISTANT }
...
put("subagent_steps", JsonPrimitive(loopSteps))
put("subagent_tool_loop_steps", JsonPrimitive(loopSteps))
```

Both keys = **assistant message count** in live subagent messages.

**Final** — `SubagentTools.kt` ~118–119:

```kotlin
put("subagent_steps", JsonPrimitive(result.steps))
put("subagent_tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
```

`subagent_steps` = **`runToCompletion` invocation count**; `subagent_tool_loop_steps` = **assistant deltas across those runs**.

**UI** — `SubagentToolUIs.kt` ~119–120 (streaming title):

```kotlin
val loopSteps = meta["subagent_tool_loop_steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    ?: meta["subagent_steps"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
```

Prefers `tool_loop_steps`; falls back to `subagent_steps`. During streaming both are equal; **after completion** `subagent_steps` can drop to 1 while `tool_loop_steps` stays high → title/step display can **change** at end of run.

Final title uses `result.toolLoopSteps` (~143) when parse succeeds.

**Issue accuracy**: **Accurate** — streaming vs final metadata use different meanings for `subagent_steps`; archived task `07-01-bug-subagent-control-ui` already noted fixing final metadata to `result.steps` (current code **has** that fix for final); streaming still uses assistant count for **both** keys.

---

## Summary: files and change directions (no code)

| # | Problem | Primary files | Direction |
|---|---------|---------------|-----------|
| 1 | `maxSteps` not inherited | `SubagentProfile.kt` (`mergeInheritedFrom`), possibly `SubagentRegistry.kt` | Extend merge for numeric/tool fields with “unset” detection (e.g. default 32 only when not explicitly stored), or merge `maxSteps` from base when local equals default and base differs |
| 2 | Slim JSON / parent visibility | `SubagentTools.kt`, optionally `SubagentToolUIs.kt` | Add `truncated` to slim payload + metadata; optionally `max_steps` from resolved profile; align failure `SubagentResult` in `SubagentHost` with full fields |
| 3 | Fallback summary wording | `SubagentHost.kt` (`buildFallbackSummary`, call site ~249) | Gate message on `truncated` / step-limit signal from `GenerationHandler`; neutral copy when summary blank but not step-limited |
| 4 | `subagent_steps` semantics | `ChatService.kt` (`updateSubagentProgress`), `SubagentTools.kt` (already split), `SubagentToolUIs.kt` | Streaming: set `subagent_steps` to same semantics as final (`result.steps` proxy: e.g. 1 while single run, or track run count); or stop using `subagent_steps` for loop count and document single key for UI |

## Caveats / Not Found

- No in-repo copy of GitHub issue #32 body; verification used task prompt + code only.
- Whether `GenerationHandler` sets an explicit “stopped at maxSteps” flag for subagents was not traced end-to-end; `truncated` on subagent result today ≠ maxSteps exhaustion.
- `mergeInheritedFrom` is `internal`; only used from `SubagentRegistry.resolveProfile` for assistant-local profiles.
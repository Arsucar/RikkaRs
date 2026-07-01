# Research: Subagent spawn_subagent token display (title vs summary)

- **Query**: How token count is displayed in `SpawnSubagentToolUI` title/summary; end-to-end `usage` population
- **Scope**: internal
- **Date**: 2026-07-01

## Findings

### 1. `SpawnSubagentToolUI.title()` — token display

**File**: `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUIs.kt`

When **streaming metadata** is present (`parseSubagentMetadata` non-null) and `subagent_streaming == "true"`, `title()` returns early (lines 115–132): display name, optional tool-call count, or cancelled string — **no token count**.

When **completed** (`parsed.result != null`), tokens are appended only if `totalTokens > 0` (lines 134–162):

```kotlin
val usage = result.usage
val totalTokens = usage?.let {
    when {
        it.totalTokens > 0 -> it.totalTokens
        else -> it.promptTokens + it.completionTokens
    }
} ?: 0
// ...
if (totalTokens > 0) {
    append(" · ")
    append(stringResource(R.string.subagent_tool_ui_token_count, totalTokens.formatNumber()))
}
```

**String resource**: `R.string.subagent_tool_ui_token_count` → `"%1$s tokens"` (default `values/strings.xml` line 118). Also in `values-zh`, `values-zh-rTW`, `values-ja`, `values-ko-rKR`, `values-ru`.

**Also in title (completed)**: `subagent_tool_ui_tool_calls_count` when `toolCalls > 0` (not loop steps).

### 2. `SpawnSubagentToolUI.Summary()` — token display

**Same file**, `Summary` composable (lines 179–241): renders `ChainOfThought` from metadata transcript, loading spinner, or error text. **Does not call `SubagentUsageLine` or show token count anywhere.**

`SubagentUsageLine` (lines 331–369) exists and formats prompt/completion/cached breakdown via `subagent_tool_ui_usage_*` strings, but **grep shows no call sites** — dead code relative to current `SpawnSubagentToolUI.Summary`.

### 3. `SubagentResult` and `usage` type

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt` (lines 99–114)

```kotlin
@Serializable
data class SubagentResult(
    @SerialName("profile_name") val profileName: String,
    @SerialName("summary") val summary: String,
    @SerialName("succeeded") val succeeded: Boolean,
    @SerialName("error") val error: String? = null,
    @SerialName("depth") val depth: Int = 0,
    @SerialName("usage") val usage: TokenUsage? = null,
    // steps, tool_call_count, tool_loop_steps, truncated, transcript ...
)
```

`TokenUsage` (`ai/src/main/java/me/rerere/ai/core/Usage.kt`): `promptTokens`, `completionTokens`, `cachedTokens`, `totalTokens`.

### 4. `SubagentHost` — `totalUsage` accumulation and `SubagentResult.usage`

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt`

- `var totalUsage: TokenUsage? = null` at spawn start (line 173).
- After each `runToCompletion`: `totalUsage = mergeUsage(totalUsage, run.usage)` (lines 197, 239).
- `runToCompletion` sets `usage = accumulateUsage(finalMessages)` (line 397): sums `message.usage` on all messages via `TokenUsage.merge` (lines 475–481).
- Success `SubagentResult` passes `usage = totalUsage` (line 252). Cancel/failure paths also pass `usage = totalUsage` where applicable (lines 216, 278, 292).
- Early **depth limit** result (lines 151–158) omits `usage` (defaults null).
- Generic failure `SubagentResult` (lines 286–294) includes `usage = totalUsage` but may be null if no run completed.

`mergeUsage` (484–487): null-safe merge of two `TokenUsage?` via `acc.merge(other)`.

### 5. Slim JSON in `SubagentTools.kt` — **usage omitted**

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt` (lines 126–136)

Tool output `text` is **slim** JSON:

```kotlin
val slimPayload = buildJsonObject {
    put("profile_name", ...)
    put("summary", ...)
    put("succeeded", ...)
    // optional error
    put("steps", ...)
    put("tool_loop_steps", ...)
    put("transcript_size", ...)
    put("tool_call_count", ...)
}.toString()
```

**No `usage` field** in slim payload. Full transcript and counts live in **metadata** (`subagent_transcript`, `subagent_tool_calls`, etc.) — metadata also has **no usage/token keys**.

### 6. `parseSubagentResult` — usage decoding gap

**File**: `SubagentToolUIs.kt` (lines 873–901)

1. **Primary path**: `JsonInstant.decodeFromString(SubagentResult.serializer(), raw)` — would decode `usage` **if** present in JSON.
2. **Fallback path** (when full deserialize fails): builds `SubagentResult` manually from JSON object + transcript from metadata — **does not read or set `usage`** (lines 888–899).

Because production tool output uses slim JSON without `usage`, primary deserialize succeeds but **`usage` is always null** on the parsed `SubagentResult`.

Unit tests (`SubagentModelTest.kt`) round-trip **full** `SubagentResult` JSON including `usage`; that does not match slim tool output shape.

## End-to-end: does token display work?

| Layer | Populates `usage`? |
|--------|---------------------|
| `SubagentHost.spawn` | Yes, when assistant messages carry `message.usage` |
| `createSubagentTools` slim `text` | **No** — stripped intentionally |
| `parseSubagentResult` → UI `result.usage` | **No** (null from slim JSON) |
| `title()` token line | Only if `result.usage` and `totalTokens > 0` |

**Conclusion**: Runtime may compute `totalUsage` correctly inside `SubagentHost`, but the UI path for `spawn_subagent` **does not receive usage** in tool output text, so **`title()` token display does not work** for normal completed spawns. Users expecting tokens in the **summary body** will also see nothing — `Summary` never shows tokens; only unused `SubagentUsageLine` would.

### If user reports "no token display" — plausible causes

1. **Slim payload omits `usage`** (primary, current code).
2. **`parseSubagentResult` fallback** without `usage` if JSON shape triggers fallback.
3. **`SubagentHost` `totalUsage` null** if no `UIMessage.usage` on subagent assistant messages (provider/streaming not attaching usage).
4. **`totalTokens` computed as 0** — title hides token segment when `totalTokens <= 0`.
5. **Still streaming** — title branch shows name/tool calls only, not tokens.
6. **Looking at Summary card** — tokens are not implemented there (only title when wired).
7. **Old messages** — same slim JSON issue persists in stored tool parts.

### Code paths summary

| Location | Tokens shown? | Source |
|----------|---------------|--------|
| `SpawnSubagentToolUI.title()` (completed) | Intended yes | `result.usage` from `parseSubagentResult(text)` |
| `SpawnSubagentToolUI.title()` (streaming) | No | metadata only |
| `SpawnSubagentToolUI.Summary()` | No | — |
| `SubagentUsageLine` | Would show breakdown | **Uncalled** |
| `AskBtwToolUI.title()` | Optional | `context.content` `usage_tokens` / `tokens` (different tool) |

## Related files

| File | Role |
|------|------|
| `SubagentToolUIs.kt` | `title`, `Summary`, `parseSubagentResult`, `SubagentUsageLine` |
| `SubagentProfile.kt` | `SubagentResult.usage: TokenUsage?` |
| `SubagentHost.kt` | `totalUsage`, `mergeUsage`, `accumulateUsage` |
| `SubagentTools.kt` | slim JSON without `usage` |
| `Usage.kt` | `TokenUsage`, `merge` |
| `app/src/main/res/values*/strings.xml` | `subagent_tool_ui_token_count`, `subagent_tool_ui_usage_*` |

## Caveats / Not Found

- No metadata key for streaming token progress (e.g. `subagent_usage`) in `ChatService` / `updateSubagentProgress` grep scope for this task.
- `TokenUsage.merge` replaces component counts when `other.promptTokens > 0` rather than adding across runs; behavior documented in `Usage.kt` — may affect `totalUsage` magnitude when multiple `runToCompletion` calls each report usage, separate from UI omission issue.
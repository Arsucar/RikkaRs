# Research: rikkahub-sub sub-agent streaming architecture

- **Query**: How `linklink256/rikkahub-sub` implements sub-agent streaming output, think/summary UI, and data model vs main/Arsucar fork
- **Scope**: internal (remote branch inspection via `git show sub/...`) + comparison to local `release/rikka-arsucar` tree
- **Date**: 2026-06-27

## Executive summary

`rikkahub-sub` treats **sub-agent execution as a nested `GenerationHandler.generateText` loop** inside `SubagentHost.spawn`. Streaming to the parent chat is **not a separate SSE channel**; it **reuses the same `GenerationChunk.Messages` flow** the main assistant uses, then **mirrors partial child state into the parent’s `spawn_subagent` tool `output`** via `ChatService.updateSubagentProgress`. The UI reads **`UIMessagePart.Text.metadata`**, not the JSON tool result body alone.

The local Arsucar fork already has **runtime + model + card UI** (`SubagentHost`, `SubagentResult`, `SpawnSubagentToolUI`), but **lacks the sub-branch streaming bridge** (`updateSubagentProgress`, `ToolCallId` context, metadata-driven transcript UI).

---

## 1. End-to-end streaming flow (sub branch)

### 1.1 Parent tool invocation

| Step | Component | Behavior |
|------|-----------|----------|
| 1 | `GenerationHandler.generateText` | Model emits `UIMessagePart.Tool` for `spawn_subagent` |
| 2 | `executeSingleTool` | Runs tool `execute`; for parallel spawns, wraps with `withToolCallId(tool.toolCallId)` |
| 3 | `createSubagentTools` → `spawn` lambda | `ChatService.buildSubagentTools` calls `subagentHost.spawn(...)` |
| 4 | `SubagentHost.runToCompletion` | Nested `generateText`; each chunk may trigger `onProgress` |
| 5 | `ChatService.updateSubagentProgress` | Patches matching `spawn_subagent` tool part `output` in live conversation |
| 6 | `SubagentToolUI` | Reads `metadata["subagent_transcript"]` and renders nested `ChainOfThought` |

**Key files (sub / `fix/subagent-streaming-render`):**

- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` (`updateSubagentProgress`, `buildSubagentTools`)
- `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` (`ToolCallIdElement`, parallel `spawn_subagent`)
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUI.kt`

### 1.2 Chunk / “SSE” handling

There is **no dedicated sub-agent SSE endpoint**. Streaming is:

1. Provider streaming → `GenerationHandler` internal `streamText` / chunk merge (same as main chat).
2. Exposed to callers as `Flow<GenerationChunk.Messages>`.
3. `SubagentHost` attaches to that flow with **throttled `onProgress`** (see §3).
4. Parent UI updates when **conversation `StateFlow` / DB-backed messages** change because tool `output` was rewritten.

So “sub-agent streaming” = **synchronous mirroring of nested generation state into parent tool metadata**, driven by the **same chunk pipeline** as normal assistant streaming.

### 1.3 Parallel `spawn_subagent`

`GenerationHandler` (sub branch) adds:

- `ToolCallIdElement` + `currentToolCallId()` / `withToolCallId`
- Parallel execution when `assistant.parallelToolExecution && n>1` **or** `subagentCount > 1`

`updateSubagentProgress` matches tools by:

```kotlin
part.toolName == "spawn_subagent" &&
  (!part.isExecuted || isStreamingSubagent(part)) &&
  (toolCallId == null || part.toolCallId == toolCallId)
```

Without `toolCallId` isolation, parallel sub-agents would **overwrite each other’s** partial outputs.

**Local fork gap:** `GenerationHandler.kt` parallelizes only when `toolsToProcess.size > 1 && subagentCount > 1` and has **no `ToolCallId` context** (grep: no matches in `app/`).

---

## 2. `updateSubagentProgress` contract (sub branch)

Location: `ChatService.kt` (private).

**Inputs:** `conversationId`, `toolCallId?`, `profileName`, `subMessages: List<UIMessage>`

**Processing:**

1. `SubagentHost.buildTranscript(subMessages, truncateToolOutput = 2000)` — limits per-tick JSON size during streaming.
2. Build `metadata` JSON object:
   - `subagent_transcript` — serialized `List<SubagentTranscriptStep>`
   - `subagent_profile`
   - `subagent_steps` (transcript size)
   - `subagent_succeeded` = `false` while streaming
   - `subagent_streaming` = `true`
3. Partial `UIMessagePart.Text`:
   - `text` = minimal JSON stub `{"profile_name":"...","succeeded":false,"streaming":true}`
   - `metadata` = above
4. `updateConversationState` → find assistant message containing target tool → `part.copy(output = listOf(partialOutput))`

**Completion:** `createSubagentTools` `execute` replaces output with full `SubagentResult` JSON in `Text.text`, and puts **full transcript in `metadata`** (not sent to model in API requests per sub-branch comments).

**Local fork:** `ChatService.buildSubagentToolsForChat` calls `subagentHost.spawn` **without `onProgress`**; `SubagentTools.execute` returns **only** `json.encodeToString(SubagentResult)` with **no metadata**. UI (`SpawnSubagentToolUI`) parses **`SubagentResult` from `text` only** after tool completes.

---

## 3. `SubagentHost.onProgress` throttling (sub branch)

In `runToCompletion`, sub branch wraps `onProgress` in `throttledProgress`:

- **Time throttle:** default **120ms** between emits for “same structure” updates.
- **Structure bypass:** signature = sum of `assistant` message `parts.size`; when signature changes (new reasoning / tool / text part), emit **immediately**.

Comments in sub `SubagentHost` also describe an **async `progressScope.launch { onProgress }`** pattern to avoid flow backpressure (commit `2263318a` on `sub/master`: “async onProgress to eliminate flow backpressure”). The streaming-render branch snippet shows `onEach { throttledProgress?.invoke }` on the generation flow.

**Local fork:** `SubagentHost.runToCompletion` calls `onProgress?.invoke(chunk.messages)` **synchronously on every chunk** with **no throttle** — but nothing wires `onProgress` from `ChatService` anyway.

---

## 4. Data model

### 4.1 `SubagentResult` / transcript (both forks, with sub extensions)

Shared core (`SubagentProfile.kt`):

- `SubagentResult`: `profile_name`, `summary`, `succeeded`, `error`, `depth`, `usage`, `steps`, `transcript`
- `SubagentTranscriptStep`: `Reasoning`, `ToolCall`, `Text`

**Sub branch extensions** (`SubagentProfile.kt` on sub):

| Field | Sub branch | Local fork |
|-------|------------|------------|
| `Reasoning.createdAt` | `Long` optional | not present |
| `ToolCall.name` | `name` | `toolName` |
| `ToolCall.executed` | `Boolean` (in-flight vs done) | not present |
| `ToolCall.childTranscript` | nested steps for grandchild | not present |
| `Text.text` | `text` | `content` |

`SubagentHost.buildTranscript` (local) maps:

- `UIMessagePart.Reasoning` → `Reasoning(text)`
- `UIMessagePart.Tool` → `ToolCall(toolName, input, output)` with **200-char truncate** in companion default
- `UIMessagePart.Text` → `Text(content)`

Sub streaming uses **`truncateToolOutput = 2000`** only in **progress** path; final tool result metadata keeps fuller transcript per `SubagentTools` comments.

### 4.2 Persistence: metadata on tool output

Sub branch **persists** streaming and final transcript under:

`UIMessagePart.Text.metadata["subagent_transcript"]`

plus flags `subagent_streaming`, `subagent_profile`, `subagent_steps`, `subagent_succeeded`.

**UIMessage / MessageNode** types are unchanged — no new `UIMessagePart` subtype; streaming is **tool output + metadata**.

### 4.3 Profile flag `streamOutput`

- `SubagentProfile.streamOutput` exists in **both** forks; child assistant gets `streamOutput = profile.streamOutput || parent.streamOutput` in `SubagentHost.buildChildAssistant`.
- Sub branch **`onProgress` wiring** in inspected `buildSubagentTools` is gated on `conversationId != null`, not explicitly on `profile.streamOutput` in the grep snippet — profile still controls whether nested `generateText` uses streaming internally.

---

## 5. Think blocks & summary blocks (UI)

### 5.1 Think / reasoning in main message stream

| Layer | Mechanism |
|-------|-----------|
| Provider-native | `UIMessagePart.Reasoning` from API |
| Tag fallback | `ThinkTagTransformer` parses `<think>...</think>` in `Text` parts |

**Sub branch** (`fix/think-tag-multi-block`): **multiple** `<think>` blocks per `Text` part → multiple `Reasoning` parts via `findAll`; streaming uses `finishedAt = null` until closing tag present.

**Local fork** (`ThinkTagTransformer.kt`): **single** `find()` per part — only first think block extracted.

Main chat UI: `ChatMessage.kt` groups reasoning/tools into `ChainOfThought` (`ThinkingStep.ReasoningStep`).

### 5.2 Sub-agent “think” in tool card

**Sub branch `SubagentToolUI`:**

- Does **not** parse `SubagentResult.summary` as primary expandable content during stream.
- Reads **`metadata.subagent_transcript`** → `SubagentTranscriptStep.Reasoning` rendered as `ChainOfThoughtStep` with Sparkles icon, label “Thinking”.
- `ToolCall` shows `executed` pending state (`"..."` in extra).
- Title uses `subagent_profile` / streaming flag (`subagent_streaming`).

**Local `SpawnSubagentToolUI` (`SubagentToolUIs.kt`):**

- Parses **completed** `SubagentResult` JSON from tool `output.text`.
- **Summary block:** `MarkdownBlock` on `result.summary` (always visible in card).
- **Transcript:** expandable section; `Reasoning` collapsed by default with tap-to-expand.
- **No metadata path**; **no live transcript** during `context.loading` except spinner — no incremental metadata updates.

### 5.3 “Summary” semantics

Two different “summary” concepts:

1. **SubagentResult.summary** — final assistant text returned to parent model (tool JSON). Shown prominently in local UI; sub streaming UI focuses on transcript timeline until complete.
2. **Conversation compression summary** — unrelated (`ChatService.compressConversation`, `summaryAsText`).

Sub-agent **summary continuation**: `SubagentHost` loops with `SUMMARY_CONTINUATION_PROMPT` when `summary.length < summaryMinLength` (local matches sub; continuation uses `selectContinuationTools` → **empty tools** on local).

---

## 6. Tool result shape: delegate / tool_use patterns

| Tool | Parent sees | UI sees |
|------|-------------|---------|
| `spawn_subagent` | `Text.text` = `SubagentResult` JSON (summary for model) | Sub: `metadata.subagent_transcript`; Local: parse same JSON |
| `ask_btw` | JSON/text answer | `AskBtwToolUI` / sub equivalent |

Sub branch explicitly splits **model-facing** (`text`) vs **UI-facing** (`metadata`) for transcript volume.

Relevant commits on `sub/fix/subagent-streaming-render`:

- `49d33e44` — continuous transcript updates during execution
- `3268378a` — throttle progress + truncate transcript per tick
- `882b5ff1` — streaming inherits parent; title tweaks
- `51b15914` — parallel same-profile + toolCallId-isolated streaming

---

## 7. Differences vs local mainline (Arsucar `release/rikka-arsucar`)

| Area | rikkahub-sub | Local fork (current tree) |
|------|----------------|---------------------------|
| Streaming to parent chat | `updateSubagentProgress` + metadata | Not implemented |
| `onProgress` from ChatService | Wired with `conversationId` + `toolCallId` | `spawn()` never receives callback |
| Parallel spawn isolation | `currentToolCallId()` | Missing |
| Parallel policy | `parallelToolExecution \|\| subagentCount>1` | `size>1 && subagentCount>1` only |
| Tool UI | `SubagentToolUI` + `ChainOfThought` + metadata | `SpawnSubagentToolUI` + `SubagentResult` JSON only |
| Transcript step schema | `executed`, `childTranscript`, `name` | Simpler `ToolCall(toolName,…)` |
| Think tags | Multi-block transformer | Single-block |
| Docs | `docs/SUBAGENT_FORK.md` | Trellis archive PRDs under `.trellis/tasks/archive/06-27-subagent-*` |

**What local already aligns with sub:**

- `SubagentHost` nested `generateText`, transcript build, summary continuation
- `SubagentTools` / `buildSubagentTools` permission model
- `SpawnSubagentToolUI` card with summary + expandable transcript **after** completion
- Strings for subagent tool UI (`subagent_tool_ui_*`)

---

## 8. Reference snippets (patterns)

**Throttle signature (sub `SubagentHost`):**

```kotlin
val signature = messages.sumOf { msg ->
    if (msg.role == MessageRole.ASSISTANT) msg.parts.size else 0
}
if (signature != lastSignature || now - lastEmitTime >= minIntervalMs) {
    cb(messages)
}
```

**Metadata keys for streaming UI:**

- `subagent_transcript`, `subagent_streaming`, `subagent_profile`, `subagent_steps`, `subagent_succeeded`

**ToolCallId injection (sub `GenerationHandler`):**

```kotlin
val result = withToolCallId(tool.toolCallId) { toolDef.execute(args) }
```

---

## Caveats / Not Found

- Could not `git diff` merge base between local HEAD and `sub/*` (histories unrelated); analysis uses `git show sub/<branch>:path` and local file reads.
- Exact `streamOutput == false` behavior for skipping `updateSubagentProgress` not confirmed in sub snippet (only `conversationId != null` gate seen).
- `sub/master` may contain additional perf commits (e.g. chat streaming recomposition `00bbd1b0`) beyond `fix/subagent-streaming-render`; streaming UI commits are on the fix branch chain listed above.
- Local task dir `.trellis/tasks/06-27-sub-agent-streaming-ui` had no `task.json` in workspace; research written per dispatch path.

## Related Specs / Archives

- `.trellis/tasks/archive/2026-06/06-27-subagent-ui-chat/prd.md` — streaming card requirements
- `.trellis/tasks/archive/2026-06/06-27-subagent-mvp/reference/INDEX.md` — maps sub-branch file roles
- `docs/SUBAGENT_FORK.md` on `sub` remote (design doc)
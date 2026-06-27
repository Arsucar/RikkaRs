# Research: Sub-agent streaming — local codebase code reference

- **Query**: Implementation patterns for sub-agent streaming UI (spawn progress, metadata, GenerationHandler, UI)
- **Scope**: internal (local `rikkahub` + archived `rikkahub-sub` reference under `.trellis/tasks/archive/.../reference/`)
- **Date**: 2026-06-27

## Findings

### Gap summary (local fork vs target)

| Capability | Local path | Status |
|------------|------------|--------|
| `SubagentHost.spawn(onProgress)` | `SubagentHost.kt` L45, L93–94, L198–200 | **Present**, not wired from `ChatService` |
| `updateSubagentProgress` | `ChatService.kt` | **Missing** (reference: archive `ChatService.kt` L1115–1174) |
| `ToolCallIdElement` / `currentToolCallId()` | `GenerationHandler.kt` | **Missing** (reference: L66–85, L375) |
| `spawn_subagent` output `metadata` | `SubagentTools.kt` L107–116 | **JSON in `text` only** (reference: L177–196) |
| UI reads `metadata["subagent_transcript"]` | `SubagentToolUIs.kt` | **Parses `SubagentResult` from `text`** (reference: `SubagentToolUI.kt` L118–131) |

---

### 1. `ChatService.buildSubagentToolsForChat`

**Path**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`  
**Key lines**: L627–638 (registration), **L1387–1458** (method), **L1461–1545** (nested spawn in `toolsForSubagentProfile`)

**Registration** (depth 0, when `assistant.enableSubagents`):

```kotlin
buildSubagentToolsForChat(
    assistant = assistant,
    settings = settings,
    parentModel = model,
    parentTools = this@buildList,
    workspaceCwd = conversation.workspaceCwd,
    depth = 0,
)
```

**`spawn` lambda (root)** — L1403–1436: **no `onProgress`**, **no `conversationId`**, **no `currentToolCallId()`**:

```1403:1436:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
                spawn = { profileName, task, _ ->
                    val profile = SubagentRegistry.resolveProfile(profileName, assistant)
                    if (profile == null) {
                        SubagentResult(/* ... */)
                    } else {
                        subagentHost.spawn(
                            profileName = profileName,
                            task = task,
                            settings = settings,
                            parentAssistant = assistant,
                            parentModel = parentModel,
                            buildChildTools = { _, childDepth ->
                                toolsForSubagentProfile(/* ... */)
                            },
                            depth = depth + 1,
                            maxDepth = maxDepth,
                            workspaceCwd = workspaceCwd,
                        )
                    }
                },
```

**Extension pattern (from archive reference)** — wire after adding `conversationId` to `buildSubagentToolsForChat` (or capture from outer `handleMessageComplete` closure):

- Read `val toolCallId = currentToolCallId()` inside `spawn` (suspend).
- Pass `onProgress = { subMessages -> updateSubagentProgress(conversationId, toolCallId, profileName, subMessages) }` when `conversationId != null`.

Reference: `.trellis/tasks/archive/2026-06/06-27-subagent-mvp/reference/app/.../ChatService.kt` L925–953.

**Nested spawn** — L1492–1525: same gap (no `onProgress`).

---

### 2. ChatService generation loop / `updateConversation` (mirror for sub-agent progress)

**Path**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

| Step | Lines | Pattern |
|------|-------|---------|
| Start generation | L514–552 | `handleMessageComplete` → `generationHandler.generateText(...)` |
| Stream chunks | L657–667 | `collect { chunk ->` on `GenerationChunk.Messages` |
| Merge messages | L660–662 | `getConversationFlow(conversationId).value.updateCurrentMessages(chunk.messages)` |
| Persist session state | L662 | `updateConversation(conversationId, updatedConversation)` |
| Completion fallback | L645–651 | `onCompletion` → `updateConversation` with `finishReasoning()` |

**Core update API**:

```1085:1094:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.state.value = conversation
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }
```

**UI exposure**: `getConversationFlow(conversationId)` → `ConversationSession.state` (`MutableStateFlow`) — L292–294, `ConversationSession.kt` L25.

**Message merge helper**: `Conversation.updateCurrentMessages` — `app/.../data/model/Conversation.kt` **L57–88** (match by `UIMessage.id`, update nodes / `selectIndex`).

**Sub-agent progress should mirror streaming** without going through `GenerationHandler` emit:

- Use `updateConversationState` + `updateCurrentMessages` on **last ASSISTANT** message.
- Patch `UIMessagePart.Tool` where `toolName == "spawn_subagent"` and `toolCallId` matches.
- Set `tool.output = listOf(UIMessagePart.Text(text = partialJson, metadata = transcriptMetadata))`.

Reference implementation: archive `ChatService.updateSubagentProgress` L1115–1174.

**DB writes**: Normal streaming only updates in-memory `session.state`; `saveConversation` (L1109–1122) is separate (e.g. after generation / tool approval). Progress ticks typically **do not** need DB on every tick unless product requires it.

---

### 3. `UIMessagePart.Text` and `metadata`

**Path**: `ai/src/main/java/me/rerere/ai/ui/Message.kt`

**Definition** — L359–368 (`UIMessagePart` sealed class, abstract `metadata` at L361):

```365:368:ai/src/main/java/me/rerere/ai/ui/Message.kt
    data class Text(
        val text: String,
        override var metadata: JsonObject? = null
    ) : UIMessagePart()
```

**Type**: `kotlinx.serialization.json.JsonObject?` (not `Map<String, String>`).

**Streaming merge behavior** — L39–51: consecutive `Text` deltas append to last `Text` part; **metadata on delta is not merged** in `appendChunk` for Text (only Image/Reasoning/Tool handle metadata).

**Elsewhere in app**:

- `WorkspaceToolUIs.kt`: `UIMessagePart.metadataAs<DiffMetadata>()` on tool **output** parts.
- Typed helpers: `ai/.../MessageMetadata.kt` — `metadataAs<T>()`, `toMetadata()` for provider-specific keys.

**Sub-agent keys (target schema, from PRD / reference)** — stored on tool output `Text.metadata`:

| Key | Purpose |
|-----|---------|
| `subagent_transcript` | JSON element: serialized `List<SubagentTranscriptStep>` |
| `subagent_profile` | Profile name string |
| `subagent_steps` | Step count |
| `subagent_succeeded` | Boolean |
| `subagent_streaming` | `true` while in progress |

**Model vs UI**: Reference comments state provider/API serialization uses **`Text.text`** for tool results; **`metadata` is for persistence + UI** (archive `SubagentTools.kt` L177–178). Confirm provider layer strips metadata when building tool messages (not re-verified in this pass).

---

### 4. `GenerationHandler` — `executeSingleTool`, parallel runs, `ToolCallIdElement`

**Path**: `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`

**Tool loop** — L95–281: steps until no pending tools; each step either streams LLM (`generateInternal`) or executes tools.

**Parallel execution** — L242–255:

```242:255:app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt
            val subagentCount = toolsToProcess.count { it.toolName == "spawn_subagent" }
            val runInParallel = toolsToProcess.size > 1 && subagentCount > 1
            val executedTools: List<UIMessagePart.Tool> = if (runInParallel) {
                coroutineScope {
                    toolsToProcess.map { tool ->
                        async { executeSingleTool(tool, toolsInternal) }
                    }.awaitAll().filterNotNull()
                }
            } else {
                toolsToProcess.mapNotNull { tool ->
                    executeSingleTool(tool, toolsInternal)
                }
            }
```

**Merge executed tools into last assistant message** — L262–269 (by `toolCallId`).

**`executeSingleTool`** — L397–466:

- `Denied` / `Answered` / `Pending` branches.
- Default: `toolDef.execute(args)` at **L438** — **no** `withToolCallId` wrapper locally.

**Reference: add `ToolCallIdElement`** — archive `GenerationHandler.kt` L66–85:

```kotlin
private class ToolCallIdElement(val toolCallId: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ToolCallIdElement>
}
suspend fun currentToolCallId(): String? = coroutineContext[ToolCallIdElement]?.toolCallId
private suspend fun <T> withToolCallId(toolCallId: String, block: suspend () -> T): T =
    withContext(ToolCallIdElement(toolCallId)) { block() }
```

Wrap execute at ~L438:

```kotlin
val result = withToolCallId(tool.toolCallId) { toolDef.execute(args) }
```

**Reference parallel rule** (archive L275–277): parallel if `(assistant.parallelToolExecution && size > 1) || subagentCount > 1` — local only uses `size > 1 && subagentCount > 1`.

**Assistant streaming (normal)** — `generateInternal` L357–374: `providerImpl.streamText` → `messages.handleMessageChunk` → `onUpdateMessages(messages)` which emits `GenerationChunk.Messages` with `visualTransforms`.

---

### 5. `ThinkTagTransformer`

**Path**: `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/ThinkTagTransformer.kt`  
**Registered**: `ChatService.kt` L152–157 (`outputTransformers`).

**Full file**: 80 lines. **Single-block** regex per `UIMessagePart.Text`:

- `THINKING_REGEX = Regex("<think>([\\s\\S]*?)(?:</think>|$)", DOT_MATCHES_ALL)` — L10
- `visualTransform` L15–46: one `Reasoning` + stripped `Text` per matching text part
- `onGenerationFinish` L48–79: sets `finishedAt` on reasoning

**Implication for sub-agent streaming**: Child messages inside `SubagentHost` still go through parent **output** transformers only on **parent** `GenerationHandler` emissions—not on each `onProgress` snapshot. Transcript for UI is built by `SubagentHost.buildTranscript` (reasoning/tool/text parts), not by `ThinkTagTransformer`, unless child stream is passed through transformers separately (it is not today).

---

### 6. `SpawnSubagentToolUI` (`SubagentToolUIs.kt`)

**Path**: `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUIs.kt`  
**Object**: `SpawnSubagentToolUI` L49–179

**Data source today**:

- `parseSubagentResult` L391–402: **`context.tool.output` Text joined → `JsonInstant.decodeFromString(SubagentResult)`**
- **Does not read `metadata`**

**Loading UX** — L109–123: spinner when `context.loading && result == null` (no partial transcript).

**Summary card** — L99–160: `Card` + optional `MarkdownBlock` for `result.summary`, error line, `SubagentUsageLine`, `SubagentTranscriptSection` from **`result.transcript`** (post-complete).

**Transcript UI** — L291–388: custom expand/collapse, not `ChainOfThought`; reasoning/tool/text step rows.

**Target changes (reference `SubagentToolUI.kt`)**:

- `subagentMetadata(context)` → first output `Text.metadata` (archive L118–120)
- `transcriptSteps(context)` → decode `metadata["subagent_transcript"]` (L123–131)
- `Summary` → `ChainOfThought` with Sparkles for reasoning (L87–97, L141–169)
- `title` uses `subagent_streaming` / `subagent_succeeded` (L58–68)

**`ToolUIContext.loading`**: `ToolUI.kt` L40–47 — `loading` true when tool not executed; progress updates require **`output` non-empty with metadata** while `isExecuted` may still be false until `execute` returns—reference uses streaming flag in metadata and `isStreamingSubagent()` on parent tool part.

---

### 7. Conversation update mechanism (ViewModel / ChatService / StateFlow)

| Layer | File | Mechanism |
|-------|------|-----------|
| Session state | `ConversationSession.kt` L25 | `val state = MutableStateFlow(initial)` |
| Chat VM read | `ui/pages/chat/ChatVM.kt` | Collects `chatService.getConversationFlow(conversationId)` (pattern; not fully opened in this research) |
| Generation job | `ConversationSession` L34–36, `ChatService.handleMessageComplete` | `session.setJob(job)` on launched coroutine |
| Stream → UI | `ChatService` L657–662 | Each `GenerationChunk.Messages` → `updateConversation` |
| Sub-agent host progress | `SubagentHost.runToCompletion` L196–202 | `onProgress?.invoke(chunk.messages)` on every child chunk |

**Child generation** runs inside **tool `execute`** (blocking parent flow collection until spawn completes). Parent UI can still refresh if **`updateSubagentProgress`** mutates `session.state` on IO thread (reference notes concurrent parallel spawns on `Dispatchers.IO`).

**Transcript builder** — `SubagentHost.buildTranscript` L309–348: walks ASSISTANT messages → `Reasoning`, `Tool`, `Text` → `SubagentTranscriptStep`. Local signature: `truncateChars` default 200; reference progress uses `truncateToolOutput = 2000` parameter name in archive (local API may need overload).

---

### Related files (modify / extend checklist)

| File | Action |
|------|--------|
| `ChatService.kt` | Add `conversationId` to `buildSubagentToolsForChat`; `onProgress`; `updateSubagentProgress`, `isStreamingSubagent` |
| `GenerationHandler.kt` | `ToolCallIdElement`, `currentToolCallId()`, `withToolCallId` around `execute` |
| `SubagentTools.kt` | Final `execute`: `metadata` with full transcript (archive L177–196) |
| `SubagentToolUIs.kt` | Read metadata; optional `ChainOfThought` |
| `SubagentHost.kt` | Optional: `buildTranscript` truncation param for progress ticks (already has `truncateChars`) |

---

### External / reference only (not in main tree)

| File | Role |
|------|------|
| `.trellis/tasks/archive/2026-06/06-27-subagent-mvp/reference/app/.../ChatService.kt` | `updateSubagentProgress`, spawn wiring |
| `.trellis/tasks/archive/2026-06/06-27-subagent-mvp/reference/app/.../GenerationHandler.kt` | `ToolCallIdElement` |
| `.trellis/tasks/archive/2026-06/06-27-subagent-mvp/reference/app/.../SubagentTools.kt` | metadata on complete |
| `.trellis/tasks/archive/2026-06/06-27-subagent-mvp/reference/app/.../SubagentToolUI.kt` | metadata-driven UI |

---

## Caveats / Not Found

- **`ToolCallIdElement` in `app/`**: not found (confirmed grep).
- **`updateSubagentProgress` in local `ChatService.kt`**: not found.
- **`buildSubagentToolsForChat` does not receive `conversationId`** today — must thread from `handleMessageComplete` (L514+) where `conversationId` is in scope.
- **PRD mentions `metadata: Map<String, String>`** — actual type is **`JsonObject?`** on all `UIMessagePart` variants.
- **Whether metadata is stripped from API tool payloads**: assumed from reference comments; provider serialization not traced in this research.
- **Exact `streamOutput == false` gating** for skipping progress: not verified in local code.
# Research: #248 streaming jank hot path (HEAD verification)

- **Query**: Verify issue #248 long-reasoning continuous streaming (~300s / ~40 tps) UI jank root-cause locations on current HEAD
- **Scope**: internal (code + prior audit notes)
- **Date**: 2026-08-10
- **HEAD**: `066ef0df` (`fix(#239): compile ReplayResponseLifecycleTest with Buffer source`)

## Findings

### End-to-end streaming path (token → UI)

```
SSE EventSource
  → ChatCompletionsAPI.callbackFlow.buffer(Channel.UNLIMITED)   // ai/.../ChatCompletionsAPI.kt:264
  → GenerationHandler.generateInternal.handleStreamChunk        // GenerationHandler.kt:469-480
      messages.handleMessageChunk  (String concat)              // ai/.../Message.kt:48,84
      onUpdateMessages(messages)
  → streamText onUpdateMessages:                                // GenerationHandler.kt:219-240
      messages = it.transforms(outputTransformers)              // mostly no-op for Output*
      emit(Messages(messages.visualTransforms(...)))            // ThinkTag + UpdateVariable visual
  → ChatService.collect chunk                                   // ChatService.kt:975-1003
      updateConversationState { prev.updateCurrentMessages(...) } // :978-988, :2558-2561
      checkFilesDelete(updated, previous)                       // :2561 → :2780-2789
      Conversation.files getter walks all parts                 // Conversation.kt:49-53
  → ConversationSession.state MutableStateFlow emit              // ConversationSession.kt:31,128-136
  → ChatVM.conversation = getConversationFlow                   // ChatVM.kt:122
  → ChatPage collectAsStateWithLifecycle(conversation)          // ChatPage.kt:136
  → ChatList / ChatMessage / ChatMessageReasoning / MarkdownBlock
```

Generation jobs are started via `appScope.launch { ... }` (`ChatService.kt:527,614,678,730`), and `AppScope` default dispatcher is **Main** (`RikkaHubApp.kt:263-269`). Stream `collect` + state publish therefore run on the main thread unless an inner `withContext` moves work.

---

### Claim verification matrix

| Issue claim | Status on HEAD | File:line |
|---|---|---|
| AppScope = Dispatchers.Main | **Confirmed** | `RikkaHubApp.kt:263-269` |
| ChatCompletionsAPI buffer UNLIMITED, no conflate | **Confirmed** (intentional for #1295) | `ChatCompletionsAPI.kt:263-264` |
| Same UNLIMITED pattern on other providers | **Confirmed** | `ResponseAPI.kt:202`, `GoogleProvider.kt:350`, `ClaudeProvider.kt:288` |
| GenerationHandler visualTransforms per chunk | **Confirmed** | `GenerationHandler.kt:219-240`, `Transformer.kt:130-151` |
| Message string concat per delta | **Confirmed** | `Message.kt:48` (Text), `Message.kt:84` (Reasoning) |
| ChatService updateConversationState + checkFilesDelete per token | **Confirmed** | `ChatService.kt:978-988`, `2558-2561`, `2780-2789` |
| Conversation.files full-scan getter | **Confirmed** | `Conversation.kt:49-53` |
| ChatMessageReasoning LaunchedEffect(reasoning.reasoning, loading) | **Confirmed** | `ChatMessageReasoning.kt:96-109` |
| Duration ticker LaunchedEffect(loading) every 50ms | **Confirmed** | `ChatMessageReasoning.kt:111-117` |
| ChainOfThought animateContentSize | **Confirmed** | `ChainOfThought.kt:94-96` |
| ChatPage full Conversation collectAsState | **Confirmed** | `ChatPage.kt:136` (+ many sibling collects 135-181) |
| Markdown mapLatest reparse thrash | **Confirmed** | `Markdown.kt:247-253`, `MarkdownNew.kt:137-143` |
| “visualTransforms 全量正则” | **Partially outdated** | See note below |

#### visualTransforms / regex note

`outputTransformers` (`ChatService.kt:386-392`):

1. `ThinkTagTransformer` — **has** `visualTransform` (regex split `<think>` every call) — `ThinkTagTransformer.kt:50-72`
2. `Base64ImageToLocalFileTransformer` — default visual no-op
3. `RegexOutputTransformer` — **only** `onGenerationFinish`, **no** visualTransform — `RegexOutputTransformer.kt:11-47`
4. `UpdateVariableOutputTransformer` — **has** `visualTransform` (strip closed `<UpdateVariable>` blocks) — `UpdateVariableOutputTransformer.kt:43-65`

Per-chunk **pipeline** regex cost is primarily ThinkTag (+ UpdateVariable when enabled), not RegexOutputTransformer.

UI still runs `replaceRegexes(..., visual = true)` on every recomposition:

- Text parts: `ChatMessage.kt:461-469`
- Reasoning content: `ChatMessageReasoning.kt:172-176`

`replaceRegexes` itself caches compiled `Regex` (`Assistant.kt:138-175`) but still folds/replaces over growing strings each call.

Also note: per-chunk callback assigns `messages = it.transforms(outputTransformers)` before visual emit (`GenerationHandler.kt:220-227`). `MessageTransformer.transform` default is identity; Output transformers do not override `transform`, so this is mostly a no-op pass — real per-chunk work is `visualTransforms`.

---

### Files Found

| File Path | Description |
|---|---|
| `app/.../RikkaHubApp.kt` | `AppScope` Main dispatcher |
| `ai/.../openai/ChatCompletionsAPI.kt` | SSE `buffer(UNLIMITED)` |
| `app/.../data/ai/GenerationHandler.kt` | per-chunk transforms + visualTransforms + emit |
| `ai/.../ui/Message.kt` | delta append via `String +` |
| `app/.../service/ChatService.kt` | collect → updateConversationState → checkFilesDelete |
| `app/.../service/ConversationSession.kt` | StateFlow publish under lock |
| `app/.../data/model/Conversation.kt` | `files` getter full walk; `updateCurrentMessages` |
| `app/.../ui/pages/chat/ChatVM.kt` | exposes session StateFlow |
| `app/.../ui/pages/chat/ChatPage.kt` | full Conversation collect |
| `app/.../ui/pages/chat/ChatList.kt` | auto-scroll snapshotFlow while loading |
| `app/.../ui/components/message/ChatMessageReasoning.kt` | LaunchedEffect key = full reasoning text |
| `app/.../ui/components/message/ChatMessage.kt` | replaceRegexes + animateContentSize + MarkdownBlock |
| `app/.../ui/components/ui/ChainOfThought.kt` | animateContentSize on CoT column |
| `app/.../ui/components/richtext/Markdown.kt` | mapLatest parse; AnnotatedString rebuild |
| `app/.../ui/components/richtext/MarkdownNew.kt` | mapLatest HTML path |
| `app/.../data/ai/transformers/ThinkTagTransformer.kt` | visualTransform think-tag split |
| `app/.../data/ai/transformers/UpdateVariableOutputTransformer.kt` | visualTransform strip |
| `app/.../data/model/Assistant.kt` | replaceRegexes + regex cache |

### Code Patterns

#### 1) AppScope Main (`RikkaHubApp.kt:263-269`)

```kotlin
class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e -> ... }
)
```

`ChatService` injects `appScope` and launches generation on it; idle checks also use that scope (`ConversationSession` ctor `scope = appScope`).

#### 2) UNLIMITED buffer (`ChatCompletionsAPI.kt:263-264`)

```kotlin
// trySend 在缓冲满时会静默丢弃 delta，导致回复中间缺字 (#1295)，因此缓冲必须无界
}.buffer(Channel.UNLIMITED)
```

No `.conflate()` / `.sample()` on this flow. Slow Main consumer ⇒ queue growth (heap), not dropped tokens.

#### 3) Per-chunk visualTransforms (`GenerationHandler.kt:219-240`, `469-480`)

```kotlin
// handleStreamChunk
messages = messages.handleMessageChunk(chunk = chunk, model = model)
onUpdateMessages(messages)

// onUpdateMessages in streamText
messages = it.transforms(...)
emit(GenerationChunk.Messages(messages = messages.visualTransforms(...), stepIndex = stepIndex))
```

At ~40 tps for ~300s ≈ **12,000** visual transform + state publish cycles for one long reasoning stream.

#### 4) String concat (`Message.kt:48`, `84`)

```kotlin
acc.dropLast(1) + lastPart.copy(text = lastPart.text + deltaPart.text)
// ...
acc.dropLast(1) + UIMessagePart.Reasoning(
    reasoning = lastPart.reasoning + deltaPart.reasoning,
    createdAt = lastPart.createdAt,
    finishedAt = null,
)
```

Each delta allocates a new full string (classic O(n²) growth with message length). Also allocates new parts list via `dropLast(1) +`.

#### 5) State publish + checkFilesDelete (`ChatService.kt:978-988`, `2558-2561`, `2780-2789`)

```kotlin
is GenerationChunk.Messages -> {
    updateConversationState(conversationId) { prev ->
        var next = prev.updateCurrentMessages(chunk.messages)
        // optional variables copy
        next
    }
    // tryEmit notification + maybeWriteGenerationCheckpoint
}

fun updateConversationState(...) {
    val result = getOrCreateSession(conversationId).updateState(update) ?: return
    val (previous, updated) = result
    checkFilesDelete(updated, previous)
}

private fun checkFilesDelete(...) {
    val newFiles = newConversation.files   // full walk
    val oldFiles = oldConversation.files   // full walk
    val deletedFiles = oldFiles.filter { file -> newFiles.none { it == file } }
    if (deletedFiles.isNotEmpty()) { filesManager.deleteChatFiles(deletedFiles) }
}
```

`Conversation.files` (`Conversation.kt:49-53`):

```kotlin
val files: List<Uri>
    get() = messageNodes
        .flatMap { node -> node.messages.flatMap { it.parts } }
        .collectAllParts()
        .mapNotNull { it.fileUri() }
```

On pure reasoning text growth, deleted set is almost always empty, but **two full part walks still run every token** on Main.

`updateCurrentMessages` (`Conversation.kt:76-110`) rebuilds `messageNodes` list structure for the visible stream.

#### 6) ChatMessageReasoning effect key (`ChatMessageReasoning.kt:96-109`)

```kotlin
LaunchedEffect(reasoning.reasoning, loading) {
    if (loading) {
        if (!state.expandState.expanded && settings.displaySetting.showThinkingContent)
            state.expandState = ReasoningCardState.Preview
        scrollState.animateScrollTo(scrollState.maxValue)
    } else {
        // auto-close / keep expanded
    }
}
```

Every token changes `reasoning.reasoning` ⇒ effect restarts ⇒ cancels prior `animateScrollTo` and starts a new one. Expand-state assignment also re-runs.

Separate duration loop (`:111-117`) keys only on `loading` and ticks every 50ms while loading — lower cost than text-keyed effect, still Main work.

`ReasoningState` is `remember(reasoning.createdAt)` (`:88-94`) — stable across tokens of one reasoning part (good). Problem is the text-keyed effect, not state identity.

#### 7) Layout animation while streaming

- CoT card: `ChainOfThought.kt:94-96` `.animateContentSize(...)`
- Assistant/user bubbles: `ChatMessage.kt:473,496,521,528` `.animateContentSize()`
- Prior audit F11-3 also flags this combination with Markdown reparse

#### 8) ChatPage full Conversation collect (`ChatPage.kt:135-181`)

```kotlin
val conversation by vm.conversation.collectAsStateWithLifecycle()
```

Any `messageNodes` mutation invalidates the whole page composition root that reads `conversation.*`. LazyColumn keys by node id (`ChatList` itemsIndexed) limit **item** reuse, but parent still recomposes and the streaming last node fully refreshes.

#### 9) Markdown mapLatest thrash (`Markdown.kt:242-253`)

```kotlin
var (data, setData) = remember { mutableStateOf(parseMarkdown(content)) }
LaunchedEffect(Unit) {
    snapshotFlow { updatedContent }
        .distinctUntilChanged()
        .mapLatest { parseMarkdown(it) }   // cancel previous if new content arrives
        .catch { ... }
        .flowOn(Dispatchers.Default)
        .collect { setData(it) }
}
```

At 40 tps with growing content, previous Default-thread parses are cancelled before completion often; successful applies still recompose full AST children. Initial composition also **synchronously** `parseMarkdown(content)` on first frame (`:242`).

Paragraph AnnotatedString path (`Markdown.kt:802-818`) uses `remember(content, ...)` so rebuilds when content string identity/value changes — expected per successful parse/recompose.

Reasoning uses `MarkdownBlock` inside preview/expanded content (`ChatMessageReasoning.kt:171-179`).

#### 10) ChatList auto-scroll (`ChatList.kt:292-303`)

```kotlin
LaunchedEffect(state) {
    snapshotFlow { state.layoutInfo.visibleItemsInfo }.collect { visibleItemsInfo ->
        if (!state.isScrollInProgress && loadingState) {
            if (visibleItemsInfo.isAtBottom()) {
                state.requestScrollToItem(conversationUpdated.messageNodes.lastIndex + 10)
            }
        }
    }
}
```

Continuous layout observation while loading; interacts with content-size growth and CoT/bubble animations.

---

### Related Specs / Prior audit

- `.trellis/tasks/08-01-08-01-full-project-audit/audit-reports/D11-ui-perf.md` — F11-3 streaming path (Conversation invalidation + animateContentSize + Markdown)
- `.trellis/tasks/08-01-08-01-full-project-audit/audit-reports/D13-concurrency.md` — F13-7 UNLIMITED buffer; AppScope Main allocation notes
- Issue body technical analysis (gh issue 248) matches current HEAD line numbers within small drift:
  - checkFilesDelete was cited `:2779` → now `:2780`
  - ChatMessageReasoning LaunchedEffect still `:96`
  - Markdown mapLatest still `:247-253`
  - ChatPage conversation collect still `:136`

### Related issue reproduction profile

- Long reasoning stream ~300s @ ~40 tps ⇒ ~12k tokens
- Symptoms: scroll/input/animation jank, worse as content grows, residual lag after stream ends (consistent with Main backlog + GC from large string/list churn)

## Caveats / Not Found

- No runtime Perfetto/Macrobenchmark traces in-repo for #248; severities are static call-graph estimates.
- `task.py current` reported no active task; research written under explicit path `08-09-issue-248-streaming-jank` per user instruction.
- `RegexOutputTransformer` is **not** on the per-token visual path (issue text slightly overstates “visualTransforms 全量正则”); UI `replaceRegexes` **is** still per-recompose.
- Checkpoint path (`maybeWriteGenerationCheckpoint`, `ChatService.kt:998-1001`) is step-index gated, not per-token DB write by default — lower priority than state/UI path for pure reasoning streams.
- Subagent progress also uses `updateConversationState` (`ChatService.kt:2588+`) but is out of scope for pure reasoning jank unless concurrent.

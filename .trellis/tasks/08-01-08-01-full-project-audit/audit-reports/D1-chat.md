# D1 Chat Core Flow — Static Audit Report

> Scope: send → transformers → context/compression → provider stream → output transforms → persist / branch / regenerate / title / delete-navigate  
> Method: read-only static analysis (no Gradle, no runtime)  
> Date: 2026-08-01  
> Package roots: `app/.../me/rerere/rikkahub`, `ai/.../me/rerere/ai`

---

## 1. 链路梳理 (Step-by-step)

### 1.1 UI entry

| Step | Location | Behavior |
|------|----------|----------|
| 1 | `ChatInput.sendMessage` / `ChatPage` | Collects `UIMessagePart` list from `ChatInputState` |
| 2 | `ChatVM.handleMessageSend(content, answer)` | Empty-input guard → `ChatService.sendMessage` |
| 3 | `ChatVM` holds `conversation: StateFlow` from `ChatService.getConversationFlow`; ref-count via `addConversationReference` / `removeConversationReference` |

### 1.2 Send path (`ChatService.sendMessage`)

1. Cancel previous generation job on same conversation; `join()` then `finishInterruptedPendingTools`.
2. `preprocessUserInputParts`: apply assistant **USER-scope** regex (`visual=false`) to text parts only.
3. Append `UIMessage(USER, parts).toMessageNode()` to `messageNodes`.
4. `saveConversation` (mutex + insert/update).
5. If `answer`: `handleMessageComplete(..., NormalSend)`.
6. Emit `_generationDoneFlow` (even for answer=false / errors other than cancel).

### 1.3 Completion / generation (`handleMessageComplete`)

1. Resolve assistant + model via `Settings.resolveGenerationTarget`; **silent return if model null**.
2. Create/reuse hook logical turn.
3. Clear `chatSuggestions`; warn if tools needed but model lacks TOOL ability.
4. `sanitizeInvalidMessages` (fix selectIndex; drop nodes whose current message has non-resumable unresolved tools).
5. `prepareGenerationRequest` → tools + input transformers + optional first `PreparedProviderInput`.
6. `maybeAutoCompressBeforeSend` (NormalSend only, when providerInput present).
7. `GenerationHandler.generateText(...).collect`:
   - each `GenerationChunk.Messages` → `updateConversationState { updateCurrentMessages }` (in-memory only during stream).
   - `AppEvent.ChatGenerationUpdate` via `tryEmit` (may drop).
8. `onCompletion`: finish reasoning, clean streaming metadata, emit `ChatGenerationEnded`.
9. Success: **persist** final conversation; hooks; background title / suggestion / semantic summarize.
10. Failure: mark turn failed, addError, cleanup subagent streaming metadata. **Cancel rethrows without final save in onSuccess** (partial stream state may already be in memory; cancel path relies on prior saves + stopGeneration cleanup).

### 1.4 Input transformers (order)

**Static base** (`ChatService.inputTransformers`):

1. `TimeReminderTransformer` — gap > 1h injects synthetic USER `<time_reminder>`
2. `PromptInjectionTransformer` — mode/lorebook/preset injections
3. `PlaceholderTransformer` — `{{cur_date}}`, `{{char}}`, …
4. `DocumentAsPromptTransformer` — parse PDF/DOCX/PPTX/EPUB into `<UploadFile>` text **and keeps original Document parts**
5. `OcrTransformer` — non-vision models: image → OCR text (cache)
6. `SlashSkillInputTransformer` — SlashSkill parts → tag prefix on text

**Dynamic append** in `prepareGenerationRequest`:

7. `MemoryTableInjectionTransformer` (if memory table enabled)
8. `SemanticMemoryTransformer` (if semantic memory on)
9. `TemplateTransformer` (Pebble messageTemplate)
10. `WorkspaceReminderTransformer`

Applied inside `GenerationHandler.prepareProviderInput` **after** system prompt assembly and **after** `limitContext`.

### 1.5 Context building / token / truncation / #59 auto-compress

| Mechanism | Where | Notes |
|-----------|-------|-------|
| System prompt | `prepareProviderInput` | Conversation system prompt if allowed, else assistant; + memory block + per-tool system prompts |
| Tiered context limit | `List<UIMessage>.limitContext` in `ai/.../Message.kt` | Hysteresis: keep ~50% of limit when exceeded; `alignContextStart` for tool pairing |
| Token estimate | `estimatePromptTokens` | **chars/4**, text parts only — used for rate limit + auto-compress gate |
| Auto-compress (#59) | `AutoCompressionPolicy` + `maybeAutoCompressBeforeSend` | Only `NormalSend` + providerInput; armed/disarmed watermark; fingerprint de-dupe; per-conversation compression mutex |
| Manual compress | `compressConversation` | Chunk summaries → hide old nodes + insert summary USER nodes (`compressHiddenCount`) |
| Tool output cap | `maybeTruncateToolOutput` | 32k chars when shell present; dump to `/tool_outputs` |

### 1.6 Provider call + streaming

1. `ProviderRateLimiter.await` then `streamText` / `generateText`.
2. Chunks merged via `handleMessageChunk` (require non-empty messages; role mismatch appends new message).
3. Multi-step tool loop up to `maxSteps` default **256**; last step injects MAX_STEPS_PROMPT and disables tools.
4. Tool approval: Pending → break; resume via `handleToolApproval` → `ToolContinuation`.
5. Parallel tools if `assistant.parallelToolExecution`; subagents semaphore 1–5.

### 1.7 Output transformers

**List** (`outputTransformers`):

1. `ThinkTagTransformer` — `visualTransform` + `onGenerationFinish`: `<think>` → Reasoning parts
2. `Base64ImageToLocalFileTransformer` — **only** `onGenerationFinish`
3. `RegexOutputTransformer` — **only** `visualTransform` with `visual=false` (persists into streamed messages via transforms path)

Streaming path in `generateText`:

- On each update: `transforms` (mutate stored list) then `visualTransforms` (emit UI view).
- After step: `visualTransforms` again, then `onGenerationFinish`, set `finishedAt`, emit.

**UI-only visual regex** (`visual=true`) applied in `ChatMessage` Compose, not in transformers.

### 1.8 Persistence / branch / regenerate / title / delete

| Operation | Path |
|-----------|------|
| Stream updates | Memory only (`updateConversationState`) |
| Final save | `saveConversation` after success |
| Branch switch | UI `ChatMessageBranch` → node `selectIndex`; API `selectMessageNode` persists |
| Edit message | New branch message on same node, `selectIndex = messages.size` (new tip) |
| Regenerate USER | Truncate nodes to that user node, re-complete |
| Regenerate ASSISTANT | `messageRange` = visible prefix excluding that assistant |
| Title | Background `generateTitle` if blank (or force); reloads DB then save title |
| Delete conversation | `ConversationRepository.deleteConversation` (DB + FTS + files); **does not stop generation or clear session** |
| Delete-then-navigate | `ChatDrawer`: delete → if current, `navigateToChatPage` (new random id via `clearAndNavigate`) |

### 1.9 Session lifecycle

`ConversationSession`: ref-count + generation job; idle 5s → `removeSession`.  
`isInUse = refs > 0 || isGenerating`.  
Streaming intentionally not blocked by `persistenceMutex`.

---

## 2. Findings

### F1-1 — Delete conversation while generating does not cancel job or session

- **File:line:** `ChatVM.kt:585-588`, `ChatDrawer.kt:287-294`, `ConversationRepository.kt:358-374`, `ChatService.kt:454-482`
- **Severity:** CRITICAL
- **Description:** Deleting a conversation only hits the repository. Active `ConversationSession` generation job continues; `saveConversation` / stream updates may re-insert or resurrect a deleted conversation (exists check → insert path). Drawer navigates away without `stopGeneration`.
- **Evidence:**
```kotlin
// ChatVM
fun deleteConversation(conversation: Conversation): Job =
    viewModelScope.launch {
        conversationRepo.deleteConversation(conversation)
    }

// ChatService.saveConversation
if (!exists) {
    conversationRepo.insertConversation(updatedConversation)
} else {
    conversationRepo.updateConversation(updatedConversation)
}
```
- **Suggested fix:** Before delete: `stopGeneration(id)`, wait join, remove session from map, then delete. Or mark session tombstoned so save/stream no-ops. Block delete while generating (UI already blocks message delete).

### F1-2 — `Conversation.currentMessages` / UI crash on invalid `selectIndex`

- **File:line:** `Conversation.kt:49-54`, `ChatMessage.kt:135`, `MessageNode.currentMessage` at `Conversation.kt:126-130`
- **Severity:** CRITICAL
- **Description:** `currentMessages` uses `node.messages[node.selectIndex]` without bounds check. `ChatMessage` same. Corrupt DB or race can throw `IndexOutOfBoundsException` / `IllegalStateException` and crash UI. Sanitizer only runs on send path, not on hydrate/display.
- **Evidence:**
```kotlin
val currentMessages get(): List<UIMessage> {
    return messageNodes
        .filter { !it.hidden }
        .map { node -> node.messages[node.selectIndex] }
}
val message = node.messages[node.selectIndex]
```
- **Suggested fix:** Coerce `selectIndex` in getter / load path (mirror `sanitizeInvalidMessages`); never index raw in Compose.

### F1-3 — Streaming progress not persisted; process kill / crash loses partial reply

- **File:line:** `ChatService.kt:872-910`, `ConversationSession.kt:36-37`
- **Severity:** HIGH
- **Description:** Chunks only update in-memory StateFlow. Final `saveConversation` runs on success. Kill mid-stream loses assistant content (user message was saved at send). Cancel path finishes tools but does not systematically persist partial assistant text in `stopGeneration`.
- **Evidence:**
```kotlin
.collect { chunk ->
    updateConversationState(conversationId) { prev ->
        prev.updateCurrentMessages(chunk.messages)
    }
}
// ...
.onSuccess {
    val finalConversation = getConversationFlow(conversationId).value
    saveConversation(conversationId, finalConversation)
}
```
- **Suggested fix:** Debounced periodic persist during stream; always save on cancel/completion/onCompletion.

### F1-4 — `generateTitle` can overwrite concurrent conversation edits (last-write races)

- **File:line:** `ChatService.kt:2084-2090`, similarly `generateSuggestion` `2145-2155`
- **Severity:** HIGH
- **Description:** Title job reloads from DB then `saveConversation(it.copy(title=...))` with **full object** from DB snapshot, not CAS against live session. Concurrent stream save / user edit can be overwritten (title job wins with stale nodes) or title job loses and title never applied depending on timing. Same pattern for suggestions.
- **Evidence:**
```kotlin
conversationRepo.getConversationById(conversation.id)?.let {
    saveConversation(
        conversationId,
        it.copy(title = result.choices[0].message?.toText()?.trim() ?: "")
    )
}
```
- **Suggested fix:** Patch only title field under `persistenceMutex` with revision CAS; or `updateConversationState { copy(title=) }` then persist delta.

### F1-5 — `handleMessageComplete` silent no-op when model unresolved

- **File:line:** `ChatService.kt:764-766`
- **Severity:** HIGH
- **Description:** User message already saved; if `resolveGenerationTarget` returns null (no chat model), generation returns without error/toast. User sees stuck “sent but no reply”.
- **Evidence:**
```kotlin
val target = settings.resolveGenerationTarget(initialConversation) ?: return
```
- **Suggested fix:** `addError` with model-settings solution; optionally rollback or mark message failed.

### F1-6 — `RegexOutputTransformer` uses `visual=false` and only implements `visualTransform`

- **File:line:** `RegexOutputTransformer.kt:11-38`, `GenerationHandler.kt:211-228`
- **Severity:** HIGH
- **Description:** Non-visual-only assistant regexes applied in `visualTransform`, but stream path assigns `transforms` then emits `visualTransforms` into state — so regex mutations enter persisted message text. UI also applies `visual=true` regexes at render. Misconfigured `visualOnly` flags cause double application or permanent content mutation contrary to “visual only” intent. No `onGenerationFinish` means finish path may re-apply inconsistently with ThinkTag.
- **Evidence:**
```kotlin
part.copy(text = part.text.replaceRegexes(assistant, scope, visual = false))
// ChatMessage UI:
part.text.replaceRegexes(..., visual = true)
```
- **Suggested fix:** Split: durable regex in `transform`/`onGenerationFinish` only; pure display regex only in UI with `visual=true`. Never mutate durable text inside `visualTransform`.

### F1-7 — DocumentAsPrompt keeps Document parts + injects full text (duplicate + token blowup)

- **File:line:** `DocumentAsPromptTransformer.kt:80-98`
- **Severity:** HIGH
- **Description:** `appendDocumentPromptsInOrder` **adds** UploadFile text but does not remove `UIMessagePart.Document`. Providers that accept documents may send binary + full text. Large PDFs explode context; OCR/document combo multiplies cost.
- **Evidence:**
```kotlin
val prompts = parts.filterIsInstance<UIMessagePart.Document>().map { ... }
parts.addAll(prompts) // originals remain
```
- **Suggested fix:** Replace Document with Text (or strip Document after extraction for text-only models); size-cap / chunk large files.

### F1-8 — Tool error returns full stack traces to the model

- **File:line:** `GenerationHandler.kt:682-704`
- **Severity:** MEDIUM
- **Description:** Tool failures encode class name + `stackTraceToString()` into tool output JSON. Leaks paths/internals into provider logs and subsequent context; inflates tokens.
- **Evidence:**
```kotlin
append("[${it.javaClass.name}] ${it.message}")
append("\n${it.stackTraceToString()}")
```
- **Suggested fix:** User-facing short error + optional debug log only.

### F1-9 — `PlaceholderTransformer` / time placeholders break prompt-cache stability

- **File:line:** `PlaceholderTransformer.kt:59-104`, contrast `TemplateTransformer.kt:29-31`
- **Severity:** MEDIUM
- **Description:** `{{cur_date}}`, battery, etc. resolve at send time with **now**, applied to **all** messages’ text parts. Historical user messages’ placeholders rewrite every turn → prefix instability → cache miss. TemplateTransformer deliberately uses message `createdAt`.
- **Evidence:** `LocalDate.now()` / `batteryLevel()` in resolvers; map over all messages.
- **Suggested fix:** Only expand placeholders on system / latest user message; or freeze values at original send and store expanded text.

### F1-10 — `estimatePromptTokens` ignores non-text modalities and tools

- **File:line:** `ProviderRateLimiter.kt:45-52`
- **Severity:** MEDIUM
- **Description:** Auto-compress and rate limiting use text-only char/4. Image/document-heavy chats under-estimate → late compress / rate limit bypass; tool schemas uncounted.
- **Evidence:**
```kotlin
message.parts.filterIsInstance<UIMessagePart.Text>()...
return (promptChars / 4L)...
```
- **Suggested fix:** Weight images/docs; include system+tools; optional real tokenizer.

### F1-11 — Multi-step loop re-runs full input transformers every step (cost/latency)

- **File:line:** `GenerationHandler.kt:190-244`, `prepareProviderInput` `554-621`
- **Severity:** MEDIUM
- **Description:** First step may reuse `firstPreparedInput`; subsequent tool steps call `prepareProviderInput` again → OCR, document parse, semantic memory, template, etc. re-execute. OCR cache helps images; documents re-read from disk each step.
- **Suggested fix:** Cache prepared non-ephemeral transforms; only re-transform suffix / tool results.

### F1-12 — `sendMessage` emits `generationDone` even when answer generation still conceptually “the job”

- **File:line:** `ChatService.kt:569-614`
- **Severity:** MEDIUM
- **Description:** `_generationDoneFlow.emit` fires at end of send job, which **includes** full `handleMessageComplete`. Naming OK for “job finished”, but any listener assuming “user message saved, generation started” is wrong. Also emitted after cancel rethrow? Cancel rethrows before emit — OK. After failure, emit still runs — consumers may think success.
- **Evidence:** emit after try/catch except CancellationException path.
- **Suggested fix:** Separate events: messagePersisted / generationStarted / generationFinished(success|failure).

### F1-13 — Regenerate assistant uses visible-index range; hidden nodes can desync

- **File:line:** `ChatService.kt:658-667`, `prepareGenerationRequest` `1661-1665`
- **Severity:** MEDIUM
- **Description:** `visibleIndex` from filtered `!hidden` list; `messageRange` indexes `currentMessages` (also visible-only). Compressed/hidden history correctly excluded from provider, but regenerate truncates only by range for generation messages — full `messageNodes` remain; `updateCurrentMessages` updates by visible alignment. Edge cases with interleaved hidden nodes + multi-branch need care (mostly OK by design) but regenerate-from-middle with compress summaries may surprise users (old hidden still in DB).
- **Suggested fix:** Document UX; optional prune after regenerate.

### F1-14 — `ChatMessage` assumes valid selectIndex (UI crash class)

- **File:line:** `ChatMessage.kt:135`
- **Severity:** MEDIUM (related to F1-2)
- **Description:** Compose reads `node.messages[node.selectIndex]` directly.
- **Suggested fix:** `messages.getOrElse(selectIndex.coerceIn(...))`.

### F1-15 — `maxSteps` default 256 with no assistant-level cap in ChatService call

- **File:line:** `GenerationHandler.kt:146`, `ChatService.kt:831-846`
- **Severity:** MEDIUM
- **Description:** `generateText` defaults maxSteps=256; ChatService does not pass a lower bound. Runaway tool loops burn quota until last-step force summary.
- **Suggested fix:** Wire assistant/settings max tool steps; lower default.

### F1-16 — Fork omits title, pin, folder, memoryTableIsolation, workspaceCwd

- **File:line:** `ChatService.kt:2768-2776`
- **Severity:** LOW
- **Description:** Fork copies nodes + some injection fields; drops title (blank → auto title OK), `folderId`, `workspaceCwd`, `memoryTableIsolation`, pin.
- **Suggested fix:** Explicit product decision; copy workspaceCwd/isolation if intended.

### F1-17 — `Base64ImageToLocalFileTransformer` via global Koin in object

- **File:line:** `Base64ImageToLocalFileTransformer.kt:12`
- **Severity:** LOW
- **Description:** `getKoin().get()` hinders testing; side effects only on finish (good) but fails closed only if Koin ready.
- **Suggested fix:** Inject FilesManager.

### F1-18 — `onCompletion` builds `updatedConversation` then ignores it for state update

- **File:line:** `ChatService.kt:846-860`
- **Severity:** LOW
- **Description:** Local `updatedConversation` computed then `updateConversationState` recomputes similar transform; notification uses possibly stale `updatedConversation` vs post-update state for preview text.
- **Suggested fix:** Single source after state update.

### F1-19 — Parallel tool execution concurrent mutation of shared tool defs / memory

- **File:line:** `GenerationHandler.kt:319-338`
- **Severity:** LOW–MEDIUM
- **Description:** Parallel `executeSingleTool` may race memory/MCP/workspace tools without per-tool isolation. Depends on tool implementations.
- **Suggested fix:** Serialize non-idempotent tools; document parallel safety.

### F1-20 — Delete conversation does not clear hook turns / subagent registry

- **File:line:** `ConversationRepository.deleteConversation`, subagent cancel only in `stopGeneration`
- **Severity:** MEDIUM
- **Description:** Related to F1-1: orphaned subagents/hooks if delete without stop.
- **Suggested fix:** Cascade cancel in delete path.

---

## 3. 亮点 / 可复用

1. **Session model** — ref-count + idle eviction + generation job CAS (`ConversationSession`) cleanly separates UI lifecycle from background generation.
2. **Tiered `limitContext`** — hysteresis + tool-boundary alignment is cache-friendly and well-tested (`MessageTest.kt`).
3. **Auto-compression policy** — pure `evaluateAutoCompression` with armed/disarmed, fingerprint, cooldown; coordinator tryLock avoids double compress; good unit tests.
4. **Compression persist CAS** — `matchesSnapshot` / `compareAndSetState` + NonCancellable + restore on failure is careful.
5. **Transformer pipeline** — Input/Output split, Preview vs Send execution mode, `PreviewSideEffectRequiredException` for OCR cache miss.
6. **Visual vs durable output** — architectural intent (ThinkTag visualTransform) is sound; TemplateTransformer uses message timestamps for cache stability.
7. **sanitizeInvalidMessages** — pragmatic recovery for broken tool states.
8. **Tool output truncation to files** — practical for shell-heavy agents.
9. **Hook logical turns** — structured terminal events after successful generation.

---

## 4. 遗漏与风险

| Area | Risk |
|------|------|
| Crash recovery mid-stream | No checkpoint → data loss (F1-3) |
| Delete × generate race | Resurrection / orphan jobs (F1-1, F1-20) |
| Corrupt selectIndex | Crash on open (F1-2) |
| Token accounting | Auto-compress false negatives (F1-10) |
| Document dual payload | Cost + provider errors (F1-7) |
| Regex semantics | visualTransform mutates durable content (F1-6) |
| Title/suggestion background jobs | Full-object save races (F1-4) |
| Provider-specific stream quirks | Not audited per-provider in this pass |
| Web routes mirror | Same ChatService APIs; same bugs apply remotely |
| `#59` + `contextMessageLimit` interaction | Both can hide/truncate differently; user may not understand dual mechanisms |
| Semantic memory / hooks | Side effects after success; failures mostly logged |

**Out of scope / not deeply audited:** full MCP transport, SubagentHost internals, every Provider implementation stream parser, Room migrations for message nodes.

---

## 5. Severity tally

| Severity | Count |
|----------|-------|
| CRITICAL | 2 |
| HIGH | 5 |
| MEDIUM | 9 |
| LOW | 4 |

---

## 6. Recommended fix order

1. F1-1 / F1-20 — stop + tombstone on delete  
2. F1-2 / F1-14 — safe selectIndex  
3. F1-3 — debounced stream persist + cancel save  
4. F1-5 — error when no model  
5. F1-4 — title/suggestion field patches  
6. F1-6 / F1-7 — transformer correctness  
7. F1-8–F1-11, F1-15 — cost/safety polish  

---

*End of D1 report.*

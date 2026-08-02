# D13 — Concurrency & Resources Audit

**Scope:** whole Android Kotlin project (all modules), static analysis only  
**Date:** 2026-08-01  
**Method:** grep for `GlobalScope`, `runBlocking`, `launch(`, `Dispatchers.Main`, `withContext`, `Channel`, `Mutex`, `stateIn`, `sharedIn`, `rememberCoroutineScope`, `.collect`; deep-read of ChatService/ConversationSession, speech/TTS/ASR, network clients, DocumentsProvider, Subagent cache.

---

## Executive summary

Concurrency architecture is generally mature around chat generation (session map, CAS state, persistence mutex, job replacement). No production `GlobalScope` usage. Main residual risks: (1) conversation full-object saves racing title/suggestion/translation against session state, (2) SAF `runBlocking` on binder threads, (3) OkHttp `Response` / `HttpURLConnection` close/timeout gaps, (4) orphan `CoroutineScope`s (TTS wrapper, AudioPlayer, SubagentContextCache persistence), (5) intentional but memory-unbounded SSE buffers.

---

## Findings

### F13-1 — `runBlocking` on DocumentsProvider binder path

| Field | Value |
|---|---|
| **ID** | F13-1 |
| **Location** | `app/.../data/provider/WorkspaceDocumentsProvider.kt:41` |
| **Severity** | **HIGH** |
| **Category** | Blocking main / binder ANR |

**Description:** `allWorkspaces()` uses `runBlocking { dao().getAll() }` and is called from `queryChildDocuments` / display-name paths. DocumentsProvider callbacks run on binder threads; blocking a Room query can stall SAF UI (file picker freezes / system ANR risk under DB load).

**Evidence:**
```kotlin
private fun allWorkspaces(): List<WorkspaceEntity> = runBlocking { dao().getAll() }
// used from queryChildDocuments → for (ws in allWorkspaces())
```

**Suggested fix:** Cache workspace list in memory (updated via Flow/`AppScope` collector or Room invalidation), or use a bounded `runBlocking` with timeout + last-known cache. Prefer non-blocking cache hit on the binder path.

---

### F13-2 — Title generation saves DB snapshot, can clobber live session state

| Field | Value |
|---|---|
| **ID** | F13-2 |
| **Location** | `app/.../service/ChatService.kt:2084-2089` (`generateTitle`) |
| **Severity** | **HIGH** |
| **Category** | Shared mutable state / race / data loss |

**Description:** After title generation, code reloads conversation **from DB** and `saveConversation` with only `title` changed. Concurrent streaming, edit, translation, or suggestion updates may already be only in memory (or newer in DB). Full-object `updateConversation` then overwrites message nodes / suggestions with the older DB row + new title.

**Evidence:**
```kotlin
conversationRepo.getConversationById(conversation.id)?.let {
    saveConversation(
        conversationId,
        it.copy(title = result.choices[0].message?.toText()?.trim() ?: "")
    )
}
```
`saveConversation` holds `persistenceMutex` but still writes the full `Conversation` object from the caller, not a field-level CAS merge against `session.state`.

**Suggested fix:** Under `persistenceMutex`, `updateState { it.copy(title = ...) }` then persist `session.state.value`, or use a title-only DAO update that does not rewrite `messageNodes`.

---

### F13-3 — Suggestion generation same full-object race

| Field | Value |
|---|---|
| **ID** | F13-3 |
| **Location** | `app/.../service/ChatService.kt:2145-2154` (`generateSuggestion`) |
| **Severity** | **HIGH** |
| **Category** | Shared mutable state / race |

**Description:** Same pattern as F13-2: loads “latest” from DB (or session fallback) and `saveConversation` with only `chatSuggestions` changed. Parallel title gen / translation / user edit can lose fields.

**Evidence:**
```kotlin
val latestConversation = conversationRepo.getConversationById(conversationId)
    ?: sessions[conversationId]?.state?.value
    ?: conversation
saveConversation(conversationId, latestConversation.copy(chatSuggestions = suggestions.take(10)))
```

**Suggested fix:** Field-level update under mutex: `session.updateState { it.copy(chatSuggestions = ...) }` then persist that snapshot; or dedicated DAO column update.

---

### F13-4 — `updateTranslationField` / `translateMessage` non-atomic RMW

| Field | Value |
|---|---|
| **ID** | F13-4 |
| **Location** | `app/.../service/ChatService.kt:2643-2702` |
| **Severity** | **HIGH** |
| **Category** | Shared mutable state / race |

**Description:**
1. `translateMessage` launches on `appScope` **without** `session.setJob` / cancel coordination — concurrent with generation jobs.
2. `updateTranslationField` does `getConversationFlow().value` then `updateConversation(full copy)` outside `updateState` transform, so two concurrent updaters can drop each other’s message edits.
3. Final `saveConversation(conversationId, getConversationFlow().value)` can persist a stale snapshot relative to concurrent title/suggestion writers (and vice versa).

**Evidence:**
```kotlin
appScope.launch(Dispatchers.IO) {
    ...
    updateTranslationField(...)  // read value → replace whole conversation
    ...
    saveConversation(conversationId, getConversationFlow(conversationId).value)
}
private fun updateTranslationField(...) {
    val currentConversation = getConversationFlow(conversationId).value
    ...
    updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
}
```

**Suggested fix:** Use `session.updateState { ... map translation ... }` for in-memory updates; serialize save with generation via mutex and prefer transform-based persistence; cancel/join translation job when generation starts or conversation leaves.

---

### F13-5 — OkHttp `Response` not closed on several `.execute()` call sites

| Field | Value |
|---|---|
| **ID** | F13-5 |
| **Location** | e.g. `search/.../ZhipuSearchService.kt:73`, `ExaSearchService.kt:97`, `BochaSearchService.kt:74`; `speech/.../OpenAITTSProvider.kt:48` (+ other TTS providers); `ai/.../ClaudeProvider.kt:90` |
| **Severity** | **HIGH** |
| **Category** | Resource leak |

**Description:** Synchronous `httpClient.newCall(...).execute()` without `response.use { }` / `close()`. OkHttp requires closing Response bodies to return connections to the pool; leaks under tool/search/TTS load cause connection pool exhaustion and hung requests.

**Evidence (pattern):**
```kotlin
val response = httpClient.newCall(request).execute()
if (response.isSuccessful) {
    val bodyRaw = response.body?.string() ?: error(...)
    ...
} else {
    println(response.body?.string())
    error(...)
}
// no close
```

**Suggested fix:** Always `response.use { ... }` or `body.string()` then close; prefer existing `await()` + structured close helpers used by other search services.

---

### F13-6 — `FilesManager.saveMessageImage` HttpURLConnection without timeout/disconnect

| Field | Value |
|---|---|
| **ID** | F13-6 |
| **Location** | `app/.../data/files/FilesManager.kt:304-319` |
| **Severity** | **HIGH** |
| **Category** | Resource leak / timeout / possible main-path block if caller not IO |

**Description:** Opens `HttpURLConnection`, no `connectTimeout`/`readTimeout`, no `disconnect()`/`inputStream` close in `finally`. Hung download can pin a thread indefinitely; stream/bitmap may leak.

**Evidence:**
```kotlin
val connection = url.openConnection() as HttpURLConnection
connection.connect()
if (connection.responseCode == HttpURLConnection.HTTP_OK) {
    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
    activityContext.exportImage(activity, bitmap)
}
```

**Suggested fix:** Set timeouts; `use` input stream; `finally { connection.disconnect() }`; recycle bitmap after export if owned; ensure always called on `Dispatchers.IO`.

---

### F13-7 — SSE stream buffers are `Channel.UNLIMITED`

| Field | Value |
|---|---|
| **ID** | F13-7 |
| **Location** | `ai/.../openai/ChatCompletionsAPI.kt:259-260`, `ResponseAPI.kt:200`, `GoogleProvider.kt:350`, `ClaudeProvider.kt:287` |
| **Severity** | **MEDIUM** (elevated under slow UI / long streams) |
| **Category** | Backpressure / memory |

**Description:** Streaming `callbackFlow` ends with `.buffer(Channel.UNLIMITED)` intentionally (#1295: bounded buffer + trySend dropped deltas → missing text). Fast producers + slow collectors can grow heap unbounded for long tool/reasoning streams.

**Evidence:**
```kotlin
// trySend 在缓冲满时会静默丢弃 delta，导致回复中间缺字 (#1295)，因此缓冲必须无界
}.buffer(Channel.UNLIMITED)
```

**Suggested fix:** Keep correctness-first buffer, but add soft caps (drop/coalesce intermediate chunks while preserving final merge), or process/consume on IO with conflated “display” channel separate from durable merge; monitor chunk queue size.

---

### F13-8 — `CustomTtsStateImpl` orphan `CoroutineScope` never cancelled

| Field | Value |
|---|---|
| **ID** | F13-8 |
| **Location** | `app/.../ui/hooks/TTS.kt:131-178` |
| **Severity** | **MEDIUM** |
| **Category** | Coroutine leak |

**Description:** Creates `CoroutineScope(Dispatchers.Main)` but never launches on it; `cleanup()` disposes controller but does not `scope.cancel()`. Dead scope is low risk today, but pattern is footgun if jobs are added later. Controller dispose path is OK via DisposableEffect.

**Evidence:**
```kotlin
private val scope = CoroutineScope(Dispatchers.Main)
...
override fun cleanup() {
    controller.dispose()
    currentJob = null
}
```

**Suggested fix:** Remove unused scope, or `scope.cancel()` in `cleanup()`.

---

### F13-9 — `AudioPlayer` scope not cancelled on `release()`

| Field | Value |
|---|---|
| **ID** | F13-9 |
| **Location** | `speech/.../tts/controller/AudioPlayer.kt:36-47`, `135-153` |
| **Severity** | **MEDIUM** |
| **Category** | Coroutine leak |

**Description:** `AudioPlayer` owns `CoroutineScope(SupervisorJob() + Main.immediate)`. `release()` only `player.release()`; does not cancel scope / stop position job. `TtsController.dispose()` calls `audio.release()` after `scope.cancel()` on **controller** scope, but **AudioPlayer’s** independent scope remains live if positionJob was cancelled only via `stopPositionUpdates` — after release, any late job could touch released ExoPlayer.

**Evidence:**
```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
fun release() = player.release()
// stopPositionUpdates cancels job but release() does not scope.cancel()
```

**Suggested fix:** `fun release() { stopPositionUpdates(); scope.cancel(); player.release() }`.

---

### F13-10 — `SubagentContextCache.persistenceScope` never cancelled

| Field | Value |
|---|---|
| **ID** | F13-10 |
| **Location** | `app/.../data/ai/subagent/SubagentContextCache.kt:73`, `415-421` |
| **Severity** | **MEDIUM** |
| **Category** | Coroutine leak / fire-and-forget IO |

**Description:** Default `CoroutineScope(SupervisorJob() + Dispatchers.IO)` for async persist. No lifecycle tie to AppScope; process death is fine, but tests/recreate/Koin rebind can leave dangling scopes writing store.

**Evidence:**
```kotlin
private val persistenceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
...
persistenceScope.launch { runCatching { target.save(snapshot) } ... }
```

**Suggested fix:** Inject `AppScope` (or parent job); add `close()` that cancels scope; wire from ChatService/DI shutdown.

---

### F13-11 — `ChatNotificationManager` lifecycle observer never removed

| Field | Value |
|---|---|
| **ID** | F13-11 |
| **Location** | `app/.../service/ChatNotificationManager.kt:49-58` |
| **Severity** | **LOW** (singleton process lifetime) / **MEDIUM** if multi-instance in tests |
| **Category** | Listener leak |

**Description:** `ProcessLifecycleOwner` observer added in `init` on `appScope`; no `removeObserver`. Acceptable for process-scoped singleton; leaks if manager recreated.

**Suggested fix:** Hold observer ref; cancel on explicit cleanup; or use `repeatOnLifecycle` tied to application scope.

---

### F13-12 — `AppScope.cancel()` only in `Application.onTerminate()`

| Field | Value |
|---|---|
| **ID** | F13-12 |
| **Location** | `app/.../RikkaHubApp.kt:242-255` |
| **Severity** | **LOW** (documented Android behavior) |
| **Category** | Coroutine lifecycle |

**Description:** `onTerminate` is not called on real devices. AppScope lives for process lifetime by design (generation continues in background). Not a bug for product, but long-lived collectors (`syncRequestLoggingFromSettings` infinite collect, notification bus collect) never stop until process death.

**Evidence:**
```kotlin
override fun onTerminate() {
    get<AppScope>().cancel()
    ...
}
class AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Main + ...)
```

**Suggested fix:** Document as intentional; for tests use injectable Job; optional ProcessLifecycle ON_DESTROY best-effort cleanup for non-critical collectors.

---

### F13-13 — Global OkHttp read timeout 10 minutes + connection retry

| Field | Value |
|---|---|
| **ID** | F13-13 |
| **Location** | `app/.../di/DataSourceModule.kt:412-417`, `494-499` |
| **Severity** | **MEDIUM** |
| **Category** | Timeouts / resource hold |

**Description:** Shared client: connect 20s, **read 10 minutes**, write 120s, `retryOnConnectionFailure(true)`. Appropriate for LLM streams; inappropriate for short REST (search, balance, model list) which inherit the same client — failed/slow endpoints hold pool slots up to 10 minutes. No app-level exponential backoff wrapper on most REST (OkHttp internal retry only for connection failures).

**Suggested fix:** Separate clients (stream vs short-request) or per-call timeouts; add retry with jitter only where classified retryable (`ApiCallErrorClassifier` already exists).

---

### F13-14 — SharedPreferences flows use `Channel.UNLIMITED`

| Field | Value |
|---|---|
| **ID** | F13-14 |
| **Location** | `app/.../ui/hooks/SharedPreferences.kt:109`, `128` |
| **Severity** | **LOW** |
| **Category** | Backpressure |

**Description:** `callbackFlow` + `buffer(UNLIMITED)` so `trySend` never fails. Preference thrashing can queue many events; usually small values.

**Suggested fix:** `conflate()` / `buffer(1, DROP_OLDEST)` if only latest value matters.

---

### F13-15 — `sendMessage` cancel+join previous job then race with parallel reference jobs

| Field | Value |
|---|---|
| **ID** | F13-15 |
| **Location** | `app/.../service/ChatService.kt:576-613`, `918-923` |
| **Severity** | **MEDIUM** |
| **Category** | Concurrency model |

**Description:** Generation job is properly replaced via `session.setJob` (cancels previous). Post-success `generateTitle` / `generateSuggestion` / semantic summarize use `launchWithConversationReference` **outside** `generationJob`, so they continue after user starts a new send. Combined with F13-2/3 this widens overwrite windows. Ref-counting correctly delays session eviction (good).

**Suggested fix:** Track background “post-gen” jobs per session; cancel or serialize on new generation; field-level merges (F13-2/3).

---

### F13-16 — TTS/ASR controllers use free-standing scopes (OK if dispose called)

| Field | Value |
|---|---|
| **ID** | F13-16 |
| **Location** | `speech/.../TtsController.kt:39`, ASR `*ASRController.kt` scopes |
| **Severity** | **LOW** |
| **Category** | Lifecycle |

**Description:** Each controller creates `CoroutineScope(SupervisorJob() + Main.immediate)` and cancels in `dispose()`. Compose hooks use `DisposableEffect` → cleanup. Residual risk if a controller is constructed outside the hook and never disposed.

**Suggested fix:** Keep dispose contract; avoid constructing without owner; optionally tie to ViewModel/AppScope.

---

### F13-17 — Cursor window inflated to 32MB

| Field | Value |
|---|---|
| **ID** | F13-17 |
| **Location** | `app/.../RikkaHubApp.kt:63`, `utils/DatabaseUtil.kt` |
| **Severity** | **MEDIUM** (memory pressure device-dependent) |
| **Category** | Resources / hot path |

**Description:** Reflectively sets `CursorWindow` size to 32MB. Helps large conversation blobs; multiplies memory under concurrent queries.

**Suggested fix:** Keep if required; ensure large reads are single-threaded / paged; document low-RAM risk.

---

### F13-18 — Hot-path conversation copy on every stream chunk

| Field | Value |
|---|---|
| **ID** | F13-18 |
| **Location** | `ChatService` streaming updates via `updateConversationState` / message node copies |
| **Severity** | **MEDIUM** (perf) |
| **Category** | Threading / allocation |

**Description:** Streaming path correctly uses locked `updateState` (good atomicity for in-memory). Each chunk allocates new `Conversation` / node lists on whatever dispatcher the generation job uses (AppScope Main + nested IO). High allocation rate on long streams → GC jank.

**Suggested fix:** Batch UI emissions (already throttled for notifications); consider incremental message builder under lock with less full-tree copy; keep heavy transform on Default/IO.

---

### Non-issues / explicitly OK

| Item | Notes |
|---|---|
| Production `GlobalScope` | **None** (only test method names mentioning “GlobalScope”) |
| Production `runBlocking` | Only `WorkspaceDocumentsProvider` (F13-1); ChatService comment documents removal of runBlocking in tool factory |
| `ConversationSession` state | `synchronized` + revision CAS + `persistenceMutex` for durable writes — solid design |
| Compression persist | `compareAndSetState` + NonCancellable restore path — careful |
| MCP reconnect | Cap + backoff + lifecycle mutex — good |
| Compose `rememberCoroutineScope` | Tied to composition; typical usage |
| `stateIn`/`WhileSubscribed` | Mostly correct; some `Eagerly` settings flows intentional for chat |
| Document parsers | Prefer `.use {}` for streams |
| WebServerService | Cancels `serviceScope` in `onDestroy` |

---

## 链路梳理

```
UI (ChatVM / ChatPage)
  → ChatService.sendMessage / regenerate / toolApproval
      → session.setJob(appScope.launch)  // cancels previous generation job
      → saveConversation (persistenceMutex + Room)
      → handleMessageComplete → GenerationHandler stream
          → provider streamText (callbackFlow + UNLIMITED buffer)
          → updateConversationState (stateLock) per chunk
          → AppEventBus.tryEmit live update
      → onSuccess: saveConversation
          → launchWithConversationReference: generateTitle / generateSuggestion / semantic summarize
              ⚠ full-object save races (F13-2/3/15)
  → translateMessage (appScope.IO, no generation job link) ⚠ F13-4

Session lifecycle:
  acquire/release refCount → idle 5s → removeSession → cleanup jobs

App process:
  AppScope (Main+Supervisor) hosts generation, settings collectors, notifications
  onTerminate cancel only (F13-12)

Speech:
  rememberCustomTtsState → TtsController + AudioPlayer scopes → dispose on leave composition
  ASR controllers similar

Network:
  Shared OkHttp (10min read) → AI stream / search / TTS
  Some execute() without close (F13-5)
  FilesManager raw HttpURLConnection (F13-6)

SAF:
  WorkspaceDocumentsProvider binder → runBlocking Room (F13-1)
```

---

## 亮点 / 可复用

1. **ConversationSession** — revisioned CAS, separate persistence mutex, generation Job CAS, idle eviction with refcount.
2. **Compression path** — snapshot match + NonCancellable persist + restore-latest loop.
3. **MCP reconnect** — single reconnect job, attempt cap, exponential delay, lifecycle mutex.
4. **SSE #1295 fix** — documented tradeoff: unlimited buffer vs silent delta drop (correctness over backpressure).
5. **ChatNotificationManager** throttle (1s) for live update binder IPC.
6. **CoroutineUtils.toMutableStateFlow** — finite retry + linear backoff + exhausted callback (#189).
7. **ProviderRateLimiter** + semantic memory `withTimeout` fail-open.
8. **BackupTaskCoordinator** — timeout + cancellation terminal states (tests cover).
9. **Workspace shell** — process timeout + stream `use`.
10. **No GlobalScope** in production code.

---

## 遗漏与风险

| Gap | Risk |
|---|---|
| No runtime ANR/leak instrumentation in this pass | Severity is static-evidence only |
| Full ChatService stream merge path not line-traced for every branch | Possible additional RMW sites in tool/subagent progress |
| Web module Ktor collect loops | Assumed cancelled with connection; not exhaustively verified under disconnect races |
| Room DAO query thread affinity | Depends on Room setup; F13-1 assumes suspend DAO needs runBlocking |
| Multi-process / work manager | Not deeply audited |
| Bitmap recycle after `exportImage` | `exportImage` may not recycle caller bitmap (F13-6 related) |
| Intentional AppScope immortality | Background generation OK; complicates leak detection |

---

## Severity tally

| Severity | Count (IDs) |
|---|---|
| CRITICAL | 0 |
| HIGH | 6 (F13-1..6) |
| MEDIUM | 8 (F13-7,8,9,10,13,15,17,18) |
| LOW | 4 (F13-11,12,14,16) |

**Priority fix order:** F13-2/3/4 (data races on conversation) → F13-5/6 (connection leaks) → F13-1 (SAF ANR) → F13-7/13 (memory/timeouts) → scope cleanup items.

---

## Top 10 (quick index)

1. **F13-2** `ChatService.kt:2084` — title save from DB overwrites concurrent session edits  
2. **F13-3** `ChatService.kt:2145` — suggestion save same full-object race  
3. **F13-4** `ChatService.kt:2643` — translation RMW + uncoordinated job  
4. **F13-1** `WorkspaceDocumentsProvider.kt:41` — `runBlocking` on SAF binder  
5. **F13-5** search/TTS/Claude `execute()` — Response not closed  
6. **F13-6** `FilesManager.kt:304` — HttpURLConnection no timeout/disconnect  
7. **F13-7** AI stream APIs — `Channel.UNLIMITED` memory growth  
8. **F13-15** `ChatService.kt:918` — post-gen jobs race new sends  
9. **F13-9** `AudioPlayer.kt:47` — scope not cancelled on release  
10. **F13-13** `DataSourceModule.kt:413` — 10min read timeout on shared client  

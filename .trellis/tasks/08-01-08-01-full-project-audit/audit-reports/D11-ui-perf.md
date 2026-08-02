# D11 UI / Performance — Static Audit Report

> Scope: Navigation, recomposition, lists, cold start, threading, memory (images), accessibility  
> Method: read-only static analysis (no Gradle, no runtime)  
> Date: 2026-08-01  
> Package roots: `app/.../me/rerere/rikkahub/ui/**`, `RouteActivity`, `RikkaHubApp`, related data/hooks

---

## 1. 链路梳理

### 1.1 Cold start → first frame

| Step | Location | Behavior |
|------|----------|----------|
| 1 | `RikkaHubApp.onCreate` | `startKoin` (4 modules) → notification channels → `DatabaseUtil.setCursorWindowSize(32MB)` → CrashHandler → **`QuickJSLoader.init()` (native, main)** → fire-and-forget IO cleanups / workspace integrity / file sync / web server / launch count |
| 2 | `RouteActivity.onCreate` | Edge-to-edge; crash gate → `SafeModeActivity`; `setContent` → Coil singleton + `AppRoutes` |
| 3 | `AppRoutes` | `settingsFlow.collectAsStateWithLifecycle` → TTS/ASR → `rememberNavBackStack(startScreen)` → `NavDisplay` with saveable + ViewModelStore decorators |
| 4 | Start destination | `Screen.Chat(id=new|lastConversationId)` from SharedPreferences |
| 5 | `ChatPage` / `ChatVM` | Koin VM keyed by conversation id; `chatService.addConversationReference` + `initializeConversation`; multi Flow collect |

### 1.2 Navigation model

```
RouteActivity.navStack (MutableList<NavKey>)
  ← rememberNavBackStack(startScreen)  [Navigation3]
  ← Navigator.navigate / clearAndNavigate / popBackStack
  ← onNewIntent(conversationId) → navStack.add(Screen.Chat)
  ← ShareHandler LaunchedEffect → backStack.add(ShareHandler)
  ← navigateToChatPage → clearAndNavigate(Screen.Chat)  // wipes entire stack
```

- Screens: large sealed `Screen : NavKey` + `@Serializable` (Chat, History, Assistant*, Setting*, Workspace*, …).
- Entry decorators: `rememberSaveableStateHolderNavEntryDecorator` + `rememberViewModelStoreNavEntryDecorator`.
- Config change: `android:configChanges="keyboardHidden|orientation|screenSize"` on `RouteActivity` — activity **not** recreated for those; process death relies on Nav3 saveable stack + SP `lastConversationId`.

### 1.3 Chat UI hot path (recomposition / jank surface)

```
ChatService StateFlow(Conversation)
  → ChatVM.conversation
  → ChatPage (many collectAsStateWithLifecycle)
  → ChatList / LazyColumn(itemsIndexed, key=node.id)
  → ChatMessage
  → MessagePartsBlock
  → MarkdownBlock (parse AST on Default; animateContentSize on bubble)
  → (streaming) auto-scroll via snapshotFlow(visibleItemsInfo)
```

Drawer path: `ChatDrawerVM` (Activity-scoped) + Paging `LazyPagingItems` → `ConversationList` with stable `itemKey`.

### 1.4 Image / export memory

- Coil3 singleton in `RouteActivity` (OkHttp fetcher, GIF, SVG, crossfade); **no explicit memory/disk cache size**.
- `AsyncImage` / `ZoomableAsyncImage` in messages, attachments, tools, share.
- `ImageUtils` / `FilesManager` use `BitmapFactory` with sample size + recycle helpers.
- Export: `BitmapComposer` offscreen Compose → bitmap (MAX 10000.dp theoretical).

---

## 2. Issues

### F11-1 — ShareHandler re-pushes route every composition / process restore risk

- **File:line:** `RouteActivity.kt:221-244`
- **Severity:** HIGH
- **Description:** `ShareHandler` captures share extras in `remember { Intent()... }` then `LaunchedEffect(backStack) { backStack.add(Screen.ShareHandler(...)) }`. `LaunchedEffect` restarts when `backStack` identity changes (e.g. after Nav restore / recomposition of parent). Combined with cold start that already seeds `Screen.Chat`, a SEND/PROCESS_TEXT launch can stack **duplicate** ShareHandler entries or re-fire after config/process edge cases. Also `EXTRA_STREAM` is read via `getStringExtra` while system share often provides a **Parcelable Uri** — image share may never populate `streamUri`.
- **Evidence:**
```kotlin
val shareIntent = remember {
    Intent().apply {
        action = intent?.action
        putExtra(Intent.EXTRA_TEXT, intent?.getStringExtra(Intent.EXTRA_TEXT))
        putExtra(Intent.EXTRA_STREAM, intent?.getStringExtra(Intent.EXTRA_STREAM)) // often wrong type
        ...
    }
}
LaunchedEffect(backStack) {
    when (shareIntent.action) {
        Intent.ACTION_SEND -> {
            backStack.add(Screen.ShareHandler(text, imageUri))
        }
        ...
    }
}
```
- **Suggested fix:** One-shot consume with `rememberSaveable`/`AtomicBoolean` flag (or handle only in `onCreate`/`onNewIntent`); read stream with `IntentCompat.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)`; clear intent action after handling; prefer `navigate` with singleTop / replace rather than unconditional `add`.

---

### F11-2 — `navigateToChatPage` always `clearAndNavigate` (back stack destruction)

- **File:line:** `ChatUtil.kt:13-28`, `NavContext.kt:28-31`
- **Severity:** HIGH
- **Description:** Switching conversations (drawer, search jump, share→chat, new chat) **clears the entire Nav back stack** and leaves only one `Screen.Chat`. User loses History/Settings/Assistant intermediate stack; system back from chat exits app rather than returning to prior screen. Also discards saveable entry state for non-chat destinations.
- **Evidence:**
```kotlin
fun navigateToChatPage(...) {
    navigator.clearAndNavigate(
        Screen.Chat(id = chatId.toString(), text = initText, files = ..., nodeId = ...)
    )
}
```
- **Suggested fix:** For in-app conversation switches, replace only the chat root (`popUpTo` chat inclusive + navigate) or mutate chat id without clearing; reserve `clearAndNavigate` for true “reset to chat home” actions. Keep deep links from search with optional `nodeId` without wiping settings stack if opened from settings.

---

### F11-3 — Streaming message path: full Conversation invalidation + `animateContentSize` + Markdown reparse

- **File:line:** `ChatList.kt:313-399`, `ChatMessage.kt:458-529`, `Markdown.kt:236-254`
- **Severity:** HIGH
- **Description:** Generation updates replace `Conversation` / `messageNodes` in a StateFlow. Chat list keys by `node.id` (good) but **last streaming node** still re-emits frequently. Each token path: `replaceRegexes` (not remembered) → `MarkdownBlock` (background AST parse, then full node tree recompose) → **`Modifier.animateContentSize()` on bubble/Surface**. Auto-scroll also observes `layoutInfo.visibleItemsInfo` continuously while loading (`ChatList.kt:286-297`). Together this is the primary jank source on long streams (layout thrash + measure animation + markdown).
- **Evidence:**
```kotlin
// ChatList auto-scroll while loading
snapshotFlow { state.layoutInfo.visibleItemsInfo }.collect { ...
    if (!state.isScrollInProgress && loadingState) {
        if (visibleItemsInfo.isAtBottom()) {
            state.requestScrollToItem(conversationUpdated.messageNodes.lastIndex + 10)
        }
    }
}
// ChatMessage bubble
Surface(modifier = Modifier.animateContentSize(), ...) {
    MarkdownBlock(content = visualText, ...)
}
// MarkdownBlock
LaunchedEffect(Unit) {
    snapshotFlow { updatedContent }.distinctUntilChanged()
        .mapLatest { parseMarkdown(it) }.flowOn(Dispatchers.Default).collect { setData(it) }
}
```
- **Suggested fix:** Disable `animateContentSize` while `loading==true` for the streaming message only; throttle markdown updates (e.g. 50–100ms / by char delta); split streaming plain-text renderer vs final Markdown; use `contentType` on Lazy items; consider `derivedStateOf` for “is at bottom” instead of collecting every visibleItemsInfo change; avoid re-running `replaceRegexes` without `remember(part.text, assistant)`.

---

### F11-4 — Cold start main-thread work: QuickJS native init + heavy Application fan-out

- **File:line:** `RikkaHubApp.kt:52-94`, `69`, `249-255`
- **Severity:** HIGH
- **Description:** `onCreate` runs **synchronously on main**: full Koin graph, notification channels, **32MB cursor window reflection**, CrashHandler, **`QuickJSLoader.init()`**. Multiple coroutines are launched on `AppScope` defaulting to **`Dispatchers.Main`** (even when individual launches pass `Dispatchers.IO` for body, scope default is Main). Web server start, settings collect, launch count also schedule from Main. This inflates TTID/TTFD and competes with first Compose frame.
- **Evidence:**
```kotlin
QuickJSLoader.init()
// ...
class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob() + Dispatchers.Main + ...
)
```
- **Suggested fix:** Move `QuickJSLoader.init` to background with lazy use-site init; keep Application `onCreate` to Koin + crash + channels only; default `AppScope` to `Dispatchers.Default`/`IO`; defer workspace integrity / file sync with WorkManager or delayed start after first frame (`ProcessLifecycleOwner` / `reportFullyDrawn` gate).

---

### F11-5 — ChatVM Eagerly-shared flows + Activity-scoped drawer VM

- **File:line:** `ChatVM.kt:166-177`, `311-323`, `418`; `ChatDrawer.kt:114`
- **Severity:** MEDIUM
- **Description:** `conversationJob`, `conversationJobs`, `settings`, `enableWebSearch`, `updateChecker` use `SharingStarted.Eagerly` — collectors stay active for VM lifetime even when UI not observing subsets. Drawer uses `koinViewModel(viewModelStoreOwner = activity)` so **ChatDrawerVM + paging + scroll state survive conversation switches** (good for list position) but also **never tear down** while RouteActivity lives — continuous paging/tag observation cost.
- **Evidence:**
```kotlin
.stateIn(viewModelScope, SharingStarted.Eagerly, null)  // conversationJob
val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)
```
- **Suggested fix:** Prefer `WhileSubscribed(5_000)` for UI-only mirrors; keep Eagerly only for generation job that must outlive brief unsubscribes. Document Activity-scope drawer intentionally; ensure paging source cancels when drawer closed for long periods if measurable.

---

### F11-6 — Lazy lists missing stable keys (identity thrash)

- **File:line:**  
  - `SearchPage.kt:202`  
  - `ShareHandlerPage.kt:84`  
  - `ChatList.kt:782` (suggestions)  
  - `WebViewPage.kt:174`  
  - `ExtensionContent.kt:110,148`  
  - `McpPicker.kt:289`  
  - `ModelList.kt:718`  
  - `BuiltinToolUIs.kt:581,705,722,793`  
  - `ChatMessageTools.kt:175`  
  - `ChatMessageTranslation.kt:116`  
  - `S3Tab.kt:385` / `WebDavTab.kt:366`  
  - `SettingDonatePage.kt:147` / `SettingMcpPage.kt:851` / `SettingProviderPage.kt:226` / `SettingProviderDetailPage.kt:1036`
- **Severity:** MEDIUM
- **Description:** Multiple `items(...)` without `key =`. On list mutation, Compose falls back to index identity → wrong item state, extra recomposition, scroll jumps. Critical for **Search results** (navigate by conversation/node id) and **Share assistant list**.
- **Evidence:**
```kotlin
items(vm.results) { result -> ... }           // SearchPage
items(settings.activeAssistants()) { ... }    // ShareHandlerPage
items(conversation.chatSuggestions) { ... }   // ChatList
```
- **Suggested fix:** Always provide stable keys (`result.nodeId`, `assistant.id`, suggestion hash/index+content, url, tool item id). Prefer `items(count, key=)` API consistently.

---

### F11-7 — `collectAsState` without lifecycle (leaks / background work)

- **File:line:**  
  - `ChatInput.kt:181`  
  - `AttachmentChips.kt:52`  
  - `FilesPicker.kt:113`  
  - `ChatMessageActions.kt:132-133`  
  - `TTSController.kt:46,60`  
  - `SettingSpeechPage.kt:709-710`  
  - `SettingFilesPage.kt:83`
- **Severity:** MEDIUM
- **Description:** Uses bare `collectAsState` / `collectAsState(initial=)` instead of `collectAsStateWithLifecycle`. While composable is in composition (including under Nav back stack if entry kept), flows keep collecting in STARTED/even when Activity stopped depending on Nav3 entry retention — unnecessary work for files observe, ASR, TTS during background.
- **Evidence:**
```kotlin
val managedFiles by filesManager.observe().collectAsState(initial = emptyList())
val asrState by asr.state.collectAsState()
```
- **Suggested fix:** Migrate all UI collects to `collectAsStateWithLifecycle`; for hot TTS/ASR prefer distinctUntilChanged + lifecycle.

---

### F11-8 — Coil ImageLoader without bounded memory/disk cache policy

- **File:line:** `RouteActivity.kt:190-207`
- **Severity:** MEDIUM
- **Description:** Singleton ImageLoader enables crossfade, GIF, SVG, OkHttp network, Cache-Control strategy, but **does not set** `memoryCache` / `diskCache` max size or `precision`/`size` defaults. Chat + imggen + favicons + tool images can pressure low-RAM devices; SVG/GIF amplify cost.
- **Evidence:**
```kotlin
ImageLoader.Builder(context)
    .crossfade(true)
    .components { ... }
    .build()
```
- **Suggested fix:** Configure `MemoryCache.Builder(context).maxSizePercent(0.15)` and disk cache percent; use `ImageRequest` size constraints for list thumbnails; disable crossfade on tiny avatars if needed.

---

### F11-9 — DocumentsProvider `runBlocking` on binder/query threads

- **File:line:** `WorkspaceDocumentsProvider.kt:41-44`
- **Severity:** MEDIUM (HIGH if system file picker hits main binder path under load)
- **Description:** `allWorkspaces()` uses `runBlocking { dao().getAll() }` and is called from `workspaceName` during document queries. SAF callbacks can block; under concurrent queries this risks ANR-class stalls (not Compose UI but same process responsiveness).
- **Evidence:**
```kotlin
private fun allWorkspaces(): List<WorkspaceEntity> = runBlocking { dao().getAll() }
```
- **Suggested fix:** Cache workspace list with invalidation; use blocking DB only via Room `allowMainThreadQueries` avoidance + dedicated single-thread executor without nested runBlocking on already-blocking paths; or pre-index roots.

---

### F11-10 — Message list lacks `contentType`; large items always full ChatMessage

- **File:line:** `ChatList.kt:323-326`
- **Severity:** MEDIUM
- **Description:** `itemsIndexed(..., key = { _, item -> item.id })` has no `contentType`. User vs assistant vs tool-heavy nodes share same recycler slot type → more rebinds. Each item mounts full `ChatMessage` (avatars, CoT, tools, actions) even when off-screen recycling is imperfect for very tall markdown.
- **Evidence:**
```kotlin
itemsIndexed(
    items = conversation.messageNodes,
    key = { index, item -> item.id },
) { index, node ->
    ChatMessage(...)
}
```
- **Suggested fix:** `contentType = { _, n -> n.currentMessage.role to n.currentMessage.parts.firstOrNull()?.javaClass }` (or similar); consider collapsing tool UI when not last; optional “compact mode” for far-from-viewport (harder in Lazy).

---

### F11-11 — `LocalSettings` is static CL but whole `Settings` object is huge & unstable for skip

- **File:line:** `LocalSettings.kt:6-8`, `PreferencesStore.kt:1042+`, `RouteActivity.kt:259,308`
- **Severity:** MEDIUM
- **Description:** `staticCompositionLocalOf<Settings>` avoids subscription tracking of reads, but **any** settings update still re-provides a new `Settings` at root (`LocalSettings provides settings`), forcing **entire Nav tree** dependents that read `LocalSettings.current` (e.g. every `ChatMessage` reads `displaySetting`) to recompose. `Settings` is a large data class (providers, assistants, prompts, …) without `@Immutable`/`@Stable` annotations; structural equality helps only if reference changes on every store update (it does via `settingsFlow.value = settings`).
- **Evidence:** ChatMessage: `val settings = LocalSettings.current.displaySetting` / `LocalSettings.current`.
- **Suggested fix:** Split composition locals: `LocalDisplaySetting`, `LocalAssistantSnapshot`; or pass `displaySetting` only into chat subtree; mark stable subsets; avoid putting full Settings at root if only display flags needed for messages.

---

### F11-12 — ChatPage right-drawer gesture layer + dual drawers complexity (jank / touch)

- **File:line:** `ChatPage.kt:261-300`, `304-311`
- **Severity:** MEDIUM
- **Description:** Full-screen `pointerInput` with `PointerEventPass.Initial` tracks all gestures to open RTL right drawer. Runs on every pointer stream in chat; interacts with left drawer, horizontal lists, text selection. Risk of extra main-thread work and occasional gesture fights (mitigations exist via exclusion state). Nested ModalNavigationDrawer + PermanentNavigationDrawer + RTL wrapper increases composition depth and layout cost on large screens.
- **Evidence:** `awaitEachGesture` / `awaitFirstDown(..., Initial)` / consume on claim; outer RTL + inner LTR.
- **Suggested fix:** Scope gesture detector to edge strip when not needed full-width; disable when IME open; profile pointerInput cost; consider single custom scaffold.

---

### F11-13 — Process death / start screen vs notification deep link

- **File:line:** `RouteActivity.kt:247-252`, `288-300`; `ChatVM.kt:188-189`
- **Severity:** MEDIUM
- **Description:**  
  - Cold start always builds `startScreen` from prefs (`create_new_conversation_on_start` or `lastConversationId`).  
  - `onNewIntent` only **adds** another `Screen.Chat` if `conversationId` extra present — does not replace root; can leave **two Chat entries** on stack.  
  - No handling of intent in `onCreate` for the same extra when activity created fresh from notification.  
  - `Screen.Chat` may include `files: List<String>` and `text` in saved NavKey — large base64 text risks **SavedState / transaction** pressure (input is in VM to avoid TTL, but route args can still be large for share).
- **Evidence:**
```kotlin
override fun onNewIntent(intent: Intent) {
    intent.getStringExtra("conversationId")?.let { text ->
        navStack?.add(Screen.Chat(text))
    }
}
```
- **Suggested fix:** Unify intent handling in `onCreate`+`onNewIntent`; use `clearAndNavigate` or singleTop chat replace; strip large `text`/`files` from NavKey after first consume (side effect once).

---

### F11-14 — Preview search list: `remember(searchQuery, message)` uses whole message object

- **File:line:** `ChatList.kt:742-754`
- **Severity:** LOW
- **Description:** Highlight recomputes with `message` as key (unstable reference every parent recompose if conversation updates). Preview mode filters all nodes into memory (`mapIndexed` full list) — OK for moderate sizes, costly near 768-node warning threshold.
- **Suggested fix:** Key on `message.id` + text hash; virtualize only; debounce search query.

---

### F11-15 — Accessibility: widespread `contentDescription = null` and tiny jump controls

- **File:line:** UI package ~**252** `contentDescription = null` hits; `ChatList.kt:822-899` MessageJumper icons mostly `null` (only bottom has string); many IconButtons rely on null CD.
- **Severity:** LOW (MEDIUM for TalkBack users on chat chrome)
- **Description:** Decorative null is OK for pure decoration; interactive Icon/Surface jumpers, drawer pins, model list icons often lack descriptions. Message jumper hit targets use `padding(4.dp)` around icons without ensuring 48dp minimum touch target.
- **Evidence:** MessageJumper Surfaces with Icon `contentDescription = null` except scroll-to-bottom; ConversationList pin icon CD null on header.
- **Suggested fix:** Add string resources for all actionable icons; `Modifier.minimumInteractiveComponentSize()` / 48.dp touch; semantics on conversation items (selected, loading).

---

### F11-16 — Nested LazyColumn inside message tool UIs

- **File:line:** `BuiltinToolUIs.kt:546-581`, `671+`, `778+`
- **Severity:** LOW–MEDIUM
- **Description:** Tool previews embed `LazyColumn`/`LazyRow` **inside** chat message items (already in outer LazyColumn). Nested scrollables cause measurement ambiguity, poor fling, and extra composition. Items often lack keys.
- **Suggested fix:** Prefer non-lazy Column for small bounded lists; if large, open bottom sheet with its own Lazy list; always key items.

---

### F11-17 — `BitmapComposer` / export unbounded dimension defaults

- **File:line:** `BitmapComposer.kt:25-58`
- **Severity:** MEDIUM
- **Description:** Default max width/height **10000.dp** converted to pixels can allocate multi‑hundred‑MB bitmaps if caller omits size — OOM risk on export of long chats.
- **Suggested fix:** Hard cap pixels (e.g. 4096); tile export; downscale density.

---

### F11-18 — Volume key listener list on Activity (ordering / leak of lambdas)

- **File:line:** `RouteActivity.kt:164`, `ChatList.kt:225-240`
- **Severity:** LOW
- **Description:** `volumeKeyListeners` is a mutable list; ChatList registers lambda capturing scope/state. DisposableEffect removes on dispose — OK. Multiple Chat entries if stacked could register multiple listeners (“last wins” is intentional). Low risk.
- **Suggested fix:** Single listener in ChatPage only; clear on pause.

---

## 3. 亮点 / 可复用

| Pattern | Where | Why good |
|---------|-------|----------|
| Navigation3 + serializable `Screen` + saveable/VM decorators | `RouteActivity` | Modern back stack; process death friendlier than ad-hoc |
| `Navigator` popUpTo / singleTop / clearAndNavigate API | `NavContext.kt` | Clear navigation semantics when used correctly |
| Message Lazy keys = `node.id` | `ChatList.kt` | Correct identity for branch/regenerate |
| Conversation drawer Paging + `itemKey` + scroll restore | `ConversationList` / `ChatDrawer` | Scales history; scroll position in Activity VM |
| Input state in ViewModel (`ChatInputState`) | `ChatVM` | Avoids TransactionTooLarge on input |
| Markdown AST parse on `Dispatchers.Default` | `Markdown.kt` | Avoids main-thread parse jank (still recompose cost) |
| SelectionContainer disabled while streaming | `ChatMessage.kt:534-544` | Avoids ConcurrentModificationException (documented) |
| Conversation size warning thresholds | `ChatSizeChecker.kt` | UX guard for huge threads |
| Git status cache / forceRefresh | `ChatVM` | Avoids repeated proot work on drawer reopen |
| Coil3 shared OkHttp client | `RouteActivity` | Connection reuse |
| `rememberSaveable` for selection collapsed / size dialog | `ChatList` | Survives config within activity |
| Crash → SafeModeActivity | `RouteActivity` / settings cold-start failure | Resilience path |
| ImeLazyListAutoScroller / ImeToastDismisser | hooks | IME UX polish |
| Horizontal gesture exclusion for nested horizontal scroll | chat context | Thoughtful gesture coexistence |

---

## 4. 遗漏与风险

1. **No runtime traces** (Macrobenchmark, Layout Inspector, Perfetto) — jank severities are static estimates.
2. **Composition skippability** of `Conversation` / `MessageNode` / `UIMessage` not fully verified (AI module stability annotations).
3. **Nav3 process death** exact restore of deep Assistant* stacks not exercised here.
4. **WebView / terminal / imggen** pages only lightly sampled; imggen has many Lazy lists — may hide more key issues.
5. **Haze** (`hazeSource`) on chat list: GPU overdraw cost not measured.
6. **Shared element / SharedTransitionLayout** at root: potential extra cost during transitions.
7. **Font loading** (`ChatFontProvider` / custom fonts) during scroll not audited line-by-line.
8. **WorkManager initializer** still registered in Manifest while Koin also `workManagerFactory()` — dual init risk (startup) not fully traced.
9. **Accessibility** pass is greps-only, not TalkBack walkthrough.
10. **Multi-window / fold** with `configChanges` — possible size change without recreate; `isBigScreen` LaunchedEffect handles drawer but not all adaptive state.

---

## 5. Severity summary

| Severity | Count | IDs |
|----------|-------|-----|
| CRITICAL | 0 | — |
| HIGH | 4 | F11-1, F11-2, F11-3, F11-4 |
| MEDIUM | 10 | F11-5 … F11-13, F11-17 |
| LOW | 4 | F11-14, F11-15, F11-16, F11-18 |

**Priority fix order (perf/UX):** F11-3 (streaming jank) → F11-4 (cold start) → F11-1/F11-13 (share/intent nav) → F11-2 (back stack) → F11-6/F11-8/F11-11 (lists/cache/settings locality).

---

## 6. File index (primary)

| Area | Paths |
|------|-------|
| App start | `RikkaHubApp.kt`, `AndroidManifest.xml` |
| Nav | `RouteActivity.kt`, `ui/context/NavContext.kt`, `utils/ChatUtil.kt` |
| Chat UI | `ui/pages/chat/ChatPage.kt`, `ChatList.kt`, `ChatVM.kt`, `ChatDrawer.kt`, `ConversationList.kt` |
| Messages | `ui/components/message/ChatMessage.kt`, `richtext/Markdown.kt` |
| Images | `RouteActivity` Coil, `ImageUtils.kt`, `BitmapComposer.kt` |
| A11y | widespread `ui/**` Icon CD |

---

*End of D11 report.*

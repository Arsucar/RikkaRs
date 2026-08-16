# Research: ChatPage Drawer Setup & Mutual Exclusion (#301)

- **Query**: Locate exact code (with line numbers) for left/right drawer StateFlow/DrawerState definitions, the outer gesture layer `drawersClosed` gate, both `gesturesEnabled` usages, and how ModalNavigationDrawer / PermanentNavigationDrawer are composed. Identify whether any mutual-exclusion logic already exists.
- **Scope**: internal
- **Date**: 2026-08-16

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | ChatPage composable; owns both DrawerStates, gesture layer, and all drawer composition |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPageGesture.kt` | `shouldClaimRightDrawerGesture` helper used by the outer gesture layer |
| `app/src/main/java/me/rerere/rikkahub/ui/context/HorizontalGestureExclusionContext.kt` | `HorizontalGestureExclusionState` + `LocalHorizontalGestureExclusionState` for letting horizontally-scrollable children opt out of the right-drawer gesture claim |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatDrawer.kt` | Left drawer content (ChatDrawerContent) — consumes `LocalHorizontalGestureExclusionState` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt` | Consumes `LocalHorizontalGestureExclusionState` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ConversationGitStatusDrawer.kt` | Right-drawer sub-content; consumes `LocalHorizontalGestureExclusionState` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ConversationMemoryTableDrawer.kt` | Right-drawer sub-content (memory table) |

### Code Patterns

#### 1. Left DrawerState — `ChatPage.kt:150`

```kotlin
150:     val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
```

Plain `rememberDrawerState(DrawerValue.Closed)`. No VM involvement; the state lives in the composable. No `StateFlow`.

#### 2. Right DrawerState — `ChatPage.kt:174`

```kotlin
172:     // #89: 右侧对话级记忆表抽屉。Compose 无原生右侧抽屉，用 RTL 包裹 ModalNavigationDrawer 实现，
173:     // drawerContent 与主内容都翻回 LTR 防止整页镜像。
174:     val rightDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
175:     val horizontalGestureExclusionState = remember { HorizontalGestureExclusionState() }
```

Also a plain `rememberDrawerState`. Comment #89 documents the RTL-wrap trick: Compose has no native right-side drawer, so the right drawer is a `ModalNavigationDrawer` wrapped in `LocalLayoutDirection = LayoutDirection.Rtl`, with content flipped back to `Ltr` to avoid mirroring.

A `HorizontalGestureExclusionState` is created here and provided down the tree so horizontally-scrollable children can opt out of the right-drawer "drag-from-right" gesture.

#### 3. `DrawerState.isActive` extension — `ChatPage.kt:577-578`

```kotlin
577: private val DrawerState.isActive: Boolean
578:     get() = currentValue == DrawerValue.Open || targetValue == DrawerValue.Open
```

A private file-level extension. "Active" means the drawer is currently open OR its target is open (covers the in-flight animation window). This is the predicate used for mutual exclusion and gesture gating.

#### 4. Existing Mutual Exclusion Logic — `ChatPage.kt:202-212`

```kotlin
202:     // #301: 左右抽屉互斥。任一抽屉开始打开时关掉另一个，避免遮罩间隙把两侧同时展开。
203:     LaunchedEffect(drawerState.targetValue) {
204:         if (drawerState.targetValue == DrawerValue.Open && rightDrawerState.isActive) {
205:             rightDrawerState.close()
206:         }
207:     }
208:     LaunchedEffect(rightDrawerState.targetValue) {
209:         if (rightDrawerState.targetValue == DrawerValue.Open && drawerState.isActive) {
210:             drawerState.close()
211:         }
212:     }
```

**Mutual exclusion already exists.** Two `LaunchedEffect`s watch each drawer's `targetValue`. When one drawer's target becomes `Open` while the other is `isActive` (open or animating to open), the other is closed via `DrawerState.close()`.

The comment explicitly references issue #301 and describes the exact problem the issue reports ("遮罩间隙把两侧同时展开" = scrim gap lets both sides expand at once). So a fix for #301 is already present in this file.

#### 5. Outer Gesture Layer `drawersClosed` Gate — `ChatPage.kt:276-318`

The outer `Box` at line 276 owns the pointer-input handler that opens the right drawer by detecting leftward drags anywhere on screen (bypassing the system right-edge gesture area on Chinese ROMs).

```kotlin
276:     Box(
277:         modifier = Modifier
278:             .fillMaxSize()
279:             .pointerInput(rightDrawerState, drawerState) {
280:                 val slop = viewConfiguration.touchSlop
281:                 awaitEachGesture {
282:                     // 用 Initial pass 读事件：父节点先于子节点拿到
285:                     val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
286:                     var totalX = 0f
287:                     var totalY = 0f
288:                     var decided = false
289:                     var claim = false
290:                     while (true) {
291:                         val event = awaitPointerEvent(PointerEventPass.Initial)
292:                         val change = event.changes.firstOrNull { it.id == down.id } ?: break
293:                         if (change.changedToUp()) break
294:                         val pc = change.positionChange()
295:                         totalX += pc.x
296:                         totalY += pc.y
297:                         if (!decided && (kotlin.math.abs(totalX) > slop || kotlin.math.abs(totalY) > slop)) {
298:                             decided = true
299:                             // 仅当水平向左拖、两个抽屉关闭且起点不在可横向滚动区域时接管手势
300:                             claim = shouldClaimRightDrawerGesture(
301:                                 totalX = totalX,
302:                                 totalY = totalY,
303:                                 touchSlop = slop,
304:                                 drawersClosed = !rightDrawerState.isActive && !drawerState.isActive,
305:                                 gestureExcluded = horizontalGestureExclusionState.isExcluded(down.id.value),
306:                             )
307:                         }
308:                         if (claim) {
309:                             // 在 Initial pass 消费，子节点（左抽屉/内容）后续拿不到该事件
310:                             change.consume()
311:                         }
312:                     }
313:                     if (claim && totalX < -slop * 2) {
314:                         scope.launch { rightDrawerState.open() }
315:                     }
316:                 }
317:             }
318:     ) {
```

The `drawersClosed` argument at line 304 is `!rightDrawerState.isActive && !drawerState.isActive` — the right-drawer drag gesture is only claimed when **both** drawers are closed (neither open nor animating).

`shouldClaimRightDrawerGesture` (`ChatPageGesture.kt:5-16`) requires: movement over slop, horizontal-dominant, `totalX < 0` (leftward), `drawersClosed`, and not excluded by a horizontally-scrollable child.

#### 6. Right Drawer `gesturesEnabled` — `ChatPage.kt:323-326`

```kotlin
323:         ModalNavigationDrawer(
324:             drawerState = rightDrawerState,
325:             // 打开后可滑动关闭；打开动作改用右缘手势条（见下方 Box），因为内层左抽屉会拦截关闭态的水平拖拽
326:             gesturesEnabled = rightDrawerState.isOpen,
```

Right drawer only enables its own swipe-to-close gesture when already open. The opening gesture is handled by the outer `Box` pointer layer (lines 276-318), not by `ModalNavigationDrawer`'s built-in edge-drag — because the inner left drawer's `pointerInput` would otherwise intercept the closed-state horizontal drag.

Note: this is `rightDrawerState.isOpen` (strict current value), **not** `.isActive`. So during the close animation `gesturesEnabled` is already false.

#### 7. Left Drawer `gesturesEnabled` — `ChatPage.kt:526-529`

This is the small-screen (modal) branch. There is no `gesturesEnabled` on the big-screen `PermanentNavigationDrawer` branch (line 491) because permanent drawers don't have edge gestures.

```kotlin
525:                     else -> {
526:                         ModalNavigationDrawer(
527:                             drawerState = drawerState,
528:                             // #301: 右抽屉打开或动画中时禁用左缘拖拽，避免遮罩间隙同时展开两侧
529:                             gesturesEnabled = !rightDrawerState.isActive,
```

Left drawer's built-in edge-swipe-open gesture is disabled whenever the right drawer is `isActive` (open or animating). Comment explicitly tags #301.

#### 8. Drawer Composition Structure

The full nesting (lines 276-574):

```
Box (outer gesture layer, fillMaxSize, pointerInput for right-drawer open)   // 276
└─ CompositionLocalProvider(LocalHorizontalGestureExclusionState, LocalLayoutDirection=Rtl)  // 319
   └─ ModalNavigationDrawer (RIGHT drawer)                                    // 323
      drawerState = rightDrawerState
      gesturesEnabled = rightDrawerState.isOpen
      drawerContent = ModalDrawerSheet(300.dp) {                              // 329
         CompositionLocalProvider(LocalLayoutDirection=Ltr) {                 // 333
            ConversationDrawerContent(...)                                    // 363
         }
      }
      content = {
         CompositionLocalProvider(LocalLayoutDirection=Ltr) {                 // 488
            when {
               isBigScreen -> PermanentNavigationDrawer {                     // 491
                  ChatDrawerContent(...)                                      // 493  (LEFT, permanent)
                  ChatPageContent(...)                                        // 502
               }
               else -> ModalNavigationDrawer (LEFT drawer) {                  // 526
                  drawerState = drawerState
                  gesturesEnabled = !rightDrawerState.isActive                // 529
                  drawerContent = ChatDrawerContent(...)                      // 531
                  ChatPageContent(...)                                        // 540
                  BackHandler(drawerState.isOpen) { drawerState.close() }     // 561
               }
            }
            BackHandler(enabled = rightDrawerState.isOpen) {                  // 568
               rightDrawerState.close()
            }
         }
      }
```

Key structural points:
- Right drawer is the **outer** `ModalNavigationDrawer`; left drawer is **inner** (only in small-screen branch). This nesting is intentional so the left drawer's `pointerInput` for closed-state edge drag sits inside the right drawer's content and the outer `Box` can preempt it via `PointerEventPass.Initial`.
- On big screens, left becomes a `PermanentNavigationDrawer` (no gesture state, always visible), while the right `ModalNavigationDrawer` still wraps everything.
- Two separate `BackHandler`s for `rightDrawerState.isOpen` exist: one at top level (lines 189-193) and one inside the inner content scope (lines 568-570). The inner one is registered later (deeper) so it wins on back press, matching the left drawer's deeper `BackHandler` at line 561.

#### 9. `HorizontalGestureExclusionState` — `HorizontalGestureExclusionContext.kt:5-48`

```kotlin
5: internal class HorizontalGestureExclusionState {
6:     private val activePointers = mutableMapOf<Long, Int>()
8:     val isActive: Boolean
9:         get() = activePointers.isNotEmpty()
11:     fun isExcluded(pointerId: Long): Boolean = activePointers.containsKey(pointerId)
13:     fun acquire(pointerId: Long) { ... }
17:     fun acquireIfScrollable(pointerId: Long, maxScrollValue: Int): AutoCloseable? { ... }
23:     fun release(pointerId: Long) { ... }
30: }
33: internal val LocalHorizontalGestureExclusionState =
34:     staticCompositionLocalOf<HorizontalGestureExclusionState?> { null }
```

Reference-counted per-pointer registry. Horizontally-scrollable children (chat list, drawer contents) call `acquireIfScrollable` to mark a pointer as "excluded" so the outer Box's `shouldClaimRightDrawerGesture` returns false and lets the child handle the drag. This is what prevents the right-drawer open gesture from hijacking horizontal scroll inside the content.

### ViewModel Involvement

**No ViewModel manages the drawer states.** Both `drawerState` and `rightDrawerState` are created via `rememberDrawerState(DrawerValue.Closed)` directly inside the `ChatPage` composable (lines 150 and 174). They are not exposed to or owned by `ChatVM` or any other VM. All open/close calls are issued from `scope.launch { ... }` inside the composable.

The only related cross-component state is `HorizontalGestureExclusionState`, also composable-owned (line 175) and provided via `LocalHorizontalGestureExclusionState`.

### Summary of Existing #301 Handling

The code already contains three layers of #301 mitigation:

1. **Mutual exclusion on open** (lines 202-212): when either drawer's `targetValue` flips to `Open` while the other is `isActive`, the other is closed.
2. **Left drawer `gesturesEnabled` gating** (line 529): `!rightDrawerState.isActive` disables the left drawer's built-in edge-swipe-open whenever the right drawer is open or animating.
3. **Outer gesture layer `drawersClosed` gate** (line 304): the custom right-drawer open gesture only fires when both drawers are inactive.

The right drawer's own `gesturesEnabled` (line 326) is `rightDrawerState.isOpen`, but the right drawer's *opening* path is the outer Box (not the built-in edge gesture), and that path is already gated by `drawersClosed` requiring both drawers inactive.

## Caveats / Not Found

- The issue text was not retrieved from GitHub in this research pass; the analysis is based solely on the in-code `#301` comments and the surrounding logic. If the issue describes a specific repro scenario not covered by the three mitigation layers above, that scenario is not represented in the current code.
- `DrawerState.close()` is async (suspend); there is a brief animation window during which both drawers may briefly have `currentValue == Open`. The `isActive` extension covers `targetValue == Open` to narrow this window, but a fully simultaneous visual state during the close animation is still theoretically possible if the open gesture fires in the exact frame the other drawer begins closing.
- No unit tests for the drawer mutex were found under `app/src/test/`; the logic is only exercised through instrumented interaction.

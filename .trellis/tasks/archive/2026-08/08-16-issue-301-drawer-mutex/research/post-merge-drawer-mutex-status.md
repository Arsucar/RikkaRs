# Research: Issue #301 drawer mutex after upstream 2.4.12 merge

- **Query**: Are the three mitigation layers still present and complete after the upstream 2.4.12 merge, or did the merge regress them? Investigate ChatPage.kt mutex LaunchedEffect, left `gesturesEnabled`, right `gesturesEnabled` / outer `drawersClosed` gate, remaining dual-open gaps, and whether commit `fdbb083f` still applies on HEAD `f8c1bc00`.
- **Scope**: mixed (internal code + git history + GitHub issue Arsucar/RikkaRs#301)
- **Date**: 2026-08-26

## Findings

### Verdict

The three #301 mitigation layers are **present and complete on current HEAD `f8c1bc00`**. The upstream 2.4.12 merge **did not regress them**. GitHub issue [Arsucar/RikkaRs#301](https://github.com/Arsucar/RikkaRs/issues/301) is still **open** with zero comments; the code fix already landed in `0a09549f` (released in CHANGELOG `v2.3.53`).

Issue can be **closed as already fixed** on current `release/rikka-arsucar` HEAD. Remaining notes below are animation-window / programmatic-open facts, not a missing layer from the original scrim-gap report.

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | Owns both `DrawerState`s, mutex `LaunchedEffect`s, both `gesturesEnabled`, outer `drawersClosed` gate, `isActive` extension |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPageGesture.kt` | `shouldClaimRightDrawerGesture`; requires `drawersClosed == true` |
| `app/src/test/java/me/rerere/rikkahub/ui/pages/chat/ChatPageGestureTest.kt` | Unit tests for `shouldClaimRightDrawerGesture`, including `drawersClosed = false` |
| `CHANGELOG.md` | `v2.3.53` documents the #301 drawer mutex fix |
| `.trellis/spec/guides/ui-modification-thinking-guide.md:76` | Nested-drawer anti-pattern matching this issue |
| `.trellis/tasks/08-16-issue-301-drawer-mutex/prd.md` | Pre-merge PRD; attributes the three layers to `fdbb083f` (incorrect; see commit section) |
| `.trellis/tasks/08-16-issue-301-drawer-mutex/research/chat-page-drawer-setup.md` | 2026-08-16 pre-merge line map; line numbers still match HEAD |

### GitHub issue (authoritative)

- Repo: `Arsucar/RikkaRs` (not upstream `rikkahub/rikkahub`; upstream #301 is an unrelated SearXNG Basic Auth request, already closed).
- URL: https://github.com/Arsucar/RikkaRs/issues/301
- State: **open**, `state_reason` unset, 0 comments, 0 closing PRs.
- Title: `bug: 对话页左右抽屉可通过遮罩间隙同时展开`
- Reported factors in the issue body:
  - A: two independent `DrawerState`s, no mutex
  - B: left `gesturesEnabled` always default `true`; right already `gesturesEnabled = rightDrawerState.isOpen`
  - C: outer `drawersClosed` gate lets events through when a drawer is open

### Layer 1 — Mutual exclusion `LaunchedEffect` (present)

`ChatPage.kt:202-212` (HEAD `f8c1bc00`, blame `0a09549f`):

```kotlin
// #301: 左右抽屉互斥。任一抽屉开始打开时关掉另一个，避免遮罩间隙把两侧同时展开。
LaunchedEffect(drawerState.targetValue) {
    if (drawerState.targetValue == DrawerValue.Open && rightDrawerState.isActive) {
        rightDrawerState.close()
    }
}
LaunchedEffect(rightDrawerState.targetValue) {
    if (rightDrawerState.targetValue == DrawerValue.Open && drawerState.isActive) {
        drawerState.close()
    }
}
```

Predicate helper `ChatPage.kt:577-578`:

```kotlin
private val DrawerState.isActive: Boolean
    get() = currentValue == DrawerValue.Open || targetValue == DrawerValue.Open
```

`isActive` covers the in-flight open animation (`targetValue == Open`), not only `isOpen`.

### Layer 2 — Left drawer `gesturesEnabled` (present)

Small-screen branch `ChatPage.kt:525-529` (blame: drawer at `9701db4b`, `gesturesEnabled` at `0a09549f`):

```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    // #301: 右抽屉打开或动画中时禁用左缘拖拽，避免遮罩间隙同时展开两侧
    gesturesEnabled = !rightDrawerState.isActive,
```

Big-screen branch `ChatPage.kt:490-522` uses `PermanentNavigationDrawer` with no `gesturesEnabled` (no edge-swipe left drawer).

### Layer 3 — Right drawer gestures / outer `drawersClosed` gate (present)

Right `ModalNavigationDrawer` `ChatPage.kt:323-326` (blame `9701db4b`):

```kotlin
ModalNavigationDrawer(
    drawerState = rightDrawerState,
    // 打开后可滑动关闭；打开动作改用右缘手势条（见下方 Box），因为内层左抽屉会拦截关闭态的水平拖拽
    gesturesEnabled = rightDrawerState.isOpen,
```

Right-drawer **open** is not this built-in edge gesture. Open path is the outer `Box` `pointerInput` at `ChatPage.kt:276-317`. Gate at `ChatPage.kt:300-304`:

```kotlin
claim = shouldClaimRightDrawerGesture(
    totalX = totalX,
    totalY = totalY,
    touchSlop = slop,
    drawersClosed = !rightDrawerState.isActive && !drawerState.isActive,
    gestureExcluded = horizontalGestureExclusionState.isExcluded(down.id.value),
)
```

`ChatPageGesture.kt:5-16` returns false unless `drawersClosed` is true (and leftward, horizontal-dominant, not excluded). `ChatPageGestureTest.kt:129-136` asserts `drawersClosed = false` does not claim.

### Open call sites (only two)

| Location | Call |
|---|---|
| `ChatPage.kt:314` | `scope.launch { rightDrawerState.open() }` after outer gesture `claim && totalX < -slop * 2` |
| `ChatPage.kt:1112` | TopBar hamburger `scope.launch { drawerState.open() }` (`ChatPage.kt:1109-1113`, only when `!bigScreen`) |

No other `drawerState.open()` / `rightDrawerState.open()` in the repo.

### Commit `fdbb083f` vs actual fix `0a09549f`

| Commit | SHA | Touches ChatPage.kt? | #301 three layers? |
|---|---|---|---|
| `fdbb083f` | `fdbb083fc64b4245c64096bafd7b34a6b002aa26` | **No** (64 files; ChatPage not in the commit) | **No** |
| `0a09549f` | `0a09549fd7a47338f061507784a151a96be1ae4b` | **Yes** | **Yes** — introduced all three layers |

At `fdbb083f`, ChatPage only had:

- `drawersClosed = !rightDrawerState.isOpen && !drawerState.isOpen` (strict `isOpen`, not `isActive`)
- right `gesturesEnabled = rightDrawerState.isOpen`
- **no** mutex `LaunchedEffect`
- **no** left `gesturesEnabled = !rightDrawerState.isActive`
- **no** `DrawerState.isActive` extension

`fdbb083f` **is** an ancestor of HEAD (`git merge-base --is-ancestor fdbb083f HEAD` exit 0), but it does **not** implement the #301 mutex. The PRD line “Already fixed in commit `fdbb083f`” is historically wrong.

`0a09549f` message explicitly lists the three layers:

> #301: Drawer mutual exclusion — LaunchedEffect on targetValue closes the other drawer; left gesturesEnabled = !rightDrawerState.isActive; outer gesture layer uses isActive instead of isOpen to cover animation window.

### Did the 2.4.12 merge regress the layers?

| Ref | ChatPage #301 markers |
|---|---|
| `0a09549f` (fix) | layers 1–3 present at lines 202-212, 304, 326, 528-529, 577-578 |
| `38148bb6` (merge first parent / ours) | **identical** markers |
| `fa0305ba` (upstream 2.4.12+ tip) | **no** `#301` / `isActive` / `drawersClosed` / dual-drawer `gesturesEnabled` in ChatPage |
| `b47d5d30` (merge result) | **identical** to ours; `git diff 38148bb6 b47d5d30 -- ChatPage.kt` empty |
| `f8c1bc00` (HEAD) | **identical** to `0a09549f` ChatPage.kt (`git log 0a09549f..HEAD -- ChatPage.kt` empty; `git diff 0a09549f HEAD -- ChatPage.kt` empty) |
| working tree vs HEAD | ChatPage.kt / ChatPageGesture.kt **unchanged** |

Merge `b47d5d30` parents: `38148bb6` (fork) + `fa0305ba` (upstream). ChatPage was kept as **ours**. Upstream ChatPage had no dual-drawer mutex; taking ours preserved the fork fix.

Post-merge `f8c1bc00` (`fix: synthesize fork APIs after upstream 2.4.12 merge`) does not touch ChatPage.kt.

### Mapping to original issue factors

| Issue factor | Pre-`0a09549f` | HEAD `f8c1bc00` |
|---|---|---|
| A: no mutex | true | false — `ChatPage.kt:202-212` |
| B: left `gesturesEnabled` always true | true | false — `ChatPage.kt:529` `!rightDrawerState.isActive` |
| C: outer gate uses `isOpen` / lets events through while a drawer is open | `drawersClosed` used `isOpen` | `ChatPage.kt:304` uses `isActive` for **both** drawers |

### Remaining facts (not merge regressions)

These exist on HEAD the same way they existed at `0a09549f`:

1. **Close-animation overlap.** `DrawerState.close()` is a suspend animation. While one drawer is closing, both can have `currentValue == Open` for the animation duration. `isActive` is true when `currentValue == Open` **or** `targetValue == Open`, so the closer still counts as active and the outer gate / left `gesturesEnabled` stay blocked. A fully simultaneous *settled* Open+Open state is what the mutex prevents; a brief visual overlap during close is still possible.
2. **Right built-in `gesturesEnabled` is `rightDrawerState.isOpen`, not gated on the left drawer.** Opening the right drawer does not use that flag; it uses the outer Box. When the right drawer is closed, built-in gestures are off regardless of the left drawer.
3. **Hamburger (`ChatPage.kt:1112`) can call `drawerState.open()` with no `rightDrawerState` check.** If that click is reachable while the right drawer is open, Layer 1 then closes the right drawer (`targetValue == Open` + `rightDrawerState.isActive` → `rightDrawerState.close()`). Result is left open / right closing, not both remaining open.
4. **Big screen.** Left drawer is permanent (`ChatPage.kt:490-522`); the scrim-gap dual-modal repro in the issue is the small-screen `else` branch (`ChatPage.kt:525+`).
5. **Tests.** `ChatPageGestureTest.kt` covers the `drawersClosed` boolean of `shouldClaimRightDrawerGesture`. There is no Compose UI / instrumented test that drives two `ModalNavigationDrawer`s. This research pass did not run tests or install.

### Related Specs

- `.trellis/spec/guides/ui-modification-thinking-guide.md` — table row “Nested left/right drawers keep independent gestures” / “Gate the idle drawer's `gesturesEnabled` on the other drawer being inactive, and close the other drawer as soon as one targets Open”
- `.trellis/tasks/08-16-issue-301-drawer-mutex/prd.md` — AC1 verify three layers; AC2 close issue. AC1 holds on HEAD. PRD’s `fdbb083f` attribution does not.

## Caveats / Not Found

- This pass did not execute Gradle tests, lint, or device install.
- This pass did not re-run the physical scrim-gap swipe on a phone; conclusion is from HEAD source + git identity of ChatPage.kt from `0a09549f` through `f8c1bc00`.
- Upstream `rikkahub/rikkahub#301` is a different issue (SearXNG Basic Auth). Close comments must target `Arsucar/RikkaRs#301`.

## Suggested close-comment evidence (if closing as already fixed)

Facts for a close comment (not executed here):

- Issue: https://github.com/Arsucar/RikkaRs/issues/301
- Branch: `release/rikka-arsucar`
- Fix commit: `0a09549fd7a47338f061507784a151a96be1ae4b` (`fix(#304,#301): prompt page blank render + drawer mutex; feat(#302): tavern card world book import`)
- Still on HEAD: `f8c1bc0087f5983598ca4c1b28f62f8d89347080`
- Changelog: `CHANGELOG.md` `v2.3.53` “修复对话页左右抽屉同时展开（#301）”
- Merge check: `b47d5d30` kept fork ChatPage; `git diff 38148bb6 b47d5d30 -- .../ChatPage.kt` empty; layers unchanged after 2.4.12 merge
- Code:
  - mutex `ChatPage.kt:202-212`
  - left `gesturesEnabled = !rightDrawerState.isActive` `ChatPage.kt:529`
  - outer `drawersClosed = !rightDrawerState.isActive && !drawerState.isActive` `ChatPage.kt:304`
  - `DrawerState.isActive` `ChatPage.kt:577-578`
- Known boundary: close-animation frames can still show both drawers visually while one is closing; hamburger can open the left drawer and the mutex then closes the right; no instrumented dual-drawer test in this pass
)

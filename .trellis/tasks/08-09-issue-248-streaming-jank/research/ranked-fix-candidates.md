# Research: #248 ranked fix candidates (impact / risk)

- **Query**: Highest-ROI minimal fixes for long-reasoning streaming jank
- **Scope**: internal (derived from verified hot-path locations)
- **Date**: 2026-08-10
- **HEAD**: `066ef0df`
- **Depends on**: `research/streaming-jank-hot-path.md`

## Ranking criteria

- **Impact**: expected reduction of Main-thread work / cancelled work / layout thrash at ~40 tps long reasoning
- **Risk**: behavior regressions (lost tokens, expand/scroll UX, file GC side effects, #1295 drop risk)
- **ROI**: impact ÷ implementation surface

Priority labels A–D match the task brief.

---

## Ranked fix list

### 1) A — Fix `ChatMessageReasoning` LaunchedEffect key

| | |
|---|---|
| **Where** | `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageReasoning.kt:96-109` |
| **What exists** | `LaunchedEffect(reasoning.reasoning, loading)` restarts on every token; calls `animateScrollTo(maxValue)` and may re-assign expand state |
| **Candidate change site** | Key on `reasoning.createdAt` + `loading` (and/or content length bucket), not full text. Keep scroll-to-end via a lighter path (e.g. `snapshotFlow { scrollState.maxValue }` or length-only effect without cancel/restart of expand logic) |
| **Impact** | **High** — removes ~12k effect restarts + animation cancellations on a 300s @ 40tps stream |
| **Risk** | **Low** — local UI; must preserve: auto Preview when loading+showThinkingContent; auto Collapsed when finished+autoCloseThinking; scroll-follow while loading |
| **ROI** | **Highest** |

Related same-file sites:

- Duration ticker already keyed only on `loading` (`:111-117`) — leave as-is or raise delay if needed
- Content still recomposes via `MarkdownBlock(content = reasoning…)` (`:171-179`) — separate issue (D / B)

---

### 2) B — Throttle / conflate UI state updates on stream path

| | |
|---|---|
| **Where (publish)** | `ChatService.kt:975-1003` (`collect` Messages → `updateConversationState`) |
| **Where (emit)** | `GenerationHandler.kt:228-239` (emit after visualTransforms) |
| **Where (session)** | `ConversationSession.kt:128-136` (`state.value = updated` every transform) |
| **Where (consume)** | `ChatPage.kt:136` (`collectAsStateWithLifecycle` full Conversation) |
| **What exists** | Every SSE chunk → Main collect → full Conversation rebuild → StateFlow → Compose |
| **Candidate change sites** | One or combine: (1) `sample(50ms)` / time-based coalesce before `updateConversationState` while still applying latest chunk; (2) conflate UI-facing flow in VM without dropping durable in-memory latest; (3) move generation `appScope.launch` body off Main for non-UI work while keeping StateFlow updates on Main less often |
| **Impact** | **High** — multiplies with all downstream UI costs (list, markdown, regex, animations) |
| **Risk** | **Medium** — must never lose final chunk; tool-call boundaries / approval UI may need immediate flush; notification `tryEmit` (`:992-995`) can stay lower rate; #1295 is provider buffer, not UI coalesce |
| **ROI** | **Very high** if done as UI coalesce only (keep full fidelity in handler-local `messages`) |

Note: Provider `buffer(UNLIMITED)` (`ChatCompletionsAPI.kt:264`) is **not** the first throttle knob — changing it risks #1295 silent drops. Prefer consumer-side coalesce.

---

### 3) C — Move / gate `checkFilesDelete` off per-token path

| | |
|---|---|
| **Where** | `ChatService.kt:2558-2561` (always called from `updateConversationState`); implementation `:2780-2789` |
| **Cost driver** | `Conversation.files` getter `Conversation.kt:49-53` — full `messageNodes → messages → parts` walk **twice** per call |
| **Candidate change sites** | (1) Skip `checkFilesDelete` when update is streaming message-text-only; (2) call only from `saveConversation` / non-stream commits / attachment mutations; (3) cheap equality: if `messageNodes` structure/file parts unchanged, skip; (4) cache file set on Conversation if must remain |
| **Impact** | **Medium–High** on long chats (O(parts) × 40/s on Main); lower if conversations are short/few attachments |
| **Risk** | **Low–Medium** — must not leak orphan chat files when images/docs removed mid-session; pure reasoning growth never deletes files, so gating is safe for that path |
| **ROI** | **High** for small code change at `updateConversationState` |

---

### 4) D — Markdown throttle / streaming renderer

| | |
|---|---|
| **Where** | `Markdown.kt:242-253` (`mapLatest` parse); `MarkdownNew.kt:137-143`; paragraph `remember(content)` AnnotatedString `:802-818` |
| **Call sites during reasoning** | `ChatMessageReasoning.kt:171-179`; assistant text `ChatMessage.kt:486-529` |
| **What exists** | Every content change cancels in-flight Default parse (`mapLatest`); successful parse recomposes full tree; first composition sync-parses |
| **Candidate change sites** | (1) `debounce(50-100ms)` or `sample` before `mapLatest`; (2) plain `Text` / incremental renderer while `loading==true`, full Markdown on finish; (3) disable `animateContentSize` while loading (`ChatMessage.kt:473+`, `ChainOfThought.kt:94-96`) |
| **Impact** | **High** for visible jank of growing reasoning/text blocks; pairs with A |
| **Risk** | **Medium** — debounce delays last paint (flush on loading→false); plain-text streaming changes visual fidelity mid-flight |
| **ROI** | **High** especially “no animateContentSize while loading” (small) + debounce parse |

---

### 5) Secondary (still real, lower immediate ROI)

| Rank | Site | Notes | Impact | Risk |
|---|---|---|---|---|
| 5a | `Message.kt:48,84` String `+` concat | O(n²) allocations as reasoning grows; `StringBuilder`/rope per open part | Medium (CPU/GC) | Low if careful with immutability |
| 5b | `GenerationHandler.kt:230-237` visualTransforms every chunk | ThinkTag full-message map + regex (`ThinkTagTransformer.kt:50-72`); UpdateVariable visual when enabled | Medium | Medium (think-tag streaming correctness) |
| 5c | `ChatMessage.kt:461-469`, `ChatMessageReasoning.kt:172-176` `replaceRegexes` | Per recompose visual regex; compile cached (`Assistant.kt:138-148`) | Low–Med | Low |
| 5d | `ChatList.kt:292-303` auto-scroll `snapshotFlow(visibleItemsInfo)` | Continuous while loading | Low–Med | Low |
| 5e | `RikkaHubApp.kt:263-269` AppScope Main | Root scheduling; large move of ChatService jobs | High systemic | **High** (touch many call sites) — not minimal |
| 5f | Provider `buffer(UNLIMITED)` | Memory if UI slow; **do not conflate here** without #1295 redesign | Mem only | High for text integrity |

---

## Suggested implementation order (minimal first)

1. **A** `ChatMessageReasoning.kt:96` key fix (+ keep scroll-follow without full-text key)
2. **C** gate `checkFilesDelete` in `ChatService.kt:2558-2561` for stream-only updates
3. **D-lite** disable `animateContentSize` while `loading` (`ChatMessage.kt`, `ChainOfThought.kt:94`)
4. **B** coalesce Conversation UI publishes (~50–100ms, always emit latest + final)
5. **D-full** Markdown debounce / streaming plain text
6. **5a** StringBuilder-style delta append in `Message.kt`
7. Defer AppScope Main migration and UNLIMITED buffer changes

## Concrete file:line checklist (edit targets)

| Priority | File | Lines | Action class |
|---|---|---|---|
| P0 | `.../ChatMessageReasoning.kt` | 96-109 | Change LaunchedEffect keys; split expand vs scroll effects |
| P0 | `.../ChatService.kt` | 2558-2561, 2780-2789 | Stop per-token checkFilesDelete / full files scan |
| P0 | `.../Conversation.kt` | 49-53 | Only if C needs cheaper file set (optional) |
| P1 | `.../ChatService.kt` | 975-1003 | Coalesce/throttle Messages → updateConversationState |
| P1 | `.../ChatMessage.kt` | 473, 496, 521, 528 | Skip animateContentSize when loading |
| P1 | `.../ChainOfThought.kt` | 94-96 | Skip animateContentSize when step loading / streaming |
| P1 | `.../Markdown.kt` | 247-253 | Debounce/sample before mapLatest; flush on idle |
| P1 | `.../MarkdownNew.kt` | 137-143 | Same as Markdown.kt |
| P2 | `.../GenerationHandler.kt` | 219-240, 469-480 | Optional emit throttle; avoid redundant transforms |
| P2 | `ai/.../Message.kt` | 48, 84 | Incremental string build for Text/Reasoning deltas |
| P2 | `.../ChatList.kt` | 292-303 | Cheaper at-bottom observation |
| P3 | `.../RikkaHubApp.kt` | 263-269 | AppScope dispatcher strategy (broad) |
| P3 | `ai/.../ChatCompletionsAPI.kt` | 264 | Leave UNLIMITED unless redesigning backpressure with no drops |

## Acceptance signals (for later implement/check)

- During ~40 tps long reasoning: scroll list and open IME without multi-second freezes
- Reasoning Preview expand state does not flicker; user Expanded choice not reset every token
- No missing characters vs pre-fix (#1295 class)
- Removing an image mid-conversation still deletes file (if C changes checkFilesDelete)
- Final token / finished reasoning always painted (if B/D throttle)

## Caveats

- This file lists **candidate edit sites** ranked by verified cost; it is not an implementation design.
- No profiler evidence attached; order is static analysis + issue reproduction profile.
- Issue claim “visualTransforms 全量正则” overstates pipeline regex; UI `replaceRegexes` + ThinkTag visual remain real.

# Research: Subagent UI Layer Audit (Route B)

- **Query**: Subagent subsystem UI — chat cards, settings, global config, composer, streaming
- **Scope**: internal (read-only code review)
- **Date**: 2026-06-28
- **Commit**: `3ebfbca45893deefb8c61768a4bdb0e01255a5f6`
- **Recent diff (HEAD~5..HEAD, subagent UI files)**: 4 files, +540 / −29 lines (`SubagentToolUIs.kt`, `SubagentUiHelpers.kt`, `ExtensionSubagentProfilePage.kt`, `ExtensionSubagentsPage.kt`)

## Scope map (task list vs repo)

| Task path | Actual location | Notes |
|-----------|-----------------|-------|
| `SubagentToolUIs.kt` | `app/.../ui/components/message/tools/SubagentToolUIs.kt` | Primary chat card UI |
| `ChatMessage.kt` | `app/.../ui/components/message/ChatMessage.kt` | No subagent-specific logic; tools via `ChatMessageTools.kt` → `ToolUIRegistry` |
| `ModelList.kt` | `app/.../ui/components/ai/ModelList.kt` | No `subagent` references; used from `SubagentProfileForm` for profile model picker |
| `ui/pages/setting/` subagent | **Not present** | Subagent settings live under `ui/pages/assistant/detail/*Subagent*` and `ui/pages/extensions/*Subagent*` |
| `ChatInput.kt` subagent injection | `app/.../ui/components/ai/ChatInput.kt` | **No matches** for subagent/Subagent |
| `SubagentProfileForm.kt` (data path) | `app/.../ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` (`internal fun SubagentProfileForm`) | Shared by assistant + extension profile pages |

## Findings

### 1. Accessibility (TalkBack / contentDescription)

| ID | File:line | Finding | Severity | Suggestion |
|----|-----------|---------|----------|------------|
| A1 | `SubagentToolUIs.kt:403,454,485,537` | Step/tool icons use `contentDescription = null`; adjacent text partially describes tool calls but icons are not individually announced. | **P2** | Add localized CDs for reasoning / tool / text step icons, or `Modifier.semantics { contentDescription = ... }` on the step row. |
| A2 | `AssistantSubagentPage.kt:142,146,230,241,264,267` | Action icons (refresh, add, delete, copy) all `contentDescription = null`. | **P2** | Mirror `PropertyEditor.kt` pattern: `stringResource` for add/delete. |
| A3 | `ExtensionSubagentsPage.kt:77,80,139,142` | Add / overflow / navigate / delete icons `contentDescription = null`. | **P2** | Same as A2; align with `SkillsPage` `skills_page_more_actions` where applicable. |
| A4 | `SubagentToolUIs.kt:141-146` | Streaming `CircularProgressIndicator` has no accessibility state (running). | **P3** | `progressBarRangeInfo` or `liveRegion` + CD on the running row. |
| A5 | `AssistantSubagentProfilePage.kt` (`SubagentProfileForm`) | Many toggles/sliders use `stringResource` labels; read-only `OutlinedTextField(readOnly=true)` at 220,230 — TalkBack may still focus without clear “read-only” semantics. | **P3** | `semantics { disabled() }` or explicit read-only description when `readOnly \|\| isGlobalOnly`. |

### 2. Recomposition / remember / derived state

| ID | File:line | Finding | Severity | Suggestion |
|----|-----------|---------|----------|------------|
| R1 | `SubagentToolUIs.kt:63,77,115-118` | `remember(context.tool)` keys only on tool **reference**; if streaming updates mutate `output`/`metadata` in place on same instance, Compose may skip re-parsing metadata/transcript. | **P1** | Key `remember` on stable fingerprint: e.g. `context.tool.output`, metadata hash, or `context.tool` + `context.loading`. |
| R2 | `SubagentToolUIs.kt:105-106` | `hasSummary` calls `parseSubagentResult` / `transcriptStepsFromMetadata` **outside** `remember` on every recomposition check. | **P2** | Hoist parsed state once in `Summary`/`title` or cache in `remember(context.tool, context.loading)`. |
| R3 | `SubagentToolUIs.kt:351` | `SubagentTranscriptSection` uses `steps.forEach` without `key()`; long transcripts may reset expand state oddly on list changes. | **P3** | `key(step)` per step type + stable id if available. |
| R4 | `ChatMessageTools.kt:76` | `remember(tool.toolName)` for renderer only — OK; tool body updates rely on parent recomposition. | **P3** | Ensure parent passes new `ToolUIContext` when `output` changes (verify `ChatMessage.kt` tool step keys). |
| R5 | `ChatMessage.kt:345` | Tool steps keyed by `toolCallId` / hash — good for CoT list. | — | Positive |

### 3. Icons (HugeIcons)

| ID | File:line | Finding | Severity | Suggestion |
|----|-----------|---------|----------|------------|
| I1 | `SubagentToolUIs.kt:59,224,453,536,587-589` | Uses `HugeIcons.Connect`, `BubbleChatQuestion`, `Sparkles`, `Search01`, `Tools` — consistent with project HugeIcons usage. | — | Positive |
| I2 | `SubagentToolUIs.kt:586-589` | `subagentToolStepIcon` maps unknown tools to `HugeIcons.Tools` (same as default tool UI). | **P3** | Optional: map common workspace tool names to `FileEdit` / `ComputerTerminal01` like `WorkspaceToolUIs.kt`. |
| I3 | `ExtensionsPage.kt:86`, `AssistantSubagentPage.kt` (leading icons) | Entry uses `HugeIcons.Connect` for subagents — aligned with spawn card icon. | — | Positive |

### 4. Empty / disabled / permission states

| ID | File:line | Finding | Severity | Suggestion |
|----|-----------|---------|----------|------------|
| E1 | `AssistantSubagentPage.kt:155` | Empty merged list: `subagent_profiles_empty` — OK. | — | Positive |
| E2 | `ExtensionSubagentsPage.kt:111` | Global list empty: `extensions_subagents_page_empty` — OK. | — | Positive |
| E3 | `AssistantSubagentPage.kt:166,175,222-237` | Disabled global profiles: alpha 0.45, refresh to re-enable, navigation blocked when disabled — clear UX. | — | Positive |
| E4 | `AssistantSubagentHubSection.kt:41-104` | Enable toggle blocked when no spawn-capable profile; dialog `subagent_enable_blocked_*` — OK. | — | Positive |
| E5 | `AssistantSubagentProfilePage.kt:166,179` | Global-only profile: banner `subagent_global_edit_in_extensions` + read-only form — OK. | — | Positive |
| E6 | `SubagentToolUIs.kt:186-195` | Fallback raw `output` text when no result/metadata and not loading — edge case covered. | **P3** | Ensure string is never sensitive/log dump in production cards. |
| E7 | Workspace “permission denied” | No dedicated subagent UI for workspace approval denial in these files (handled elsewhere in chat/tool approval flow). | **P3** | Confirm workspace tool cards surface denial; out of narrow UI file set. |

### 5. Chat cards: collapse / expand / streaming scroll

| ID | File:line | Finding | Severity | Suggestion |
|----|-----------|---------|----------|------------|
| C1 | `SubagentToolUIs.kt:153-163` | Streaming uses nested `ChainOfThought` with `collapsedVisibleCount = metaTranscript.size` when streaming — shows full live transcript. | — | Positive |
| C2 | `SubagentToolUIs.kt:332-338` | Post-completion transcript uses manual expand/collapse (`subagent_tool_ui_expand_details` / `collapse_details`). | — | Positive |
| C3 | `SubagentToolUIs.kt:209,279` | `Preview` uses `verticalScroll(rememberScrollState())` — sheet scroll OK. | — | Positive |
| C4 | `ChatMessageReasoning.kt:96-100` | Reasoning steps auto `animateScrollTo` on stream — subagent inner CoT does **not** auto-scroll. | **P2** | For long `metaTranscript` during streaming, mirror reasoning `LaunchedEffect` + scroll or cap height. |
| C5 | `SubagentToolUIs.kt:136-147` | Spinner when `context.loading \|\| streaming` and no summary — correct UX contract with metadata. | — | Positive (if metadata cleared; see S1) |

### 6. `subagent_streaming=true` stale metadata (cold start / lifecycle)

| ID | File:line | Finding | Severity | Suggestion |
|----|-----------|---------|----------|------------|
| S1 | `ChatService.kt:245-260` | `getOrCreateSession` seeds `Conversation.ofId(...)` only — **does not** run `cleanStaleStreamingMetadata` on session create. | **P1** | On first load of persisted messages into session (or in `getOrCreateSession` after DB hydrate), apply `cleanStaleStreamingMetadata()` once before UI subscribes. |
| S2 | `ChatService.kt:666,677,1105` | `cleanStaleStreamingMetadata()` applied when applying message chunks / committing state — mitigates stale flags during normal load paths. | — | Partial mitigation |
| S3 | `ChatService.kt:1204-1235` | Pure function clears metadata `subagent_streaming` → `false` for all assistant tool parts — UI reads metadata (`SubagentToolUIs.kt:67,116`). | — | UI contract consistent |
| S4 | `SubagentToolUIs.kt:67,116` | UI treats `subagent_streaming == "true"` as streaming; no UI-side stale cleanup. | **P2** | Defensive UI: if `!context.loading && !session.isGenerating` and streaming true, show completed/failed affordance (or hide spinner); prefer service fix (S1). |
| S5 | Process kill mid-stream | Documented gap in task `06-28-review-fixes` R3; service cleanup exists but session bootstrap gap remains. | **P1** | Wire cleanup at conversation open (same as S1). |

### 7. Internationalization

| ID | File:line | Finding | Severity | Suggestion |
|----|-----------|---------|----------|------------|
| L1 | `SubagentToolUIs.kt` (broad) | User-visible strings use `stringResource` for running/failed/steps/tokens/ask_btw/etc. | — | Positive |
| L2 | `SubagentToolUIs.kt:65,81,97-100` | Fallback profile label `"subagent"` and title separators built with literal `" · "` in `buildString` — profile slug not localized (acceptable); separator is punctuation. | **P3** | Optional: use locale-aware separators if product cares. |
| L3 | `SubagentToolUIs.kt:260` | `question.ifBlank { "..." }` inside `stringResource(R.string.subagent_tool_ui_ask_q, ...)` — ellipsis is hardcoded English glyph. | **P3** | Use `@string/common_ellipsis` or omit blank Q line. |
| L4 | `AssistantSubagent*.kt`, `ExtensionSubagent*.kt` | Settings/copy overwhelmingly `stringResource`; strings in `values/strings.xml` under `subagent_*` / `extensions_subagents_*`. | — | Positive |
| L5 | `SubagentToolUIs.kt:173-195` | Error fallback and raw output `Text` display model/error text from runtime — not i18n (content, not chrome). | — | OK |

### 8. Integration / settings gaps

| ID | File:line | Finding | Severity | Suggestion |
|----|-----------|---------|----------|------------|
| G1 | `ExtensionSubagentProfilePage.kt:86-99` | `SubagentProfileForm` called **without** `globalProfiles = globalProfiles` (defaults `emptyList()`). | **P2** | Pass `globalProfiles` like `AssistantSubagentProfilePage.kt:175` so create-mode / name collision logic matches assistant path. |
| G2 | `ExtensionSubagentProfilePage.kt:47-57` | Create mode uses in-memory `SubagentProfile(name)` until first `persist` — no pre-insert stub in settings on navigate. | **P2** | Document or align with assistant create flow (`upsertSubagentProfile` on confirm). |
| G3 | `ui/components/ai/ChatInput.kt` | No subagent composer injection (task listed; not implemented in this file). | **P3** | If product expects composer hints/chips for spawn, track separate task; N/A for current code. |
| G4 | `ChatMessage.kt` | Subagent only via generic tool pipeline — no special-case subagent layout in message root. | — | By design |

## Related specs / prior research

- `.trellis/tasks/archive/2026-06/06-28-06-28-review-all-changes/research/ui-components-code-review.md` — ModelList a11y, SubagentToolUIs step icon CDs
- `.trellis/tasks/archive/2026-06/06-28-06-28-review-all-changes/research/extension-subagent-pages-review.md` — `globalProfiles` omission on extension profile page
- `.trellis/tasks/06-28-review-fixes/prd.md` R3 — stale `subagent_streaming` on conversation load
- `.trellis/tasks/06-27-sub-agent-streaming-ui/design.md` — metadata contract (`subagent_streaming`, `subagent_transcript`, …)

## Caveats / not found

- Could not read full file bodies via `Read` tool (schema issue); line refs from ripgrep / selective `Get-Content`.
- `ui/pages/setting/` has no subagent pages in this repo layout.
- `data/ai/subagent/SubagentProfileForm.kt` does not exist; form is UI-layer `internal` composable.
- Workspace tool approval UI for subagent runs not audited in workspace tool files this pass.

---

## 明早 Top 5

1. **P1 — `remember(context.tool)` may miss in-place streaming updates** (`SubagentToolUIs.kt:63-118`): fix remember keys before users see frozen subagent cards mid-run.
2. **P1 — Stale `subagent_streaming` on cold open** (`ChatService.kt:245-260` vs `1204+`): session bootstrap does not clean metadata; permanent spinner after process death until chunk path runs.
3. **P2 — Extension profile form missing `globalProfiles`** (`ExtensionSubagentProfilePage.kt:86-99`): parity bug with assistant editor / create-mode validation.
4. **P2 — Subagent settings action icons lack contentDescription** (`AssistantSubagentPage.kt`, `ExtensionSubagentsPage.kt`): TalkBack gap on primary CRUD affordances.
5. **P2 — Long streaming transcript without auto-scroll** (`SubagentToolUIs.kt` CoT vs `ChatMessageReasoning.kt:96-100`): UX regression risk on long subagent runs.
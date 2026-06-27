# Code Review: UI Components (uncommitted diff)

- **Branch**: `release/rikka-arsucar`
- **Scope**: 6 files under `ui/components`
- **Date**: 2026-06-28
- **Reviewer role**: Compose / a11y / data-flow / performance (review only)

---

## Executive summary

| File | Risk | Theme |
|------|------|--------|
| `ModelList.kt` | **Medium–High** | Large feature delta; a11y gaps; `remember` key mismatch; possible dead imports |
| `SubagentToolUIs.kt` | **Medium** | Streaming UX + `ChainOfThought`; composable/non-composable asymmetry; heavy markdown while streaming |
| `McpPicker.kt` | **Low** | i18n only |
| `Markdown.kt` | **Low** | i18n for table actions |
| `ChainOfThought.kt` | **Low** | Preview i18n |
| `ShareSheet.kt` | **Low** | Title i18n; share icon a11y unchanged |

---

## 1. `McpPicker.kt`

### Changes
- Hardcoded `"${enabledTools.size}/${tools.size} tools"` → `stringResource(R.string.mcp_tools_count, ...)`.

### Issues / suggestions
| Severity | Item |
|----------|------|
| **None (blocking)** | — |
| **Info** | Aligns with project i18n; pluralization handled in locale strings (`%1$d/%2$d`). |

### Compose / ViewModel
- No state or data-flow changes in this diff.

---

## 2. `ModelList.kt`

### Changes (summary)
- `@Stable` on `ModelListState`.
- Bottom sheet: `sheetGesturesEnabled = false`, height 0.85f, layout padding tweaks.
- Favorites/providers **collapse**; **tag filter** (`FilterChip` + `filter_all`); expand/collapse all; provider tabs **FlowRow vs LazyRow**.
- `favoriteModels` wrapped in `remember`; `providerGroupExpanded` via `mutableStateMapOf`.
- Scroll index logic updated for collapsed sections; `LaunchedEffect` auto-expands provider containing `currentModel`.
- Clear button: `contentDescription` → `R.string.common_clear`.
- Multiple icons: `contentDescription = null`.

### Issues / suggestions

| Severity | Issue | Detail |
|----------|--------|--------|
| **Medium** | **`remember` dependency incomplete for `favoriteModels`** | `remember(settings.value.favoriteModels, providers, modelType)` but body uses `settings.value.providers` for `findModelById` / `findProvider`. If `favoriteModels` and `providers` (sheet arg) are unchanged while **settings providers/models** update, favorites list can be **stale**. Prefer keys that include a stable fingerprint of `settings.value.providers` or drop `remember` and derive from latest `settings.value`. |
| **Medium** | **Accessibility: decorative vs actionable icons** | Collapse-all, provider-tabs toggle, section headers (favorite/provider), search leading icon, favorite heart buttons still use `contentDescription = null`. Toggle buttons should use localized strings (e.g. expand/collapse all, show provider list). Section headers use `clickable` without semantics role/label — TalkBack may not announce as expandable. |
| **Medium** | **`providerGroupExpanded.toMap()` in `remember`** | Correct for invalidating `providerPositions`, but **new map instance every recomposition** when any provider toggles; acceptable at current scale; watch if provider count is large. |
| **Low** | **Unused imports** | Diff adds `background`, `fillMaxSize`; grep shows **no usage** in file — likely lint noise; remove. |
| **Low** | **`sheetGesturesEnabled = false`** | Disables swipe-to-dismiss; may frustrate users and conflict with platform sheet expectations; document intentional UX or offer alternative dismiss. |
| **Low** | **Tag chips use raw `tag` string** | Provider tags are user/config strings — OK for display; ensure tags are not unbounded length (ellipsis not applied on `FilterChip` label). |
| **Low** | **Reorder + collapse** | Favorite reorder only when section expanded; collapsed favorites hidden from list — reorder state still valid; verify edge case: collapse during drag. |
| **Info** | **`@Stable` on `ModelListState`** | Helps skip recomposition when state holder identity stable; ensure all mutable fields inside state class remain properly observable elsewhere (not changed in this diff). |

### Compose state / recomposition
- Many new `remember` / `mutableStateOf` / map states — reasonable locality in `ModelList`.
- `settings` via `collectAsStateWithLifecycle()` — favorite toggle still uses `settings.value` inside `items` → recomposes on settings updates (good).
- `FlowRow` for all provider chips when expanded: **all chips composed at once** (not lazy). Fine for tens of providers; could jank with very large lists.

### ViewModel data flow
- Still driven by `ModelListState.filteredProviders`, `SettingsStore.settingsFlow`, and callbacks `onSelect` / `onDismiss` — no ViewModel introduced here; pattern unchanged.
- Tag filter is **UI-local** (`selectedModelListTag`) filtering `providers` prop — consistent; not persisted (product choice).

### UI thread
- `fastFilter` / `associate` in `remember` — off hot path except when keys change; OK.
- `snapshotFlow` + `debounce(100)` for badge scroll — main-safe.

---

## 3. `SubagentToolUIs.kt`

### Changes (summary)
- Streaming metadata on tool output (`JsonObject` on text part metadata): `subagent_streaming`, `subagent_steps`, `subagent_transcript`, `subagent_succeeded`, etc.
- `title()` / `Summary()` / `hasSummary()` extended for streaming.
- `ChainOfThought` + `SubagentStreamingStepView` for live transcript.
- Loading row shown when `loading || streaming` and no summary yet.

### Issues / suggestions

| Severity | Issue | Detail |
|----------|--------|--------|
| **Medium** | **`hasSummary` vs `Summary` parity** | `hasSummary` calls `transcriptStepsFromMetadata(context)` **without** `remember` on every check (non-composable). `Summary` uses `remember(context.tool) { transcriptStepsFromMetadata(...) }`. Usually consistent; if metadata mutates within same `context.tool` identity during streaming, behavior depends on parent recomposition — verify tool object updates when metadata streams. |
| **Medium** | **`title()` composable work** | `parseSubagentMetadata` + conditional `stringResource` inside streaming branch; then `parseSubagentResult` again. Consider single remembered parse in `title` if profiling shows churn (minor). |
| **Medium** | **`MarkdownBlock` in streaming `Text` step** | Full markdown render per streaming text chunk can be **expensive on main thread** during rapid updates. Existing `SubagentTranscriptSection` may have same pattern — still a risk for jank. |
| **Low** | **Duplicate transcript UI** | When `metaTranscript` non-empty, final `result.transcript` section suppressed — good. When stream ends, ensure metadata cleared or result populated to avoid empty/flicker. |
| **Low** | **Failure detection** | `failed` uses metadata `subagent_succeeded == false` only when not streaming — sensible. Confirm host always sets streaming false before succeeded false. |
| **Low** | **a11y** | New step icons `contentDescription = null`; tool name in label text partially compensates for tool calls. |
| **Info** | **Metadata vs body JSON** | `parseSubagentMetadata` uses **first** `UIMessagePart.Text` **metadata** only; `parseSubagentResult` joins **all** text parts — contract with `SubagentHost` must stay aligned (see data-layer review). |

### Compose / data flow
- Correctly treats tool output as single source for streaming (metadata) and final result (JSON body) — UI-layer only; no ViewModel.

---

## 4. `Markdown.kt`

### Changes
- Table copy/download icons: hardcoded `"Copy"` / `"Download"` → `stringResource(R.string.markdown_table_*)`.

### Issues / suggestions
| Severity | Item |
|----------|------|
| **None (blocking)** | — |
| **Info** | Improves a11y for screen readers on table actions. |

### Performance
- No change to parsing/rendering pipeline in this diff.

---

## 5. `ChainOfThought.kt`

### Changes
- Preview `TopAppBar` title → `stringResource(R.string.chain_of_thought_title)`.

### Issues / suggestions
| Severity | Item |
|----------|------|
| **None** | Production composable API unchanged. |
| **Info** | `stringResource` already imported at file level — preview-only improvement. |

---

## 6. `ShareSheet.kt`

### Changes
- Sheet title hardcoded Chinese → `R.string.share_sheet_title`.

### Issues / suggestions
| Severity | Issue | Detail |
|----------|--------|--------|
| **Low** | **Share `IconButton` a11y (pre-existing)** | `Icon(HugeIcons.Share03, null)` — no `contentDescription`; not introduced by i18n change but still in touched file. |
| **Info** | i18n keys present across `values*` locales — good consistency with other files in same change set. |

---

## Cross-cutting recommendations (non-blocking)

1. **ModelList**: Add string resources for expand/collapse controls; use `Modifier.semantics { }` on clickable section headers.
2. **ModelList**: Fix `favoriteModels` `remember` keys or derive without stale `settings.value.providers`.
3. **ModelList**: Remove unused imports from diff.
4. **SubagentToolUIs**: Validate streaming recompositions when metadata updates; consider lighter renderer for in-progress text steps.
5. **ShareSheet**: Add `contentDescription` for share action while touching the file.

---

## Caveats

- Review based on **`git diff` only** for the six paths; related string resources assumed present in companion `strings.xml` changes (not fully audited here).
- No runtime / device testing performed.
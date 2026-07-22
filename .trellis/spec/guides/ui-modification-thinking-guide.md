# UI Modification Thinking Guide

> Android Compose UI changes must be reviewed against this guide before implementation. The goal is to prevent the recurring regressions recorded in archived UI tasks and issues #131, #140, and #163.

## When This Guide Applies

Read this guide before changing any user-visible Android Compose screen, dialog, sheet, list, card, gesture, state indicator, string, or accessibility behavior. It does not apply to web-ui/React.

When an active Trellis task exists, record the applicable UI cases in that task's PRD/design acceptance criteria. For a lightweight direct edit without a task, do not create placeholder task artifacts; run the same verification matrix and record the evidence in the check or delivery report. In both paths, update this guide in the same change whenever the request introduces a new reusable rule, a new failure mode, or changes an existing UI contract. Do not defer a newly discovered UI lesson to a later task.

## Pre-Implementation Checklist

- [ ] Identify the primary user task. Keep diagnostics, low-frequency controls, and implementation details out of the main path.
- [ ] Enumerate states: normal, empty, loading, success, failure, cancelled, disabled, permission denied, and stale data.
- [ ] Enumerate data sizes: zero, one, typical, many, duplicate names, and long localized text.
- [ ] Check small width, landscape, IME visible, dark/light theme, scrolling, TalkBack, and large font behavior.
- [ ] Separate display projection from persistence semantics. UI filtering or deduplication must not silently delete or merge stored entities.
- [ ] Check every repeated item has a stable business key and every derived list has the narrowest correct memoization keys.
- [ ] Check whether state changes should alter scroll position. Only explicit navigation should trigger automatic scrolling.
- [ ] Define feedback for every command: success, rejection, failure, cancellation, and confirmation for destructive or relaxing actions.
- [ ] Define the primary click target separately from dangerous actions. Put delete/remove in an overflow or secondary action with scoped confirmation.
- [ ] Provide localized descriptions for icons and localize enum names, preset slugs, status reasons, and other machine-facing values.
- [ ] If a page mutates Activity orientation or system-bar visibility, snapshot the entering values, derive UI from the
  actual configuration/insets, and restore every window-level side effect on mode change and page disposal.

## Interaction and Layout Contracts

### Lists and scrolling

- Initial positioning may use `rememberLazyListState(initialFirstVisibleItemIndex = ...)`.
- Do not put expansion, filtering, or unrelated derived maps into an automatic-scroll effect key.
- Collapsed groups keep a recognizable header and count, and do not render child rows.
- On short landscape viewports, collapsing only the app bar is insufficient when tabs or low-frequency controls remain
  fixed outside the content scroller. Keep essential navigation reachable, but move secondary controls into the
  scrollable header or the same collapsing region and verify the reclaimed editor height with the IME visible.
- Capacity-uncertain pickers must scroll. A plain `Column` inside an `AlertDialog` is not sufficient for long lists or IME scenarios.

### Selection and actions

- Multi-select is an explicit mode. Do not show empty checkboxes on every row by default; enter through a clear long-press or equivalent action and restore normal single-item navigation on exit.
- A row that represents an existing object must open that object, not appear to create a duplicate.
- Scope or mode choices use an explicit control. Keep one clear primary action rather than overloading confirm/dismiss slots with different creation meanings.
- Empty states include a useful explanation and a reachable CTA.

### Information hierarchy

- A title is user-readable. Supporting text, tags, and status explain state without replacing the item's description.
- Keep diagnostic copy for an explicit copy action or a collapsed advanced section. Do not render machine-oriented summaries as ordinary prose.
- Use one named, tested helper for a count shown in multiple locations; the denominator must match the user's mental model, not merely the domain catalog.

## Anti-Patterns From This Repository

| Anti-pattern | Typical symptom | Corrective rule |
|---|---|---|
| Diagnostic string used as headline and supporting text | Narrow Chinese text stacks vertically; settings page becomes a debug console | Render one readable summary; keep raw/redacted details behind copy or Advanced |
| `reason ?: description` for a row | Users see an error reason but not what the feature does | Always render description plus a localized status/reason tag |
| Default checkbox on every row | Empty boxes dominate the page and compete with switches | Reveal selection controls only in explicit multi-select mode |
| Plain `Column` for an unbounded picker | Templates or keyboard make the dialog overflow | Use a scrollable list and test small width plus IME |
| Main list filtered but favorites/derived list unfiltered | Two parts of the screen disagree about the same filter | Apply the same predicate to every projection; preserve full persistence order |
| Delete as a permanent inline icon | Accidental deletion competes with card navigation | Use a secondary/overflow action and confirm the exact ID/name and impact |
| Domain count exposed directly | Numbers such as `4/12` are technically valid but confusing | Define and test a user-facing count helper |
| UI-only compile as visual validation | Layout, theme, keyboard, and gesture regressions reach users | Record device/screenshot limitations honestly and cover the matrix below |
| Only the app bar collapses in landscape | Tabs and secondary controls still consume most of a short viewport | Put low-frequency controls in the content scroller or shared collapsing header; measure the editor with IME visible |
| Page-local orientation or immersive mode is not restored | Later routes stay rotated or lose system bars | Snapshot Activity/window state, use actual configuration as truth, and restore on portrait transition plus disposal |
| Reply draft and ASR both write the composer | Recording or late model chunks overwrite user text | Make asynchronous composer producers mutually exclusive and keep cancellation generation-guarded |
| Non-empty conversation treated as a valid reply target | Draft action appears after a user-only turn or during a streaming assistant reply | Require a completed latest assistant turn for actions that semantically reply to the assistant |

## Verification Matrix

For a UI change, record applicable evidence for:

| Dimension | Minimum cases |
|---|---|
| Content | empty, normal, long, many, duplicate, localized |
| State | loading, success, failure, cancelled, disabled, stale |
| Form factor | narrow phone, landscape, IME visible |
| Theme/accessibility | light, dark, large text, TalkBack, non-empty content descriptions |
| Interaction | scroll, tap, long-press, multi-select, back/cancel, destructive confirmation |

Preferred verification order: focused helper/unit tests, Compose/UI tests when available, compile/resource checks, then device installation and visual/gesture inspection. Do not report installation or visual validation as passed when the device was unavailable.

## Good / Base / Bad Cases

- **Good:** A template picker uses a scrollable sheet, shows name/description/scope/status, opens an existing document when already added, and confirms deletion by template ID.
- **Base:** A short list works in a dialog but has an explicit maximum height and remains usable with keyboard and localization.
- **Bad:** A long plain `Column` overflows, duplicates historical entities by name, or silently creates a second document.

## Wrong vs Correct

### Wrong

```kotlin
LaunchedEffect(selectedModel, expandedGroups.toMap()) {
    listState.animateScrollToItem(selectedPosition)
}
Text(copySummary())
Checkbox(checked = selected, onCheckedChange = onSelect)
```

### Correct

```kotlin
val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
var navigationTargetId by remember { mutableStateOf<String?>(null) }

// Set navigationTargetId only from an explicit click or navigation handler.
LaunchedEffect(navigationTargetId) {
    val targetId = navigationTargetId ?: return@LaunchedEffect
    val targetIndex = visibleItems.indexOfFirst { it.id == targetId }
    if (targetIndex in visibleItems.indices) {
        listState.animateScrollToItem(targetIndex)
    }
    navigationTargetId = null
}
Text(readableSummary)
if (multiSelectMode) {
    Checkbox(checked = selected, onCheckedChange = onSelect)
}
```

## Related Specs and Evidence

- [App UI Localization](../app/ui-localization.md)
- [Kotlin Concurrency and Compose](./kotlin-concurrency-and-compose.md)
- [Memory Capabilities](../app/memory-capabilities.md)
- `.trellis/tasks/archive/2026-06/06-27-model-select-perf/`
- `.trellis/tasks/archive/2026-07/07-16-issue-140-ui-optimization/`
- `.trellis/tasks/archive/2026-07/07-20-assistant-tools-page-ux/`
- GitHub issues [#131](https://github.com/Arsucar/RikkaRs/issues/131), [#140](https://github.com/Arsucar/RikkaRs/issues/140), [#163](https://github.com/Arsucar/RikkaRs/issues/163)

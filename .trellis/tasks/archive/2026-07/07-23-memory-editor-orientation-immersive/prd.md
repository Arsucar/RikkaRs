# Memory editor orientation and immersive mode

## Goal

Make landscape memory-table editing reachable for users who keep Android auto-rotate disabled, and use the short
landscape viewport as a focused editing surface instead of spending it on page chrome.

## Confirmed Facts

- Scope is the `app` module memory-table document editor.
- The existing layout reacts to `LocalConfiguration.current.orientation`, but users cannot enter landscape when system
  auto-rotate is disabled.
- `RouteActivity` handles `orientation|screenSize` configuration changes, so a requested orientation normally recomposes
  the existing editor without recreating the Activity.
- The app is already edge-to-edge. It does not currently have a page-local requested-orientation or system-bar
  visibility pattern.
- The editor has one authoritative save path (`persistDraft`) and one authoritative back path (`requestBack`); new UI
  actions must reuse them.
- The orientation button must follow the actual configuration, not an optimistic local Boolean, because Android large
  screens, split-screen, or OEM window managers may reject fixed orientation requests.
- Any actual configuration other than `ORIENTATION_LANDSCAPE`, including `ORIENTATION_UNDEFINED`, uses the
  "switch to landscape" FAB state and target.

## Requirements

- Show a bottom-right orientation FAB on the memory-table document editor.
- Tapping the FAB in portrait requests fixed landscape; tapping it in landscape requests fixed portrait, independent of
  the system auto-rotate setting.
- Capture the Activity's prior requested orientation on entry and restore it when the editor leaves, including save,
  normal Back, and discard-confirmation exits.
- In actual landscape configuration, remove the editor TopAppBar and Table/JSON tab row from the layout rather than
  merely collapsing them.
- In landscape, replace the removed TopAppBar actions with two independent 48dp overlay icon buttons placed next to
  each other at the top left: Back first and Save second. They have transparent containers, no shared bar background,
  and reuse `requestBack` / `persistDraft`.
- In landscape, fully hide the status bar using a page-local immersive mode, allow transient reveal by swiping from the
  top edge, and restore its prior visibility when returning to portrait or leaving the editor.
- Do not hide the navigation bar unless the user explicitly expands scope.
- Preserve draft, selected mode, validation, scope controls, table/JSON editing, IME padding, and unsaved-change
  confirmation semantics across direction changes.
- Preserve the currently selected Table/JSON mode when switching orientation; orientation changes must not parse,
  normalize, or otherwise change editor mode or content.
- The FAB must have a localized accessibility label, stable size, sufficient contrast, and bottom/navigation/IME inset
  handling.
- Hide the orientation FAB while the IME is visible and restore it when the IME closes.
- The landscape top-left action cluster must avoid display cutouts and rounded safe areas. It is a transparent overlay
  above the editor and must not reserve a fixed-height strip or add an opaque background over content. A transiently
  revealed status bar may overlay the actions temporarily, but they must return unobscured when the transient bar hides.
- New UI strings must exist in the default and every configured Android locale.

## Acceptance Criteria

- [ ] With system auto-rotate disabled, tapping the FAB rotates a supported phone from portrait to landscape and back.
- [ ] The FAB icon/accessibility label reflects the actual current orientation and remains reachable with gesture or
  three-button navigation.
- [ ] Undefined/non-landscape configuration uses the landscape target label/icon and requests fixed landscape.
- [ ] Landscape shows no TopAppBar and no Table/JSON tabs; portrait restores both without changing the selected mode.
- [ ] Landscape fully hides only the status bar and permits transient swipe reveal; portrait and editor exit restore the
  pre-editor status-bar visibility.
- [ ] Leaving the editor restores the Activity's pre-editor requested-orientation value.
- [ ] Existing Back and unsaved-discard behavior remains correct in both orientations.
- [ ] Landscape Back and Save remain reachable as adjacent top-left icon buttons without filled containers or a
  TopAppBar background; disabled/error semantics match portrait and Save reuses `persistDraft`.
- [ ] On a rounded or display-cutout viewport, landscape Back/Save remain inside the top-left safe area while editor
  content keeps its normal landscape top padding and renders beneath the transparent overlay without a dedicated action
  band; transient status-bar reveal may overlay the actions only for the system-controlled transient interval.
- [ ] Normal, empty-schema, validation-error, conversation-scope, long-table, and long-JSON states remain scrollable.
- [ ] A focused cell/JSON field stays reachable with the IME visible; the FAB does not cover the focused editing target.
- [ ] With long content, focusing the last table row and the bottom JSON field keeps the caret/input line fully visible
  after the IME opens and still permits vertical scrolling.
- [ ] The orientation FAB is absent while the IME is visible and returns after the IME closes without shifting editor
  state.
- [ ] Light/dark theme, large text, TalkBack labels, portrait, 360dp/480dp-high landscape, and orientation round trips
  are verified or explicitly reported as unavailable.
- [ ] Focused resource checks, Kotlin compilation, relevant tests, Debug assembly, and device install fallback complete
  under the repository's `--no-daemon` rules.

## Out of Scope

- Changing global app orientation policy or enabling Android auto-rotate.
- Hiding the navigation bar unless separately approved.
- Reworking memory-table persistence, validation, or DataTable behavior.
- Guaranteeing fixed orientation in Android large-screen or multi-window modes that ignore app orientation requests.

## Decisions

- Landscape keeps Back and Save as adjacent top-left 48dp overlay icon buttons with transparent containers and no
  shared bar background or reserved layout band.
- The orientation FAB remains a separate bottom-right action.
- Landscape fully hides the status bar with transient swipe reveal; the navigation bar remains visible.
- Orientation changes preserve the current Table/JSON mode.
- The orientation FAB hides while the IME is visible.
- Undefined and other non-landscape configurations use the landscape target state.

## Open Questions

- None.

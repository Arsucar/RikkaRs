# Design: Memory editor orientation and immersive mode

## Scope and Boundaries

Implement the feature inside `AssistantMemoryTableDocumentEditorPage.kt` plus localized string resources, focused tests,
and the shared UI contract update. `RouteActivity`, the Manifest, repositories, ViewModels, and persistence contracts
remain unchanged.

## Orientation Contract

- Obtain the host Activity from the current Compose context using the repository's Activity-unwrapping helper (or
  `LocalActivity` if the implementation can keep null handling explicit).
- Capture the Activity's entering `requestedOrientation` exactly once for that Activity instance.
- The FAB requests fixed landscape when actual configuration is portrait and fixed portrait when it is landscape.
- Every actual configuration other than `ORIENTATION_LANDSCAPE`, including undefined, targets fixed landscape.
- Do not update a local orientation Boolean. `LocalConfiguration.current.orientation` controls layout, icon, label,
  system-bar visibility, and the next requested orientation.
- On page disposal, restore the captured requested orientation after restoring window UI state.
- If Activity lookup fails, do not show the FAB or mutate Activity/window state; the editor remains otherwise usable.
- Show the FAB as soon as Activity lookup succeeds. Root insets may be null during the first composition, so a
  lifecycle-aware effect retries after frames until a non-null snapshot is available; the snapshot is captured once and
  is never replaced by later orientation insets.

## System Bar Contract

- Capture entering status-bar visibility and `WindowInsetsControllerCompat.systemBarsBehavior`.
- Mutate system bars only after a non-null root-window-insets snapshot. Never substitute an assumed visibility value;
  restore only successfully captured fields. A null first-frame snapshot must not permanently suppress later handling.
- In actual landscape, set transient-swipe behavior and hide `statusBars()` only.
- In portrait, restore the entering status-bar visibility and controller behavior.
- On page disposal, restore both values again as a final guard. Theme icon appearance remains owned by `RikkahubTheme`.
- Expose one idempotent restore callback. Call it immediately before no-change Back navigation and confirmed discard
  navigation. For Save, the existing `persistDraft` `onSuccess` branch after schema/payload validation and
  serialization is the success point: restore immediately before its existing `onSave(saved)` callback. Validation
  failure does not restore or navigate; the underlying ViewModel write/pop contract remains unchanged and disposal is
  the final fallback for route transitions.

## Compose Layout

- Portrait keeps the existing TopAppBar and full-width Table/JSON tab row.
- Landscape renders neither TopAppBar nor tabs and removes the obsolete collapsing-app-bar/nested-scroll path.
- Landscape content is hosted in a `Box`:
  - Back and Save are transparent-container 48dp `IconButton`s in one wrap-content top-left `Row`, with Back first and
    Save second. Save calls `persistDraft()` and follows the portrait enabled state.
  - Apply display-cutout top/horizontal insets plus a small top/start offset to the action row. Compose the row after
    editor content as a transparent overlay that contributes no padding or height to the content layout.
  - Transient status-bar reveal is allowed to overlay top actions temporarily; actions return unobscured when it hides.
  - Keep a small fixed gap between the two buttons. Do not use `SpaceBetween`, filled button containers, a shared bar
    background, or a reserved action band; landscape content retains its normal compact top padding and scrolls under
    the overlay.
- `Scaffold.floatingActionButton` owns a stable bottom-right orientation FAB. It is hidden while IME bottom inset is
  non-zero and returns when the IME closes. Placement must respect the visible navigation bar without double padding.
- Scope controls, validation errors, table/JSON state, scroll behavior, dialogs, BackHandler, and persistence callbacks
  retain their current owners.
- Device validation must focus the last table row and bottom JSON field with the IME open. If the caret/input line is
  not fully visible, add focused bring-into-view handling rather than treating `imePadding()` as sufficient.

## Icons and Accessibility

- Use HugeIcons discovered through the repository icon lookup skill; prefer a phone/orientation symbol that clearly
  communicates the target direction. Do not draw a custom SVG.
- Add separate localized labels for "switch to landscape" and "switch to portrait". Back and Save reuse existing
  localized resources. Every icon action has a non-null content description.

## Test Strategy

- Extract a small internal pure helper for actual configuration -> target `ActivityInfo` orientation and cover portrait,
  landscape, and undefined configuration with JVM tests; undefined targets fixed landscape.
- Resource coverage validates every configured locale and default/Simplified Chinese text quality.
- Kotlin/resource compilation checks API/import correctness. One lint run covers Activity/window API usage.
- Device acceptance is authoritative for fixed orientation, insets, transient system bars, IME visibility, draft
  preservation, and route-exit restoration.

## Rollback and Compatibility

- The implementation is page-local and can be rolled back by reverting the editor/resource/test changes.
- Unsupported large-screen orientation requests degrade to no configuration change; UI continues to reflect actual
  state.
- No schema, storage, navigation route, backup, or network compatibility changes.

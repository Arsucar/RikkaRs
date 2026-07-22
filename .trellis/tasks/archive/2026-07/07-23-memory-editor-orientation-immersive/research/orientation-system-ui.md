# Orientation and System UI Research

## Repository Facts

- `RouteActivity` is the host `ComponentActivity` and calls `enableEdgeToEdge()` before `super.onCreate()`.
- `RouteActivity` declares `configChanges="keyboardHidden|orientation|screenSize"`, so supported phone orientation
  changes normally recompose the same Activity instead of recreating it.
- The editor already uses `LocalConfiguration.current.orientation`; actual configuration is the correct source for UI
  mode and FAB semantics.
- The global theme changes status/navigation icon appearance but does not hide or show system bars.
- `Context.getActivity()` already unwraps `ContextWrapper`, and the project also has `LocalActivity` usage.
- No existing page owns `requestedOrientation` or `WindowInsetsControllerCompat.hide/show`; this task introduces the
  first page-local contract and must restore every mutated Activity/window value.

## Recommended APIs

- Request `ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE` from portrait and
  `ActivityInfo.SCREEN_ORIENTATION_PORTRAIT` from landscape. Fixed values work even when system auto-rotate is off.
- Read layout state from `LocalConfiguration`, not a local optimistic toggle.
- Treat every configuration other than `ORIENTATION_LANDSCAPE` as the landscape request target; this provides a
  deterministic fallback for `ORIENTATION_UNDEFINED`.
- Use `WindowCompat.getInsetsController(window, view)` and hide only
  `WindowInsetsCompat.Type.statusBars()` in actual landscape.
- Set `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` for transient top-edge reveal.
- Snapshot the entering Activity requested orientation, status-bar visibility, and controller behavior. Restore all
  three from a page-level `DisposableEffect`; synchronize hide/show from actual `isLandscape` while composed.
- If the Activity or root-window-insets snapshot is unavailable, keep the FAB absent and do not mutate orientation or
  system UI. Restore only values that were successfully captured.

## Platform Boundaries

- Android large screens, foldables, split-screen, freeform windows, and OEM window managers may ignore fixed
  orientation requests. The FAB must continue to represent the actual configuration.
- The navigation bar remains visible. Do not use `WindowInsetsCompat.Type.systemBars()`.
- Process death does not guarantee `onDispose`; a new process starts from the Manifest orientation, while normal route
  exits must still restore eagerly.
- The editor's remembered draft is expected to survive because the Activity consumes configuration changes. Device
  testing must verify this; persistence semantics are not changed as a fallback.

## Verification Targets

- Auto-rotate disabled phone: portrait -> landscape -> portrait.
- Table and JSON modes independently retain drafts and selected mode across both round trips.
- Landscape status bar hidden with transient reveal; navigation bar visible; portrait/exit restoration correct.
- Back, Save, discard confirmation, validation errors, long content, 360dp/480dp height, IME, dark/light, large text,
  and TalkBack descriptions.

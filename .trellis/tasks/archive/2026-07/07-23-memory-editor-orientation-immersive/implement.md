# Implementation Plan

1. Load implementation specs/research and use `find-hugeicons` to select target-orientation and Save icons.
2. Use `locale-tui-localization` to add orientation FAB accessibility labels to every configured locale; verify default
   and Simplified Chinese wording and key coverage.
3. Add a focused pure orientation-target helper and JVM tests for portrait, landscape, and undefined configuration.
4. In `MemoryTableDocumentEditorScaffold`, add page-local Activity/window snapshots, actual-orientation requests,
   landscape status-bar hide/transient behavior, explicit pre-navigation restoration, and disposal fallback. Show the
   FAB when Activity lookup succeeds; retry root-insets capture after first composition until available, capture once,
   and do not mutate system UI before capture. A missing Activity produces no FAB or Activity/window mutation.
5. Refactor editor chrome:
   - portrait keeps TopAppBar and Table/JSON tabs;
   - landscape removes both and overlays transparent Back/Save actions next to each other at the safe top left without
     reserving a fixed content band;
   - remove obsolete landscape app-bar scroll behavior;
   - add the bottom-right FAB and hide it while IME is visible.
6. Audit Table/JSON mode retention, errors, scope controls, dialogs, scrolling, IME, theme, TalkBack, and action enabled
   states against the PRD matrix. Restore window state immediately before the existing successful `persistDraft`
   `onSave(saved)` callback, before no-change Back navigation, and before confirmed discard navigation; validation
   failure must not restore or navigate. Focus the final table row and bottom JSON field with IME; add bring-into-view
   handling if the caret/input line is not fully visible. Update the shared UI guide contract in the same change.
7. Run focused checks with `--no-daemon`: resource processing, JVM tests, Kotlin compile, AndroidTest source compile,
   Debug assembly, and one `:app:lintDebug` audit. Do not run connected Android tests by default.
8. Follow the device flow: connect/install when available; otherwise discover the generated Debug APK from output
   metadata (currently an arm64 split), upload that actual artifact with SHA-256, and report unverified cases honestly.
9. Run an independent static review, final `trellis-check`, spec-sync review, commit the work, archive the task, record
   the session journal, and push only to `origin/release/rikka-arsucar` after the normal confirmation gate.

## Risk and Rollback Points

- Before window effects: confirm captured values are keyed to the Activity and every exit hits disposal restoration.
- Before UI refactor: preserve `persistDraft`, `requestBack`, `selectedTab`, and existing editor state ownership.
- Before delivery: verify no status-bar/orientation state leaks to another route; if device evidence is unavailable, do
  not mark those acceptance criteria passed.

## Device Evidence Format

Record each applicable PRD matrix item as `pass`, `fail`, or `unavailable`, plus device/API, navigation mode,
orientation, viewport height, IME state, and selected Table/JSON mode. Observe the destination immediately after Save,
Back, and confirmed discard; if route transitions retain the old composition and leak window state, move restoration
earlier while retaining the disposal fallback.

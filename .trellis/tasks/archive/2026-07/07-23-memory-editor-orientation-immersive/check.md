# Check Evidence

Date: 2026-07-23

## Automated Checks

- `:app:processDebugResources`: pass.
- `:app:testDebugUnitTest`: pass for the complete Debug JVM test suite.
- `:app:compileDebugKotlin`: pass.
- `:app:compileDebugAndroidTestKotlin`: pass.
- `:app:assembleDebug`: pass.
- Focused `AssistantMemoryTableDocumentEditorOrientationTest`: pass after the final test edit.
- Post-feedback incremental `:app:testDebugUnitTest --tests AssistantMemoryTableDocumentEditorOrientationTest`,
  `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug`: pass.
- `git diff --check`: pass.
- `:app:lintDebug`: fail on the repository's existing lint backlog: 180 errors, 68 warnings, and 1 hint.
  The first failure is `ChatInput.kt:603` (`LocalContextGetResourceValueCall`), outside this task. The lint text report
  contains no finding for the changed editor, orientation test, or new resource keys. Lint was run once as planned and
  was not rerun after confirming the baseline-only failure.

## Static Review

- Activity orientation and status-bar state are captured per Activity, system UI is restored before orientation, and
  Save/no-change Back/confirmed discard restore before navigation.
- Landscape omits the TopAppBar and Table/JSON tabs. Back/Save are transparent 48dp actions in an adjacent top-left
  overlay that reserves no content height and applies top/horizontal display-cutout insets.
- The Scaffold uses navigation-bar insets for both content and FAB placement; the FAB is omitted while the IME is
  visible and all icon actions have non-null localized descriptions.
- The selected mode remains owned by `remember(document.id)` and is not keyed to orientation.

## Device Matrix

Device `100.99.129.110:5555` remained `offline`; one `adb connect` attempt timed out. API level, navigation mode,
viewport height, theme, font scale, and TalkBack state are therefore unknown.

The user supplied `.mindfs/upload/2026-07-23/Screenshot_2026-07-23-02-28-36-96_1b62e04641382742443ece72a081e331.jpg`
from the first APK. It failed the landscape chrome check: filled action backgrounds obscured the first content text,
`SpaceBetween` placed Save in the rounded top-right area, and the dedicated action-height padding behaved like another
fixed tab row. The follow-up implementation removes the filled containers and reserved band, and places both actions in
an adjacent transparent top-left overlay. A post-fix screenshot is still pending.

- Auto-rotate-off portrait/landscape round trip: unavailable.
- Table/JSON draft and selected-mode retention across round trips: unavailable.
- Landscape status-bar hide, transient swipe reveal, and visible navigation bar: unavailable.
- Save, Back, and confirmed-discard destination state restoration: unavailable.
- Display-cutout/rounded-corner actions and 360dp/480dp-high landscape layout: first APK failed; post-fix unavailable.
- Last table row and bottom JSON field visibility with IME: unavailable.
- Light/dark theme, large text, TalkBack, and gesture/three-button navigation: unavailable.

## Debug APK

- Artifact: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- ABI: `arm64-v8a`
- Application ID: `me.arsucar.rikka.debug`
- Version: `2.3.35` (`197`)
- SHA-256: `0FFC710A50997D3BE62DD4C3B06528789F557560C5D7949B9A381FC300778CC1`
- Download: https://gofile.io/d/3pCdat

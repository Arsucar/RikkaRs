# Fast Bugfix Batch

## Goal

Resolve the smallest, clearest bugs first to reduce user-visible friction and
clear low-risk backlog before larger feature work.

## Requirements

- Fix #101: compressed/hidden chat messages must not cause scroll-to-bottom to
  land near hidden items after UI/window switches.
- Fix #72: preset selection in extension management must behave as single
  selection where the issue expects one active preset.
- Fix #71: shorten the model/provider search prompt text to avoid wrapping.
- Fix #76: include subagent token usage in the tool slim payload so existing UI
  can display it.
- Fix #78: preset edit BottomSheet should open at a usable height and avoid
  partial-expanded bounce behavior.
- Preserve existing uncommitted changes in overlapping preset files.

## Acceptance Criteria

- [x] #101 scroll targets use the full rendered list or another correct item
      index, not visible-message count where hidden nodes remain in the list.
- [x] #72 extension-manager preset toggles replace the selected preset instead
      of accumulating multiple active presets.
- [x] #71 prompt text is shortened to the requested wording.
- [x] #76 `usage` is serialized into the subagent slim payload and parsed by
      existing UI.
- [x] #78 preset edit sheet uses the same stable expansion behavior as the
      working sheets or otherwise disables partial expansion.
- [x] Focused validation passes or failures are reported.

## Validation

- `.\gradlew --no-daemon :app:compileDebugKotlin` passed.
- `git diff --check` passed.
- `adb devices` found no connected device, so install validation was skipped.

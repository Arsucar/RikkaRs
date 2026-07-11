# Chat And Extension UX Batch

## Goal

Improve common chat and extension-management workflows without changing core
data contracts.

## Requirements

- Implement #70: clicking an assistant avatar in the message stream should open
  that assistant's detail/configuration page.
- Implement #75: in the extension selector dialog, clicking outside the Switch
  area on an extension row should navigate to that extension's management page,
  while clicking the Switch still toggles enablement.
- Implement #80: add a collapsed/expanded display-density toggle for share
  multi-select mode. The toggle affects only selection UI density, not exported
  content.

## Acceptance Criteria

- [x] Assistant avatar click navigates to `AssistantDetail` for the current
      assistant where the assistant id is available.
- [x] Extension row click and Switch click are distinct and do not double-toggle
      or double-navigate.
- [x] Share multi-select collapsed mode truncates message display and hides
      tool/reasoning blocks only in the selection view.
- [x] Export/share output remains based on selected messages, not display
      truncation.

## Validation

- `.\gradlew --no-daemon :app:compileDebugKotlin` passed.
- `git diff --check` passed.
- No connected adb device is available, so install validation remains skipped.

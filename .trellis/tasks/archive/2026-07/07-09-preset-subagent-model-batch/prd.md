# Preset And Subagent Model Batch

## Goal

Make presets and subagents more reusable and observable, while keeping existing
assistant behavior compatible.

## Requirements

- Implement #73: introduce a default/full preset path and isolate quick
  injections so preset editing does not present unrelated global entries.
- Implement #74: allow subagent profiles to reference existing presets and
  include their mode injections in subagent prompt construction.
- Implement #79: show the actual context passed to subagents in the existing
  subagent tool details modal, preferably as a collapsible section.
- Preserve existing preset-related working-tree changes until reviewed.

## Acceptance Criteria

- [x] Existing users retain access to current quick injections through a
      default/full preset or equivalent migration-compatible behavior.
- [x] Preset editing no longer encourages accidental cross-preset injection
      selection.
- [x] Subagent profile data can store selected preset ids with compatible
      defaults.
- [x] Subagent prompt construction includes selected preset injections in a
      documented order.
- [x] Tool details UI exposes transmitted context without inventing fields that
      were not sent.

## Validation

- `.\gradlew --no-daemon :app:compileDebugKotlin` passed.
- `git diff --check` passed.
- No connected adb device is available, so install validation remains skipped.

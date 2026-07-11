# Implementation Plan

- [x] Normalize settings with a default preset for existing mode injections.
- [x] Limit `PresetEditSheet` entries to the current preset's bound injection
      ids; keep new-entry creation bound to the preset.
- [x] Add `presetIds` to `SubagentProfile`.
- [x] Add subagent profile preset selection UI.
- [x] Pass profile preset ids into `SubagentHost.buildChildAssistant`.
- [x] Add transmitted-context metadata for `spawn_subagent`.
- [x] Render transmitted-context section in `SpawnSubagentToolUI.Preview`.
- [x] Run `.\gradlew --no-daemon :app:compileDebugKotlin`.
- [x] Run `git diff --check`.

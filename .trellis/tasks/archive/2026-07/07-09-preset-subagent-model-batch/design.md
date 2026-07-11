# Design

## Scope

This child task covers #73, #74, and #79.

## Decisions

- Keep `PromptInjection.ModeInjection` as the global storage pool for now.
- Use `Preset.modeInjectionIds` as the ownership boundary shown by the preset
  editor. A preset editor shows only entries already bound to that preset; new
  entries created from the editor are bound to that preset.
- Add a stable default preset to settings normalization when presets are absent
  from older data. It points at the current mode-injection set so existing users
  retain the old "all quick injections together" behavior.
- Add `presetIds` to `SubagentProfile`. Build child assistants with those ids so
  the existing `PromptInjectionTransformer` expands preset injections without a
  second prompt-injection implementation.
- Record subagent call context in tool metadata and display it in the existing
  spawn-subagent details sheet.

## Compatibility

- `SubagentProfile.presetIds` defaults to empty for old serialized profiles.
- The default preset uses a stable id and does not change existing injection ids.
- Existing assistant `presetIds` and `modeInjectionIds` remain valid.

## Risks

- Preset isolation is UI-scoped in this iteration; the underlying global pool
  remains unchanged.
- The default preset should avoid repeatedly mutating user-managed presets.
- Context display must show only actual transmitted fields, not inferred data.

# PRD: Fix subagent enable guard

## Problem
`assistantHasSpawnableProfile()` in `SubagentUiHelpers.kt:53` requires at least one profile with `canSpawn=true` before allowing the user to toggle "enable subagents" ON. This is a bug: `canSpawn` only controls whether a subagent can *nest further* (spawn its own children), not whether it can be *called by the parent*. As a result, if only `explore` / `reviewer` profiles are available (both have `canSpawn=false`), the user cannot enable subagents at all — even though those profiles are perfectly valid for single-depth delegation.

## Requirements
1. Change the enable-guard to check "at least one non-disabled profile exists" instead of "at least one profile with `canSpawn=true`".
2. The blocked dialog (`subagent_enable_blocked_title` / `subagent_enable_blocked_desc`) should still show when truly no profiles are available (all disabled / empty).
3. `canSpawn` semantics must remain unchanged — it still controls nesting depth in `SubagentHost` and `SubagentPermissionBuilder`.
4. Existing unit tests in `SubagentPermissionTest` that assert `spawn_subagent` is removed when `canSpawn=false` must remain passing.

## Acceptance Criteria
- [ ] Enable switch is ON-able when at least one profile (any `canSpawn` value) is available and not disabled.
- [ ] Blocked dialog appears only when zero profiles are available (all globals disabled + no custom profiles).
- [ ] `canSpawn` field on `SubagentProfile` is NOT changed.
- [ ] `SubagentHost.buildChildAssistant` nesting logic (`childCanSpawn = profile.canSpawn && …`) is NOT changed.
- [ ] `SubagentPermissionBuilder` spawn-tool filtering logic is NOT changed.
- [ ] Existing tests pass.

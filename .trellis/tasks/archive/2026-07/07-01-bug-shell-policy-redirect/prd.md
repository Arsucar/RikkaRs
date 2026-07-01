# PRD: Fix workspace_shell false-positive on stderr redirect

## Issue

- #16: `workspace_shell` rejects commands like `echo test 2>/dev/null` or `grep -r TODO . 2>/dev/null | head -3`

## Problem

### Historical bug (now fixed in current tree)

Original implementation used substring match `Regex(">/dev/")` which blocked **any** command containing `>/dev/`, including safe `2>/dev/null` redirects.

### Current state

Current `WorkspaceShellPolicy.kt` line 74 uses a whitelist regex with negative lookahead:

```kotlin
Regex("""[0-9]*>\s*/dev/(?!null|zero|urandom|random)""")
```

This correctly:
- **Allows** redirects to `/dev/null`, `/dev/zero`, `/dev/urandom`, `/dev/random` (including fd prefixes like `2>`, `1>`)
- **Blocks** redirects to other devices like `/dev/sda`, `/dev/mmcblk0`

Tests `allowsSafeDeviceRedirection` and `rejectsUnsafeDeviceRedirection` cover the #16 scenarios.

### PRD scope

This is a **verification/regression** task. The fix appears already landed on the current branch. PRD scope:

1. Verify all #16 scenarios pass existing tests
2. Close issue #16 as fixed
3. (Optional) add edge-case tests if any gaps found

## Acceptance Criteria

- All scenarios from issue #16 pass:
  - `echo test 2>/dev/null` → Allowed
  - `grep -r TODO . 2>/dev/null | head -3` → Allowed
  - `dd if=/dev/zero of=/dev/sda` → Rejected
  - `echo data > /dev/mmcblk0` → Rejected
- Unit tests cover safe vs unsafe device redirection (already present)
- Issue #16 can be closed

## Constraints

- No code changes expected — pure verification task
- If code is already fixed, just close the issue
- If any edge case is unhandled (e.g. `>/dev/null 2>&1` pattern), add test and fix

## Related Code

| File | Lines | Key |
|------|-------|-----|
| `WorkspaceShellPolicy.kt` | 74 | `/dev/` redirect rule with negative lookahead |
| `WorkspaceShellPolicyTest.kt` | `allowsSafeDeviceRedirection`, `rejectsUnsafeDeviceRedirection` | Existing tests |
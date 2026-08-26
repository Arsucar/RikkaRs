# PRD: Issue #301 — Chat page drawer mutual exclusion

## Problem
Left and right drawers in ChatPage could open simultaneously via scrim gap.

## Status: Already Fixed
Commit `fdbb083f` (fix(audit): batch1/2 CRITICAL+HIGH) already implemented three layers of mitigation:

1. **Mutual exclusion** (`ChatPage.kt:202-212`): `LaunchedEffect` on each drawer's `targetValue` closes the other when one opens.
2. **Left drawer `gesturesEnabled`** (`ChatPage.kt:529`): `!rightDrawerState.isActive` disables left drawer edge-swipe when right is open/animating.
3. **Outer gesture layer `drawersClosed`** (`ChatPage.kt:304`): Right-drawer custom open gesture only fires when both drawers are inactive.

## Acceptance Criteria
- [ ] AC1: Verify code contains all three mitigation layers (confirmed via research)
- [ ] AC2: Close issue #301 with verification comment

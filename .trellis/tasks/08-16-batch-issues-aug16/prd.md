# PRD: Batch process open issues #301 #302 #304

## Overview
Batch parent task for processing 3 open GitHub issues.

## Children
- **#304** (bug, P0): Assistant prompt page renders blank — regression from #298 WhileSubscribed(5000)
- **#301** (bug, P1): Chat page left/right drawer can open simultaneously via scrim gap
- **#302** (enhancement, P2): Tavern character card bindings import with confirmation dialog

## Status Summary
- #304: Needs fix — `AssistantPromptPage.kt:161` missing inbound sync guard (counterpart at `AssistantSubagentProfilePage.kt:346` already has it)
- #301: Already fixed in commit `fdbb083f` (three layers: mutex LaunchedEffect + gesturesEnabled + drawersClosed gate). Verify and close.
- #302: Complex enhancement — needs design + implement

## Acceptance Criteria
- [ ] #304: Prompt page renders saved system prompt on first entry and after page navigation
- [ ] #301: Confirm fix is complete, close issue with verification
- [ ] #302: Import flow detects and optionally imports world_info/presets with user confirmation

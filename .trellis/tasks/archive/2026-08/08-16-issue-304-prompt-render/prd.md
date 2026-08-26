# PRD: Issue #304 — Fix assistant prompt page render regression

## Problem
After #298 changed `SharingStarted.Eagerly` → `WhileSubscribed(5000)` in `AssistantDetailVM`, the `assistant` StateFlow starts cold with `initialValue = Assistant()` (empty). `AssistantPromptPage.kt:161` uses `rememberTextFieldState(initialText = assistant.systemPrompt)` which captures the empty default on first composition and never syncs when real data arrives.

## Root Cause
- `AssistantDetailVM.kt:117`: `SharingStarted.WhileSubscribed(5000)` + `initialValue = Assistant()`
- `AssistantPromptPage.kt:161`: `rememberTextFieldState(initialText = assistant.systemPrompt)` — one-time init, no inbound sync
- `AssistantSubagentProfilePage.kt:346` has the same pattern BUT includes a sync guard at line 352: `if (promptState.text.isEmpty() && resolved.systemPrompt.isNotEmpty()) { LaunchedEffect(Unit) { promptState.edit { ... } } }`

## Fix
Add the same inbound sync guard to `AssistantPromptPage.kt` that `AssistantSubagentProfilePage.kt` already uses.

## Acceptance Criteria
- [ ] AC1: Entering the prompt page immediately renders the saved system prompt
- [ ] AC2: Navigating away and returning preserves the prompt content (no blank)
- [ ] AC3: Template preview area also renders correctly
- [ ] AC4: Typing in the field still persists changes (outbound sync unaffected)

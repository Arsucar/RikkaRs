# Research: Is the inbound sync guard already present on AssistantPromptPage?

- **Query**: Is the inbound sync guard already present on AssistantPromptPage, and what exact code change is still needed? (issue #304 blank render after #298 WhileSubscribed)
- **Scope**: internal
- **Date**: 2026-08-26

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantPromptPage.kt` | Prompt page. Inbound empty→loaded guard is present at 172–176 (added in `0a09549f`). Outbound still `LaunchedEffect(Unit)` capturing first-composition `assistant` at 177–185. |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` | Intended pattern: inbound empty-guard 352–356, profile-change resync 348–350, outbound via `persist` + `rememberUpdatedState` 357–361 / 157. |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt` | `assistant` StateFlow: `SharingStarted.WhileSubscribed(5000)`, `initialValue = Assistant()` at 112–118. `settings` uses `Settings.dummy()` at 102–103. |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` | `writeAssistantConfig` 1434–1449 no-ops when `assistant.id` is not in the stored list. Dummy `Assistant()` uses a random UUID, so those writes are dropped. |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceFileEditorPage.kt` | Only other production `rememberTextFieldState` (line 61). No `initialText = assistant.*`; loads via `LaunchedEffect` + `setTextAndPlaceCursorAtEnd`. |
| `CHANGELOG.md` | v2.3.53 documents the #304 inbound-guard fix. |
| `.trellis/tasks/08-16-issue-304-prompt-render/research/prompt-page-blank-render.md` | Stale 2026-08-16 snapshot written from pre-fix source (same commit later added the guard). Do not treat it as current. |

### 1. AssistantPromptPage.kt — current inbound / outbound

`rememberTextFieldState` + inbound guard + outbound (lines 169–185):

```169:185:app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantPromptPage.kt
                val systemPromptValue = rememberTextFieldState(
                    initialText = assistant.systemPrompt,
                )
                if (systemPromptValue.text.isEmpty() && assistant.systemPrompt.isNotEmpty()) {
                    LaunchedEffect(Unit) {
                        systemPromptValue.edit { replace(0, length, assistant.systemPrompt) }
                    }
                }
                LaunchedEffect(Unit) {
                    snapshotFlow { systemPromptValue.text }.collect {
                        onUpdate(
                            assistant.copy(
                                systemPrompt = it.toString()
                            )
                        )
                    }
                }
```

Subscription (lines 114, 134–138):

```114:138:app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantPromptPage.kt
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    ...
        AssistantPromptContent(
            innerPadding = innerPadding,
            assistant = assistant,
            settings = settings,
            onUpdate = { vm.update(it) }
        )
```

`git blame` on 172–176: commit `0a09549fd7a47338f061507784a151a96be1ae4b` (Arsucar, 2026-08-16), message:

> fix(#304,#301): prompt page blank render + drawer mutex; feat(#302): tavern card world book import
>
> #304: Add inbound sync guard to AssistantPromptPage rememberTextFieldState,
> matching AssistantSubagentProfilePage pattern. WhileSubscribed(5000) cold
> start caused empty initial value to persist.

That commit is on `origin/release/rikka-arsucar` and is contained in tag `v2.3.53`. GitHub issue https://github.com/Arsucar/RikkaRs/issues/304 is still **open** (0 comments).

Template preview (`produceState` keyed on `assistant`, lines 340–359) is a controlled Compose value, not `rememberTextFieldState`. Once `assistant` leaves the empty `Assistant()` default, preview recomposes from `assistant.messageTemplate` (default `"{{ message }}"`).

### 2. AssistantSubagentProfilePage.kt — intended pattern (346–361)

```346:361:app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt
                        val promptState = rememberTextFieldState(initialText = resolved.systemPrompt)
                        // Sync when profile changes (user navigates to another subagent)
                        LaunchedEffect(profileName) {
                            promptState.edit { replace(0, length, resolved.systemPrompt) }
                        }
                        // Sync when async-loaded data arrives after composition
                        if (promptState.text.isEmpty() && resolved.systemPrompt.isNotEmpty()) {
                            LaunchedEffect(Unit) {
                                promptState.edit { replace(0, length, resolved.systemPrompt) }
                            }
                        }
                        LaunchedEffect(promptState) {
                            snapshotFlow { promptState.text.toString() }.collect { text ->
                                persist { it.copy(systemPrompt = text) }
                            }
                        }
```

`persist` reads `rememberUpdatedState(assistant)` at line 157, so outbound writes always copy the latest assistant rather than the first-composition snapshot.

`LaunchedEffect(profileName)` exists because this page can switch subagent profiles. `AssistantPromptPage` is a single-assistant route (`Screen.AssistantPrompt` / `RouteActivity.kt:398–399`) and has no profile-name key.

### 3. AssistantDetailVM.kt — SharingStarted / initialValue

```102:118:app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings.dummy())
    ...
    val assistant: StateFlow<Assistant> = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistants.find { it.id == assistantId } ?: Assistant()
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = Assistant()
        )
```

`Assistant()` default (`Assistant.kt:17–25`) has `systemPrompt = ""` and `id = Uuid.random()`. `Settings.dummy()` is `Settings(init = true)` with `assistants = DEFAULT_ASSISTANTS` (`PreferencesStore.kt:1728–1729`, `1674`, `2069–2094`). The `assistant` flow does **not** use `Settings.dummy().assistants`; it uses a fresh `Assistant()` until `settingsFlow` emits.

Cause of the original blank field: `rememberTextFieldState` only applies `initialText` on first composition. First composition sees `Assistant().systemPrompt == ""`. Real data arrives later; without the inbound `if`, the field stayed empty. That inbound `if` is now present.

### 4. Other `rememberTextFieldState(initialText = assistant.*)` sites

Production `rememberTextFieldState` usages:

| File | Line | `initialText` | Inbound sync |
|---|---|---|---|
| `AssistantPromptPage.kt` | 169 | `assistant.systemPrompt` | Yes — empty-field guard 172–176 |
| `AssistantSubagentProfilePage.kt` | 346 | `resolved.systemPrompt` | Yes — profile-name resync 348–350 + empty-field guard 352–356 |
| `WorkspaceFileEditorPage.kt` | 61 | none (default empty) | Loads file in `LaunchedEffect(id, area, path, …)` then `textState.setTextAndPlaceCursorAtEnd(content)` (66–78) |

No other `rememberTextFieldState(initialText = assistant.*)` call exists. Other prompt-page fields (`messageTemplate`, preset messages, regexes) use controlled `OutlinedTextField(value = …)` and recompose when `assistant` updates.

Archive copy at `.trellis/tasks/archive/2026-06/06-27-subagent-mvp/reference/.../AssistantSubagentProfilePage.kt:185` is not production code.

### 5. Git history

| Commit | Role |
|---|---|
| `3cdc6aaa` | #298: `SharingStarted.Eagerly` → `WhileSubscribed(5000)` (includes `AssistantDetailVM`) |
| `0a09549f` | #304 inbound guard added to `AssistantPromptPage.kt` (+5 lines). Also on `origin/release/rikka-arsucar`. |
| `38148bb6` | release v2.3.53 changelog for #304 / #301 / #302 |

No later commit on `AssistantPromptPage.kt` changes the guard. Working tree for that file is clean.

### Whether the reported blank-render bug still exists

**Inbound blank render (issue #304 AC1/AC2 as described): the inbound guard is already present. No further inbound patch is required for that symptom.**

Remaining delta versus the full SubagentProfile pattern (not the original blank-field report):

- Prompt page has **no** `rememberUpdatedState(assistant)` on outbound.
- Outbound is `LaunchedEffect(Unit)` closing over the first-composition `assistant` (dummy random UUID + empty prompt).
- `writeAssistantConfig` (`PreferencesStore.kt:1441–1443`) returns without writing when `assistant.id` is not in the stored list, so those dummy-id `onUpdate` calls are dropped.
- After the inbound `replace`, `snapshotFlow` emits the loaded prompt, still via the captured dummy `assistant` — also dropped.
- Subsequent typing in the same composition continues to call `onUpdate` with the dummy UUID, so those writes are also dropped until the composable is disposed and recomposed (navigate away/back) with a loaded `assistant`.

SubagentProfile outbound avoids that capture by `persist { it.copy(systemPrompt = text) }` on `latestAssistant.value` (line 157).

`LaunchedEffect(profileName)` from SubagentProfile is not applicable to `AssistantPromptPage` (single assistant id, no profile switch).

### Exact remaining patch (only if matching full SubagentProfile outbound, not for inbound blank)

Inbound block 172–176 already matches SubagentProfile 352–356. Do not add a second copy.

If outbound is aligned with SubagentProfile, the current 177–185 block is the remaining difference. Pattern used on SubagentProfile:

```kotlin
val latestAssistant = rememberUpdatedState(assistant)
LaunchedEffect(systemPromptValue) {
    snapshotFlow { systemPromptValue.text.toString() }.collect { text ->
        onUpdate(latestAssistant.value.copy(systemPrompt = text))
    }
}
```

That would replace `AssistantPromptPage.kt` 177–185. It also requires `import androidx.compose.runtime.rememberUpdatedState` (already imported on SubagentProfile, not on Prompt page).

Do **not** key the outbound effect on `assistant` / `assistant.id`: restarting it after the real assistant arrives while the field is still empty would emit `""` against the real id and `writeAssistantConfig` would persist an empty prompt.

### Related files that also need the same guard

None for `rememberTextFieldState(initialText = assistant.*)`. SubagentProfile already has the guard. Workspace file editor uses a different load path.

### Test files that exist or should be added

**Exist (assistant detail, none cover prompt TextFieldState):**

- `app/src/test/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantHooksPageTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantToolsPageTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryTableTrashPageTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryTableScopeTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryTableDocumentEditorOrientationTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantAutoCompressInputTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/datastore/AssistantConfigPersistenceTest.kt` — `writeAssistantConfig` id-match / no-op behavior, not UI sync

**Do not exist:**

- `AssistantPromptPageTest.kt` (unit or androidTest)
- Any `createComposeRule` / Compose UI test in this repo (`app/src/androidTest` has no Compose rule tests)

`AssistantPromptContent` is `private`, so a JVM test cannot call it without changing visibility or extracting the sync predicate.

A JVM-testable extraction of the inbound predicate already inlined at 172:

```kotlin
fun shouldSyncInboundSystemPrompt(fieldText: CharSequence, loadedPrompt: String): Boolean =
    fieldText.isEmpty() && loadedPrompt.isNotEmpty()
```

No such helper exists today.

## Caveats / Not Found

- No runtime reproduction this session. Conclusion is from current source + blame + `writeAssistantConfig` id check.
- GitHub issue #304 remains open; the inbound fix is in tree and in v2.3.53 changelog.
- Stale research file `prompt-page-blank-render.md` still says the guard is missing; that described HEAD before `0a09549f` was applied in the same commit.
- `Settings.dummy()` late-arrival does not feed `rememberTextFieldState` on this page.

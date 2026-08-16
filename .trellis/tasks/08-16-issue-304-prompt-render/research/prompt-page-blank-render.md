# Research: Issue #304 — Assistant prompt page renders blank after WhileSubscribed(5000) change

- **Query**: Code context for issue #304 (prompt page renders blank after #298's WhileSubscribed(5000) change)
- **Scope**: internal
- **Date**: 2026-08-16

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantPromptPage.kt` | Prompt page with `rememberTextFieldState` at line 161 — NO sync guard for late-arriving data |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` | Subagent profile page with `rememberTextFieldState` at line 346 — HAS sync guards |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt` | VM exposing `assistant` StateFlow with `WhileSubscribed(5000)` and `initialValue = Assistant()` |

### Commit 3cdc6aaa4 confirmation

```
commit 3cdc6aaa4cafa7c17b84dc300fff3e7b5e9d63fb
    fix: replace SharingStarted.Eagerly with WhileSubscribed (#291) (#298)
    22 StateFlow stateIn calls used Eagerly, keeping upstream Flows
    active even with no subscribers. Switch to WhileSubscribed(5000)
    to stop upstream 5s after the last subscriber leaves.
```

Stats: 8 files changed, 22 insertions(+), 22 deletions(-). `AssistantDetailVM.kt` had 11 of the 22 replacements (it has 11 `stateIn` calls). All `SharingStarted.Eagerly` → `SharingStarted.WhileSubscribed(5000)`.

### How `assistant` state flows from VM to Composable

**VM definition** (`AssistantDetailVM.kt:112-118`):

```kotlin
112:    val assistant: StateFlow<Assistant> = settingsStore
113:        .settingsFlow
114:        .map { settings ->
115:            settings.assistants.find { it.id == assistantId } ?: Assistant()
116:        }.stateIn(
117:            scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = Assistant()
118:        )
```

- `initialValue = Assistant()` → empty/default Assistant object.
- `WhileSubscribed(5000)` → upstream `settingsStore.settingsFlow` only starts collecting when the StateFlow has subscribers; stops 5s after the last subscriber leaves.
- On first composition the Composable subscribes via `collectAsStateWithLifecycle()`, but the first emitted value is `Assistant()` (empty). The real assistant data arrives asynchronously after `settingsFlow` emits.

**Composable subscription** (`AssistantPromptPage.kt:99-133`):

```kotlin
99:  @Composable
100: fun AssistantPromptPage(id: String) {
101:     val vm: AssistantDetailVM = koinViewModel(
102:         parameters = { parametersOf(id) }
103:     )
104:     val assistant by vm.assistant.collectAsStateWithLifecycle()
107:     val settings by vm.settings.collectAsStateWithLifecycle()
108:     val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
...
126:         AssistantPromptContent(
127:             innerPadding = innerPadding,
128:             assistant = assistant,
129:             settings = settings,
130:             onUpdate = { vm.update(it) }
131:         )
```

- Uses `collectAsStateWithLifecycle()` — lifecycle-aware; stops collecting when the host is `STOPPED`.
- `assistant` starts as `Assistant()` (empty default), real data arrives later.

### The blank-render root cause (AssistantPromptPage.kt:161-179)

```kotlin
154:        Card(
...
161:                val systemPromptValue = rememberTextFieldState(
162:                    initialText = assistant.systemPrompt,
163:                )
164:                LaunchedEffect(Unit) {
165:                    snapshotFlow { systemPromptValue.text }.collect {
166:                        onUpdate(
167:                            assistant.copy(
168:                                systemPrompt = it.toString()
169:                            )
170:                        )
171:                    }
172:                }
173:
174:                TextArea(
175:                    state = systemPromptValue,
...
```

- `rememberTextFieldState(initialText = assistant.systemPrompt)` captures `assistant.systemPrompt` **only on first composition**. On first composition `assistant == Assistant()` (empty default), so `initialText = ""`.
- When the real `assistant` data arrives later (StateFlow re-emits with the loaded assistant), the Composable recomposes — but `rememberTextFieldState` does NOT re-read `initialText`; the field stays empty → **blank render**.
- The `LaunchedEffect(Unit)` at line 164 only listens to `systemPromptValue.text` changes (outbound: text field → `onUpdate`); it does NOT push inbound `assistant.systemPrompt` updates into the text field.
- Before #298 (`Eagerly`), `settingsStore.settingsFlow` was always active and had likely already cached/emitted the real assistant value by the time the Composable subscribed, so `assistant.systemPrompt` was already populated on first composition. After `WhileSubscribed(5000)`, the upstream starts cold on subscribe → the first value the Composable sees is the `Assistant()` default → the bug surfaces.

### The working counterpart (AssistantSubagentProfilePage.kt:346-368)

```kotlin
346:                        val promptState = rememberTextFieldState(initialText = resolved.systemPrompt)
347:                        // Sync when profile changes (user navigates to another subagent)
348:                        LaunchedEffect(profileName) {
349:                            promptState.edit { replace(0, length, resolved.systemPrompt) }
350:                        }
351:                        // Sync when async-loaded data arrives after composition
352:                        if (promptState.text.isEmpty() && resolved.systemPrompt.isNotEmpty()) {
353:                            LaunchedEffect(Unit) {
354:                                promptState.edit { replace(0, length, resolved.systemPrompt) }
355:                            }
356:                        }
357:                        LaunchedEffect(promptState) {
358:                            snapshotFlow { promptState.text.toString() }.collect { text ->
359:                                persist { it.copy(systemPrompt = text) }
360:                            }
361:                        }
```

- This location uses the same `rememberTextFieldState(initialText = resolved.systemPrompt)` pattern, BUT it adds two sync guards:
  - `LaunchedEffect(profileName)` resyncs when the profile name changes (navigation).
  - `if (promptState.text.isEmpty() && resolved.systemPrompt.isNotEmpty())` resyncs when async-loaded data arrives after initial composition — this is the guard that `AssistantPromptPage.kt` is MISSING.
- `resolved` itself comes from `SubagentRegistry.resolveProfile(currentProfileName, assistant, globalProfiles)` (line 150), where `assistant` is collected from `vm.assistant.collectAsStateWithLifecycle()` (line 86) — same WhileSubscribed(5000) source, so the same late-arrival issue would apply without these guards.

### All `rememberTextFieldState` usages in assistant pages

Only 2 occurrences exist in `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/`:

1. `AssistantPromptPage.kt:161` — `systemPromptValue` — **no inbound sync guard (BUGGY)**
2. `AssistantSubagentProfilePage.kt:346` — `promptState` — **has inbound sync guards (OK)**

No other `rememberTextFieldState` calls exist in the assistant page tree. (Other text inputs in these pages use `OutlinedTextField(value=..., onValueChange=...)` which is a controlled value that recomposes naturally.)

### Subscribe pattern (all assistant pages)

All assistant pages use `collectAsStateWithLifecycle()` (lifecycle-aware). No `collectAsState()` (non-lifecycle) usage found in the assistant page tree. 70 `collectAsStateWithLifecycle` call sites across 15 files (includes imports). The relevant `vm.assistant.collectAsStateWithLifecycle()` sites:

- `AssistantPromptPage.kt:106`
- `AssistantSubagentProfilePage.kt:86`
- `AssistantBasicPage.kt:65`
- `AssistantDetailPage.kt:60`
- `AssistantExperimentsPage.kt:41`
- `AssistantExtensionsPage.kt:70`
- `AssistantHooksPage.kt:92` and `:287`
- `AssistantLocalToolPage.kt:48`
- `AssistantMcpPage.kt:33`
- `AssistantMemoryPage.kt:105`
- `AssistantRequestPage.kt:37`
- `AssistantSubagentPage.kt:65`
- `AssistantToolsPage.kt:261`

### Related Specs

- `.trellis/spec/app/dependency-injection.md` — DI rules referenced at `AssistantDetailVM.kt:86` (VM-scoped business object rule).

## Caveats / Not Found

- I did not reproduce the bug at runtime; the analysis is based on static code inspection + the StateFlow semantics of `WhileSubscribed(5000)` + `initialValue = Assistant()`. The timing argument (that the real data arrives after the empty default) is the standard behavior of a cold `stateIn` with a non-Eagerly start — confirmed by the `AssistantSubagentProfilePage.kt` having added an explicit "async-loaded data arrives after composition" sync guard at line 352, which only makes sense if that ordering happens in practice.
- `Settings.dummy()` is the `settings` StateFlow initial value (`AssistantDetailVM.kt:103`); same late-arrival pattern, but `AssistantPromptPage.kt` does not use `rememberTextFieldState` for settings-derived fields, so settings are not affected by this specific bug.
- Issue #304 text was not directly fetched; the task description states it is about the prompt page rendering blank after #298's WhileSubscribed(5000) change, which matches the code evidence above.

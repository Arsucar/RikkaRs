# Research: Subagent port — working-tree code facts (2026-06-29)

- **Query**: Verify research for `parallelToolExecution`, `subagentDelegateOnly`, `extraLocalTools`, nav restructure against current `release/rikka-arsucar` tree
- **Scope**: internal
- **Date**: 2026-06-29

## Summary vs prior research

| Predicted field / behavior | In working tree? |
|---|---|
| `Assistant.parallelToolExecution` | **No** (grep `app/`: zero matches) |
| `Assistant.subagentDelegateOnly` | **No** |
| `SubagentProfile.extraLocalTools` | **No** |
| `createSubagentTools(..., delegateOnly)` | **No** — params unchanged |
| `buildCommonTools` in `ChatService.kt` | **No** — root tools inline in `buildList` |
| `GenerationHandler` `executeSingleTool` + parallel `spawn_subagent` | **Yes** — condition is **narrow** (`size > 1 && subagentCount > 1`), not sub’s `parallelToolExecution \|\| subagentCount > 1` |
| Uncommitted subagent file edits | **Yes** — `SubagentHost.kt`, `SubagentProfile.kt`, `SubagentRegistry.kt`, `SubagentTools.kt` modified; diff is **`tool_call_count` / JSON `tool_calls`**, not the three port fields |

---

## 1) Assistant model

**File**: `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`

Subagent-related fields (L50–L54):

```kotlin
    val enableSubagents: Boolean = false,
    val subagentMaxDepth: Int = 2,
    val subagentProfiles: List<SubagentProfile> = emptyList(),
    val disabledBuiltinSubagents: Set<String> = emptySet(),
    val disabledGlobalSubagents: Set<String> = emptySet(),
```

- **`parallelToolExecution`**: not present on `Assistant`.
- **`subagentDelegateOnly`**: not present on `Assistant`.
- **Shape**: `@Serializable data class Assistant` with kotlinx.serialization; nested `SubagentProfile` is a separate `@Serializable data class` (import L10). No custom serializers on `Assistant` itself in this file; standard `@Serializable` + `@SerialName` on nested enums elsewhere.

Related defaults: `localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo)` (L39).

---

## 2) GenerationHandler — `runInParallel` / `executeSingleTool`

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`

Subagent parallel branch (L257–L270):

```kotlin
            val subagentCount = toolsToProcess.count { it.toolName == "spawn_subagent" }
            val runInParallel = toolsToProcess.size > 1 && subagentCount > 1
            val executedTools: List<UIMessagePart.Tool> = if (runInParallel) {
                Log.i(TAG, "generateText: executing ${toolsToProcess.size} tools in parallel (subagents=$subagentCount)")
                coroutineScope {
                    toolsToProcess.map { tool ->
                        async { executeSingleTool(tool, toolsInternal) }
                    }.awaitAll().filterNotNull()
                }
            } else {
                toolsToProcess.mapNotNull { tool ->
                    executeSingleTool(tool, toolsInternal)
                }
            }
```

- **`executeSingleTool`**: exists at L412: `private suspend fun executeSingleTool(tool: UIMessagePart.Tool, toolsInternal: List<Tool>): UIMessagePart.Tool?`.
- **Note**: Parallel path requires **more than one tool AND more than one `spawn_subagent`** in the batch. A single `spawn_subagent` plus other tools runs **serially**. No `assistant.parallelToolExecution` factor.

---

## 3) ChatService — root tool assembly

**File**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

Root `tools = buildList { ... }` (approx. L656–L717): **inline assembly**, no `buildCommonTools` helper in this module (only in archived reference task).

Order and pieces:

1. If `settings.enableWebSearch` → `createSearchTools(settings)`
2. `localTools.getTools(assistant.localTools)`
3. If `assistant.enableRecentChatsReference` → `createConversationTools(...)`
4. `createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), conversation.workspaceCwd)` — full workspace tools when ready
5. If `assistant.enabledSkills.isNotEmpty()` → `createSkillTools(...)`
6. MCP: `mcpManager.getAllAvailableTools()` → `Tool(name = "mcp__${serverName}__${tool.name}", ...)`
7. If `assistant.enableSubagents` → `buildSubagentToolsForChat(...)`

Subagent hook (L704–L716):

```kotlin
                    if (assistant.enableSubagents) {
                        addAll(
                            buildSubagentToolsForChat(
                                assistant = assistant,
                                settings = settings,
                                parentModel = model,
                                parentTools = this@buildList,
                                workspaceCwd = conversation.workspaceCwd,
                                conversationId = conversationId,
                                depth = 0,
                            ),
                        )
                    }
```

**`buildSubagentToolsForChat`** (L1563+): builds merged profiles, then calls **`createSubagentTools`** (L1588) with `json`, `getProfiles`, `spawn` → `subagentHost.spawn(...)`, etc. No `delegateOnly` argument at call site.

---

## 4) SubagentTools — `createSubagentTools` / spawn system prompt

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt`

Signature (L31–L38):

```kotlin
fun createSubagentTools(
    json: Json,
    spawn: suspend (profileName: String, task: String, description: String) -> SubagentResult,
    askBtw: suspend (question: String) -> String,
    getProfiles: () -> List<SubagentProfile>,
    includeAskBtw: Boolean = true,
): List<Tool> {
```

- **`delegateOnly` param**: **not present**.

`spawn_subagent` tool `systemPrompt` (L54–L68):

```kotlin
        systemPrompt = { _, _ ->
            buildString {
                appendLine()
                appendLine("**Subagents — Delegation Guidance**")
                appendLine("Use `spawn_subagent` to delegate substantial work. Task prompts must be self-contained.")
                appendLine("- `explore`: research and context gathering")
                appendLine("- `coder`: coding and editing tasks")
                appendLine("- `reviewer`: review and critique without changes")
                appendLine()
                appendLine("<available_subagent_profiles>")
                getProfiles().forEach { p ->
                    ...
                }
                append("</available_subagent_profiles>")
            }
        },
```

Description (L50) mentions parallel `spawn_subagent` in the same response.

**Working-tree diff** (uncommitted): adds `tool_calls` to spawn result JSON payload from `result.toolCallCount` — not delegate-only.

---

## 5) SubagentProfile

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt`

`SubagentProfile` fields (L59–L84): `name`, `displayName`, `description`, `systemPrompt`, `chatModelId`, `temperature`, `topP`, `maxTokens`, `reasoningLevel`, `maxSteps`, `workspaceAccess`, `workspaceApproval`, `allowedPathPrefixes`, `canSpawn`, `streamOutput`, **`inheritTools`**, **`excludedTools: Set<String>`**, **`localTools: List<LocalToolOption>`**, `enabledSkills`, `mcpServerIds`, `toolApprovalOverrides`, `enableMemory`, `summaryMinLength`, `summaryContinuationAttempts`.

- **`extraLocalTools`**: **not present**.
- **`inheritTools`**: `Boolean = true`
- **`localTools`**: `List<LocalToolOption> = emptyList()`
- **`excludedTools`**: `Set<String>`

Uncommitted diff on this file: `SubagentResult.toolCallCount` + comment only.

---

## 6) SubagentHost.buildChildAssistant

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt` (L251–L293)

```kotlin
        val localTools = buildList {
            if (profile.inheritTools) {
                addAll(parent.localTools)
            } else {
                addAll(profile.localTools)
            }
            removeAll { it == LocalToolOption.AskUser }
        }.distinct()
        ...
            localTools = localTools,
            mcpServers = if (profile.inheritTools) parent.mcpServers else profile.mcpServerIds,
            enabledSkills = if (profile.inheritTools) parent.enabledSkills else profile.enabledSkills,
```

- **`profile.extraLocalTools`**: **not referenced**.
- Child always strips **`AskUser`** from local tools regardless of inherit mode.

---

## 7) SubagentPermissionBuilder.buildSubagentTools

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt` (L114–L141)

```kotlin
fun buildSubagentTools(
    profile: SubagentProfile,
    depth: Int,
    maxDepth: Int,
    parentTools: List<Tool>,
    workspaceToolsFactory: (WorkspaceAccess) -> List<Tool>,
    spawnToolBuilder: (() -> Tool)? = null,
): List<Tool> {
    val workspaceTools = workspaceToolsFactory(profile.workspaceAccess)
    val base = if (profile.inheritTools) {
        val nonWorkspaceParent = parentTools
            .filter { it.name !in WorkspaceToolNames }
            .filter { it.name !in profile.excludedTools }
        nonWorkspaceParent + workspaceTools
    } else {
        // TODO: expand with profile.localTools, enabledSkills, mcpServerIds when inheritTools is false
        workspaceTools
    }
```

- **Inherit branch**: parent non-workspace `Tool` list minus exclusions + profile-scoped workspace tools.
- **Non-inherit branch**: **workspace tools only** today; TODO explicitly says local/skills/MCP not wired here.
- **`LocalToolOption` → `Tool`**: **not** in this file; child `Assistant.localTools` is set in `buildChildAssistant`; runtime tool list for child likely assembled elsewhere in `SubagentHost` / `ChatService` child path (not expanded in this research item).

---

## 8) LocalToolOption enum

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalToolOption.kt`

Values (`@Serializable` sealed class, `data object` + `@SerialName`):

| Kotlin name | Serial name |
|---|---|
| `JavascriptEngine` | `javascript_engine` |
| `TimeInfo` | `time_info` |
| `Clipboard` | `clipboard` |
| `Tts` | `tts` |
| `AskUser` | `ask_user` |
| `ScreenTime` | `screen_time` |
| `Logs` | `logs` |

Delegate-only whitelist candidates requested: **`AskUser`**, **`TimeInfo`**, **`Clipboard`**, **`Logs`** — all exist. (`ScreenTime` also exists but was not in the whitelist question.)

`LocalTools.getTools` (`LocalTools.kt` L22+): maps options to tools including TimeInfo, Clipboard, AskUser, Logs.

---

## 9) UI files

### AssistantDetailPage.kt (L146–L161)

- Nav row to `Screen.AssistantSubagent(id)` with `assistant_detail_subagent_desc` / `assistant_page_tab_subagent` (L146–L152).
- **Same screen** also embeds **`AssistantSubagentHubControls`** in a separate `item { }` (L156–L161) with `vm.update`.

### AssistantSubagentPage.kt

- Top: `LargeFlexibleTopAppBar` title `assistant_page_tab_subagent` (L69–L74).
- Body: **`AssistantSubagentContent`** (L96+) — **first content** is profiles section header (`subagent_profiles_section`), **not** `AssistantSubagentHubControls` / helper-level Card.
- No `AssistantSubagentHubControls` on this page in current tree.

### AssistantSubagentHubSection.kt — `AssistantSubagentHubControls`

- Wrapped in **`Card`** (L27–L30).
- Enable switch: `subagent_enable_title` / `assistant.enableSubagents` (L31–L44).
- Max depth **`Slider`**: `valueRange = 1f..3f`, `steps = 1`, `coerceIn(1, 3)` (L66–L77), `enabled = assistant.enableSubagents`.

### AssistantSubagentProfilePage.kt — inherit tools UI (L515–L556)

- **`inheritTools` Switch**: `subagent_profile_inherit_tools` (L518–L525).
- **`if (resolved.inheritTools)`**: shows **`excluded_tools`** `PathChipEditor` (L528–L546).
- **`if (!resolved.inheritTools)`**: shows **`LocalToolsSkillMcpSection`** (L550–L556).

---

## 10) strings.xml — `subagent_*` naming

**Convention**: prefix `subagent_` for feature strings; profile fields `subagent_profile_*`; tool UI `subagent_tool_ui_*`; steps `subagent_step_*`.

**`values/strings.xml`**: subagent block starts ~L20 (`assistant_detail_subagent_desc`, then L22+ `subagent_enable_*`, `subagent_max_depth_*`, profiles, profile form, tool UI). **No** keys for `parallel_tool_execution`, `delegate_only`, or `extra_local_tools` in current files.

**`values-zh/strings.xml`**: parallel Chinese set (~L20–L104, extensions L1405+, steps L1421+).

**Missing for port** (fact: not in repo): e.g. `subagent_parallel_tool_execution_*`, `subagent_delegate_only_*`, `subagent_profile_extra_local_tools_*` would be new keys following existing `subagent_profile_*` pattern.

---

## Caveats / Not Found

- `Assistant.kt` is **not** in `git status` modified list; three port fields are not partially added there.
- Archived `.trellis/tasks/.../reference/ChatService.kt` documents sub fork’s `buildCommonTools` / `subagentDelegateOnly` — **not** in live `app/` sources.
- `GenerationHandler` parallel rule differs from task PRD line citing sub formula with `parallelToolExecution`.
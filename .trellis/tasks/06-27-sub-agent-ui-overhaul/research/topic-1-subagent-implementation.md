# Research: Current sub-agent implementation

- **Query**: Subagent spawn/management, UI, data model, ChatService, configuration scope
- **Scope**: internal (local `app/` + `ai/` runtime)
- **Date**: 2026-06-27

## Findings

### 1. Spawn and management

| Component | Path | Role |
|-----------|------|------|
| `SubagentHost` | `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt` | Runtime: `spawn()` (L35–147), `askBtw()` (L149–175), child assistant build (L212–258), transcript builder (L309–349) |
| DI | `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` L160–164 | `SubagentHost` registered as Koin `single`, wired with `GenerationHandler` |
| Tool factories | `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt` | `createSubagentTools()` (L30–153), `createManageSubagentTool()` (L155–244); tool names in `SUBAGENT_TOOL_NAMES` (L24–28) |
| Profile resolution | `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentRegistry.kt` L60–70 | `resolveProfile(name, assistant)` merges builtin + custom, respects `disabledBuiltinSubagents` |
| Effective child tools | `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt` | `buildSubagentTools()` (L114+), workspace filtering/approval (`createSubagentWorkspaceTools` L97–112) |
| Chat wiring | `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | Injects `subagentHost` (L167); registers tools when `assistant.enableSubagents` (L627–638); `buildSubagentToolsForChat` (L1387–1458), `toolsForSubagentProfile` (L1461–1545), `manageSubagentProfile` (L1548+) |

**Spawn flow (main chat, depth 0):**

1. `ChatService` builds parent tool list (local, workspace, MCP, …).
2. If `enableSubagents`, `buildSubagentToolsForChat` appends `spawn_subagent`, optional `ask_btw`, and at depth 0 `manage_subagent_profile`.
3. `createSubagentTools` `execute` (L107–117) calls `spawn` lambda → `subagentHost.spawn(...)` (L1414–1436) with `buildChildTools` → `toolsForSubagentProfile`.
4. `SubagentHost.spawn` resolves profile (L47–54), enforces `depth >= maxDepth` (L56–64), picks model (`profile.chatModelId` or parent model, L66–68), builds synthetic child `Assistant` (L70, L212–258), runs `generationHandler.generateText` in a loop with optional summary continuation (L99–119).
5. Nested spawn: child profile with `canSpawn` gets a single `spawn_subagent` tool via `spawnToolBuilder` inside `toolsForSubagentProfile` (L1483–1532); depth increments on each spawn.

**Parallelism:** Documented in tool description (`SubagentTools.kt` L54); actual concurrency depends on parent model executing multiple tool calls in one turn (standard tool loop), not a separate executor in `SubagentHost`.

**Progress callback:** `SubagentHost.spawn` accepts `onProgress: ((List<UIMessage>) -> Unit)?` (L45, invoked in `runToCompletion` L198–200). **`ChatService.buildSubagentToolsForChat` does not pass `onProgress`** when calling `subagentHost.spawn` (L1414–1436, L1503–1525)—only post-completion conversation updates from parent generation apply.

**Sandboxing:** `SubagentHost.sandboxToolsForSubagent` (L351–352) sets all child tools `needsApproval = { false }`. Parent chat tools are built separately with normal approval rules.

### 2. Sub-agent UI components

| UI | Path | Notes |
|----|------|-------|
| `SpawnSubagentToolUI` | `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUIs.kt` L49–179 | `toolName = "spawn_subagent"`; card with loading spinner (L109–123), summary markdown (L125–129), error state (L131–138), usage line (L140–142), expandable transcript (L143–145, `SubagentTranscriptSection` L291–316) |
| `AskBtwToolUI` | same file L181–248 | `ask_btw` Q/A card |
| Registry | `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/ToolUI.kt` L107–108 | Registered in `ToolUIRegistry.renderers` |

**Title line** (`SpawnSubagentToolUI.title`, L55–80): display name from builtin registry or raw `profile_name`; after completion shows steps + token count from `SubagentResult`.

**Settings UI (not chat cards):**

- `AssistantSubagentHubControls` — `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentHubSection.kt` (enable toggle, max depth slider 1–3).
- `AssistantSubagentPage` — list/create/delete custom profiles (`AssistantSubagentPage.kt`).
- `AssistantSubagentProfilePage` — per-profile editor (model, tools, workspace, skills, approvals, etc.).
- Navigation: `RouteActivity.kt` L386–391, L627–630 (`Screen.AssistantSubagent`, `Screen.AssistantSubagentProfile`).

### 3. Data model for sub-agent output

**Serializable result** — `SubagentProfile.kt`:

```kotlin
// L97–107
data class SubagentResult(
    profile_name, summary, succeeded, error?, depth, usage?, steps, transcript: List<SubagentTranscriptStep>
)
```

**Transcript steps** — sealed `SubagentTranscriptStep` (L109–130): `Reasoning`, `ToolCall` (toolName, input, output), `Text`.

**Persistence in chat messages:**

- Tool execution returns `listOf(UIMessagePart.Text(text = payload))` where `payload` is JSON-encoded `SubagentResult` (`SubagentTools.kt` L114–116).
- No `Text.metadata` / `subagent_transcript` key in current `SubagentTools.execute` (contrast with upstream reference noted in task `06-27-sub-agent-streaming-ui/research/`).
- UI reads **`UIMessagePart.Tool.output`** text parts and deserializes JSON (`SubagentToolUIs.kt` `parseSubagentResult` L391–402); fallback parse from `context.content` JsonElement.

**UIMessage tool part** — `ai/src/main/java/me/rerere/ai/ui/Message.kt`: tools live as `UIMessagePart.Tool` with `output`, `isExecuted`, `toolCallId`, etc. (grep hits L94+, L167).

### 4. ChatService and generation

- Main generation: `ChatService` collects `GenerationChunk.Messages` and `updateConversation` (e.g. L657–662).
- Subagent runs **inside** parent tool `execute` (suspend): nested `generationHandler.generateText` in `SubagentHost` does not directly mutate parent conversation until parent tool completes and parent loop merges tool output into assistant message.
- Child model/provider: `settings.findModelById` for profile override (`SubagentHost.kt` L66–68); provider resolved later in generation stack via `Model.findProvider` (e.g. `ChatService.kt` L802, L843, L898 for other flows).

### 5. Configuration scope (per-assistant vs global)

| Setting | Location | Scope |
|---------|----------|--------|
| `enableSubagents`, `subagentMaxDepth` | `Assistant.kt` L50–51 | Per assistant |
| `subagentProfiles`, `disabledBuiltinSubagents` | `Assistant.kt` L52–53 | Per assistant (custom profiles + disabled builtins) |
| Builtin profiles | `SubagentRegistry.BUILTIN_PROFILES` L7–55 | Global defaults; merged per assistant |
| Global providers/models | `Settings.providers` (`PreferencesStore.kt` L559) | App-wide; subagent `chatModelId` references `Model.id` in that list |
| Profile CRUD at runtime | `manage_subagent_profile` tool → `ChatService.manageSubagentProfile` | Persists to **that assistant’s** `subagentProfiles` in `SettingsStore` (L1565–1575) |

There is **no** global subagent profile store separate from `Assistant`; builtins are code-defined and overridden/disabled per assistant.

### Code patterns (citations)

```107:117:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt
        execute = { args ->
            ...
            val result = spawn(profileName, task, description)
            val payload = json.encodeToString(SubagentResult.serializer(), result)
            listOf(UIMessagePart.Text(text = payload))
        },
```

```627:637:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
                    if (assistant.enableSubagents) {
                        addAll(
                            buildSubagentToolsForChat(
                                assistant = assistant,
                                ...
                                depth = 0,
                            ),
                        )
                    }
```

```121:130:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt
            val result = SubagentResult(
                profileName = profile.name,
                summary = summary.ifBlank { "(subagent produced no textual summary)" },
                succeeded = true,
                depth = depth,
                usage = totalUsage,
                steps = steps,
                transcript = transcript,
            )
```

### Related specs / tasks

- `.trellis/tasks/archive/2026-06/06-27-subagent-mvp/` — design, reference INDEX for upstream parity notes
- `.trellis/tasks/06-27-sub-agent-streaming-ui/research/` — documents gap: no `onProgress` / metadata streaming in local fork

## Caveats / Not Found

- No `updateSubagentProgress` in current `ChatService.kt` (grep only finds `onProgress` inside `SubagentHost`).
- `manage_subagent_profile` only when `depth == 0` (`SubagentTools.kt` L161).
- `description` argument to `spawn` lambda is accepted in tool schema but ignored in `ChatService` spawn lambdas (`_` placeholder L1403, L1492).
- When `inheritTools == false`, `buildSubagentTools` in `SubagentPermissionBuilder.kt` L128–130 has TODO for full local/MCP/skill expansion.
# Research: Subagent MVP — RikkaHub codebase context

- **Query**: Implementation context for Subagent System MVP
- **Scope**: Internal (local repo `release/rikka-arsucar`); PRD references external `linklink256/rikkahub-sub`
- **Date**: 2026-06-27

## PRD / task notes

| Path | Status |
|------|--------|
| `subagent-prd-draft.md` (repo root) | **Not present** in this workspace |
| `.trellis/tasks/06-27-subagent-mvp/prd.md` | Placeholder only (TBD) |
| `.trellis/tasks/06-27-subagent-mvp/task.json` | `status: planning`, `base_branch: release/rikka-arsucar` |

---

## 1. Existing subagent-related app code (`data/ai/subagent/`, etc.)

### Local codebase (application subagent feature)

**None of the PRD-named types or packages exist locally.**

| Expected path / symbol | Present locally? |
|------------------------|------------------|
| `app/.../data/ai/subagent/` | **No** directory |
| `SubagentHost` | **No** matches in `*.kt` |
| `SubagentProfile` | **No** |
| `SubagentTools` | **No** |
| `SubagentResult` | **No** |
| `SubagentToolUI` | **No** |
| `sandboxToolsForSubagent` | **No** |

### What *does* exist (Trellis / IDE “subagent”, not Rikka chat subagents)

- `.claude/hooks/inject-subagent-context.py`, `.opencode/plugins/inject-subagent-context.js` — inject task PRD/JSONL into **coding** sub-agents.
- No `upstream` remote branch matching `*sub*` (`git ls-remote upstream`); no `rikkahub-sub` in this clone.

### External branch `linklink256/rikkahub-sub`

Not checked out and not found via GitHub repo search from this environment. **Implementers should fetch that branch/fork explicitly** and diff against `app/src/main/java/me/rerere/rikkahub/data/ai/` and `ChatService.kt` before greenfield work.

---

## 2. `GenerationHandler` — nested generation / tool loop

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`  
**Class**: `GenerationHandler` (lines ~65–562)  
**DI**: `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` (~152)

### `generateText` signature (lines ~71–86)

```kotlin
fun generateText(
    settings: Settings,
    model: Model,
    messages: List<UIMessage>,
    inputTransformers: List<InputMessageTransformer> = emptyList(),
    outputTransformers: List<OutputMessageTransformer> = emptyList(),
    assistant: Assistant,
    memories: List<AssistantMemory>? = null,
    tools: List<Tool> = emptyList(),
    maxSteps: Int = 256,
    processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    conversationSystemPrompt: String? = null,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
    workspaceCwd: String? = null,
): Flow<GenerationChunk>
```

Returns `Flow<GenerationChunk>` where `GenerationChunk.Messages` carries updated `List<UIMessage>`. Runs on `Dispatchers.IO` (`flowOn` at end of `generateText`).

### Tool assembly inside handler

Inside the step loop (~94–116), `toolsInternal` = optional **memory tools** (if `assistant.enableMemory`) + caller-supplied `tools`.

### Tool loop (agentic steps)

1. **Step loop**: `for (stepIndex in 0 until maxSteps)` (~90).
2. **LLM call**: `generateInternal(...)` (~127–164) unless resuming tool execution.
3. **Post-generation**: `visualTransforms`, `onGenerationFinish`, sets `finishedAt` on last message (~165–183).
4. **Tool detection**: `messages.last().getTools().filter { !it.isExecuted }` (~185–188); empty → **break**.
5. **HITL**: Maps tools needing approval to `ToolApprovalState.Pending` (~192–224); if any pending → **emit and break** (~226–229).
6. **Execute**: For each tool in `toolsToProcess`, handles `Denied` / `Answered` / executes via `toolDef.execute(args)` (~240–314). `CancellationException` is rethrown (~296).
7. **Persist tool output**: Updates **same ASSISTANT message** parts (unified `UIMessagePart.Tool`), not a separate TOOL role message (~324–341).
8. **Resume path**: If `pendingTools` with `canResumeExecution`, skips new LLM call and continues execution (~232–236).

### Streaming

`generateInternal` (~346–456): if `assistant.streamOutput`, uses `providerImpl.streamText(...).collect` and `messages.handleMessageChunk`; else single `generateText`. Usage merged onto **last message** via `TokenUsage.merge` (~424–432, ~441–449).

### Cancellation

No explicit `Job` parameter on `generateText`. Cancellation propagates when the **collecting coroutine** is cancelled; tool `execute` rethrows `CancellationException`. Parent orchestration: `ChatService.stopGeneration` cancels session job (~1359–1363).

**Subagent implication**: Subagent can call the same `generateText` with a **subset of tools** and a **child message list**, as long as the parent collector/job lifecycle is defined (nested job vs shared session job).

---

## 3. `ChatService` — main agent tools and generation

**File**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`  
**Class**: `ChatService` (injected with `GenerationHandler`, `LocalTools`, `McpManager`, `WorkspaceRepository`, etc.)

### Entry: `handleMessageComplete` (~494+)

1. Loads `Settings`, `Conversation`, `Assistant`, resolves model:  
   `settings.findModelById(assistant.chatModelId ?: settings.chatModelId)` (~507).
2. Builds **input** transformers: global list + `TemplateTransformer` + `WorkspaceReminderTransformer` (~558–561).
3. Builds **tools** `buildList` (~564–611):
   - `createSearchTools` if `settings.enableWebSearch`
   - `localTools.getTools(assistant.localTools)`
   - `createConversationTools` if `enableRecentChatsReference`
   - `createWorkspaceToolsIfReady(assistant.workspaceId, conversation.workspaceCwd)` (~572, ~665–675)
   - `createSkillTools` if `assistant.enabledSkills` non-empty
   - MCP: wraps each as `Tool(name = "mcp__${serverName}__${tool.name}", needsApproval = { tool.needsApproval }, execute = { mcpManager.callTool(...) })` (~598–610)
4. Calls `generationHandler.generateText(...)` with conversation-scoped messages (`currentMessages` or `messageRange`) (~536–611).
5. **Collect loop** (~639–651): `GenerationChunk.Messages` → `conversation.updateCurrentMessages(chunk.messages)` → `updateConversation` (in-memory session state).
6. On success: `saveConversation`, title/suggestion side jobs (~656–663).

### Tool result flow

Tool outputs live in **assistant message** `UIMessagePart.Tool.output` (see GenerationHandler). Chat UI reads conversation `messageNodes` → `currentMessages` path.

### Tool approval continuation

`handleToolApproval` (~435–494): cancels current job, patches `approvalState` on matching `UIMessagePart.Tool`, saves, calls `handleMessageComplete` when no pending tools remain.

### Session / jobs

`ConversationSession` (`service/ConversationSession.kt`): `generationJob`, `setJob`, `getJob`. User send/regenerate/approval each `appScope.launch` and `session.setJob(job)`.

**No `sandboxToolsForSubagent`**: workspace tools are the same `createWorkspaceTools` list; filtering for subagents must be **new** (profile `workspaceAccess`, approval overrides, tool allowlist).

---

## 4. Workspace tools

**File**: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt`

| Function / symbol | Lines (approx.) | Role |
|-------------------|-----------------|------|
| `WorkspaceToolDefaultApprovals` | 27–32 | Defaults: read/write/edit **false**, shell **true** |
| `resolveWorkspaceToolApproval` | 34–35 | Override map → default |
| `createWorkspaceTools` | 37–54 | Factory; loads overrides from `WorkspaceRepository.getById` |
| `createReadFileTool` | 61–95 | `workspace_read_file` |
| `createWriteFileTool` | 97–… | `workspace_write_file`; approval if override **or** `pathOutsideWorkspace("path")` (~125) |
| `createEditFileTool` | … | `workspace_edit_file`; same path rule (~168) |
| `createShellTool` | … | `workspace_shell` |
| `pathOutsideWorkspace` | 412–416 | Extension on `JsonElement`; uses `absolutePath` |
| `isOutsideWorkspace` | 418–422 | Path not under `/workspace` |

**Registration**: Only via `ChatService.createWorkspaceToolsIfReady` when assistant has `workspaceId` and workspace `shellStatus == READY`.

**Subagent filtering hooks (to add)**: Filter tool list by profile; optionally force `needsApproval` true for subagent even when workspace default is false; respect `pathOutsideWorkspace` for write/edit.

---

## 5. Tool approval / HITL

| Layer | File | Mechanism |
|-------|------|-----------|
| Tool definition | `ai/src/main/java/me/rerere/ai/core/Tool.kt` (~12–19) | `needsApproval: (JsonElement) -> Boolean` |
| Runtime | `GenerationHandler.kt` (~192–229) | Auto → Pending; break until user acts |
| States | `ai/src/main/java/me/rerere/ai/ui/Message.kt` (~326–356, ~430+) | `ToolApprovalState`: Auto, Pending, Approved, Denied, Answered |
| Workspace config | `data/db/entity/WorkspaceEntity.kt` (~33–38) | JSON `tool_approvals` per workspace |
| Repository | `WorkspaceRepository.setToolApproval` | Persists overrides |
| UI (workspace settings) | `ui/pages/extensions/workspace/WorkspaceDetailPage.kt` | Toggles per tool name |
| UI (chat) | `ChatMessageTools.kt`, `ChatPage.kt` | `onToolApproval` → `ChatService.handleToolApproval` |
| MCP | `data/ai/mcp/McpConfig.kt` | `needsApproval` per MCP tool; passed in ChatService wrapper (~605) |
| Local tools | e.g. `LocalTools.kt` (~300) | Some tools hardcode `needsApproval = { true }` |

Subagents that run **inside** the same generation loop will surface Pending tools in the **parent** chat unless subagent runs with auto-approve policy or a separate nested flow that does not pause the parent.

---

## 6. `Assistant` data model

**File**: `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`  
**Class**: `Assistant` (lines ~14–51)

Current fields (serialization-backed, settings store):

- Identity / UI: `id`, `name`, `avatar`, `useAssistantAvatar`, `tags`, `background`, …
- Model: `chatModelId: Uuid?` (null → global default) — **already exists** for per-assistant model; subagent **profile** can mirror this pattern.
- Prompt / gen: `systemPrompt`, `temperature`, `topP`, `contextMessageSize`, `streamOutput`, `maxTokens`, `reasoningLevel`, `messageTemplate`, `presetMessages`, …
- Tools / integrations: `mcpServers`, `localTools`, `workspaceId`, `enabledSkills`, `enableMemory`, `useGlobalMemory`, `enableRecentChatsReference`, …
- Injections: `modeInjectionIds`, `lorebookIds`, `enableTimeReminder`, conversation-level flags.

**Not present** (PRD targets): `enableSubagents`, `subagentMaxDepth`, `subagentProfiles`, `disabledBuiltinSubagents`.

Persistence: `PreferencesStore` / settings JSON (`data/datastore/PreferencesStore.kt`); assistant list updates e.g. `assistant.copy(chatModelId = modelId)` (~467).

---

## 7. `Conversation` / `MessageNode` / persistence

**Files**:

- `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`
- `ai/src/main/java/me/rerere/ai/ui/Message.kt`

### Structure

- `Conversation.messageNodes: List<MessageNode>` — tree via branching.
- `MessageNode`: `messages: List<UIMessage>`, `selectIndex` — regenerate / branch.
- `currentMessages`: one selected message per node (~45–47).
- `updateCurrentMessages`: merges streaming updates by message `id` (~57–88).

### Tool calls in tree

- Modern: single `UIMessagePart.Tool` on **ASSISTANT** messages (`input`, `output`, `approvalState`).
- Legacy migration: `ToolCall` / `ToolResult` + `migrateToolMessages` in `Message.kt` (~572+).
- No dedicated “subagent transcript” part type today.

### UI rendering

`ChatMessage.kt` composes parts; tools via `ChatMessageTools.kt` / chain-of-thought steps. Unknown deprecated parts skipped (~574 in ChatMessage.kt per grep).

**Subagent persistence options**: New `UIMessagePart` subtype or metadata on `Tool` / annotation; or store summary in `Tool.output` JSON consumed by custom `ToolUIRenderer`.

---

## 8. UI patterns (assistant settings + tool cards)

### Assistant settings navigation

- Routes: `RouteActivity.kt` — `Screen.AssistantDetail`, `Screen.AssistantBasic`, … (~350–355, ~591+).
- Hub: `AssistantDetailPage.kt` — `Scaffold` + `LargeFlexibleTopAppBar`, `LazyColumn`, `CardGroup` rows navigating to sub-pages (`AssistantBasic`, `Prompt`, `Mcp`, `LocalTool`, `Extensions`, …).
- Shared VM: `AssistantDetailVM` (Koin, `parametersOf(id)`); pages call `vm.updateAssistant { copy(...) }` pattern (see `AssistantBasicPage.kt` for model picker ~246–252).

### Tool result UI

- Registry: `ui/components/message/tools/ToolUI.kt` — `ToolUIRegistry.resolve(toolName)` (~109).
- Registered renderers: memory, search, skills, workspace (`WorkspaceToolUIs.kt`), builtins (`BuiltinToolUIs.kt`).
- Chat step: `ChatMessageTools.kt` — `ControlledChainOfThoughtStep`, approval chips, bottom sheet `Preview`.
- **Extension point for subagent**: register `SubagentToolUI` (or similar) in `ToolUIRegistry` list (~94–107).

---

## 9. Token usage tracking

| Layer | Location | Behavior |
|-------|----------|----------|
| Model | `ai/src/main/java/me/rerere/ai/core/Usage.kt` | `TokenUsage`, `merge` sums prompt/completion/cached |
| Message | `UIMessage.usage` | Set during stream/generate on **last** message in handler |
| Display | `ChatMessageNerdLine.kt` (~50+) | If `displaySetting.showTokenUsage`, shows prompt/completion/cached |
| Setting | `PreferencesStore` `DisplaySetting.showTokenUsage` (~664); `SettingPreferencesUIPage.kt` toggle |
| DB | Historical schemas reference conversation-level `usage` / `tokenUsage` in Room JSON schemas; per-message usage is on serialized `UIMessage` inside `nodes` |

**Subagent PRD** (card-level + parent merge): No existing aggregation across tool-nested runs. Parent message `usage.merge(child)` would need explicit logic after subagent `generateText` completes; child usage could be stored in tool output metadata or a dedicated part for the card UI.

---

## 10. Model selection

| Concept | File | Notes |
|---------|------|-------|
| `Model` | `ai/src/main/java/me/rerere/ai/provider/Model.kt` | `id: Uuid`, `modelId: String`, `displayName`, `abilities` (TOOL, REASONING), modalities |
| Global default | `Settings.chatModelId` | `PreferencesStore.kt` |
| Per-assistant | `Assistant.chatModelId` | Nullable override |
| Resolution | `Settings.findModelById(uuid, fallback)` | `PreferencesStore.kt` ~720–738 |
| Chat send | `ChatService.handleMessageComplete` | `assistant.chatModelId ?: settings.chatModelId` |
| UI picker | `AssistantBasicPage`, `ModelList.kt`, `ChatInput.kt` | Pattern for selecting `Uuid` model id |

Subagent profile field `chatModelId: Uuid?` aligns with existing assistant field semantics (null → inherit parent or global).

---

## Related specs / docs

| Path | Relevance |
|------|-----------|
| `README_FOR_AGENT.md` | Points to `ChatService.kt`, `GenerationHandler.kt` for tool/generation work |
| `AGENTS.md` | Documents Assistant, Conversation, MessageNode, UIMessage concepts |
| `.trellis/spec/` | No subagent-specific spec yet (guides only per task injection) |

## Caveats / not found

1. **`subagent-prd-draft.md`** and filled **task PRD** not in repo; scope taken from research task prompt + codebase inspection.
2. **`linklink256/rikkahub-sub`** implementation not available locally; zero Kotlin references to `SubagentHost` et al.
3. **`sandboxToolsForSubagent`** does not exist; workspace sandboxing is “full workspace tools or none” today.
4. **Nested `generateText`** while parent job is active: `ChatService` uses one `generationJob` per conversation — subagent design must address concurrent/nested jobs and HITL.
5. **Task context**: `task.py current` reported no active task at research time; output path is `.trellis/tasks/06-27-subagent-mvp/research.md` per assignment.

## Suggested implementation anchors (factual only)

| Concern | Existing anchor |
|---------|-----------------|
| Nest LLM+tools | `GenerationHandler.generateText` |
| Expose tool to main agent | `ChatService` `tools` buildList + new `Tool` in `data/ai/tools/` or `subagent/` |
| Profile storage | Extend `Assistant` + settings migration |
| Card UI | `ToolUIRegistry` + `ChatMessageTools.kt` |
| Workspace policy | Filter/wrap `createWorkspaceTools` |
| Usage rollup | `TokenUsage.merge` on parent `UIMessage` after child run |
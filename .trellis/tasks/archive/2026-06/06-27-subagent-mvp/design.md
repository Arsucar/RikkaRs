# Subagent System MVP — Technical Design

Parent task: `.trellis/tasks/06-27-subagent-mvp`. Requirements: `prd.md`. Codebase context: `research.md`.

## 1. Architecture Overview

The MVP splits into five implementation children that share **type contracts** from the model child. Runtime orchestrates nested generation; permissions shapes the effective tool surface; chat and settings UI consume serialized results and assistant configuration.

```
                    ┌─────────────────────────────────────┐
                    │  Assistant (settings + profiles)     │
                    │  enableSubagents, maxDepth, profiles │
                    └──────────────┬──────────────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         │                         │                         │
         v                         v                         v
┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────────┐
│ ui-settings     │    │ model                │    │ permissions         │
│ CRUD profiles   │───▶│ SubagentProfile      │───▶│ buildSubagentTools  │
│ enable / depth  │    │ SubagentResult       │    │ resolveProfile      │
└─────────────────┘    │ Assistant +4 fields  │    └──────────┬──────────┘
                       └──────────┬───────────┘               │
                                  │                           │
                                  v                           v
                       ┌──────────────────────┐    ┌─────────────────────┐
                       │ runtime              │◀───│ filtered Tool list  │
                       │ SubagentHost.spawn   │    │ + spawn_subagent    │
                       │ GenerationHandler    │    └─────────────────────┘
                       └──────────┬───────────┘
                                  │ SubagentResult JSON in Tool.output
                                  v
                       ┌──────────────────────┐
                       │ ui-chat              │
                       │ ToolUIRegistry card  │
                       └──────────────────────┘
```

### Child dependency graph

| Child | Depends on | Provides to |
|-------|------------|-------------|
| `06-27-subagent-model` | — | Types, serialization, `resolveProfile` contract, Assistant fields |
| `06-27-subagent-permissions` | model (`SubagentProfile`, workspace enums) | `buildSubagentTools`, profile-scoped HITL posture |
| `06-27-subagent-runtime` | model, permissions | `SubagentHost`, spawn tools, nested `generateText`, summaries |
| `06-27-subagent-ui-chat` | model (`SubagentResult` schema) | Card rendering, ask_btw / continuation UX |
| `06-27-subagent-ui-settings` | model | Editor for profiles and assistant subagent flags |

**Build order (recommended):** model → permissions → runtime (spawn tool registration in ChatService ties runtime + permissions) → ui-chat + ui-settings in parallel.

**Integration seam:** `ChatService` remains the parent tool assembler; when `assistant.enableSubagents`, it appends `spawn_subagent` (and related) tools built by runtime/permissions. Subagent execution does **not** replace `handleMessageComplete`; it runs as a **nested coroutine** under the parent generation job when the parent model invokes the spawn tool.

---

## 2. Contracts & Interfaces

### 2.1 `SubagentProfile` (model → all consumers)

Logical fields (exact Kotlin names live in model child):

| Field group | Purpose |
|-------------|---------|
| Identity | Stable `id` (builtin seed or UUID), `displayName`, optional description |
| Prompt | `systemPrompt` for the nested run (role boundary) |
| Model | `chatModelId: Uuid?` — null inherits parent resolution (`assistant.chatModelId ?: settings.chatModelId`) |
| Tools | Allow/deny or capability flags aligned with parent-available tools (search, local, MCP, workspace, skills, memory) |
| Workspace | `workspaceAccess`, `workspaceApproval`, optional `allowedPathPrefixes` |
| Spawn policy | `canSpawnSubagents: Boolean` (nested spawn only if depth &lt; max and profile allows) |
| Origin | `builtin` vs `custom`; builtins skippable via `disabledBuiltinSubagents` on assistant |

**Consumers:**

- **permissions:** filters parent tool list to profile policy; never widens parent capabilities.
- **runtime:** passes profile into nested `generateText` (system prompt, model, tools).
- **ui-settings:** CRUD and validation; summary of tool/workspace posture for editor.

### 2.2 `SubagentResult` (runtime → chat UI, parent LLM)

Serialized **JSON string** stored in `UIMessagePart.Tool.output` for tool name `spawn_subagent` (and continuation variant if distinct). Not a new `UIMessagePart` subtype in MVP.

Minimum payload:

| Field | Role |
|-------|------|
| `status` | `running` (optional live updates), `succeeded`, `failed`, `cancelled` |
| `profileId` / `profileName` | Card label |
| `summary` | Text returned to parent model as tool result body (primary answer) |
| `error` | Optional structured failure |
| `usage` | `TokenUsage`-compatible object for card + merge |
| `transcript` | Optional list of `SubagentTranscriptStep` (truncated for persistence cap) |
| `continuationHandle` | Opaque id for explicit continuation spawns |

---
Parent model sees **summary + compact metadata** in the tool result channel; full transcript is for UI expand and continuation hydration, not default parent context.

### 2.3 `buildSubagentTools`

```kotlin
fun buildSubagentTools(
    profile: SubagentProfile,
    depth: Int,
    maxDepth: Int,
    parentWorkspaceId: Uuid?,
    workspaceCwd: String?,
    parentToolContext: ParentToolContext,
): List<Tool>
```

**Owner:** permissions child (may live in `data/ai/subagent/` package next to runtime).

**Behavior:**

1. Start from the same **parent-available** tool sources `ChatService` uses (search, local, MCP wrappers, workspace factory, skills, memory) — passed in via `ParentToolContext`, not re-fetched ad hoc.
2. Apply profile allow/deny and capability flags.
3. Apply `workspaceAccess` (none / read-only / full) and `workspaceApproval` (stricter `needsApproval` than workspace defaults where configured).
4. Apply `allowedPathPrefixes` for mutating workspace tools (`pathOutsideWorkspace` / prefix checks).
5. Apply `excludedTools` by name.
6. If `depth + 1 < maxDepth` and profile `canSpawnSubagents`, inject filtered `spawn_subagent` (profile list restricted to spawnable profiles).

**Consumer:** runtime `SubagentHost` before calling `GenerationHandler.generateText`.

### 2.4 `resolveProfile`

```kotlin
fun resolveProfile(
    nameOrId: String,
    assistant: Assistant,
): SubagentProfile?
```

**Owner:** model (or thin registry in `data/ai/subagent/`).

**Behavior:**

- Merge builtin catalog + `assistant.subagentProfiles`.
- Exclude ids in `assistant.disabledBuiltinSubagents`.
- Return null if disabled, unknown, or subagents globally off (spawn tool should validate before host).

### 2.5 Assistant extension (four fields)

| Field | Type | Default |
|-------|------|---------|
| `enableSubagents` | `Boolean` | `false` |
| `subagentMaxDepth` | `Int` | `2` |
| `subagentProfiles` | `List<SubagentProfile>` | `emptyList()` |
| `disabledBuiltinSubagents` | `Set<String>` | `emptySet()` |

Serialization: same JSON settings path as today (`PreferencesStore`); new fields omitted in old JSON deserialize to defaults. No Room migration.

### 2.6 Spawn tool surface (runtime registers, permissions shapes execution env)

| Tool | Input (conceptual) | Output |
|------|-------------------|--------|
| `spawn_subagent` | `profile` (id/name), `task` (string), optional `continuationHandle` | `SubagentResult` JSON |
| `ask_btw` | `question` (self-contained string) | `{ answer }` JSON |
| `manage_subagent_profile` | `action` + fields | persisted profile list |

Exact parameter JSON schema is owned by model child for stable persistence and tests.

---

## 3. Data Flow

### 3.a Spawn flow

```
Main agent (depth 0)
  GenerationHandler step loop
    └─ tool call: spawn_subagent(profile, task)
         ChatService / tool execute (runtime)
           ├─ resolveProfile(profile, assistant)
           ├─ if depth >= maxDepth → SubagentResult failed (no nested loop)
           ├─ SubagentHost.spawn(...)
           │    ├─ buildSubagentTools(profile, depth, maxDepth, ...)
           │    ├─ child messages: [system from profile, user task (+ continuation context)]
           │    ├─ child coroutine: collect GenerationHandler.generateText(...)
           │    ├─ on finish: extract summary (last assistant text + tool outcomes policy)
           │    ├─ TokenUsage.merge into result.usage
            │    └─ optional continuationHandle
           ├─ merge result.usage → parent UIMessage.usage (same turn)
           └─ return SubagentResult JSON as Tool.output on parent ASSISTANT message
Parent loop continues with tool output in context (summary only by default).
```

**Parallel spawn (MVP posture):** PRD default is serial queueing first; if multiple `spawn_subagent` calls appear in one assistant step, runtime uses `coroutineScope` + `async` only when explicitly delivered—otherwise execute sequentially to avoid session job races.

**Cancellation:** Parent `ConversationSession.generationJob` cancellation must cancel child scope; terminal `SubagentResult.status = cancelled`.

### 3.b Permission flow

```
Parent tools (ChatService buildList)
  └─ buildSubagentTools(profile, ...)
       ├─ workspaceAccess == NONE → drop all workspace_* tools
       ├─ READ_ONLY → read/shell read paths only; drop write/edit
       ├─ FULL → include per allowlist; workspaceApproval forces needsApproval true where configured
       ├─ allowedPathPrefixes → wrap write/edit needsApproval or deny execute when path fails prefix
       ├─ excludedTools / deny list → remove by name (incl. mcp__* names)
       ├─ memory / search / local / skills → profile flags
       └─ canSpawn + depth → add spawn_subagent with narrowed profile picker metadata
Nested GenerationHandler:
  HITL Pending tools resolved inside child collection loop (policy: auto-deny / auto-approve sandbox / route to parent — default: do not block parent chat; see PRD decision 16).
```

### 3.c UI flow

```
UIMessagePart.Tool(name = "spawn_subagent", output = SubagentResult JSON)
  ChatMessageTools / chain-of-thought
    ToolUIRegistry.resolve("spawn_subagent")
      SubagentToolUI
        ├─ parse JSON → status, profileName, summary, usage (if DisplaySetting.showTokenUsage)
        ├─ expandable transcript from transcript[]
        └─ continuation affordance → new spawn with continuationHandle

UIMessagePart.Tool(name = "ask_btw", output = { question, answer } JSON)
  ToolUIRegistry.resolve("ask_btw")
    AskBtwToolUI
        └─ simple Q&A card with token count
```

Unknown or legacy tool output: degrade to generic tool preview (no crash); aligns with cross-child acceptance “disabled / old messages”.

---

## 4. Key Technical Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Subagent runs in a **child coroutine** under the parent generation job | Matches `ChatService` session ownership; propagates `CancellationException` from `GenerationHandler` |
| 2 | **SubagentResult in `Tool.output` JSON** | No conversation schema migration; same persistence path as workspace/search tools |
| 3 | **Token usage:** merge into parent `UIMessage.usage` and duplicate in JSON for card | `TokenUsage.merge` already used in handler; card needs stable historical display |
| 4 | **Transcript steps only inside result JSON** | Conversation tree stays parent branches only (PRD §8) |
| 5 | **Multiple spawns in one step:** use `coroutineScope` + `async` per call when parallel is enabled; **MVP integration gate** may ship **serial** execution first (PRD #17) then flip to parallel without contract changes | Child jobs only touch isolated message lists until results are written back to distinct `Tool` parts on the parent message |
| 6 | **Nested `generateText` with isolated `List<UIMessage>`** | Reuses tool loop, HITL, streaming chunk types without forking handler |
| 7 | **Spawn tools only when `enableSubagents`** | Zero behavior change for existing assistants |
| 8 | **Profile cannot widen tools** | Security posture; permissions child deny-by-default |
| 9 | **ask_btw** as explicit `SubagentResult.status` | Distinct from generic tool errors; chat child owns response UX |
| 10 | **Continuation via handle**, not replay of full parent chat | Explicit contract in spawn input; runtime loads prior transcript slice from stored result or in-memory session map |

---

## 5. Compatibility & Migration

| Area | Approach |
|------|----------|
| Assistant settings | New fields default off / empty; kotlinx.serialization or manual defaults on load |
| Database | No migration; assistants stored as JSON in preferences |
| Historical messages | `spawn_subagent` tool parts with JSON output render on new app; old app versions ignore custom registry and show raw/generic tool UI |
| Disabled feature | `enableSubagents == false` → no spawn tools registered; no `SubagentHost` calls |
| Builtin profiles | Seeded ids stable across versions; disabling via `disabledBuiltinSubagents` only affects spawnability, not deserialization |

Version field inside `SubagentResult` JSON (optional `schemaVersion: 1`) recommended for forward-compatible UI parsing.

---

## 6. Rollback Shape

| Level | Action |
|-------|--------|
| Feature off | Set `enableSubagents = false` (default); parent code paths unchanged |
| Per child | Revert child PR independently if contracts unchanged |
| Runtime only | Remove spawn tool registration from `ChatService` tools list |
| Permissions only | `buildSubagentTools` unused; spawn path removed |
| Model only | Last revert if no persisted subagent results in user data—or keep types for read-only history parsing |
| Worst case | Feature flag false + delete `ToolUIRegistry` entry + stop registering spawn tool; historical JSON remains in DB harmless |

Coupling is intentionally limited to **shared types** (`SubagentProfile`, `SubagentResult`, assistant fields) and **two functions** (`buildSubagentTools`, `resolveProfile`).

---

## 7. Glossary (shared types & functions)

| Symbol | Layer | Meaning |
|--------|-------|---------|
| `SubagentProfile` | model | Named configuration for one subagent run |
| `WorkspaceAccess` | model | Enum: none, read-only, full (exact names in model child) |
| `WorkspaceApproval` | model | How HITL applies to workspace tools for subagent |
| `SubagentResult` | model | Outcome DTO serialized to spawn tool output |
| `SubagentTranscriptStep` | model | One summarized step (role, text snippet, tool name, status) for UI expand |
| `ParentToolContext` | permissions | Snapshot of parent-resolved tools/sources for filtering |
| `buildSubagentTools()` | permissions | Effective `List<Tool>` for a nested run |
| `resolveProfile()` | model | Builtin + custom merge with disable set |
| `SubagentHost` | runtime | Spawns nested generation, returns `SubagentResult` |
| `spawn_subagent` | runtime | Main-agent `Tool` definition |
| Assistant `enableSubagents` | model | Master switch |
| Assistant `subagentMaxDepth` | model | Hard cap at spawn |
| Assistant `subagentProfiles` | model | User-defined profiles |
| Assistant `disabledBuiltinSubagents` | model | Builtin ids user turned off |

---

## Child implementer notes

- Do not duplicate parent PRD acceptance criteria in this file; integration gates remain in parent `prd.md` §5.
- Per-child `design.md` may refine internal class names and file paths under `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/` (greenfield per `research.md`).
- Anchors: `GenerationHandler.generateText`, `ChatService` tools `buildList`, `createWorkspaceTools`, `ToolUIRegistry`, `Assistant` in `data/model/Assistant.kt`.
# Subagent Data Model

## Goal

Define all persistent and serializable data types for the Subagent MVP so runtime, permissions, and UI children can depend on a single, stable contract. This child task covers **model definitions only**—no orchestration, tool wiring, or UI.

## Parent

- Parent task: `06-27-subagent-mvp` (Subagent System MVP)

## Dependencies

- **None.** Other MVP child tasks depend on this task’s types and defaults.

## Requirements

### 1. SubagentProfile

A profile describes one subagent kind (built-in or user-defined). It must include:

| Field | Requirement |
|-------|-------------|
| `name` | Stable identifier (e.g. built-in slug or user-defined key); unique within an assistant’s profile set for resolution |
| `displayName` | Human-readable label for settings and UI |
| `description` | Short purpose text for the main agent and settings |
| `systemPrompt` | Subagent-specific system instructions |
| `chatModelId` | Optional model override; null must mean inherit per product rules documented in acceptance (align with existing assistant `chatModelId` semantics) |
| `temperature` | Optional generation parameter |
| `topP` | Optional generation parameter |
| `maxTokens` | Optional cap |
| `reasoningLevel` | Same conceptual model as main assistant reasoning level |
| `maxSteps` | Upper bound on agentic tool/LLM steps for this profile |
| `workspaceAccess` | See WorkspaceAccess (section 2) |
| `workspaceApproval` | See WorkspaceApproval (section 3) |
| `allowedPathPrefixes` | Optional path allowlist for workspace tools when access is not NONE |
| `canSpawn` | Whether this profile may delegate to other subagents |
| `streamOutput` | Whether subagent generation streams |
| `inheritTools` | Whether parent tool surface is inherited before exclusions |
| `excludedTools` | Tool names to remove from inherited set |
| `localTools` | Explicit local tool options enabled for this profile |
| `enabledSkills` | Skill names enabled for this profile |
| `mcpServerIds` | MCP server IDs enabled for this profile |
| `enableMemory` | Whether memory tools apply for this profile |
| `summaryMinLength` | Minimum length threshold for result summarization policy |
| `summaryContinuationAttempts` | How many continuation attempts allowed when summarizing |

All fields must have **sensible defaults** so partial JSON and new profiles deserialize without failure.

### 2. WorkspaceAccess

Enum with exactly: `NONE`, `READ_ONLY`, `FULL`.

Each value must imply a **fixed tool matrix** (which workspace tool capabilities are conceptually allowed for a subagent with that access). The matrix must be:

- Documented in this task’s acceptance criteria as testable expectations (tool names or capability groups as used by the app’s workspace tools).
- `NONE`: no workspace tools for the subagent.
- `READ_ONLY`: read-only workspace capabilities only; no mutating or shell execution unless explicitly excluded by product matrix.
- `FULL`: full workspace tool set subject to `workspaceApproval`, path rules, and parent workspace readiness (enforcement in runtime child; **matrix definition** is in scope here).

### 3. WorkspaceApproval

Enum with exactly: `INHERIT`, `AUTO`, `OVERRIDE`.

Semantics for subagent runs:

- `INHERIT`: use workspace / parent approval configuration without subagent-specific override.
- `AUTO`: subagent workspace tools do not surface parent HITL pause for approval (auto-approve at subagent boundary per product rules).
- `OVERRIDE`: subagent forces approval behavior distinct from inherit (exact override shape must be serializable and testable; details consumed by permissions/runtime children).

### 4. SubagentResult

Structured outcome of a subagent invocation, suitable for persistence inside tool output or downstream UI. Must capture at minimum:

- Success vs failure (or equivalent status)
- Final or summary text presented to the parent agent
- Optional structured error or cancellation indication
- Optional token usage rollup fields if the MVP card requires them (fields nullable with defaults)

Exact field list must round-trip through JSON serialization used by the app.

### 5. SubagentTranscriptStep

Sealed hierarchy for ordered transcript segments shown in chat UI. Required variants:

- **Reasoning** — reasoning segment content
- **ToolCall** — tool invocation identity, arguments snapshot, and execution state sufficient for UI replay
- **Text** — plain assistant text segment

Steps must serialize as a discriminated union (sealed class / polymorphic JSON) consistent with existing `UIMessage` / settings serialization patterns in the app.

### 6. Assistant extensions

Extend the existing `Assistant` model with new fields (all with defaults for migration):

| Field | Requirement |
|-------|-------------|
| `enableSubagents` | Master switch; default off for existing assistants |
| `subagentMaxDepth` | Maximum nesting depth for subagent → subagent; default safe MVP value |
| `subagentProfiles` | List of `SubagentProfile` (user-defined and/or overrides of built-ins) |
| `disabledBuiltinSubagents` | Set of built-in profile `name` values disabled for this assistant |

Persisted through the same settings / preferences path as other assistant fields.

### 7. Built-in profile registry

Provide a **registry** of three built-in profiles: `explore`, `coder`, `reviewer`.

Each built-in must ship with **documented default values** for all `SubagentProfile` fields (including workspace access, approval, tools, steps, and spawn policy). User assistants may disable built-ins via `disabledBuiltinSubagents` and may override via `subagentProfiles` entries that share the built-in `name` (merge semantics defined in acceptance).

### 8. Serialization

- All new types (`SubagentProfile`, enums, `SubagentResult`, `SubagentTranscriptStep` variants, Assistant new fields) must support the app’s standard JSON serialization (Kotlinx.serialization as used for `Assistant` and settings).
- Polymorphic transcript steps must deserialize reliably from stored JSON.
- Unknown enum values must not corrupt assistant load (forward-compatible strategy required—e.g. default fallback or explicit migration rule in acceptance).

### 9. Migration and compatibility

- Existing stored assistant JSON **without** new fields must load identically to today’s behavior after upgrade (subagents off, empty profiles, default depth).
- Round-trip save/load must not drop or alter unrelated assistant fields.
- Adding/removing entries in `subagentProfiles` must not require a global settings reset.

## Out of scope

- `SubagentHost`, `generateText` nesting, `ChatService` tool registration
- Workspace tool factory changes, MCP wiring, HITL UI
- Assistant settings screens, chat card UI, `ToolUIRegistry`
- Unit test **implementation** is not required in this PRD file, but **acceptance** requires tests exist in the codebase before this child is done

## Acceptance Criteria

- [ ] `SubagentProfile` includes every field listed in section 1 with defaults; compiles and serializes/deserializes in isolation tests
- [ ] `WorkspaceAccess` enum values `NONE`, `READ_ONLY`, `FULL` each map to the agreed workspace tool matrix; unit tests assert matrix membership per access level
- [ ] `WorkspaceApproval` enum values `INHERIT`, `AUTO`, `OVERRIDE` have documented, test-covered resolution rules at the model/policy layer (no UI)
- [ ] `SubagentResult` round-trips JSON with success/failure and summary fields
- [ ] `SubagentTranscriptStep` sealed variants `Reasoning`, `ToolCall`, `Text` round-trip polymorphic JSON
- [ ] `Assistant` loads legacy JSON without new keys unchanged in behavior; with new keys round-trips all subagent fields
- [ ] Built-in registry exposes exactly three profiles (`explore`, `coder`, `reviewer`) with defaults matching section 7 specifications in tests
- [ ] Disabling a built-in via `disabledBuiltinSubagents` is reflected when resolving effective profiles for an assistant

## Notes

- Align optional `chatModelId` on profiles with existing `Assistant.chatModelId` null = inherit semantics unless parent MVP PRD states otherwise.
- Runtime and permissions children consume `WorkspaceAccess` / `WorkspaceApproval` matrices; keep matrices stable once tests lock them.
# Research: Related Domain Models

- **Query**: Find related models: ToolDiagnostics, ToolCapabilityCatalog, ToolPermissionPreset, ToolPermissionPolicy, ToolConnectionStatus
- **Scope**: internal
- **Date**: 2026-07-20

## Findings

### ToolDiagnostics.kt

- **File**: `app/src/main/java/me/rerrere/rikkahub/data/model/ToolDiagnostics.kt` (74 lines)
- **Key types**:
  - `ToolDiagnosticTarget` enum (L4): `NONE, WORKSPACES, SKILLS, MEMORY, MEMORY_TABLE, MCP, ASSISTANT_TOOLS`
  - `ToolDiagnosticCheck` enum (L7): `CONFIGURED, AVAILABLE, EFFECTIVE`
  - `ToolDiagnosticStep` (L9-12): `check: ToolDiagnosticCheck, passed: Boolean`
  - `ToolCapabilityDiagnostic` (L14-19): `capability, primaryReason, reasonChain, repairTarget`
  - `ToolDiagnosticsSnapshot` (L21-34): `diagnostics: List<ToolCapabilityDiagnostic>`, `copySummary()` method
- **`copySummary()`** (L26-33): Produces the problematic no-space string like `tools=12;effective=4;builtin=4;memory=1;...`. Deliberately excludes runtime names, ids, URLs, headers, and configuration payloads.
- **Extension functions**:
  - `ToolCapability.diagnostic()` (L46-53): Builds deterministic chain `[CONFIGURED, AVAILABLE, EFFECTIVE]`
  - `ToolCapabilitySnapshot.diagnostics()` (L55-56): Maps all capabilities to diagnostics
  - `collectToolDiagnostics()` (L59-73): Collects independent sources with failure resilience

### ToolCapabilityCatalog.kt

- **File**: `app/src/main/java/me/rerrere/rikkahub/data/model/ToolCapabilityCatalog.kt` (207 lines)
- **Key types**:
  - `ToolCapabilitySource` enum (L10): `BUILTIN, LOCAL, CALENDAR, MEMORY, MEMORY_TABLE, WORKSPACE, SKILL, MCP, SUBAGENT`
  - `ToolApproval` enum (L11): `NONE, USER, DELEGATE_ONLY`
  - `ToolCapabilityReason` enum (L12-16): `AVAILABLE, DISABLED, GLOBAL_DISABLED, NOT_SELECTED, MISSING, UNAVAILABLE, CONNECTING, CONNECTION_ERROR, NEEDS_AUTHORIZATION, DELEGATE_ONLY, INVALID_CONFIGURATION, POLICY_DENIED`
  - `ToolCapability` (L18-28): `id, source, runtimeName, displayName, configured, available, effective, reasonCode, approval`
  - `ToolCapabilitySnapshot` (L30-37): `capabilities: List<ToolCapability>`, `effectiveRuntimeNames`, `effectiveCount`, `matchesRuntimeNames()`
- **`assistantToolCapabilitySnapshot()`** (L61-206): The central factory that builds the entire capability list from assistant + workspaces + skills + MCP configs/statuses. Returns `ToolCapabilitySnapshot` with `applyPermissions` applied.
- **Key logic**:
  - L89-92: BUILTIN tools (search_web, scrape_web, recent_chats, conversation_search)
  - L94-100: MEMORY/MEMORY_TABLE with global gate
  - L102-122: WORKSPACE tools with delegate-only mode filtering
  - L124-138: LOCAL/CALENDAR tools with delegate-only allowlist
  - L140-164: SKILL capabilities including `skill:management`
  - L166-199: MCP capabilities with detailed connection status checking
  - L201-205: SUBAGENT tools (spawn_subagent, ask_btw, manage_subagent_profile)
- **`effectiveCount`** (L33): Counts distinct `effectiveRuntimeNames` (not capability IDs) — so `use_skill` counts once even if multiple skills

### ToolPermissionPreset.kt

- **File**: `app/src/main/java/me/rerrere/rikkahub/data/model/ToolPermissionPreset.kt` (153 lines)
- **Key types**:
  - `ToolPermissionPreset` (L7-13): `id: Uuid, name: String, description: String, permissions: Map<String, ToolPermission>, version: Int`
  - `ToolPresetApplyStatus` enum (L15): `APPLIED, SKIPPED_UNKNOWN, REJECTED_RELAXATION, TARGET_NOT_FOUND, INVALID`
  - `ToolPresetTargetResult` (L17-22): `assistantId, status, changed, skippedKeys`
  - `ToolPresetDiff` (L24-28): `changed, unknownKeys, widensAccess`
- **Built-in presets** (L41-76):
  - `readonly-research` (UUID `00000000-0000-0000-0000-000000000001`): workspace read-only, deny shell/write
  - `approval-workspace` (UUID `00000000-0000-0000-0000-000000000002`): workspace tools with ASK
  - `least-privilege` (UUID `00000000-0000-0000-0000-000000000003`): deny most, allow web_search/scrape_web
- **Key functions**:
  - `isValidForPersistence()` (L30-39): Validates name ≤128, description ≤512, permissions ≤256, no mcp: keys, limited skill: keys
  - `diffToolPermissionPreset()` (L78-93): Computes what changes vs. current permissions, detects `widensAccess`
  - `applyToolPermissionPreset()` (L95-118): Applies preset with relaxation guard, returns `ToolPresetTargetResult`
  - `permissionPresetFromAssistant()` (L121-132): Captures current assistant permissions as a preset (strips mcp: and skill: except `skill:management`)
  - `batchToolPermission()` (L134-152): Batch set permission for multiple capability IDs

### ToolPermissionPolicy.kt

- **File**: `app/src/main/java/me/rerrere/rikkahub/data/model/ToolPermissionPolicy.kt` (71 lines)
- **Key functions**:
  - `resolveToolPermission()` (L6-13): RESOLVE inherit chain
  - `Tool.applyToolPermission()` (L15-18): Applies permission to Tool object (DENY → null, ASK → needsApproval, ALLOW/INHERIT → pass through)
  - **`applyAssistantToolPermissions()`** (L21-27): **MUST NOT CHANGE** — final gate used by `ChatService` and `SubagentPermissionBuilder`. Maps runtime names to capability IDs via `stableCapabilityIdForRuntimeName`, then applies policy.
  - `finalizeGenerationTools()` (L31-35): Shared alias for `applyAssistantToolPermissions`
  - `stableCapabilityIdForRuntimeName()` (L37-51): Runtime name → capability ID mapping
  - `ToolCapabilitySnapshot.applyPermissions()` (L53-66): Applies permission map to snapshot (DENY → effective=false + POLICY_DENIED, ASK → approval=USER)
  - `orphanToolPermissionIds()` (L68-71): Finds permission keys not in known capability IDs

### ToolConnectionStatus.kt

- **File**: `app/src/main/java/me/rerrere/rikkahub/data/model/ToolConnectionStatus.kt` (102 lines)
- **Key types**:
  - `ToolConnectionState` enum (L7-16): `IDLE, CONNECTING, SUCCESS, EMPTY, NEEDS_AUTHORIZATION, NETWORK_ERROR, PROTOCOL_ERROR, ERROR`
  - `ToolConnectionStatus` (L18-24): `state, toolCount, message, revision, checkedAtEpochMillis`
  - `ToolConnectionStatusStore` (L27-52): Thread-safe store with revision-based dedup
- **Key functions**:
  - `workspaceConnectionStatus()` (L54-60): Converts `WorkspaceShellStatus` string → `ToolConnectionStatus`
  - `aggregateToolConnectionStatus()` (L62-76): Priority-based aggregation of multiple statuses
  - `McpStatus.toToolConnectionStatus()` (L78-94): Converts raw MCP status to user-facing status
  - `String.redactConnectionSecrets()` (L96-102): Redacts auth tokens from error messages

### WorkspaceToolCapability.kt

- **File**: `app/src/main/java/me/rerrere/rikkahub/data/model/WorkspaceToolCapability.kt` (121 lines)
- `WorkspaceToolCapability` (L46-52): `configured, workspace, available, availableToolNames, unavailableReason`
- `resolveWorkspaceToolCapability()` (L54-91): Resolves workspace ID → capability; handles UNCONFIGURED, MISSING, DISABLED, INSTALLING, BROKEN, UNKNOWN
- `decideWorkspaceEnable()` (L101-108): 0 valid → CreateOrManage, 1 → Bind, many → Select
- `decideWorkspaceSelection()` (L116-121): string ID → Uuid parse → Bind or Cancel/Invalid
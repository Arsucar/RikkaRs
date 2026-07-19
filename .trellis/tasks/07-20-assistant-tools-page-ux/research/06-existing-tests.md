# Research: Existing Tests

- **Query**: Identify existing tests related to tools page, diagnostics, presets
- **Scope**: internal
- **Date**: 2026-07-20

## Findings

### Test Files

| File | Lines | What it tests |
|---|---|---|
| `app/src/test/java/me/rerrere/rikkahub/ui/pages/assistant/detail/AssistantToolsPageTest.kt` | 140 | `empowermentToolStats`, `memoryTableToolUiState`, `persistWorkspaceBinding` |
| `app/src/test/java/me/rerrere/rikkahub/data/model/ToolPermissionPolicyTest.kt` | 44 | `applyAssistantToolPermissions`, `ToolCapabilitySnapshot.applyPermissions`, `orphanToolPermissionIds` |
| `app/src/test/java/me/rerrere/rikkahub/data/model/WorkspaceToolCapabilityTest.kt` | ? | `resolveWorkspaceToolCapability` |
| `app/src/test/java/me/rerrere/rikkahub/data/model/MemoryTableTest.kt` | ? | `resolveMemoryCapabilities` |
| `app/src/test/java/me/rerrere/rikkahub/service/GenerationMemoryPreparationTest.kt` | ? | `resolveMemoryCapabilities` in service context |

### AssistantToolsPageTest.kt Details

**File**: `app/src/test/java/me/rerrere/rikkahub/ui/pages/assistant/detail/AssistantToolsPageTest.kt` (140 lines)

| Test (L#) | What |
|---|---|
| `titleStatsIncludeDynamicLocalSkillAndMcpFromTheSharedSnapshot` (L24-46) | Verifies `empowermentToolStats` matches `snapshot.effectiveCount` and `capabilities.size`; confirms `effectiveRuntimeNames` includes calendar_query, calendar_create, use_skill, mcp__demo__lookup |
| `memoryTableUiPreservesPreferenceWhenGlobalGateIsOff` (L49-58) | When global gate is off, `checked`=true (preference preserved), `active`=false, `controlEnabled`=false |
| `memoryTableUiActivatesStoredPreferenceWhenGlobalGateReturns` (L61-70) | When global gate returns, `checked`=true, `active`=true, `controlEnabled`=true |
| `empowermentStatsCountOnlyEffectiveMemoryTableCapability` (L73-85) | With global gate on, effective count is 1 higher than with gate off |
| `workspaceSaveReportsMissingAssistantAsFailure` (L88-100) | `persistWorkspaceBinding` returns `Failure` when update returns `NOT_FOUND` |
| `workspaceSaveSuccessCommitsRequestedBinding` (L102-118) | `persistWorkspaceBinding` returns `Success` with correct IDs |
| `empowermentStatsUseTheSameReadyGateAsWorkspaceCapability` (L121-139) | Workspace READY yields 4 more effective tools than DISABLED or missing |

### ToolPermissionPolicyTest.kt Details

**File**: `app/src/test/java/me/rerrere/rikkahub/data/model/ToolPermissionPolicyTest.kt` (44 lines)

| Test (L#) | What |
|---|---|
| `denyRemovesTool` (L10-12) | `DENY` → tool removed from list |
| `askForcesApproval` (L14-17) | `ASK` → `needsApproval` returns true |
| `missingPolicyInheritsToolBehavior` (L19-22) | Missing policy → tool unchanged |
| `catalogProjectionAndOrphansUseStableIds` (L24-43) | `applyPermissions` with DENY → `POLICY_DENIED` reason; `orphanToolPermissionIds` works |

### Test Gaps (No Coverage)

1. **No Compose UI tests** for `AssistantToolsPage` or `AssistantToolsContent` — no screenshot tests, no layout tests
2. **No tests for `copySummary()`** being used in wrong UI context (headline + supporting dual display)
3. **No tests for `ToolGroupCard`** rendering logic (tool description vs reason display, checked/active/controlEnabled states)
4. **No tests for preset UI flow** (apply, preview, save, copy)
5. **No tests for checkbox multi-select** state transitions
6. **No tests for connection status display** (localization of enum names)
7. **No tests for diagnostic expansion** (expanded/collapsed, problems-only filter)
8. **No tests for `toToolConnectionStatus`** or `workspaceConnectionStatus` mapping
9. **No tests for `diffToolPermissionPreset`** or `batchToolPermission` (pure functions, testable)
10. **No tests for `assistantToolCapabilitySnapshot`** with various configurations

### Tests That Should Be Extended

| Test | Extension Needed |
|---|---|
| `AssistantToolsPageTest` | Add UI state verification tests for `memoryTableToolUiState` edge cases; add `ToolGroupUi` state computation tests |
| `ToolPermissionPolicyTest` | Add `diffToolPermissionPreset` test; add `batchToolPermission` test; add `applyToolPermissionPreset` with `REJECTED_RELAXATION` |
| (new) `ToolConnectionStatusTest` | Test `toToolConnectionStatus` mapping; test `workspaceConnectionStatus`; test `aggregateToolConnectionStatus` |
| (new) `ToolDiagnosticsTest` | Test `copySummary` format stays stable; test `diagnostic` chain construction |
| (new) `AssistantToolsPageUITest` | Compose instrumentation test for layout, checkbox visibility, diagnostic card rendering |
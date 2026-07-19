# Research: Domain Logic That Must NOT Change

- **Query**: Note what domain logic must NOT change (ChatService.applyAssistantToolPermissions etc.)
- **Scope**: internal
- **Date**: 2026-07-20

## Findings

### IMMUTABLE: `applyAssistantToolPermissions` in ToolPermissionPolicy.kt

**File**: `app/src/main/java/me/rerrere/rikkahub/data/model/ToolPermissionPolicy.kt` L21-27

```kotlin
fun applyAssistantToolPermissions(
    tools: List<Tool>,
    permissions: Map<String, ToolPermission>,
    capabilityIdFor: (Tool) -> String = { stableCapabilityIdForRuntimeName(it.name) },
): List<Tool> = tools.mapNotNull { tool ->
    val policy = permissions[capabilityIdFor(tool)] ?: ToolPermission.INHERIT
    tool.applyToolPermission(policy)
}
```

**Used by**:
- `ChatService.kt` (actual generation pipeline)
- `SubagentPermissionBuilder.kt` L157: `val parentFiltered = applyAssistantToolPermissions(withSpawn, parentToolPermissions)`

**Must NOT change**: The resolution logic, the DENY→null, ASK→needsApproval, ALLOW/INHERIT→pass-through chain. Any UI fix must not alter how permissions are resolved at runtime.

### IMMUTABLE: `stableCapabilityIdForRuntimeName` in ToolPermissionPolicy.kt

**File**: L37-51

Maps runtime names to stable capability IDs. Used by `applyAssistantToolPermissions` and `ToolCapabilitySnapshot.applyPermissions`. Changing this mapping would break the permission resolution chain.

### IMMUTABLE: `ToolCapabilitySnapshot.applyPermissions` in ToolPermissionPolicy.kt

**File**: L53-66

```kotlin
fun ToolCapabilitySnapshot.applyPermissions(
    permissions: Map<String, ToolPermission>,
): ToolCapabilitySnapshot = ToolCapabilitySnapshot(
    capabilities.map { capability ->
        when (permissions[capability.id] ?: ToolPermission.INHERIT) {
            ToolPermission.DENY -> capability.copy(effective = false, reasonCode = ToolCapabilityReason.POLICY_DENIED)
            ToolPermission.ASK -> capability.copy(approval = ToolApproval.USER)
            ToolPermission.ALLOW, ToolPermission.INHERIT -> capability
        }
    },
)
```

This is called at the end of `assistantToolCapabilitySnapshot()` (L206). Must not change — it determines which capabilities are `effective` in the UI.

### IMMUTABLE: `assistantToolCapabilitySnapshot` in ToolCapabilityCatalog.kt

**File**: L61-206

The central factory function. Must not change its logic. However, the **consumer** of its output (the UI) can interpret the resulting `ToolCapabilitySnapshot` differently (e.g., filtering capabilities for display, computing a different count for the user).

### IMMUTABLE: `copySummary` format in ToolDiagnostics.kt

**File**: L26-33

The `copySummary()` format is a deliberate contract — it excludes runtime names, IDs, URLs, headers, and configuration payloads. The **format** must not change (it's used for diagnostics), but the **UI presentation** can change (e.g., not putting it in `headlineContent` + `supportingContent`).

### IMMUTABLE: `resolveWorkspaceToolCapability` in WorkspaceToolCapability.kt

**File**: L54-91

Used by `ChatService.kt` L1849, `WorkspaceReminderTransformer.kt` L25, and `ToolCapabilityCatalog.kt` L102. Must not change.

### IMMUTABLE: `resolveMemoryCapabilities` in MemoryTable.kt

**File**: L124-131

Used by `ChatService.kt` L1576 and `AssistantToolsPage.kt` L180. Must not change.

### IMMUTABLE: VM save paths

**File**: `AssistantDetailVM.kt`

- `saveToolPermission()` (L345) — single tool permission save
- `saveToolPermissionPreset()` (L380) — preset save
- `batchSetToolPermissions()` (L436) — batch permission set
- `copyToolPermissionsToAssistants()` (L423) — copy to other assistants

These persist to `settingsStore` and must continue to work correctly. The UI can change how they are triggered but not the persistence logic.

### SAFE TO CHANGE (UI-only)

- `AssistantToolsPage.kt` composable functions (layout, styling, visibility)
- `AssistantToolsContent.kt` composable (rendering order, CardGroup usage)
- `ToolGroupCard` composable (checkbox visibility, row content)
- `empowermentToolStats` helper — can be wrapped or replaced with a different count function for the UI, as long as the underlying `assistantToolCapabilitySnapshot` is not altered
- `memoryTableToolUiState` — pure UI state computation, safe to change
- String resources (adding new keys, updating existing values)
- `ToolCapabilityReason.stringResource()` extension — can be updated to return different strings
- `ToolConnectionState` display — can add localized string resources without changing the enum

### SAFE TO ADD NEW

- New `fun uiToolCount(snapshot: ToolCapabilitySnapshot): Pair<Int, Int>` for user-friendly count
- New `@StringRes` mappings for `ToolConnectionState` enum values
- New `@StringRes` mappings for built-in preset names
- New `fun ToolConnectionState.displayString(): String` extension
- Compose UI tests for the tools page
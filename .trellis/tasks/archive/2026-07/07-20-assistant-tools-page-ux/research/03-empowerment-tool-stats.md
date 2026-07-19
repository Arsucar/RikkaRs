# Research: empowermentToolStats and Entry Count Logic

- **Query**: Find empowermentToolStats / entry count logic
- **Scope**: internal
- **Date**: 2026-07-20

## Findings

### Definition

**File**: `AssistantToolsPage.kt` L156-168

```kotlin
fun empowermentToolStats(
    assistant: Assistant,
    workspaces: List<WorkspaceEntity> = emptyList(),
    memoryTableGloballyEnabled: Boolean = true,
    visibleSkills: List<SkillMetadata> = emptyList(),
    mcpServerConfigs: List<McpServerConfig> = emptyList(),
    mcpStatuses: Map<kotlin.uuid.Uuid, McpStatus> = emptyMap(),
): Pair<Int, Int> {
    val snapshot = assistantToolCapabilitySnapshot(
        assistant, memoryTableGloballyEnabled, workspaces, visibleSkills, mcpServerConfigs, mcpStatuses,
    )
    return snapshot.effectiveCount to snapshot.capabilities.size
}
```

Returns `(effectiveCount, totalCapabilities)`.

### Call Sites

| Location | Line | Context |
|---|---|---|
| `AssistantDetailPage.kt` | L151-159 | Entry card badge: `"N/M enabled"` (via `assistant_tools_enabled_count`) |
| `AssistantToolsPage.kt` | L279-281 | TopBar title: `"Assistant tools (N/M)"` (via `assistant_tools_title_with_count`) |

### `effectiveCount` Semantics

Defined in `ToolCapabilitySnapshot` (ToolCapabilityCatalog.kt L33):
```kotlin
val effectiveCount: Int get() = effectiveRuntimeNames.size
```
where `effectiveRuntimeNames` (L31-32) collects **distinct** runtime names of all `effective` capabilities.

### `capabilities.size` Semantics

Total count of ALL capabilities in the snapshot, including:
- `skill:management` (always present, almost always effective)
- 4 workspace tools × unbound workspace (counted even when workspace is NOT_SELECTED)
- All MCP tools from configured servers (even if NOT_SELECTED)
- Local tools, calendar tools, etc.

### The Count Problem

Old behavior: fixed-ish counts like 14/15 (groups were manually hardcoded).

New behavior with `effectiveCount / capabilities.size`: common ratios like 4/12 (when only basic built-in + memory are enabled, but workspace×4, MCP, skills, local tools are all counted in total).

The `total` includes items the user never configured (unbound workspace tools, unselected MCP tools), making the ratio incomprehensible.

### Test Coverage

**File**: `AssistantToolsPageTest.kt`

| Test | Lines | What it verifies |
|---|---|---|
| `titleStatsIncludeDynamicLocalSkillAndMcpFromTheSharedSnapshot` | L24-46 | `empowermentToolStats` matches `snapshot.effectiveCount` and `snapshot.capabilities.size` |
| `empowermentStatsCountOnlyEffectiveMemoryTableCapability` | L73-85 | Global gate correctly subtracts 1 from effective count |
| `empowermentStatsUseTheSameReadyGateAsWorkspaceCapability` | L121-139 | Workspace READY vs DISABLED vs missing yields correct count diff |

### Key Observations

1. `effectiveCount` counts distinct runtime names, not capability IDs. Multiple skills share `use_skill` runtime name → count as 1.
2. `skill:management` is always effective (unless delegate-only) → always contributes to count.
3. The total includes ALL capabilities regardless of `configured` status → confusing for users.
4. Tests verify the function works correctly as designed — the issue is that the **design** produces confusing user-facing counts.
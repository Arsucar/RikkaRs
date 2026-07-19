# Research: UI Structure and Problem Hotspots

- **Query**: Map current UI structure and problem hotspots in AssistantToolsPage.kt
- **Scope**: internal
- **Date**: 2026-07-20

## Findings

### File

`app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantToolsPage.kt` (1003 lines)

### Entry Point

- `AssistantDetailPage.kt` L148-163: navigates to `Screen.AssistantTools(id)`, shows `empowermentToolStats` count as badge text
- `RouteActivity.kt` L436: `AssistantToolsPage(key.id)` registered as `entry<Screen.AssistantTools>`

### Page Structure (Top-to-Bottom)

1. **TopBar** (L277-293): `LargeFlexibleTopAppBar` with `assistant_tools_title_with_count` (format: `enabled/total`), back button, presets button
2. **Content** (L610-904): `AssistantToolsContent` composable with vertical scroll, items in order:
   - **Diagnostics CardGroup** (L776-813): `copySummary()` string in both `headlineContent` and `supportingContent`, trailing "Copy safe summary" button. Expanded detail shows per-capability diagnostics with repair targets.
   - **Presets CardGroup** (L814-828): if `permissionPresets.isNotEmpty()`, shows each preset with apply button
   - **Selected batch card** (L829-836): if `selectedCapabilityIds.isNotEmpty()`, shows "Batch edit selected" button
   - **Tool Group Cards** (L837-845): iterates `groups` list → `ToolGroupCard` for each:
     - Workspace, Memory, Memory Table, Search, Conversations, Skills, Local, MCP, Subagent
   - **MCP Connection CardGroup** (L846-874): selected MCP servers with connection status + test button
   - **Workspace Connection CardGroup** (L876-887): workspace status
   - **Orphan permissions CardGroup** (L888-902): stale permission IDs with clear button
3. **Sheets** (modal overlays): Capability Permission Sheet (L358-392), Workspace Select (L394-416), Preset List (L418-455), Save Preset (L457-490), Preset Preview (L492-525), Batch Permission (L527-546), Copy Targets (L548-606)

### Problem Hotspots (mapped to Issue #163)

| # | Location | Problem |
|---|---|---|
| **H1** | L776-794 | `copySummary()` no-space string in **both** `headlineContent` and `supportingContent` → vertical character stacking on narrow screens |
| **H2** | L776-794 | `copySummary()` format: `tools=12;effective=4;builtin=4;...` — unreadable for users |
| **H3** | L814-828 | Preset display uses raw `preset.name` (English slugs: `readonly-research`, `approval-workspace`, `least-privilege`) |
| **H4** | L954-975 | Tool rows: `reasonCode` overrides `descriptionRes` via `?:` fallback (L968-970); when closed, shows "此助手未选择" instead of actual description |
| **H5** | L954-975 | Tool rows always visible even when group is disabled/closed |
| **H6** | L847-848, L876-878 | MCP groups title "MCP tools" appears twice (once in `groups` L753, once in `selectedMcpServers` L848) |
| **H7** | L859-861 | Connection status: `state?.name` raw enum string (e.g. `IDLE`, `SUCCESS`) — not localized |
| **H8** | L957-961 | Checkbox always visible on every tool row (leading content) — `selectedCapabilityIds` empty by default → full screen of empty checkboxes |
| **H9** | L492-525 | Preset apply: `confirmRelaxation = false` (L512) → `REJECTED_RELAXATION` silently ignored, no snackbar |
| **H10** | L518, L378 | Inconsistent save paths: preset apply uses `vm.update(assistant.copy(...))` (L518) vs single tool uses `vm.saveToolPermission` (L378) |
| **H11** | L795-813 | Diagnostics expanded view uses `CardGroup` per capability (wrong container for detail list) |
| **H12** | L662, L746-753 | MCP `dynamicTools` uses `configuredOnly = false` → all MCP tools shown even when not selected |

### Data Flow

```
Assistant (state) → assistantToolCapabilitySnapshot() → ToolCapabilitySnapshot
  → snapshot.diagnostics() → ToolDiagnosticsSnapshot
  → snapshot.capabilities → ToolGroupCard rendering
  → snapshot.effectiveCount → TopBar title count
```

### State Variables (Local)

- `selectedCapabilityIds: Set<String>` — checkbox multi-select state (L230)
- `selectedCapability: ToolCapability?` — permission edit sheet trigger (L229)
- `presetToPreview: ToolPermissionPreset?` — preset diff preview (L231)
- `showPresetSheet`, `showSavePresetSheet`, `showCopyTargetsSheet`, `showBatchPermissionSheet` — modal visibility (L232-235)
- `editingPreset`, `selectedTargetIds`, `presetName` — preset editing state (L237-238)
- `diagnosticsExpanded`, `problemsOnly` — diagnostics filter (L773-774)
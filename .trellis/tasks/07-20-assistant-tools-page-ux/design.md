# Design: Assistant tools page UX (#163)

## Boundaries

| Layer | Change? | Notes |
|---|---|---|
| UI `AssistantToolsPage.kt` | Yes | Primary surface |
| UI helpers (count, labels, multi-select) | Yes | Prefer internal pure functions for tests |
| String resources | Yes | Preset names, connection states, advanced section |
| `ToolPermissionPreset` display | UI mapping preferred | Keep slug/name storage; map built-in IDs/names to `@StringRes` |
| `ToolConnectionStatus` | UI mapping | Extension or when-map in UI layer |
| Domain catalog / policy | No | Freeze list in research/07 |

## Key design decisions

### 1. User-facing count
```kotlin
fun userFacingToolStats(snapshot: ToolCapabilitySnapshot): Pair<Int, Int> {
  val total = snapshot.capabilities.count { it.configured || it.effective }
  return snapshot.effectiveCount to total.coerceAtLeast(snapshot.effectiveCount)
}
```
`empowermentToolStats` either switches to this or adds a parallel helper used by detail entry + top bar. Prefer updating `empowermentToolStats` + tests so both call sites stay aligned.

### 2. Diagnostics display
- Headline: localized readable summary from effective + userFacing total.
- Supporting: optional short status (problems count) — not raw copySummary.
- Trailing: copy button → clipboard = `diagnostics.copySummary()`.

### 3. Tool row content
- Headline: description from `descriptionRes` / capability description.
- Supporting or Tag: localized reason/status.
- Never `reason ?: description`.

### 4. Group collapse
- When group switch off / not configured for workspace/memory: hide child tool rows (or show empty collapsed body).
- Group header switch remains.

### 5. Multi-select mode
```
var selectionMode by remember { mutableStateOf(false) }
var selectedCapabilityIds by remember { mutableStateOf(emptySet()) }
// long press → selectionMode=true + add id
// when selectionMode false → clear selected set; no leading checkbox
// top bar or batch card: Done exits selectionMode
```

### 6. Preset apply UX
- Preview sheet shows Chinese name.
- On apply:
  - compute `diffToolPermissionPreset` / result
  - if relaxation needed → AlertDialog confirm → re-apply with `confirmRelaxation=true`
  - if REJECTED_RELAXATION without confirm → snackbar reason
  - success → snackbar + save via same path as today (`vm.update` OK if current domain returns updated assistant; prefer consistency with single-tool save when easy)

### 7. Advanced section
Single collapsible block wrapping:
- diagnostics card
- presets
- MCP connection
- workspace connection
- orphan permissions

Main path starts with tool group cards.

### 8. MCP list
`configuredOnly = true` by default; local toggle expands to all.

## Compatibility / rollback
- UI-only + strings + pure helpers.
- Revert page composable restores old UX; domain untouched.
- Preset UUID/slug storage unchanged.

## Risks
- Count formula change affects detail badge — update both + tests.
- Long-press must not block scroll/gesture badly; use standard ListItem long click.

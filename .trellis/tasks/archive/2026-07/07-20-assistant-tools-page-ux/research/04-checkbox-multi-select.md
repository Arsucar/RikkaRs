# Research: Checkbox Multi-Select and batchSetToolPermissions

- **Query**: Find checkbox multi-select / selectedCapabilityIds / batchSetToolPermissions
- **Scope**: internal
- **Date**: 2026-07-20

## Findings

### Checkbox Multi-Select State

**File**: `AssistantToolsPage.kt`

- **State declaration** (L230): `var selectedCapabilityIds by remember { mutableStateOf<Set<String>>(emptySet()) }`
- **Toggle handler** (L329-333): `onToggleCapabilitySelected` adds/removes from set
- **Passed to** `AssistantToolsContent` as `selectedCapabilityIds` and `onToggleCapabilitySelected`

### Checkbox Rendering in ToolGroupCard

**File**: `AssistantToolsPage.kt` L957-961

```kotlin
leadingContent = {
    Checkbox(
        checked = tool.capabilityId in selectedCapabilityIds,
        onCheckedChange = { onToggleCapabilitySelected(tool.capabilityId) },
    )
},
```

Every tool row gets a Checkbox as leading content. Default state is `emptySet()` → all checkboxes are empty. This is the **always-visible empty checkbox** problem from the issue comment.

### Batch Action Card

**File**: `AssistantToolsPage.kt` L829-836

```kotlin
if (selectedCapabilityIds.isNotEmpty()) {
    CardGroup(title = { Text(stringResource(R.string.assistant_tools_selected_count, selectedCapabilityIds.size)) }) {
        item(
            headlineContent = { Text(stringResource(R.string.assistant_tools_preset_batch)) },
            trailingContent = { TextButton(onClick = onBatchEdit) { Text(stringResource(R.string.assistant_tools_preset_batch)) } },
        )
    }
}
```

Shows count of selected items and a "Batch edit" button. Card appears above the tool groups.

### Batch Permission Sheet

**File**: `AssistantToolsPage.kt` L527-546

```kotlin
if (showBatchPermissionSheet) {
    ModalBottomSheet(onDismissRequest = { showBatchPermissionSheet = false }) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.assistant_tools_preset_batch), style = MaterialTheme.typography.titleMedium)
            ToolPermission.entries.forEach { permission ->
                TextButton(onClick = {
                    scope.launch {
                        vm.batchSetToolPermissions(
                            selectedCapabilityIds,
                            permission,
                            capabilitySnapshot.capabilities.map { it.id }.toSet(),
                        )
                    }
                    showBatchPermissionSheet = false
                    selectedCapabilityIds = emptySet()
                }) { Text(toolPermissionLabel(permission)) }
            }
        }
    }
}
```

After applying, `selectedCapabilityIds` is cleared to `emptySet()`.

### batchSetToolPermissions in VM

**File**: `AssistantDetailVM.kt` L436-453

```kotlin
suspend fun batchSetToolPermissions(
    capabilityIds: Set<String>,
    permission: ToolPermission,
    knownCapabilityIds: Set<String>,
): ToolPresetTargetResult? {
    val current = settingsStore.settingsFlow.value.assistants.firstOrNull { it.id == assistantId } ?: return null
    val result = batchToolPermission(current, capabilityIds, permission, knownCapabilityIds)
    settingsStore.updateAssistantConfig(
        current.copy(
            toolPermissions = if (permission == ToolPermission.INHERIT) {
                current.toolPermissions - result.changed.keys
            } else {
                current.toolPermissions + result.changed
            },
        )
    )
    return result
}
```

Uses `batchToolPermission()` from `ToolPermissionPreset.kt` (L134-152), which filters by `knownCapabilityIds` and returns `SKIPPED_UNKNOWN` for unknown IDs.

### Issue Comment Requirement

From issue #163 comment:
> 1. Default: no checkbox (no leading multi-select)
> 2. Long press tool row → enters multi-select mode, shows checkboxes
> 3. Exit multi-select → restore default list
> 4. Non-multi-select: tap row → single tool permission sheet
> 5. Batch permission ability preserved, entry changed to long-press triggered

### Key Interactions

- Single tool permission edit: `onSelectCapability` → `selectedCapability` state → ModalBottomSheet (L358-392)
- Multi-select checkbox: `onToggleCapabilitySelected` → `selectedCapabilityIds` set
- Batch apply: `onBatchEdit` → `showBatchPermissionSheet` → `vm.batchSetToolPermissions`
- Both use `ToolPermission.entries` for radio/button options
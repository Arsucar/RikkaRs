# Research: WorkspaceDetailPage + AssistantToolsPage skill_tool sheet

- **Query**: WorkspaceToolApprovalCard; AssistantToolsPage skill_tool permission sheet
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/.../workspace/WorkspaceDetailPage.kt` | `WorkspaceToolApprovalCard` |
| `app/src/main/java/.../workspace/WorkspaceDetailVM.kt` | `setToolApproval` |
| `app/src/main/java/.../assistant/detail/AssistantToolsPage.kt` | Capability permission bottom sheet |
| `app/src/main/java/.../data/model/ToolCapabilityCatalog.kt` | `skill:management` / `skill_tool` |
| `app/src/main/java/.../data/ai/tools/SkillManagementTools.kt` | `skill_tool` hard `needsApproval = { true }` |
| `app/src/main/res/values*/strings.xml` | Approval + permission strings |

### Code Patterns

#### WorkspaceToolApprovalCard (management entry for tool-name toggles)

```522:599:app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceDetailPage.kt
item {
    WorkspaceToolApprovalCard(
        workspace = workspace,
        onToolApprovalChange = onToolApprovalChange,
    )
}

private fun WorkspaceToolApprovalCard(...) {
    // title + desc from workspace_detail_tool_approval*
    workspaceToolApprovalItems().forEach { (toolName, label) ->
        Switch(
            checked = resolveWorkspaceToolApproval(toolName, overrides),
            onCheckedChange = { onToolApprovalChange(toolName, it) },
        )
    }
}

private fun workspaceToolApprovalItems() = listOf(
    "workspace_read_file" to stringResource(...),
    "workspace_write_file" to ...,
    "workspace_edit_file" to ...,
    "workspace_shell" to ...,
)
```

- Issue #258: add **adjacent card** “受信写入目录” listing trusted prefixes with per-item delete.
- Empty list / loading / delete confirm are required UX states.

#### AssistantToolsPage permission sheet (lines 439–472)

```439:471:app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantToolsPage.kt
selectedCapability?.let { capability ->
    val currentPermission = assistant.toolPermissions[capability.id] ?: ToolPermission.INHERIT
    val sourceDefault = if (capability.approval == ToolApproval.USER) {
        stringResource(R.string.assistant_tools_permission_ask)
    } else {
        stringResource(R.string.assistant_tools_permission_allow)
    }
    ModalBottomSheet(...) {
        // displayName, source default, effective, next-generation note
        // RadioButtons for ToolPermission.entries
    }
}
```

- Generic for **all** capabilities; no skill_tool-specific copy today.
- Issue #258 AC5: when capability is `skill:management` / `skill_tool`, add explanatory string that hard approval still applies and trusted roots are configured on workspace detail page.

#### skill_tool hard approval source

```155:164:app/src/main/java/me/rerere/rikkahub/data/model/ToolCapabilityCatalog.kt
add(
    id = "skill:management",
    source = ToolCapabilitySource.SKILL,
    runtimeName = "skill_tool",
    ...
    approval = ToolApproval.USER,
)
```

```82:82:app/src/main/java/me/rerere/rikkahub/data/ai/tools/SkillManagementTools.kt
needsApproval = { true },
```

- Catalog marks USER approval; tool always returns true from `needsApproval`.
- Path-based trusted roots **do not apply** to `skill_tool` (separate write path via SkillManager, not workspace_write_file).

### Related Specs

- Not found.

## Caveats / Not Found

- Strings exist for tool approval toggles (`workspace_detail_tool_*`) and permission sheet (`assistant_tools_permission_*`); no trusted-root strings yet (zh/en + other locales per project i18n practice).
- Workspace detail card currently only toggles **name-level** approval; path-level hard gate remains even if write/edit switch is off (default false).

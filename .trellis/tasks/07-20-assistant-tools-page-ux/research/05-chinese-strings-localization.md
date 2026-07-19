# Research: Chinese Strings and Localization Patterns

- **Query**: Find Chinese strings / localization patterns for presets and connection status
- **Scope**: internal
- **Date**: 2026-07-20

## Findings

### String Resource Files

| File | Language | Total `assistant_tools_*` keys |
|---|---|---|
| `app/src/main/res/values/strings.xml` | English (default) | 78 |
| `app/src/main/res/values-zh/strings.xml` | Simplified Chinese | 78 |
| `app/src/main/res/values-zh-rTW/strings.xml` | Traditional Chinese | 78 |

### Missing Localizations

#### Preset Names (Hardcoded English)

Preset names are **hardcoded** in `ToolPermissionPreset.kt` L41-76 and displayed directly via `preset.name` in the UI:

| Preset ID | English Name | Has Chinese? |
|---|---|---|
| `readonly-research` | "readonly-research" | ❌ None |
| `approval-workspace` | "approval-workspace" | ❌ None |
| `least-privilege` | "least-privilege" | ❌ None |

These are displayed as raw English slugs (`preset.name` field) in:
- `AssistantToolsPage.kt` L436: `Text(preset.name)` in preset sheet
- `AssistantToolsPage.kt` L818: `Text(preset.name)` in presets CardGroup
- `AssistantToolsPage.kt` L500: `Text(preset.name)` in preview sheet

**No string resources exist** for preset display names. Fix requires either:
- Adding `@StringRes` to each preset and localizing, OR
- Adding string resources with keys like `assistant_tools_preset_readonly_research`, `assistant_tools_preset_approval_workspace`, `assistant_tools_preset_least_privilege`

#### Connection Status (Raw Enum)

Connection status is displayed as raw `state.name`:
- `AssistantToolsPage.kt` L860: `status?.state?.name ?: ToolConnectionState.IDLE.name`

This produces English enum names like `IDLE`, `CONNECTING`, `SUCCESS`, `NEEDS_AUTHORIZATION`, `NETWORK_ERROR`, `PROTOCOL_ERROR`, `ERROR`, `EMPTY`.

**No string resources exist** for `ToolConnectionState` values. Need to add like:
- `assistant_tools_connection_state_idle`
- `assistant_tools_connection_state_connecting`
- etc.

### Existing String Resources (Key Reference)

All strings follow the `assistant_tools_` prefix convention. Key categories:

**Page titles**:
- `assistant_tools_title` = "Assistant tools" / "赋能工具"
- `assistant_tools_title_with_count` = "Assistant tools (%1$d/%2$d)" / "赋能工具（%1$d/%2$d）"
- `assistant_tools_enabled_count` = "%1$d/%2$d enabled" / "%1$d/%2$d 已启用"

**Tool descriptions** (L1868-1891 in English):
- `assistant_tools_workspace_read_file_desc`, `_write_file_desc`, `_edit_file_desc`, `_shell_desc`
- `assistant_tools_memory_desc`, `_memory_table_desc`
- `assistant_tools_search_web_desc`, `_scrape_web_desc`
- `assistant_tools_recent_chats_desc`, `_conversation_search_desc`
- `assistant_tools_use_skill_desc`
- `assistant_tools_spawn_subagent_desc`, `_ask_btw_desc`, `_manage_subagent_profile_desc`

**Reason codes** (L1857-1867 + L1905):
- `assistant_tools_reason_available` through `assistant_tools_reason_policy_denied`

**Permissions** (L2047-2053):
- `assistant_tools_permission_inherit`, `_allow`, `_ask`, `_deny`

**Presets** (L2061-2072):
- `assistant_tools_presets_title`, `_preset_apply`, `_preset_save`, `_preset_copy`, `_preset_batch`, `_preset_name`, `_preset_preview`, `_preset_relaxation`, `_preset_targets`, `_preset_results`

**Diagnostics** (L2057-2075):
- `assistant_tools_diagnostics_title`, `_copy`, `_details`, `_problems_only`, `_repair`

**Connection** (L2059-2060):
- `assistant_tools_connection_test`, `_status`

### User-Created Presets

User presets (`vmSettings.toolPermissionPresets`) have arbitrary names set by the user via `assistant_tools_preset_name` text field. These are NOT localizable and remain as-entered.

### Built-in Preset Description

Built-in presets have English descriptions in code:
- `readonly-research`: "Read-only research tools"
- `approval-workspace`: "Workspace tools require approval"
- `least-privilege`: "Deny every known capability unless explicitly allowed"

These descriptions are displayed via `Text(preset.description)` in the UI (L437, L819). They should also be localized.
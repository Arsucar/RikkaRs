# Research: WorkspaceTools writable roots & needsApproval

- **Query**: feat #258 trusted write roots — WorkspaceTools needsApproval, WRITABLE_ROOT_PREFIXES, pathOutsideWritableRoots
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt` | Workspace tool factory + hard path approval gate |
| `app/src/test/java/me/rerere/rikkahub/data/ai/tools/WorkspaceToolsTest.kt` | Unit tests (image path / shell metadata only; no prefix tests) |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | Assembles tools via `createWorkspaceToolsIfReady` |
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt` | Subagent path: may AUTO-clear needsApproval |

### Code Patterns

#### Default per-tool approval map (tool-name level)

```32:40:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read_file" to false,
    "workspace_write_file" to false,
    "workspace_edit_file" to false,
    "workspace_shell" to true,
)

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false
```

- `workspace_write_file` / `workspace_edit_file` default **do not** need name-level approval.
- Path-level hard gate still forces approval outside `/workspace` and `/tmp`.

#### Tool assembly

```48:68:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
suspend fun createWorkspaceTools(
    workspaceId: String?,
    workspaceRepository: WorkspaceRepository,
    cwd: String? = null,
    knownMounts: List<WorkspaceKnownMount> = emptyList(),
    extraBindMounts: List<WorkspaceBindMount> = emptyList(),
    approvalOverrides: Map<String, Boolean>? = null,
): List<Tool> {
    // ...
    fun needsApproval(name: String) = resolveWorkspaceToolApproval(name, resolvedApprovalOverrides)
    return listOf(
        createReadFileTool(...),
        createWriteFileTool(...),
        createEditFileTool(...),
        createShellTool(...),
    )
}
```

- `approvalOverrides` comes from `WorkspaceEntity.toolApprovalOverrides()` (ChatService passes them at `:2112`).
- **No trusted-roots parameter exists today.**

#### Hard path gate on write/edit

```145:145:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
needsApproval = { needsApproval("workspace_write_file") || it.pathOutsideWritableRoots("path") },
```

```190:190:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
needsApproval = { needsApproval("workspace_edit_file") || it.pathOutsideWritableRoots("path") },
```

- Shell uses name-only: `needsApproval = { needsApproval("workspace_shell") }` (no path roots).
- Read uses name-only.

#### Prefix match rules (current)

```525:537:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
// 免强制审批的可写安全区: 工作区文件目录, 以及临时目录 /tmp
private val WRITABLE_ROOT_PREFIXES = listOf("/workspace", "/tmp")

private fun kotlinx.serialization.json.JsonElement.pathOutsideWritableRoots(name: String): Boolean =
    runCatching {
        jsonObject.absolutePath(name).isOutsideWritableRoots()
    }.getOrDefault(true)

private fun String.isOutsideWritableRoots(): Boolean {
    val normalized = trimEnd('/').ifBlank { "/" }
    return WRITABLE_ROOT_PREFIXES.none { prefix ->
        normalized == prefix || normalized.startsWith("$prefix/")
    }
}
```

| Input path | Outside? | Reason |
|---|---|---|
| `/workspace` | false | exact match |
| `/workspace/foo` | false | `$prefix/` |
| `/tmp` | false | exact match |
| `/tmp/x` | false | `$prefix/` |
| `/skills` | true | not in list |
| `/skills/my-skill` | true | not in list |
| `/skills_private/x` | true | not in list |
| `/workspace-extra` | true | **not** matched by `/workspace` (requires `/` separator) |
| invalid / missing path | true | `getOrDefault(true)` on parse failure |

`absolutePath` also enforces absolute path starting with `/`, no NUL, backslash→slash.

#### ChatService wiring

```2097:2113:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
val all = createWorkspaceTools(
    workspaceId = workspaceId,
    workspaceRepository = workspaceRepository,
    cwd = cwd,
    knownMounts = listOf(
        WorkspaceKnownMount(target = "/skills", source = skillManager.getSkillsDir(...), ...),
        uploadKnownMount(),
    ) + privateSkillMounts.knownMounts,
    extraBindMounts = privateSkillMounts.bindMounts,
    approvalOverrides = resolvedWorkspace.toolApprovalOverrides(),
)
```

- `/skills` and `/skills_private` are **mounted** for read, but are **outside** WRITABLE_ROOT_PREFIXES, so every write/edit under them forces approval.

#### Generation hard stop on Pending

```283:317:app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt
toolDef?.needsApproval(tool.inputAsJson()) == true &&
    tool.approvalState is ToolApprovalState.Auto -> {
    hasPendingApproval = true
    tool.copy(approvalState = ToolApprovalState.Pending)
}
// ...
if (hasPendingApproval) {
    break  // wait for user
}
```

- Approval granularity is **per toolCallId** (no directory memory).

#### Subagent caveat

- `WorkspaceApproval.AUTO` in `SubagentPermissionBuilder.applySubagentWorkspaceApproval` sets `needsApproval = { false }` entirely — bypasses path gate for subagents only.
- Issue #258 targets main-assistant HITL, not this path.

### External References

- GitHub issue #258 (arsucar/rikkahub): trusted write root prefix for multi-step skill authoring.

### Related Specs

- None under `.trellis/spec/` specific to trusted write roots (not found).

## Caveats / Not Found

- `pathOutsideWritableRoots` / `isOutsideWritableRoots` / `WRITABLE_ROOT_PREFIXES` are **private**; no unit tests cover prefix boundary today.
- Trusted roots list does not exist; issue wants:  
  `needsApproval(name) || (pathOutsideWritableRoots(path) && !inTrustedRoots(path))`  
  with trusted roots unioned into effective free-write prefixes.

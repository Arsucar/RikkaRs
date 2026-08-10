# Research: ToolPermissionPolicy ALLOW vs hard approval

- **Query**: ToolPermissionPolicy ALLOW never overrides hard approval
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/model/ToolPermissionPolicy.kt` | Policy apply + stable IDs |
| `app/src/test/java/me/rerere/rikkahub/data/model/ToolPermissionPolicyTest.kt` | Basic policy tests |
| `ai/src/main/java/me/rerere/ai/core/Tool.kt` | `needsApproval: (JsonElement) -> Boolean` |

### Code Patterns

#### Explicit contract (line 5 comment + implementation)

```5:19:app/src/main/java/me/rerere/rikkahub/data/model/ToolPermissionPolicy.kt
/** Stable-key policy resolution. ALLOW never overrides a tool's hard approval requirement. */
fun resolveToolPermission(...): ToolPermission = ...

fun Tool.applyToolPermission(permission: ToolPermission): Tool? = when (permission) {
    ToolPermission.DENY -> null
    ToolPermission.ASK -> copy(needsApproval = { true })
    ToolPermission.ALLOW, ToolPermission.INHERIT -> this  // keeps original needsApproval
}
```

| Policy | Effect on `needsApproval` |
|---|---|
| DENY | Tool removed from list |
| ASK | Forced `{ true }` |
| ALLOW | **Unchanged** (tool’s own lambda kept) |
| INHERIT | **Unchanged** |

Therefore:

- Setting AssistantToolsPage **ALLOW** on `workspace:workspace_write_file` does **not** disable `pathOutsideWritableRoots`.
- Setting ALLOW on `skill:management` does **not** disable `skill_tool`’s `{ true }`.
- Trusted write roots must be implemented **inside** the tool’s own `needsApproval` lambda (or a factory param feeding it), not via ToolPermission ALLOW.

#### Stable capability IDs

```37:51:app/src/main/java/me/rerere/rikkahub/data/model/ToolPermissionPolicy.kt
fun stableCapabilityIdForRuntimeName(name: String): String = when (name) {
    ...
    "skill_tool" -> "skill:management"
    ...
    else -> when {
        name.startsWith("workspace_") -> "workspace:$name"
        else -> "local:$name"
    }
}
```

#### Catalog projection of ASK

```57:64:app/src/main/java/me/rerere/rikkahub/data/model/ToolPermissionPolicy.kt
ToolPermission.ASK -> capability.copy(approval = ToolApproval.USER)
ToolPermission.ALLOW, ToolPermission.INHERIT -> capability  // keeps source approval
```

### Related Specs

- Issue references security boundary from #154/#36: do not change ALLOW to override hard approval.

## Caveats / Not Found

- Unit tests cover DENY / ASK / inherit-default, but **do not** assert that ALLOW preserves a tool with `needsApproval = { true }`.
- Subagent `WorkspaceApproval.AUTO` **does** replace `needsApproval` with `{ false }` — separate mechanism, out of ToolPermissionPolicy.

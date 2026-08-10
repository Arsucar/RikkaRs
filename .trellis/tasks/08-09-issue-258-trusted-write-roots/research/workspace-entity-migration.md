# Research: WorkspaceEntity toolApprovals + Room version

- **Query**: WorkspaceEntity toolApprovals field + latest Room migration version
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/db/entity/WorkspaceEntity.kt` | Entity + toolApprovals JSON |
| `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt` | `version = 50` |
| `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_49_50.kt` | Latest manual migration |
| `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` | Registers migrations through 49→50 |
| `app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceRepository.kt` | `setToolApproval` pattern to mirror |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceDetailVM.kt` | UI VM for approval toggles |
| `app/src/androidTest/.../Migration_46_47_Test.kt` | Migration test template |

### Code Patterns

#### Existing entity field (approval overrides only)

```33:39:app/src/main/java/me/rerere/rikkahub/data/db/entity/WorkspaceEntity.kt
// 工具审批的用户覆盖项 (toolName -> needsApproval)，未覆盖的工具沿用默认值
@ColumnInfo("tool_approvals", defaultValue = "{}")
val toolApprovals: String = "{}",
) {
    fun toolApprovalOverrides(): Map<String, Boolean> = runCatching {
        JsonInstant.decodeFromString<Map<String, Boolean>>(toolApprovals)
    }.getOrDefault(emptyMap())
```

- Stored as JSON string on column `tool_approvals`.
- Issue #258: add **sibling** field for trusted write roots (not inside toolApprovals map).

#### Repository write pattern to mirror

```105:114:app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceRepository.kt
suspend fun setToolApproval(id: String, toolName: String, needsApproval: Boolean): Boolean {
    val workspace = dao.getById(id) ?: return false
    val overrides = workspace.toolApprovalOverrides() + (toolName to needsApproval)
    dao.upsert(
        workspace.copy(
            toolApprovals = JsonInstant.encodeToString(overrides),
            updatedAt = System.currentTimeMillis(),
        )
    )
    return true
}
```

Expected parallel APIs (from issue):

- `addTrustedWriteRoot(id, root: String): Boolean`
- `removeTrustedWriteRoot(id, root: String): Boolean`
- decode helper e.g. `trustedWriteRoots(): List<String>`

#### Room version

| Item | Value |
|---|---|
| Current `AppDatabase.version` | **50** |
| Latest migration object | `Migration_49_50` (conversation variables) |
| Next migration number | **50 → 51** (`Migration_50_51`) |
| Registration | `DataSourceModule` `.addMigrations(... Migration_49_50, Migration_50_51)` |
| Auto-migrations | Only up through 41→42; recent changes are **manual** migrations |

#### Suggested entity field (from issue body)

```kotlin
// JSON list of trusted absolute root prefixes, e.g. ["/skills/my-skill"]
@ColumnInfo("trusted_write_roots", defaultValue = "[]")
val trustedWriteRoots: String = "[]",
```

Helpers:

```kotlin
fun trustedWriteRootList(): List<String> = runCatching {
    JsonInstant.decodeFromString<List<String>>(trustedWriteRoots)
}.getOrDefault(emptyList())
```

Migration sketch:

```sql
ALTER TABLE workspaces ADD COLUMN trusted_write_roots TEXT NOT NULL DEFAULT '[]'
```

- Old rows → empty list (default).
- Workspace row delete cascades trusted roots with the workspace (no separate table).

#### Detail VM pattern

```204:209:app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceDetailVM.kt
fun setToolApproval(toolName: String, needsApproval: Boolean) {
    viewModelScope.launch {
        val workspace = state.value.workspace ?: return@launch
        repository.setToolApproval(workspace.id, toolName, needsApproval)
        loadWorkspace()
    }
}
```

### Related Specs

- Not found.

## Caveats / Not Found

- No existing migration currently touches `tool_approvals` column (column introduced earlier; search only hits entity + repository).
- Instrument migration tests exist for several hops (e.g. 46→47) but **not** for 49→50; template still usable for 50→51.
- `Workspace.toWorkspace()` does **not** export toolApprovals or trusted roots to domain `Workspace` model — only entity carries them; keep storage on entity unless domain needs it.

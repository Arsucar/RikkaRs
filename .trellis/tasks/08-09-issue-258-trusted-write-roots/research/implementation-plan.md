# Research: Implementation plan for #258 trusted write roots

- **Query**: Return implementation plan — entity field, migration number, approval UI hook, prefix match rules
- **Scope**: mixed (code map + issue #258 requirements)
- **Date**: 2026-08-09

## Findings

### Problem (as-is)

1. Write/edit under `/skills`, `/skills_private`, bind-mounts, etc. always hit hard approval via `pathOutsideWritableRoots`.
2. Each small write is a separate toolCallId → GenerationHandler pauses → user taps ✅ repeatedly.
3. Assistant Tools **ALLOW** cannot relax hard approval (`ToolPermissionPolicy`).

### Target behavior (issue AC)

| AC | Behavior |
|---|---|
| AC1 | First write under `/skills/x` still Pending; after “始终允许”, same prefix auto-passes later (cross-session) |
| AC2 | Workspace detail lists/deletes trusted prefixes |
| AC3 | `workspace_shell` / `skill_tool` never get always-allow |
| AC4 | `/workspace` `/tmp` unchanged; `/skills` ≠ `/skills-private` boundary |
| AC5 | ALLOW semantics unchanged; skill_tool sheet gets explanatory copy |
| AC6 | Room migrate old DBs; revoke restores per-call approval |

---

### 1. Entity field

**File:** `WorkspaceEntity.kt`

| Field | Column | Type | Default | Decode |
|---|---|---|---|---|
| `trustedWriteRoots` | `trusted_write_roots` | `String` (JSON) | `"[]"` | `List<String>` via `JsonInstant` |

Keep beside `toolApprovals`. Do not overload the toolApprovals map.

Helpers:

- `fun trustedWriteRootList(): List<String>`
- Optional normalize on write: absolute path, trim trailing `/`, reject `..` / blank.

**Repository** (`WorkspaceRepository.kt`):

- `addTrustedWriteRoot(id, root): Boolean`
- `removeTrustedWriteRoot(id, root): Boolean`
- (optional) `setTrustedWriteRoots(id, roots): Boolean`

**VM** (`WorkspaceDetailVM.kt`):

- Mirror `setToolApproval` → `addTrustedWriteRoot` / `removeTrustedWriteRoot` + `loadWorkspace()`.

---

### 2. Migration number

| Item | Value |
|---|---|
| Current DB version | **50** |
| New version | **51** |
| Migration object | `Migration_50_51 : Migration(50, 51)` |
| SQL | `ALTER TABLE workspaces ADD COLUMN trusted_write_roots TEXT NOT NULL DEFAULT '[]'` |
| Wire-up | `AppDatabase.version = 51`; `DataSourceModule.addMigrations(..., Migration_50_51)` |
| Test | Instrument `Migration_50_51_Test` following `Migration_46_47_Test` |

---

### 3. Approval UI hook

#### A. Chat pending card (primary)

**File:** `ChatMessageTools.kt` (`ChatMessageToolStep`)

When:

- `approvalState is Pending`
- `tool.toolName` ∈ `workspace_write_file`, `workspace_edit_file`
- path from args is outside built-in writable roots (`/workspace`, `/tmp`)

Show secondary action next to ✅: “始终允许此目录”.

Flow:

1. Confirm dialog (risk copy: executable + shared for skills).
2. Callback e.g. `onTrustWriteRootAndApprove(toolCallId, rootPrefix)` **or** extend approval API with optional `trustedRoot`.
3. Service: persist root on bound workspace → set Approved → continue generation (existing path).

**Derive root prefix from path** (recommend):

- Prefer longest sensible directory prefix for skill authoring, e.g. parent of file or first two segments (`/skills/my-skill` for `/skills/my-skill/SKILL.md`).
- Issue text says “该目录前缀”; implement explicit normalization helper + unit tests.
- Reject paths with `..` after normalize.

**Do not show** for: shell, skill_tool, read, paths already free (`/workspace`, `/tmp`).

#### B. Workspace detail (management)

**File:** `WorkspaceDetailPage.kt` next to `WorkspaceToolApprovalCard`

- New card: list trusted prefixes, delete with confirm, empty state.

#### C. Assistant tools sheet (copy only)

**File:** `AssistantToolsPage.kt` sheet ~453–471

- If `capability.id == "skill:management"` (or runtimeName `skill_tool`): extra Text that hard approval remains; configure trusted roots on workspace detail for **workspace_write/edit** paths.

#### D. Service/VM chain

| Layer | Change |
|---|---|
| ChatMessageTools | Secondary button + confirm |
| ChatPage | Wire new callback |
| ChatVM | `trustWriteRootAndApprove` or extended `handleToolApproval` |
| ChatService | Persist trusted root using conversation’s assistant `workspaceId`, then existing approval resume |
| ConversationRoutes (optional) | Only if web needs same feature |

---

### 4. Prefix match rules

Reuse / generalize current logic:

```kotlin
// Built-in free roots (unchanged)
val BUILTIN = listOf("/workspace", "/tmp")

fun String.matchesRootPrefix(prefix: String): Boolean {
    val n = trimEnd('/').ifBlank { "/" }
    val p = prefix.trimEnd('/').ifBlank { "/" }
    return n == p || n.startsWith("$p/")
}

fun String.needsPathHardApproval(
    trustedRoots: List<String>,
): Boolean {
    val free = BUILTIN + trustedRoots
    return free.none { matchesRootPrefix(it) }
}
```

Write/edit `needsApproval` becomes:

```kotlin
needsApproval = { args ->
    val nameRequires = needsApproval("workspace_write_file") // tool-name override
    val path = ...
    nameRequires || (path.isOutsideBuiltinWritableRoots() && !path.isUnderAny(trustedRoots))
}
```

Equivalent form from issue:

```text
needsApproval(name) || (pathOutsideWritableRoots(path) && !inTrustedRoots(path))
```

where `pathOutsideWritableRoots` stays **builtin-only** (`/workspace`,`/tmp`), and trusted roots are a second check.

**Boundary cases (must unit-test):**

| Path | Trusted | Builtin | Approve? |
|---|---|---|---|
| `/workspace/a` | — | free | no |
| `/tmp/a` | — | free | no |
| `/skills/x` | — | outside | **yes** |
| `/skills/x` | `["/skills"]` | outside | **no** |
| `/skills-private/x` | `["/skills"]` | outside | **yes** (no false prefix) |
| `/skills_private/x` | `["/skills"]` | outside | **yes** |
| `/skills/x/y` | `["/skills/x"]` | outside | **no** |
| `/skills/x2` | `["/skills/x"]` | outside | **yes** |

Pass `trustedRoots` into `createWorkspaceTools` (from entity), same site as `approvalOverrides` (`ChatService.createWorkspaceToolsIfReady`, `SubagentPermissionBuilder` if needed).

**Subagent note:** AUTO still zeroes approval; trusted roots mainly affect INHERIT/main assistant path.

---

### 5. Layered file touch list

| Area | Files |
|---|---|
| Domain match | Extract pure functions (prefer public/internal for tests) from `WorkspaceTools.kt` or new `TrustedWriteRoots.kt` |
| Data | `WorkspaceEntity`, `Migration_50_51`, `AppDatabase`, `DataSourceModule`, `WorkspaceRepository` |
| Tools | `WorkspaceTools.createWorkspaceTools` + write/edit lambdas; ChatService factory call |
| Chat UI | `ChatMessageTools`, `ChatPage`, `ChatVM`, `ChatService.handleToolApproval` (or sibling) |
| Detail UI | `WorkspaceDetailPage`, `WorkspaceDetailVM` |
| Tools page copy | `AssistantToolsPage` + strings |
| Tests | Prefix unit tests; optional migration instrument test; policy ALLOW regression optional |

### 6. Non-goals / explicit denials from issue

- Do **not** change ALLOW to override hard approval.
- Do **not** add always-allow for shell or skill_tool.
- Session-only trust is not the main design (Room persistence is).
- Subagent AUTO is not the main solution for skill authoring on main assistant.

## Caveats / Not Found

- Exact “directory” granularity when trusting (file path vs parent dir vs skill root) needs product choice; issue examples use skill directory prefixes.
- Tools are recreated per generation; trusted list must be read from Room **at tool assembly time** so new trusts apply next generation (after approve+persist, next write in same session may need tools rebuilt — ChatService typically rebuilds on continuation; verify continuation path reloads workspace entity).
- PRD under task dir is still TBD stub; issue body is the authoritative requirement source.

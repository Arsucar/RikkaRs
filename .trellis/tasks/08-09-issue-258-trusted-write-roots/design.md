# Design: feat(#258) trusted write roots

## 边界

| In | Out |
|---|---|
| WorkspaceEntity + Room 50→51 | ALLOW 覆盖 hard approval |
| write/edit needsApproval + 信任前缀 | shell/skill_tool always-allow |
| 审批卡次级按钮 + 详情管理卡 | 仅会话内存信任为主方案 |

## 数据模型

**文件**: `app/.../data/db/entity/WorkspaceEntity.kt`

| Field | Column | Type | Default |
|---|---|---|---|
| `trustedWriteRoots` | `trusted_write_roots` | `String` JSON | `"[]"` |

Decode: `List<String>` via `JsonInstant`；helper `trustedWriteRootList()`。

Normalize on write: absolute、trim trailing `/`、reject `..` / blank。

## Migration

| Item | Value |
|---|---|
| Current | **50** (`AppDatabase.kt:84`) |
| New | **51** |
| Object | `Migration_50_51 : Migration(50, 51)` |
| SQL | `ALTER TABLE workspaces ADD COLUMN trusted_write_roots TEXT NOT NULL DEFAULT '[]'` |
| Wire | `AppDatabase.version = 51`; `DataSourceModule.addMigrations(..., Migration_50_51)` |
| Test | Instrument `Migration_50_51_Test` 仿 `Migration_46_47_Test` |

**与 #259 冲突说明**：父任务顺序 #258 先于 #259。本任务占用 **50→51**（workspaces 列）。#259 若再改 ConversationEntity，应使用 **51→52**，不得再声称 50→51。

## 前缀匹配

纯函数（建议 `TrustedWriteRoots.kt` 或从 `WorkspaceTools.kt` 抽出可测 API）：

```kotlin
BUILTIN = ["/workspace", "/tmp"]

matchesRootPrefix(path, prefix):
  n = path.trimEnd('/').ifBlank { "/" }
  p = prefix.trimEnd('/').ifBlank { "/" }
  n == p || n.startsWith("$p/")

path needs hard approval for write:
  toolNameOverride || (outsideBuiltin(path) && !underAny(path, trustedRoots))
```

`pathOutsideWritableRoots` 保持 **仅 builtin**；trusted 为第二检查。

**必须单测的边界**:

| Path | Trusted | Approve? |
|---|---|---|
| `/workspace/a` | — | no |
| `/tmp/a` | — | no |
| `/skills/x` | — | yes |
| `/skills/x` | `["/skills"]` | no |
| `/skills-private/x` | `["/skills"]` | yes |
| `/skills_private/x` | `["/skills"]` | yes |
| `/skills/x/y` | `["/skills/x"]` | no |
| `/skills/x2` | `["/skills/x"]` | yes |

## needsApproval 组装

`createWorkspaceTools` 传入 `trustedRoots`（与 `approvalOverrides` 同站点：`ChatService.createWorkspaceToolsIfReady`；Subagent 若 INHERIT 需读实体）。

```text
needsApproval(name) || (pathOutsideWritableRoots(path) && !inTrustedRoots(path))
```

**续写注意**：信任写入后同会话下一次 tool 组装须 reload workspace entity（验证 continuation 重建 tools）。

## UI 钩子

### A. 审批卡 — `ChatMessageTools.kt` (`ChatMessageToolStep`)

显示条件：

- Pending
- tool ∈ `workspace_write_file`, `workspace_edit_file`
- path outside builtin writable roots

次级：「始终允许此目录」→ 确认框（可执行 + 跨助手共享风险）→  
`onTrustWriteRootAndApprove(toolCallId, rootPrefix)` 或扩展 approval API。

**前缀推导**：文件父目录或 skill 根两段（如 `/skills/my-skill` for `.../SKILL.md`）；显式 normalize + 单测；拒绝 `..`。

不显示：shell、skill_tool、read、已 free 路径。

### B. 详情 — `WorkspaceDetailPage.kt`

`WorkspaceToolApprovalCard` 旁「受信写入目录」：列表、删确认、空态。

### C. 文案 — `AssistantToolsPage.kt` ~453–471

`skill_tool` / `skill:management` sheet 说明硬审批 + 详情页配置 write 受信。

### D. 链路

| Layer | Change |
|---|---|
| ChatMessageTools | 次级按钮 + 确认 |
| ChatPage | 接线 |
| ChatVM | `trustWriteRootAndApprove` / 扩展 handleToolApproval |
| ChatService | 用 conversation assistant 的 `workspaceId` 持久化 root → Approved → resume |
| WorkspaceRepository | `addTrustedWriteRoot` / `removeTrustedWriteRoot` |
| WorkspaceDetailVM | 镜像 setToolApproval |

## 非目标

- 不改 ToolPermissionPolicy ALLOW 语义
- 不为 shell/skill_tool 加 always-allow
- Subagent AUTO 不作为主方案

## 回滚

- Feature flag 非必须；回滚 = 还原 needsApproval + 忽略列（列可留）
- 迁移向前-only；降版本不支持

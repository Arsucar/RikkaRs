# Implement: feat(#258) trusted write roots

## 前置

- `research/implementation-plan.md`
- 辅助: `workspace-entity-migration.md`, `workspace-tools-writable-roots.md`, `approval-ui-and-flow.md`, `tool-permission-policy-hard-approval.md`

## Checklist

### 1. Domain 匹配纯函数

- [ ] `matchesRootPrefix` / `needsPathHardApproval` / normalize
- [ ] 边界表单测（含 `/skills` vs `/skills-private` / `/skills_private`）
- [ ] 文件建议: 新 `TrustedWriteRoots.kt` 或 `WorkspaceTools.kt` 可测提取

### 2. Data + Migration 50→51

- [ ] `WorkspaceEntity` 字段 `trustedWriteRoots` / column `trusted_write_roots` default `[]`
- [ ] `Migration_50_51.kt` ALTER TABLE
- [ ] `AppDatabase.version = 51`
- [ ] `DataSourceModule` 注册
- [ ] `WorkspaceRepository.addTrustedWriteRoot` / `removeTrustedWriteRoot`
- [ ] 可选 instrument `Migration_50_51_Test`

**注意**: 本迁移占用 50→51；#259 不得再声明 50→51。

### 3. Tools 接线

- [ ] `createWorkspaceTools` 接收 trustedRoots
- [ ] write/edit `needsApproval` lambda 按 design 公式
- [ ] `ChatService.createWorkspaceToolsIfReady` 从 entity 读入
- [ ] 验证 approval 后续写同会话 tools 重建含新 roots

**文件**: `WorkspaceTools.kt`, `ChatService.kt`（及 Subagent 若需）

### 4. 审批 UI + 服务

- [ ] `ChatMessageTools` 次级「始终允许此目录」+ 确认
- [ ] 仅 write/edit + outside builtin
- [ ] ChatPage / ChatVM / ChatService 持久化 + approve resume
- [ ] 前缀从 path 推导 helper + 单测
- [ ] shell/skill_tool 无按钮

### 5. 详情管理 UI

- [ ] `WorkspaceDetailVM` add/remove + reload
- [ ] `WorkspaceDetailPage` 受信目录卡片
- [ ] strings 中英

### 6. AssistantToolsPage 文案

- [ ] skill_tool sheet 说明（~453–471）
- [ ] ALLOW 行为回归（不覆盖 hard approval）

### 7. 验证

- [ ] 单元：前缀边界
- [ ] 手动 AC1–AC6
- [ ] `.\gradlew --no-daemon` 聚焦 test/compile（最终检查子代理）
- [ ] 有设备 installDebug

## 文件总表

| Area | Files |
|---|---|
| Domain | `TrustedWriteRoots.kt` 或 `WorkspaceTools.kt` |
| Data | `WorkspaceEntity`, `Migration_50_51`, `AppDatabase`, `DataSourceModule`, `WorkspaceRepository` |
| Tools | `WorkspaceTools`, `ChatService` factory |
| Chat UI | `ChatMessageTools`, `ChatPage`, `ChatVM`, `ChatService.handleToolApproval` |
| Detail | `WorkspaceDetailPage`, `WorkspaceDetailVM` |
| Copy | `AssistantToolsPage`, strings |
| Tests | prefix unit; optional migration instrument |

## Rollback

- 还原 needsApproval + 隐藏 UI；列可保留

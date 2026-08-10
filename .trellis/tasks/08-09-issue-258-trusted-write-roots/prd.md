# PRD: feat(#258) 工作区信任写入目录

## Goal

用户对安全区外路径（如 `/skills/my-skill`）确认一次「始终允许写入」后，该目录前缀下的 `workspace_write_file` / `workspace_edit_file` 跨会话免逐次审批；受信前缀可查看、可撤销。`workspace_shell` / `skill_tool` 永不提供「始终允许」。

## Background

技能创作为多次小写入；每次 toolCallId 强制审批打断流程。`/skills` 等不在 `WRITABLE_ROOT_PREFIXES = ["/workspace","/tmp"]`；ALLOW 不能覆盖 hard approval。

权威：issue #258；计划：`research/implementation-plan.md`。

## Requirements

### 功能

1. 首次区外写仍 Pending；审批卡可「始终允许此目录」→ 确认 → 持久化前缀 + 放行本次。
2. 同前缀后续写（含新会话）自动通过。
3. Workspace 详情列出/删除受信前缀；删除后立即恢复逐次审批。
4. 仅 write/edit 工具；shell / skill_tool 无入口、始终审批。
5. skill_tool 权限 sheet 增加说明：硬审批仍在，受信目录在工作区详情配置。
6. Room 持久化；旧库迁移默认 `[]`。

### 约束

- 不改 ALLOW 覆盖 hard approval 语义（#154/#36）。
- 前缀边界：`/skills` 不得匹配 `/skills-private` 或 `/skills_private`（须 `$prefix/` 分隔）。
- `/workspace` `/tmp` 行为不变。
- 含 `..` 的路径拒绝信任。
- 子代理 AUTO 不是本需求主路径。

## Non-goals

- 会话级-only 信任（可作未来辅助，非主方案）
- 修改 ALLOW 覆盖硬审批
- shell / skill_tool always-allow

## Acceptance Criteria

- [ ] AC1: 首次写 `/skills/x` 出审批卡；「始终允许」后同目录后续写入（含新会话）不再审批
- [ ] AC2: WorkspaceDetailPage 可查看/删除受信目录；删除后恢复逐次审批
- [ ] AC3: `workspace_shell`、`skill_tool` 无论受信始终审批，不显示「始终允许」
- [ ] AC4: `/workspace`、`/tmp` 不变；前缀边界 `/skills ≠ /skills-private`（及 `_` 变体）
- [ ] AC5: AssistantToolsPage ALLOW 语义不变；skill_tool sheet 有说明文案
- [ ] AC6: Room 迁移旧数据无崩溃；撤销信任后全部回归逐次审批

## 数据（产品）

- 每 workspace 一份 `trustedWriteRoots: List<String>`（绝对路径前缀，默认空）
- 与 `toolApprovals` 并列，不塞进同一 map

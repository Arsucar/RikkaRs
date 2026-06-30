# fix: workspace_shell 误杀合法 stderr 重定向

## Goal

修复 `WorkspaceShellPolicy` 中 `>/dev/` 子串匹配规则误杀合法 stderr/stdout 重定向（如 `2>/dev/null`、`>/dev/null`），同时继续拦截真正危险的块设备写入（如 `>/dev/sda`、`>/dev/sda1`）。

## Background

`WorkspaceShellPolicy.kt:74` 使用 `Regex(">/dev/").containsMatchIn(lower)` 做子串匹配，导致任何包含 `>/dev/` 的命令均被拒绝。`2>/dev/null` 包含该子串，因此被误杀。

Issue #16 记录了以下复现场景：
- `echo test 2>/dev/null` → 拒绝
- `grep -r LogPage app 2>/dev/null | head -3` → 拒绝
- `grep -r LogPage app | head -3` → 允许（去掉 `2>/dev/null` 后正常）

## Confirmed Facts

- 根因：`WorkspaceShellPolicy.kt:74` 的 `>/dev/` 正则过宽
- 该规则意图拦截写入块设备（`>/dev/sda` 等），但未区分 `/dev/null`、`/dev/zero` 等安全设备
- `WorkspaceShellPolicy` 文档声明仅为启发式拦截，非安全边界
- 现有单测未覆盖 `2>/dev/null` 等常见合法重定向场景

## Requirements

1. **允许**重定向到安全设备：`/dev/null`、`/dev/zero`、`/dev/urandom`、`/dev/random`
2. **继续拒绝**重定向到块设备或非安全 `/dev/*` 路径（如 `/dev/sda`、`/dev/mmcblk0`）
3. 拒绝规则应基于正则精确匹配，而非简单子串

## Acceptance Criteria

- [ ] `echo test 2>/dev/null` 返回 `Allowed`
- [ ] `grep -r TODO . 2>/dev/null | head -5` 返回 `Allowed`
- [ ] `>/dev/null` 返回 `Allowed`
- [ ] `dd if=/dev/zero of=/dev/sda` 仍然返回 `Rejected`
- [ ] `cat /dev/sda > /dev/sda1` 仍然返回 `Rejected`
- [ ] 所有现有多测通过（`WorkspaceShellPolicyTest`）
- [ ] 新增测试覆盖安全设备重定向场景

## Out of Scope

- 不修改 shell 执行环境或沙箱权限
- 不增加新的危险命令规则
- 不重构 `WorkspaceShellPolicy` 整体架构

## Open Questions

无（根因和修复方向已明确）

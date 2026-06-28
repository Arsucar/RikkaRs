# Implement: Blocker B — Workspace Shell Policy Hardening

> 子代理派发时 prompt 必须以 `Active task: .trellis/tasks/06-28-blocker-b-shell-hardening` 开头。

## 步骤

- [ ] **1.1** 读 `workspace/src/main/java/me/rerere/workspace/WorkspaceShellPolicy.kt` 与 `WorkspaceShellPolicyTest.kt` 全文。
- [ ] **1.2** 在文件头新增 KDoc 注释：明确「**启发式拦截，非强隔离；真实隔离依赖 WorkspaceManager cwd 限制与进程权限**」。
- [ ] **1.3** 扩展规则（保持纯函数）：
  - `rm -rf` 根类：`/`、`/*`、`/<绝对路径>`（`/home`、`/etc`、`/data`、`/system`、`/sdcard`、`/storage`）、`~`、`$HOME`、`*`。
  - fork bomb 典型模式（`:(){` 或 `:()` 启发式）。
  - `chmod -R 777 /`。
  - `shutdown` / `reboot` / `halt` / `init 0` / `init 6`（仅命令首 token 或 `;` `&&` `||` 之后）。
- [ ] **1.4** 保留 `/data/data/` `/data/user/` `/sdcard/` `/storage/` `/system/` 子串拒绝，注释说明「workspace cwd 内不应出现」。
- [ ] **1.5** `WorkspaceShellPolicyTest` 扩充：新增至少 6 条拒绝 + 4 条允许（含 `grep shutdown log.txt` 反例）。

## 验证

- [ ] **2.1** `.\gradlew :workspace:test --no-daemon` 通过。
- [ ] **2.2** CHANGELOG `v2.3.6` 「Workspace shell」条目更新为「启发式拦截（非强隔离）」表述。

## 回滚点

- 改动集中在 `WorkspaceShellPolicy.kt` + 测试 → 单 commit revert。

## 不做

- 不引入命令解析库。
- 不改 `WorkspaceManager.executeCommand` 签名。
- 不改 `ShellCommandVerdict` 数据结构。

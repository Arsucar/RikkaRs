# PRD: Blocker B — Workspace Shell Policy Hardening

## 问题

`evaluateShellCommand`（`workspace/.../WorkspaceShellPolicy.kt`）当前：

1. **过宽误杀**：对 `/storage/`、`/sdcard/` 做**子串**匹配，workspace 内合法命令只要含这些片段就被拒。
2. **漏杀**：`rm -rf /` 仅匹配 `rm -rf /\s*$` 和 `rm -rf /\s*;`；`rm -rf /*`、`rm -rf /home`、`rm -rf ~`、`rm -rf /data`、`:(){ :|:& };:`（fork bomb）、`chmod -R 777 /`、`shutdown`、`reboot` 等未覆盖。
3. **可绕过**：用 `&&` / `||` / 换行 / 变量（`rm -rf $HOME`）拼接可绕过简单正则。
4. **语义不清**：CHANGELOG 写「应用层拒绝明显高危 shell 命令」，但代码注释未说明这是**启发式**而非强隔离。

## 目标

加固启发式规则，明确「非强隔离」语义，并补充正反回归用例。**不**重写为基于 cwd 的强隔离（超出本任务范围）。

## 验收标准

1. **AC-1**：拒绝更多破坏性命令变体：`rm -rf /*`、`rm -rf /<path>`（`/`、`/etc`、`/data`、`/system`、`/sdcard`、`/storage`、`~`、`$HOME`、`*` 等绝对/家目录/通配根）、fork bomb、`chmod -R 777 /`、`shutdown`、`reboot`、`init 0`、`halt`。
2. **AC-2**：workspace 内**合法**命令不被误杀（基于 cwd 解析或在白名单语义下）：`grep -r foo .`、`ls`、`cat README.md`、`npm test`、`cd ../build/ && ls`、`find . -name '*.kt'`。
3. **AC-3**：文件头/函数注释明确写「**启发式拦截，非强隔离；真实隔离依赖 workspace cwd 限制**」。
4. **AC-4**：`WorkspaceShellPolicyTest` 扩充：至少新增 6 条拒绝用例 + 4 条允许用例（覆盖误杀回归）。
5. **AC-5**：`.\gradlew :workspace:test --no-daemon` 通过。
6. **AC-6**：CHANGELOG `v2.3.6` 「Workspace shell」条目更新为「启发式拦截高危命令（非强隔离）」。

## 约束

- 不改 `WorkspaceManager.executeCommand` 的调用契约（`ShellCommandVerdict` 返回结构不变）。
- 不引入新依赖。
- 规则保持**纯函数**（无 IO、无状态），便于测试。
- 不破坏现有 5 条测试（`allowsCommonDevCommands` / `rejectsSensitivePathsAndDestructiveCommands`）。

## 不在本任务范围

- 基于 cwd / chroot 的强隔离。
- 子进程级 sandbox（seccomp / namespace）。
- 命令解析器（tokenize + AST 分析）。

# Design: Blocker B — Workspace Shell Policy Hardening

## 现状

`WorkspaceShellPolicy.evaluateShellCommand`（55 行）：

```kotlin
// 已有规则
- rm -rf /\s*$ | rm -rf /\s*;        // 仅匹配根 + 空格/分号结尾
- mkfs. | dd if=                      // 格式化 / 块设备
- >/dev/                             // 写设备
- 子串: /data/data/ /data/user/ /sdcard/ /storage/ /system/
- ../ + 上述绝对路径
```

## 方案：分层启发式

### 第 1 层：破坏性命令（不依赖路径）

```
- rm -rf 根类: rm\s+-rf\s+(/+|\$HOME|~|\*)\b   // /、/*、/~、$HOME、*
- fork bomb: :\(.*\).*:|.*&.*\)                 // 启发式，容忍误报
- chmod -R 777 根: chmod\s+-R\s+777\s+/($|\s|;)
- 关机/重启: \b(shutdown|reboot|halt|init\s+0)\b
- mkfs. | dd if= | > /dev/ | mke2fs
```

### 第 2 层：敏感绝对路径（保持，但收口语义）

保留 `/data/data/`、`/data/user/`、`/system/` 子串匹配（这些路径在 workspace 内**不应**出现）。

**调整** `/sdcard/`、`/storage/`：
- 这些在 Android 上可能出现在**用户可见路径**；但 workspace shell 的 cwd 已限制在 workspace files 目录，命令里出现 `/sdcard/` / `/storage/` 几乎必然是越界访问。
- **保留拒绝**，但在注释和 CHANGELOG 明确这是启发式，workspace 内合法命令不应包含这些绝对路径。
- 若未来需要白名单（例如 workspace 映射到外部存储），再单独处理。

### 第 3 层：路径遍历

保留 `../` + 敏感路径的组合检测；新增 `../` 指向 `/data` `/sdcard` `/system` 的检测（已有，确认覆盖）。

## 边界

- **不解析命令**：仍用正则 + 子串，明确写「启发式」。
- **不引入 shell parser**：超出范围。
- 误报容忍：`shutdown` 作为普通单词出现在 `grep shutdown log.txt` 中会误报 → 用 `\b(shutdown|reboot|...)\b` 且要求出现在命令开头或 `;` / `&&` / `||` 之后（启发式收窄）。
  - 简化：仅在命令 token 首字匹配时拒绝，即 `^(shutdown|reboot|halt)\b` 或 `[\s;|&](shutdown|reboot|halt)\b`。

## 测试矩阵（节选）

| 命令 | 期望 | 备注 |
|------|------|------|
| `rm -rf /` | Rejected | 现有 |
| `rm -rf /*` | Rejected | 新增 |
| `rm -rf /home` | Rejected | 新增 |
| `rm -rf ~` | Rejected | 新增 |
| `rm -rf $HOME` | Rejected | 新增 |
| `:(){ :\|:& };:` | Rejected | fork bomb |
| `chmod -R 777 /` | Rejected | 新增 |
| `reboot` | Rejected | 新增 |
| `grep -r foo .` | Allowed | 现有类 |
| `npm test` | Allowed | 新增 |
| `find . -name '*.kt'` | Allowed | 新增 |
| `cd ../build/ && ls` | Allowed | 现有类 |
| `grep shutdown log.txt` | Allowed | 新增（确认不误杀） |

## 风险

- **低**：fork bomb 正则可能误报复杂命令 → 收窄为典型模式。
- **低**：`$HOME` 变量在 Windows host shell 不会展开，但 workspace shell 语境以 Linux 为主 → 保留拒绝。

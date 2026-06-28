# 安全与合规修复 — 技术设计

任务：`06-28-audit-security`（对应 PRD 五项 A-01 / D L1 / F-01 / G-01 / G-02）

---

## 1. 威胁模型与统一原则

| 路径 | 风险 | 本任务收口 |
|------|------|------------|
| 子代理工具链 | `sandboxToolsForSubagent` 清零 `needsApproval`，与 profile/workspace 审批配置矛盾 | 保留 `buildSubagentTools` / `applySubagentWorkspaceApproval` 已计算的 `needsApproval`，不再全局覆盖 |
| LogPage UI | 详情 sheet 读原始 `RequestLog`，成为密钥明文唯一入口 | UI 展示与 `LogEntry.redacted()` / 导出 JSON 同源 |
| 发版 | tag `v2.3.5` 构建内嵌 `2.3.4` | 文档化历史事实；后续 tag 与 Gradle/CHANGELOG 对齐 |
| Web `/api` | 默认 JWT 关 + `0.0.0.0` 监听 → LAN 未认证访问会话/设置/文件 | 收紧**新默认** + LAN 开启时的显式确认/警告 |
| Workspace shell | `executeCommand` 仅校验 cwd 目录，命令字符串无过滤 | 在 `WorkspaceManager.executeCommand` 入口增加可测的拒绝策略 |

**A-01 与 G-02**：shell 若 profile 要求审批，须先过 `GenerationHandler` Pending（A-01），再在 `executeCommand` 层拒绝高危命令（G-02）；两层互不替代。

---

## 2. A-01 — 子代理 sandbox 绕过审批

### 2.1 现状（已核对源码）

- `SubagentHost.kt:35`：`NO_APPROVAL = { false }`（`needsApproval` 恒 false）。
- `SubagentHost.kt:405-406`：`sandboxToolsForSubagent` 对列表内**每个** `Tool` 执行 `copy(needsApproval = NO_APPROVAL)`。
- `ChatService.kt:1765-1774`：子代理工具在 `buildSubagentTools(...)` 之后**必过** `SubagentHost.sandboxToolsForSubagent`。
- `SubagentPermissionBuilder.kt:76-92,110`：`applySubagentWorkspaceApproval` 已按 `WorkspaceApproval.AUTO/INHERIT/OVERRIDE` 设置 `needsApproval`；`createSubagentWorkspaceTools` 已应用。
- `GenerationHandler.kt:214-218`：仅当 `toolDef.needsApproval(input) == true` 且状态为 `Auto` 时进入 `Pending`。

结论：当前 `sandboxToolsForSubagent` **仅做审批清零**，未实现其它 sandbox 语义；函数名与行为不符，属于 P0 缺陷。

### 2.2 方案（选定）

**方案 A（选定）— 停止清零 `needsApproval`**

1. 将 `sandboxToolsForSubagent` 改为**恒等**：`fun sandboxToolsForSubagent(tools: List<Tool>): List<Tool> = tools`，或直接从 `ChatService.buildSubagentToolsForChat`（约 1765 行）移除该包裹，仅返回 `buildSubagentTools(...)`。
2. 删除未再使用的 `NO_APPROVAL` 常量（`SubagentHost.kt:35`），避免误用。
3. **不**在本任务引入「子代理全自动」新产品模式；`WorkspaceApproval.AUTO` 仍通过 `applySubagentWorkspaceApproval` 显式 `needsApproval = { false }`（`SubagentPermissionBuilder.kt:81`）。

**未选方案 B**：仅对 `workspace_*` 保留审批、对其余工具清零 — 会违背「继承 MCP 且父会话需审批」的 PRD 验收，故不采用。

**未选方案 C**：文档化「子代理永不审批」 — 与现有设置 UI / workspace overrides 用户预期冲突。

### 2.3 继承工具与 INHERIT

- `buildSubagentTools`（`SubagentPermissionBuilder.kt:123-127`）从 `parentTools` 继承的非 workspace 工具**不**经过 `applySubagentWorkspaceApproval`；其 `needsApproval` 来自父会话工具定义（如 MCP `needsApproval`）。
- 修复后这些 lambda **不再**被 `sandboxToolsForSubagent` 覆盖，满足「继承工具若 profile/父配置需审批则生效」。

### 2.4 回归与风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| 子代理 workspace 写/shell 开始弹出审批，用户觉得变慢 | 中 | 与主会话一致；`AUTO` profile 仍无审批 |
| 误以为还有其它 sandbox（路径前缀等） | 低 | 路径门控仍在 `withPathValidation`（`SubagentPermissionBuilder.kt:111`），与本次无关 |
| `spawn_subagent` 嵌套子代理工具链 | 低 | 同一 `buildSubagentToolsForChat` 路径，行为一致 |

### 2.5 测试契约

- 在 `SubagentPermissionTest.kt` 或新建 `SubagentSandboxTest.kt` 增加：`buildSubagentTools` + 模拟 `sandboxToolsForSubagent` 后，`WorkspaceApproval.OVERRIDE` + `workspace_shell` override true 时 `needsApproval(...)` 为 true。
- 可选：对 `applySubagentWorkspaceApproval` + 恒等 sandbox 的集成式单测，无需真机 `GenerationHandler`。

---

## 3. D L1 — LogPage 请求详情脱敏

### 3.1 现状

- 导出：`LogPage.kt:87-89` 使用 `Logging.getRecentLogs().map { it.redacted() }`。
- 详情：`RequestLogDetail`（`LogPage.kt:304-411`）直接使用 `log.url`、`log.requestHeaders`、`log.requestBody`、`log.responseHeaders`。
- `common/.../LogRedaction.kt:39-45`：`RequestLog.redacted()` 脱敏头、体，**未**脱敏 `url`。
- 列表卡片：`RequestLogCard`（`LogPage.kt:262-266`）展示原始 `log.url`。

### 3.2 方案（选定）

1. **详情 sheet**：在 `RequestLogDetail` 入口将 `log` 转为展示用副本：`val display = remember(log) { log.redacted() }`（`LogEntry.RequestLog`），后续字段一律读 `display`。
2. **URL 与 `redacted()` 对齐**：在 `LogRedaction.kt` 扩展 `RequestLog.redacted()`，对 `url` 调用现有 `redactSecrets(url)`（覆盖 query/body 风格 JSON 键名），使详情 URL 与导出策略一致，满足 PRD「至少与当前 `redacted()` 同等覆盖」。
3. **列表卡片**：`RequestLogCard` 的 URL 行改为 `log.redacted().url`（或 `redactSecrets(log.url)`），避免列表成为带 `api_key` query 的明文入口。

**不改**：内存中 `Logging` 仍存原始日志（便于用户本机调试）；仅 UI/导出/AI `get_logs` 走脱敏（`LogsTool` 已用 `redacted()`，保持不动）。

### 3.3 权衡

- 扩展 `redacted()` 会影响导出与 `get_logs` 的 URL 展示 — **期望行为**，三端一致。
- `LogRedaction` 规则未覆盖的密钥形态（L2/L3）仍可能泄漏 — 本任务范围外。

### 3.4 风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| 用户抱怨详情看不清完整 Authorization | 低 | 与导出一致；本机仍可关闭请求记录 |
| `remember(log)` 引用相等性 | 低 | `RequestLog` 为 data class，按 id 稳定 |

### 3.5 测试

- `common` 模块 JVM 测试：样本 `RequestLog` + 含敏感头/体/url query → `redacted()` 断言 `***REDACTED***`。
- 可选 `LogPage` 不建 Compose 仪器测试，以 `LogRedaction` 单测为主。

---

## 4. F-01 — 版本号与 tag 合规

### 4.1 事实

- `git show v2.3.5:app/build.gradle.kts`：`versionName = "2.3.4"`，`versionCode = 167`。
- 当前 HEAD：`app/build.gradle.kts:23-24` 仍为 `167` / `"2.3.4"`。
- `CHANGELOG.md` 已有 `## v2.3.5` 段落（含「LogPage 原始日志不脱敏」等表述，与本修复后需更新一句说明）。

### 4.2 目标版本（选定）

- **下一发版号：`2.3.6`**，`versionCode = 168`（单调递增，不与错误 tag 构建抢 167 语义）。
- **不**重打远程 `v2.3.5` tag（除非维护者单独决策）；在 `CHANGELOG.md` 的 `v2.3.5` 段落下增加 **Notes** 子条：说明 Git tag `v2.3.5` 对应 APK 内嵌版本为 `2.3.4`（`versionCode` 167），便于溯源。
- 本安全修复条目写入新发版段落 **`## v2.3.6`**（发版时由 implement 执行，本任务实现阶段可只改 Gradle + 文档草稿）。

### 4.3 发版检查清单（设计层）

打 tag `vX.Y.Z` 前：

1. `app/build.gradle.kts` 的 `versionName` 去掉 `v` 后与 tag 一致。
2. `versionCode` 大于任意已发布 Release。
3. `CHANGELOG.md` 存在对应 `## vX.Y.Z` 且含中英条目。

### 4.4 风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| 用户已装 2.3.4 与即将 2.3.6 升级路径 | 低 | versionCode 递增，正常覆盖安装 |
| CHANGELOG v2.3.5 描述与修复后行为矛盾 | 中 | v2.3.6 修正 LogPage 描述；v2.3.5 加历史注记 |

---

## 5. G-01 — Web JWT 与 LAN 暴露

### 5.1 现状

- `PreferencesStore.kt:622-624`：`webServerJwtEnabled = false`，`webServerLocalhostOnly = false`（`Settings` 默认）。
- DataStore 读取：`webServerJwtEnabled = preferences[WEB_SERVER_JWT_ENABLED] == true`（缺省 false）；`webServerLocalhostOnly = preferences[WEB_SERVER_LOCALHOST_ONLY] == true`（缺省 false）。
- `WebApiModule.kt:65,166-178`：`jwtEnabled == false` 时 `/api` 下 conversation/settings/files/assets **无** `authenticate`。
- `WebServerManager.kt:63`：`localhostOnly == false` → host `0.0.0.0`。
- `SettingWebPage.kt` 已有 JWT、密码、仅 localhost 开关；**无**「JWT 关 + LAN」组合警告。

### 5.2 方案（选定：默认收紧 + 显式 opt-in）

**5.2.1 新默认（仅影响「键未写入」的语义）**

- 将 DataStore 缺省与 `Settings` 默认改为 **`webServerLocalhostOnly = true`**：
  - `PreferencesStore.kt`：`Settings.webServerLocalhostOnly: Boolean = true`
  - 读取：`preferences[WEB_SERVER_LOCALHOST_ONLY] ?: true`（**注意**：已显式存 `false` 的老用户保持 LAN）。
- **保持** `webServerJwtEnabled` 默认 false（避免无密码时默认开启 JWT 导致 `/api` 全 403 且用户不懂配密码）；靠 localhost 默认 + LAN opt-in 降暴露。

**5.2.2 开启 LAN 时的强制确认**

- 在 `SettingWebPage.kt`：当用户将「仅 localhost」从 true → false，且 `webServerJwtEnabled == false`，弹出 **AlertDialog**（不可静默跳过）：说明 LAN 未认证可访问 `/api`；按钮「取消」（恢复 localhost only）与「仍要开启」（写入 `webServerLocalhostOnly = false`）。
- 当用户启动 Web 服务（`WebServerService` / SettingWebPage 启动逻辑）：若 `!webServerLocalhostOnly && !webServerJwtEnabled`，在启动前同样拦截或复用同一确认（与 UI 开关二选一实现，至少覆盖「关 JWT + 关 localhost only」路径）。

**5.2.3 持续警告文案**

- `SettingWebPage.kt`：当 `!settings.webServerLocalhostOnly && !settings.webServerJwtEnabled`，在 JWT/localhost 区块下显示 **error 色** `Text`（新增 string key，英文 + 中文 `values` / `values-zh`，其它 locale 可 follow-up 或暂用英文 per AGENTS「未要求 i18n 可硬编码」— 本 PRD 要求可见文案，建议至少 `values` + `values-zh`）。

**5.2.4 不改动**

- `WebApiModule` JWT 开/关路由结构不变（避免破坏 `web-ui` `webAuthEnabled`，`conversations.tsx:1090`）。
- 不默认 `webServerJwtEnabled = true`（老用户 LAN + 无 JWT 仍可通过确认后使用）。

### 5.3 权衡

| 选项 | 优点 | 缺点 |
|------|------|------|
| 默认 JWT on | LAN 默认安全 | 无密码时 API 不可用，支持成本高 |
| 默认 localhost on（选定） | 新用户默认不暴露 LAN | 需确认对话框治理主动 opt-in |
| 启动时绑定 127.0.0.1 直到配 JWT | 最强 | 改变「开服务即 LAN」习惯过大 |

### 5.4 风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| `?: true` 使从未存过 key 的用户突然只能 localhost | 中 | 比 LAN 裸奔更安全；Release note 说明 |
| 确认对话框重复烦人 | 低 | 仅在关 localhost 且 JWT off 时；启动服务可只拦一次（`remember` 会话级） |

### 5.5 验证矩阵（implement 记录）

- 新安装默认：localhost only 开，JWT 关 → 仅本机可访问 `/api`。
- LAN + JWT off + 用户确认后：未认证可访问（与现行为一致，但知情）。
- LAN + JWT on + 密码已设：401/403 与现网一致。
- `web-ui` 在 `webAuthEnabled=true` 时登录流不变。

---

## 6. G-02 — Workspace shell 命令约束

### 6.1 现状

- `WorkspaceManager.kt:123-147`：`executeCommand` 校验 command 非空、cwd 存在且为目录，然后 `shellRunner.execute`。
- `WorkspaceShellRunner.kt:24-27`（`HostShellRunner`）：`ProcessBuilder(shell, "-c", context.command)`。
- `ProotShellRunner.kt` 同样传入原始 command（审计引用）。
- AI 路径：`WorkspaceTools.kt:256` → `WorkspaceRepository.executeCommand`。

### 6.2 方案（选定：denylist + 统一入口）

在 `workspace` 模块新增 **`WorkspaceShellPolicy`**（新文件，如 `workspace/.../WorkspaceShellPolicy.kt`）：

```kotlin
sealed class ShellCommandVerdict {
    data object Allowed : ShellCommandVerdict()
    data class Rejected(val userMessage: String) : ShellCommandVerdict()
}

fun evaluateShellCommand(command: String): ShellCommandVerdict
```

**拒绝规则（首批，可单测）**：

1. 空白 / 仅空白符 — 已有 `require`，可合并。
2. 命令行匹配（忽略大小写、trim）高危子串或正则：
   - 读应用私有数据：`/data/data/`、`/data/user/`
   - 试图直接写系统分区：`> /dev/`、`mkfs.`、`dd if=`
   - 明显破坏：`rm -rf /`（作为独立模式，避免误杀 `rm -rf ./build`）
   - **工作区外路径穿越**：在 command 中出现 `../` 且与绝对路径 `/sdcard`、`/storage`、`/system` 等组合（简化实现：拒绝包含 `../` 且同时包含 `/data/` 或 `/sdcard/` 或 `` `/` `` 根删除类；或拒绝 `$(` / `` ` `` 嵌套执行以降低注入面 — 第二条可选 P1.5）

**允许样本（须通过）**：`ls`，`cat foo.md`，`pwd`，`grep -r pattern .`，proot 内常见构建命令。

在 `WorkspaceManager.executeCommand` **开头**调用 `evaluateShellCommand`；若 `Rejected`，返回 `WorkspaceCommandResult(exitCode = 1, stderr = userMessage, ...)` **不**启动进程（与超时结构一致，便于 AI 解析）。

**范围**：默认对所有 `executeCommand` 调用生效（含 `WorkspaceDetailVM` 手动执行）。若产品要求「仅 AI 工具受限」，可在后续加参数 `enforcePolicy: Boolean = true`；本任务 PRD 强调 AI 路径，统一入口更简单且 VM 手动执行同样受益。

### 6.3 与 A-01

审批通过后的 shell 仍须过 policy；policy 拒绝时不执行，无需二次审批。

### 6.4 权衡

- 白名单过严，开发命令易被拒 → 先用 denylist。
- 黑名单可绕过（编码、变量展开）→ 明确非完整沙箱，依赖 proot/文件系统边界 + 审批；本项降低「误一键」与明显越界。

### 6.5 风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| 误杀合法命令（如文档含 `../` 说明） | 中 | 单测调规则；错误信息简短 |
| 仅 app 层，Host shell 仍可危险 | 中 | 与审计一致；cwd 仍在 workspace 内 |

### 6.6 测试

- `workspace/src/test/.../WorkspaceShellPolicyTest.kt`：拒绝/允许样本表。
- 可选：`WorkspaceManager` 用 fake `WorkspaceShellRunner` 断言拒绝时不调用 runner。

---

## 7. 同文件变更归组

| 文件 | 项 |
|------|-----|
| `SubagentHost.kt` | A-01：sandbox 恒等 / 删 NO_APPROVAL |
| `ChatService.kt` | A-01：可选去掉 sandbox 包裹（与 Host 同步） |
| `SubagentPermissionTest.kt` 或新测试 | A-01 |
| `LogRedaction.kt` | D L1：url 脱敏 |
| `LogPage.kt` | D L1：详情 + 卡片 |
| `common` 测试 | D L1 |
| `app/build.gradle.kts` + `CHANGELOG.md` | F-01 |
| `PreferencesStore.kt` + `SettingWebPage.kt` + strings | G-01 |
| `WebServerService.kt` 或 Setting 启动 | G-01 启动确认（若采用） |
| `WorkspaceShellPolicy.kt` + `WorkspaceManager.kt` + 测试 | G-02 |

---

## 8. 实施顺序建议

1. **D L1**（独立、风险低）
2. **A-01** + **G-02**（同一威胁章节，可并行子代理）
3. **G-01**（设置/DataStore/UI）
4. **F-01**（Gradle/CHANGELOG，可与 1–3 并行）

---

## 9. 回滚策略（设计层）

- 每项均为可独立 revert 的提交：A-01 恢复 `NO_APPROVAL`；L1 恢复原始 log 展示；G-01 恢复 DataStore 缺省 `false`；G-02 删除 policy 调用；F-01 仅文档/版本号回退。
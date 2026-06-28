# 安全与合规修复（P0/P1）

## 背景与目标

来源：夜间审计任务 `06-28-nightly-audit`（`audit-report.md`）批次 A「安全与合规」。本任务为父任务下的子任务，仅覆盖 **5 条** 与安全、隐私、发版合规直接相关的发现，不包含 i18n、ChatService 流式状态机、CI 文档等其它审计项。

**目标**：在不大改产品形态的前提下，消除已确认的 P0 安全/隐私/合规缺口，并收紧两条 P1 默认暴露面（Web API、Workspace shell），使行为与 `LogsTool`/导出、`SubagentPermissionBuilder`、发版标签语义一致。

## 范围

| 包含 | 审计 ID | 优先级 |
|------|---------|--------|
| 子代理 sandbox 绕过工作区审批 | A-01 | P0 |
| 日志页请求详情明文展示 | D L1 | P0 |
| `v2.3.5` 标签与 `versionName` 脱节 | F-01 | P0 |
| Web 服务 JWT 默认关闭 + LAN 暴露 | G-01 | P1 |
| Workspace shell 无应用层命令约束 | G-02 | P1 |

## 非范围

- `ChatService.kt:331-335` 冷启动 stale `subagent_streaming`（D S1，属其它子任务）。
- `LogRedaction.kt` 规则扩展（D L2/L3）、列表快照是否脱敏（D L4）。
- Firebase/`release.yml`、双 workflow 文档（F-02～F-09）。
- CORS `anyHost()` 单独治理（G-07，可与 G-01 在 design 中关联但非本 PRD 独立交付项）。
- ListCard dedup、search `Call.await` 等架构债（G-03～G-06）。

## 约束

- 不改变 `applicationId`、namespace 与 arm64 发版路径。
- 子代理修复须与现有 `WorkspaceApproval` / `toolApprovalOverrides` / `GenerationHandler` Pending 流语义兼容或可文档化替代策略。
- 日志 UI 脱敏须与 `LogsTool.kt` 导出及 `LogPage` JSON 导出已使用的 `redacted()` 策略一致。
- F-01 须区分：**历史 tag `v2.3.5` 的合规说明/补救** 与 **当前 HEAD 及后续发版的 `versionName` 正确性**（具体策略见 design.md）。
- Web/Workspace 变更须考虑用户已依赖「局域网无 JWT」或「任意 shell」的既有习惯，避免静默破坏；产品侧提示或迁移路径在 design 中定义。

---

## 需求项

### 1. A-01 — 子代理 sandbox 绕过工作区审批（P0）

**位置（精确引用）**

- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt:35` — `NO_APPROVAL` 恒为 `{ false }`（即 `needsApproval` 永不触发审批）。
- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt:405-406` — `sandboxToolsForSubagent` 对**全部**工具 `copy(needsApproval = NO_APPROVAL)`。
- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt:76-92,110` — `applySubagentWorkspaceApproval` 按 profile 设置审批；`createSubagentWorkspaceTools` 在子代理路径上已应用。
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt:1765-1774` — 子代理工具链在 `buildSubagentTools` 之后**统一**经 `sandboxToolsForSubagent` 包裹。

**应达成什么（需求层面，不含实现方案）**

- 子代理运行时，经 profile / workspace overrides 配置为需审批的工作区写、shell 及从父会话继承且应受审批约束的工具，**不得**因 sandbox 包装而一律跳过 Pending 审批流。
- 若产品决策为「子代理全自动执行」，须显式、可审计（例如仅对明确安全的工具子集免审批，或在设置/文档中声明子代理路径不经过审批且保留日志），**不得**与主会话「需审批」配置静默矛盾。

**验收标准**

- [ ] 在 `WorkspaceApproval.OVERRIDE`（或等价需审批配置）下，子代理调用工作区写/shell 类工具时，行为与主会话一致：该 Pending 的进入 `GenerationHandler` 审批流，不该 Pending 的不误拦。
- [ ] 继承的 MCP/父工具若 profile 要求审批，子代理路径同样生效（或 design 中书面豁免范围与 UI/文档一致）。
- [ ] 新增或更新自动化测试覆盖「sandbox 不再无条件清零 `needsApproval`」的核心契约（具体用例在 implement.md 列出）。
- [ ] 无回归：`WorkspaceApproval.AUTO` 下子代理仍可自动执行（若仍为产品预期）。

---

### 2. D L1 — LogPage 请求详情明文展示（P0）

**位置（精确引用）**

- `app/src/main/java/me/rerere/rikkahub/ui/pages/log/LogPage.kt:186-191` — 底部 sheet 展示选中 `RequestLog`。
- `app/src/main/java/me/rerere/rikkahub/ui/pages/log/LogPage.kt:304-411` — `RequestLogDetail` 直接使用原始 `log.url`、`log.requestHeaders`、`log.requestBody`、`log.responseHeaders`（经 `HeaderItem`/`DetailSection` 展示）。
- 对照：`LogPage.kt:87-89` 导出 JSON 已 `map { it.redacted() }`；`common` 模块 `LogRedaction.kt` 提供 `LogEntry.redacted()`。

**应达成什么**

- 用户在应用内打开「请求详情」时，看到的头、体内容与导出/AI `LogsTool` 输出采用**同一脱敏规则**，不得在 UI 详情成为密钥、Cookie、`Authorization` 等明文的唯一入口。
- URL 若含敏感 query（如 `api_key`），详情展示须与脱敏策略一致（规则扩展范围若不足，本项至少达到与当前 `redacted()` 对 `RequestLog` 的同等覆盖）。

**验收标准**

- [ ] 含 `Authorization`、`x-api-key`、`Cookie` 等头的样本日志，详情页展示值为脱敏形态，非原文。
- [ ] 含 JSON 体内 `api_key`/`token`/`secret` 等字段的样本，详情页与 `log.redacted()` 展示一致。
- [ ] 导出 JSON 与详情页对同一条 `RequestLog` 敏感字段处理一致（人工或单测比对）。
- [ ] 列表卡片（`RequestLogCard`）若展示 URL/错误信息，不引入新的明文密钥泄漏（本项以详情为主；若 URL 全文明文含 key，须脱敏或截断）。

---

### 3. F-01 — `v2.3.5` 标签与内嵌 `versionName` 不一致（P0）

**位置（精确引用）**

- 历史事实：`git show v2.3.5:app/build.gradle.kts` 中 `versionName` 为 `2.3.4`、`versionCode` 为 `167`，与 tag 名 `v2.3.5` 不一致（审计 `subagent-f-infra.md` §8）。
- 当前工作树：`app/build.gradle.kts:23-24` — `versionCode = 167`，`versionName = "2.3.4"`。
- `CHANGELOG.md` 已存在 `v2.3.5` 段落，与 tag 构建内嵌版本脱节。

**应达成什么**

- 发版合规：任意新打出的 **Release APK** 的 `versionName`/`versionCode` 与 CHANGELOG 段落、Git tag 命名策略一致，不出现「Release 资产名为 v2.3.5、包内仍为 2.3.4」类问题。
- 对**已发布**的 `v2.3.5` tag：在仓库内留下可核查的说明或补救动作（例如 CHANGELOG 注释、文档记录「该 tag 构建时 versionName 为 2.3.4」、或团队认可的 tag 重打策略），避免后续维护者误判。

**验收标准**

- [ ] 当前 `app/build.gradle.kts` 中版本号与「下一预期发版号」及 CHANGELOG 未发版段落一致（若下一版为 2.3.5 修复版或 2.3.6，须在 PRD/design 中明确目标版本，本任务交付时 Gradle 与 CHANGELOG 对齐）。
- [ ] 发版检查清单（可写入 `implement.md`）：打 tag 前 `versionName` 与 tag 后缀一致；`versionCode` 单调递增。
- [ ] 文档或 CHANGELOG 中记录 `v2.3.5` 历史不一致事实及用户可见影响（若有）。

---

### 4. G-01 — Web JWT 默认关闭且 LAN 绑定暴露 `/api`（P1）

**位置（精确引用）**

- `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt:622` — `webServerJwtEnabled: Boolean = false` 默认值。
- `app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt:65,86-178` — `jwtEnabled == false` 时 `/api` 下 `conversationRoutes`、`settingsRoutes`、`filesRoutes`、`assetsRoutes` **无** `authenticate` 包裹（`173-177`）。
- `web/Entry.kt` / `WebServerManager` — 非 localhost 时 host `0.0.0.0`（审计 G 报告 §5.1）；与默认 JWT 关组合扩大 LAN 暴露面。
- 设置 UI：`SettingWebPage.kt` 已有 JWT 开关与密码联动（`256-300` 行附近）。

**应达成什么**

- 降低「用户开启 Web 服务但未配置 JWT」时的默认风险：在**新用户/默认配置**或**首次开启 LAN 访问**场景下，须使用户明确知晓未认证 API 暴露，并采取 design 定义的默认收紧（例如默认仅 localhost、默认启用 JWT、或开启 LAN 前强制确认/配置密码）。
- 已显式关闭 JWT 且绑定 LAN 的老用户：允许保留能力，但须有设置页风险提示，且行为可预测。

**验收标准**

- [ ] 全新安装（或清除 Web 相关偏好）后，默认配置下 LAN 不可在未认证情况下访问敏感 `/api` 路由，**或**启动/开启 LAN 前出现不可跳过的安全确认且需主动 opt-in 无 JWT。
- [ ] `webServerJwtEnabled == true` 且密码已配置时，`/api` 受保护路由行为与现网一致（401/403 语义不变）。
- [ ] `SettingWebPage`（或等价入口）对「JWT 关闭 + 非 localhost」组合有可见警告文案。
- [ ] 手动测试矩阵记录在 implement.md：localhost only / LAN + JWT off / LAN + JWT on。

---

### 5. G-02 — Workspace shell 无应用层命令白名单（P1）

**位置（精确引用）**

- `workspace/src/main/java/me/rerere/workspace/WorkspaceManager.kt:123-147` — `executeCommand` 仅要求 `command` 非空、`cwd` 在 workspace 内为目录。
- `workspace/src/main/java/me/rerere/workspace/WorkspaceShellRunner.kt:26-27`（`HostShellRunner`）— `ProcessBuilder(shell, "-c", context.command)`，无命令过滤。
- `workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt:47` — 同样接收原始 `command` 字符串。
- 调用链：`app/.../data/ai/tools/WorkspaceTools.kt`（AI 工具）、`WorkspaceRepository.executeCommand`、`WorkspaceDetailVM` 等。

**应达成什么**

- 在应用层对经 AI 工具（及与主会话/子代理共享的 workspace 执行路径）传入的 shell 字符串增加**可配置的约束**，使「任意命令」不再是默认无限制行为；约束形式（白名单、黑名单、仅允许 proot 内、按工具名分级等）在 design.md 选定，但须覆盖明显高危模式（如读写 workspace 外路径的尝试、环境破坏类命令的最低限度拦截或审批挂钩）。
- 与 A-01 协同：若 shell 需审批，约束与审批流同时生效，不互相绕过。

**验收标准**

- [ ] design 中选定的策略对至少一组高危样本命令产生拒绝、降级或强制审批（样本列表在 implement.md）。
- [ ] 合法开发用途命令（如 `ls`、`cat` 项目内文件、用户文档中的示例）在默认/推荐配置下仍可用。
- [ ] `workspace` 模块单测或仪器测试覆盖命令过滤/拒绝路径（不依赖真机 shell 的用例优先 JVM 测试）。
- [ ] AI `WorkspaceTools` 执行失败时，错误信息对模型/用户可理解（不泄漏内部策略细节过多）。

---

## 总体验收标准

- [ ] 上述 5 项各自的子验收全部满足。
- [ ] `.\gradlew :app:compileDebugKotlin` 与相关模块 `:workspace:test`（若新增）通过。
- [ ] 本任务 **不** 修改 `06-28-nightly-audit` 归档报告；变更说明可写入本任务 `implement.md` 或 CHANGELOG（仅当发版相关项 F-01 触达用户可见版本时更新 CHANGELOG）。
- [ ] `design.md` 完成后再 `task.py start` 进入实现阶段（复杂任务门禁）。

## 依赖与顺序建议（供 design/implement，非实现细节）

1. **A-01** 与 **G-02** 均影响「子代理 + workspace + 审批」；宜在同一设计章节统一威胁模型。
2. **G-01** 可与设置页文案、默认 DataStore 迁移独立并行，但需与 Web 前端 `web-ui` 鉴权假设（`conversations.tsx` `webAuthEnabled`）对齐验证。
3. **D L1** 可优先交付（改动面集中在 `LogPage` + 可能复用 `LogRedaction`）。
4. **F-01** 可与代码修复并行，但涉及 tag/CHANGELOG 决策需产品/维护者确认目标版本号。

## 参考材料

- `.trellis/tasks/archive/2026-06/06-28-nightly-audit/audit-report.md`（批次 A #1、#2、#4、#8、#9）
- `.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-a-runtime.md`（A-01）
- `.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-d-chat-log.md`（L1）
- `.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-f-infra.md`（F-01）
- `.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-g-dedup-web.md`（G-01、G-02）

## Notes

- 本文件仅含需求、约束与验收；技术方案、数据流、默认迁移与回滚见 `design.md`；执行顺序与验证命令见 `implement.md`。
- 下一步：撰写 `design.md` 后评审，再 `task.py start`。
# 夜间无人值守审计总报告

- **日期**: 2026-06-28（夜间）
- **任务**: `.trellis/tasks/06-28-nightly-audit`
- **HEAD**: `3ebfbca45893deefb8c61768a4bdb0e01255a5f6`
- **仓库**: `D:\2026Code\Group_android\rikkahub`
- **分支**: `release/rikka-arsucar`（本地领先 `origin` 4 commits；stash 保存了 5 个未提交文件，待恢复）
- **范围**: 自 fork 独立于上游以来所有 fork-only 变更（`upstream/master..HEAD --no-merges`，≈ 2464 commits，1904 行 commit 主体）
- **性质**: **只读审计**，未修改任何业务代码。报告全文 7 路并行子代理结果聚合。

## 一、 关键摘要

> **本审计只为「明早读者」服务**：每条发现都给出 file:line + 修复方向 + 评级，方便人工取舍。
> P0 是必须修的崩溃/数据丢失/安全/合规性问题。
> P0/P1 合计 **14** 条；建议明早一次性评估 P0 后再动工。

### 行动前 8 条（按 severity 排序，最优先在前）

| 序 | 严重度 | 摘要 | 位置 |
|----|--------|------|------|
| 1 | **P0 安全** | Sandbox 把所有 sandbox 工具 `needsApproval=NO_APPROVAL`，**绕过** `applySubagentWorkspaceApproval`：子代理工作区写/shell + 继承 MCP 工具**永不进入** Pending 审批流。 | `app/.../subagent/SubagentHost.kt:35,405-406` + `app/.../service/ChatService.kt:1765-1774` |
| 2 | **P0 安全/隐私** | LogPage 详情（`RequestLogDetail`）明文展示 `requestHeaders/Body/responseHeaders`，与 LogsTool/导出策略**不一致**。 | `LogPage.kt:186-191,304-411` |
| 3 | **P0 UX/状态机** | 会话加载路径（`initializeConversation` / `getOrCreateSession`）**不**清理陈旧 `subagent_streaming=true`。冷启动遭遇进程被杀、UI 永久 spinner。 | `ChatService.kt:245-260,331-335` |
| 4 | **P0 发版合规** | `tag v2.3.5` 对应 `app/build.gradle.kts` 内 `versionName` 仍为 `2.3.4`，与 CHANGELOG.md 段落脱节。 | `git show v2.3.5:app/build.gradle.kts` |
| 5 | **P0 i18n** | `ImgGenPage.kt` 回收站 / 分组 / gpt-image-2 设置 / 列数增减 `contentDescription` 大量中文硬编码（**当前最大单文件硬编码面**）。 | `app/.../pages/imggen/ImgGenPage.kt:363,449,1572-1648,2413-2525,2627-2734,2808,2822` |
| 6 | **P0 i18n** | `safe_mode_enter_app` 在简中亦缺失；非英文用户看到 "Enter App" 字面回退。 | `SafeModeActivity.kt:119` + `values/strings.xml:662` |
| 7 | **P0 i18n（102 keys 空洞）** | ja / ko-rKR / ru / zh-rTW **缺失 102 个 key**，集中在 subagent / model_list / log export。`6dca48a1` 大提交未走 locale-tui 同步。 | `values-{zh-rTW,ja,ko-rKR,ru}/strings.xml` |
| 8 | **P1 安全** | `webServerJwtEnabled` 默认 false；LAN 绑定 `0.0.0.0` 暴露 `/api` 全部未认证路由（conversations/settings/files/assets）。 | `WebApiModule.kt:65,86-178` + `PreferencesStore.kt:622` |

### 后续 P1（建议本次冲刺一并处理）

| 序 | 摘要 | 位置 |
|----|------|------|
| 9 | Workspace shell 无应用层命令白名单（AI 工具可执行任意 `command`）。 | `WorkspaceManager.kt:123-147` + `HostShellRunner.kt:26-27` |
| 10 | remember(`context.tool`) 在 `SubagentToolUIs` 中只对引用计 key，原地更新 metadata 可能跳过重组 → 静态 spinner。 | `SubagentToolUIs.kt:63,77,115-118` |
| 11 | ModelList tag 筛选下，**收藏列表不与 tag 联动**，与"按 tag 看模型"心智冲突。 | `ModelList.kt:319-327 vs 336-338` |
| 12 | Assistant 扩展（quickMessage / modeInjection / lorebook）**孤立 ID 残留**（Web API 校验通过、原生扩展页不 prune）。 | `AssistantExtensionsPage.kt:112-173` + `AssistantDetailVM.kt:163-178` |
| 13 | `ThinkTagTransformer` 多块解析**无单测**；子代理流是否同样被此 transformer 处理未确认。 | `ThinkTagTransformer.kt:10-36` |
| 14 | LogRedaction 头/体覆盖窄：缺 session-id/password 头，body 仅匹配 `Bearer` 后的 token/JSON 字符串，无法去 query `?api_key=` 或非 JSON 文本。 | `common/.../LogRedaction.kt:3-46` |

### 中性/正信号（无需改）

- BUILTIN → GLOBAL 迁移幂等性、disabledGlobal 合并正确（`PreferencesStore.migrateSubagentBuiltinsIfNeeded` + `SubagentModelTest.kt`）。
- `childTranscript` 死代码已 grep-clean（`SubagentProfile.kt`）。
- CancellationException 在主链路 (`SubagentHost:145`, `GenerationHandler:457`) 已正确传播。
- Path prefix gate + tests 良好（`SubagentPermissionBuilder:56-74`）。
- 三个 Provider sealed 子类（OpenAI/Google/Claude）的 `copyProvider` 已实现并传播 `tags`。
- 5 语言 strings.xml：search 模块 5 key 完全对齐；en ↔ zh 占位符无差异。

---

## 二、 分模块发现（全量）

### Subagent 数据 / 运行时 / 权限（Subagent A → `research/subagent-a-runtime.md`）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| P0 | **A-01** | `SubagentHost.kt:35,405-406` + `ChatService.kt:1765-1774` | `sandboxToolsForSubagent` 把 `needsApproval=NO_APPROVAL`，绕开 `applySubagentWorkspaceApproval`（`SubagentPermissionBuilder.kt:76-92`）；子代理工作区写/shell/继承 MCP 工具**永不进入** `GenerationHandler.kt:214-218` Pending 流。修复：把 sandbox 限定为安全工具子集，或对 child 路径不调 approval builder 并文档化"全自动 + audit"。 |
| P1 | A-02 | `SubagentPermissionBuilder.kt:134-136` | `inheritTools=false` 仅留工作区工具，仍为 TODO。 |
| P1 | A-03 | `GenerationHandler.kt:257-265` + `ChatService.kt:1135-1195` | 并行 `spawn_subagent` 缺会话级 progress backpressure。 |
| P2 | A-04 | `SubagentHost.kt:201-218` | 节流 `var` 非原子；并发 `launch` 下仍可能高频更新。 |
| P2 | A-05 | `ChatService.kt:1682-1690` | workspace 工具工厂 `runBlocking`。 |
| P2 | A-06 | `PreferencesStore.kt:644-649` + `Assistant.kt:53` | 迁移合并到 `disabledGlobalSubagents` 但保留 `disabledBuiltinSubagents`（兼容，可接受）。 |
| P2 | A-07 | `SubagentRuntimeTest.kt` | 无 `SubagentHost.spawn` / 取消 / sandbox 测试。 |
| P3 | A-08 | `PreferencesStore.kt:647` | 未知 builtin disable 名静默丢弃。 |
| P3 | A-09 | `SubagentProfile.kt:118-125` | `childTranscript` 已 grep-clean。 |
| P3 | A-10 | 全局 | 无 `PermissionGate` / approval job join；命名与设计文档略漂移。 |

### Subagent UI（Subagent B → `research/subagent-b-ui.md`）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| P1 | **R1** | `SubagentToolUIs.kt:63,77,115-118` | `remember(context.tool)` 仅以引用计 key，原地更新 `output`/`metadata` 会跳过重组 → streaming 卡顿/冻结。 |
| P1 | **S1/S5** | `ChatService.kt:245-260`（与 `06-28-review-fixes` R3 同） | 会话 bootstrap 不清 stale `subagent_streaming`；进程死后 UI 永久 spinner。 |
| P2 | R2 | `SubagentToolUIs.kt:105-106` | `hasSummary` 在 `remember` 外每次重算。 |
| P2 | E6 | `SubagentToolUIs.kt:186-195` | fallback raw `output` 文本可能含敏感或日志 dump。 |
| P2 | G1 | `ExtensionSubagentProfilePage.kt:86-99` | `SubagentProfileForm` 未传 `globalProfiles`（与 assistant 编辑路径不一致）。 |
| P2 | A1 | `SubagentToolUIs.kt:403,454,485,537` | step/tool 图标 `contentDescription = null`。 |
| P2 | A2 | `AssistantSubagentPage.kt:142,146,230,241,264,267` | action 图标（refresh/add/delete/copy）`contentDescription = null`。 |
| P2 | A3 | `ExtensionSubagentsPage.kt:77,80,139,142` | add/overflow/navigate/delete 图标 `contentDescription = null`。 |
| P2 | C4 | `SubagentToolUIs.kt` vs `ChatMessageReasoning.kt:96-100` | streaming 长 transcript 无自动滚动，与推理 CoT 不对称。 |
| P3 | A4/A5/I2/L3/R3/R4/R4 | ... | a11y / icon / i18n 小缺口 |

> **路径订正说明**：`ui/pages/setting/` 下**无** subagent 设置；实际在 `assistant/detail/*Subagent*` 与 `extensions/*Subagent*`；`SubagentProfileForm` 是 ui 层 `internal fun`，**非** `data/ai/subagent/SubagentProfileForm.kt`（该文件不存在）。

### i18n（Subagent C → `research/subagent-c-i18n.md`）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| P0 | L-01 | `ImgGenPage.kt:363,449,1572-1648,2413-2525,2627-2734,2808,2822` | 回收站/分组/gpt-image-2/列数 contentDescription 大量中文硬编码；非 en 语言用户看到中文。 |
| P0 | L-02 | `values-{zh-rTW,ja,ko-rKR,ru}/strings.xml` | **102 keys 整块缺失**（subagent / model_list / log export / safe_mode_enter_app 等）。 |
| P0 | L-03 | `SafeModeActivity.kt:119` | `safe_mode_enter_app` 在简中亦缺。 |
| P1 | L-04 | `values-zh/strings.xml` | 简中缺 15 个 key（model_list a11y / subagent_step_* / safe_mode_enter_app）。 |
| P1 | L-05 | `TTSProviderConfigure.kt:1122-1399` | TTS StepFun 配置页 description/placeholder 中文硬编码；en 已有 ASR 对称资源。 |
| P1 | L-06 | `ASRProviderConfigure.kt:529` | 热词 placeholder 硬编码。 |
| P1 | L-07 | `SettingDonatePage.kt:119` | "爱发电" 硬编码（en/繁中用户看到中文专有名）。 |
| P2 | L-08 | `values/strings.xml:507-511,743,750` | en 默认串含中文示例（语言自名 + "阶跃星辰"/"三百 → 300"）；与"en 纯英文"惯例不一致；上轮 review 仅查 4 key 远不够。 |
| P2 | L-09 | `.agents/skills/locale-tui-localization/SKILL.md` | `6dca48a1` / `8be9f419` 直接改 XML，未走 locale-tui 同步；缺发布前 key 覆盖率门禁。 |
| P2 | L-10 | `values-zh` vs `values-zh-rTW` | 1096 key 用语差异（属预期）；但繁中**整段缺失** 102 个，繁体用户体验先于简体遇英文回退。 |
| P3 | L-11/L-12/L-13 | ... | Debug/Preview/HTML/字典类中文非 UI i18n 缺陷。 |

> **统计**：app 基准 1395 keys；zh 缺 15；zh-TW/ja/ko/ru 各缺 102；search 模块 5/5 一致；ai/document/web/speech/highlight/common 模块无 strings.xml。

### ChatService / 日志脱敏 / 持久化（Subagent D → `research/subagent-d-chat-log.md`）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| P0 | S1 | `ChatService.kt:331-335` | 加载会话不清理 stale `subagent_streaming`。 |
| P0 | L1 | `LogPage.kt:304-411` | `RequestLogDetail` 明文密钥/Cookie。 |
| P1 | S2 | `ChatService.kt:1238-1280` | `cleanupStreamingSubagentMetadata` 只处理**最后一条** ASSISTANT 的 spawn 工具 — 更早条目仍残留。 |
| P1 | S3 | `ChatService.kt:1108-1132` + `673-677` | 长流式回复 + 并行子代理 → CAS retry 满 → 丢 UI/内存状态更新。 |
| P1 | DS-1 | `ConversationRepository.kt:220-228` | save = 全量 delete + insert message_nodes。 |
| P1 | L2 | `common/.../LogRedaction.kt:22-27` | body 脱敏面窄；`?api_key=` / 非 JSON 体不遮。 |
| P1 | L3 | `common/.../LogRedaction.kt:3-13` | 缺 x-session-id / session-id / password 头。 |
| P2 | S4 | `ChatService.kt:1159-1163` vs `1204-1235` | JSON text `streaming` 与 metadata 字段不同步。 |
| P2 | S5 | `ChatService.kt:389-392,449-451` | `catch (e: Exception)` 吞掉取消，不区分 Cancellation。 |
| P2 | S6 | `SubagentHost.kt:211-215` | 节流后 IO 队列仍可堆积。 |
| P2 | L4 | `LogRedaction.kt:39-40` | `TextLog.redacted()` 原样返回；栈轨迹可能含 provider 错误正文。 |
| P2 | DS-2 | `PreferencesStore.kt:417-459` | settings JSON 整包写（写放大）。 |
| P3 | S7 | `ChatService.kt:687-695` | 取消走 onFailure 日志路径。 |
| P3 | L5 | `Logging.kt:47-49` | 日志关闭后不清历史。 |
| P3 | T1 | `LogsTool.kt:85-105` | 入参 schema 白名单充分。 |

### ProviderTags / ModelList / 扩展 sections / ThinkTag（Subagent E → `research/subagent-e-provider-ext.md`）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| P1 | E-P1-1 | `ModelList.kt:319-327` vs `336-338` | tag 筛选时收藏区展示所有 modelType 收藏，与当前 tag 心智不一致。 |
| P1 | E-P1-2 | `AssistantExtensionsPage.kt:112-173` + `AssistantDetailVM.kt:163-178` | 助手上的扩展 ID 在全局条目删除后**不自动 prune**（Web 已校验，原生扩展页不会）。 |
| P1 | E-P1-3 | `ThinkTagTransformer.kt:10-36` | 多块解析无单测；子代理流路径覆盖不完整。 |
| P2 | E-P2-1 | `SettingModelPage.kt:270-272` + `ChatService.kt:819` | title 模型 UI 空显示「选择模型」，后台**静默**用 fastModelId，用户不知。 |
| P2 | E-P2-2 | `SettingModelPage.kt:207-209` + `ChatService.kt:860` | suggestion 模型同上（fallback fast）。 |
| P2 | E-P2-3 | SettingProviderPage/ModelList tag | tag 匹配**大小写敏感**，中英混用易筛不到。 |
| P2 | E-P2-4 | `SettingProviderDetailPage.kt:308-329` | 删除 chip 的 trailing `IconButton` `contentDescription = null`（L324）。 |
| P2 | E-P2-5 | `ModelList.kt:347-352` | tag+search 后 provider 空分组：仅有 header，无单独空状态文案。 |
| P3 | E-P3-1..5 | - | 文本省略 / 硬编码 / 持久化策略 / 测试覆盖。 |

> **路径订正**：`app/.../data/model/Provider.kt`、`ModelMapping.kt` 不存在；提供商类型为 `ai/.../ProviderSetting.kt`。

### 构建/CI/文档/release 流程（Subagent F → `research/subagent-f-infra.md`）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| **P0** | F-01 | tag `v2.3.5` + `app/build.gradle.kts` | 标签 v2.3.5 构建时 `versionName` 仍为 2.3.4；与 CHANGELOG 段落脱节。 |
| P1 | F-02 | `.github/workflows/release.yml:37-38` | 仍写入 `app/google-services.json`（已无需）。 |
| P1 | F-03 | `release-apk.yml:4-7` vs `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:66` | workflow 同时支持 `push: tags` + `dispatch`；文档称仅 dispatch。 |
| P1 | F-04 | `release-apk.yml:21-38` vs `4-7` | bump 仅在 dispatch；push tag 时不 bump。 |
| P2 | F-05 | `gradle/libs.versions.toml:40-42,134-137,192-193` | Firebase / google-services catalog 残留。 |
| P2 | F-06 | `AGENTS.md:17` + README*.md + `README_FOR_AGENT.md:23,54,68` | 仍要求/描述 Firebase 与过时版本。 |
| P2 | F-07 | `.github/workflows/release.yml` | 上游式 workflow 无 submodule/pnpm/arm64 约束（与 fork 策略冲突）。 |
| P2 | F-08 | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` §2.6/§3 | 实施前速查与触发策略过时。 |
| P2 | F-09 | 双 workflow | "Release Build" vs "Release APK (arm64)" 易误用。 |
| P3 | F-10..12 | - | 文档版本号、baselineProfiles 历史 Firebase 符号、README_FOR_AGENT 应用配置。 |

### ListCard / dedup / Web / Workspace / Speech（Subagent G → `research/subagent-g-dedup-web.md`）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| P1 | G-01 | `WebApiModule.kt:173-177` + `PreferencesStore.kt:622` | JWT 默认关闭 + 0.0.0.0 → LAN 完整 Web API 无认证。 |
| P1 | G-02 | `WorkspaceManager.kt:123-147` + `HostShellRunner.kt:26-27` | shell 无应用层命令白名单。 |
| P2 | G-03 | 全 app UI 列表 | 无 `BaseListCard`；reorder/swipe/删除模式分裂（8 套独立 `*Item`）。 |
| P2 | G-04 | `SearchService.kt:343-358` vs `common/http/Request.kt:11-27` | 重复 `Call.await()`。 |
| P2 | G-05 | `ExaSearchService.kt:88` 等 vs Tavily `await()` | search 同步/异步 HTTP 混用。 |
| P2 | G-06 | `SearchServiceOptions` | 无 `copyProvider`（与 AI/speech 目标不一致）。 |
| P2 | G-07 | `Entry.kt:29` | CORS `anyHost()` 与 JWT 关闭组合放大。 |
| P2 | G-08 | `DashScopeASRController` + `ASR.kt` | 严格 `stop` 绑定页面生命周期。 |
| P3 | G-09..11 | - | 搜索列表无侧滑删除、scrape failure 文案统一、TTS/ASR 与 AI tags 不对称。 |

### DashScope 澄清（path verification）

- DashScope 是**完整 ASR WebSocket 实现**（`DashScopeASRController.kt`），**不**属于 search scrape stub 范畴。

---

## 三、 跨子代理共有主题分布

| 主题 | 子代理 | P0 | P1 | P2 | P3 |
|------|--------|----|----|----|----|
| **subagent 权限/沙箱** | A | 1 | 2 | 4 | 2 |
| **subagent UI 流式与生命周期** | B / D | 1 (D) | 2 | 5 | 4 |
| **i18n / locale-tui** | C | 3 | 4 | 3 | 1 |
| **ChatService / DataStore** | D | 1 | 3 | 3 | 2 |
| **日志脱敏** | D | 1 | 2 | 1 | 1 |
| **Provider tags / ModelList** | E | 0 | 3 | 5 | 5 |
| **Assistant 扩展 ID / ThinkTag** | E | 0 | 2 | 0 | 0 |
| **构建/发版/CI/文档** | F | 1 | 3 | 5 | 3 |
| **Web JWT / CORS / Workspace shell** | G | 0 | 2 | 6 | 3 |
| **dedup / copyProvider** | G | 0 | 0 | 4 | 2 |
| **合计** | - | **8** | **21** | **36** | **23** |

> 子代理 D 的"清理仅最后一条 spawn"是 P1，与子代理 B 的 P1 同源但不同位置，**未双计数**到总 P1 21 中作去重。

---

## 四、 推荐明早行动（按可并行切片）

### 批次 A：安全与合规（必做，时序敏感）

1. **P0 #1（Subagent A-01）** — 子代理 sandbox 与审批流冲突。
2. **P0 #2（Subagent D L1）** — LogPage 详情脱敏。
3. **P0 #4（Subagent F F-01）** — v2.3.5 tag/versionName 对齐（决定下一发版基础）。
4. **P1 #9（Subagent G G-01）** — Web JWT / localhost 默认值。
5. **P1 #9（Subagent G G-02）** — Workspace shell 白名单（亦与 #1 关联）。

### 批次 B：状态机与持久化

6. **P0 #3（Subagent D S1）** — 会话 bootstrap 清 stale streaming（已是 `06-28-review-fixes` R3）。
7. **P1 #10（Subagent B R1）** — `remember` deps 修正（已是 `8be9f419` 后续）。
8. **P1 (D S3, DS-1)** — CAS 上限 + saveConversation 增量持久化。

### 批次 C：i18n 批量补

9. **P0 #5/#6/#7** — ImgGenPage + safe_mode_enter_app + 102 keys（一次性 `locale-tui add` 与手工 fallback）。
10. **P1 (C L-04..L-07)** — 简中补 15 + ASR/TTS placeholder + 捐赠页。

### 批次 D：UI 一致性

11. **P1 #11/#12（Subagent E-P1-1/2）** — ModelList tag 与收藏一致性 + 助手扩展 ID prune。
12. **P1 (#13)** — ThinkTagTransformer 单测。
13. **P2 (B A1..A3, E-P2-4)** — a11y contentDescription 补全。

### 批次 E：构建/CI/文档

14. **P1 (F-02..F-04)** — 关闭上游 workflow、tag/dispatch 策略对齐。
15. **P2 (F-05..F-09)** — catalog 清理、文档刷新、双 workflow 风险标记。

### 批次 F：架构长期债务（建议单独立项）

16. **P2 (G-03)** — 列表 UI 抽象统一（若仍要求 11 ListCard 一致）。
17. **P2 (G-04..G-06)** — `Call.await` dedup、`copyProvider` 全模块对齐。

---

## 五、 子代理报告索引

- `.trellis/tasks/06-28-nightly-audit/research/subagent-a-runtime.md` — Subagent 数据 / 运行时 / 权限
- `.trellis/tasks/06-28-nightly-audit/research/subagent-b-ui.md` — Subagent UI
- `.trellis/tasks/06-28-nightly-audit/research/subagent-c-i18n.md` — i18n
- `.trellis/tasks/06-28-nightly-audit/research/subagent-d-chat-log.md` — ChatService + 日志脱敏 + 持久化
- `.trellis/tasks/06-28-nightly-audit/research/subagent-e-provider-ext.md` — Provider tags / ModelList / 扩展 sections / ThinkTag
- `.trellis/tasks/06-28-nightly-audit/research/subagent-f-infra.md` — 构建 / CI / 发布 / 文档 / 派生流程
- `.trellis/tasks/06-28-nightly-audit/research/subagent-g-dedup-web.md` — ListCard / dedup / Web / Workspace / Speech

---

## 六、 任务状态 / 不变量

- 工作区已 `git stash push --include-untracked -m nightly-audit-2026-06-28`：5 文件暂存。
- `git status`：working tree clean。
- `git log --no-merges upstream/master..HEAD --oneline | measure`：2464 commits（含任务归档）。
- 任何 source/manifest 的 `git diff --stat` 应为空（除 stash）。
- 下一步：恢复 stash → `git stash pop`，并把本审计报告作为下一轮修整的源头目录。

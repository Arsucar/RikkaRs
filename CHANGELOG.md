# Changelog / 更新日志

All notable changes to the Rikka-Arsucar fork will be documented in this file.
本文件记录 Rikka-Arsucar 下游 Fork 的所有重要变更。

> **发版流程**：每次推送版本标签前，必须先在本文件中新增对应版本段落并提交。
> AI 助手在用户请求「发版 / 打标签 / 推版本」时，应：
> 1. 用 `git log <上个标签>..HEAD --oneline` 列出上个标签至今的所有提交，逐条判断是否为用户可见变更（忽略 chore/task archive/docs 等）；
> 2. 将用户可见变更写入本文件对应版本段落（未存在则新建 `## vX.Y.Z`），格式为中英双语，包含 issue 编号；
> 3. 从对应版本段落截取中英双语内容，作为 `git tag -a` 的消息体 and GitHub Release body；
> 4. 再执行打标签和推送。

---

## Unreleased

### 新功能与修复 / Features & Fixes

- **合并上游 rikkahub 2.4.6–2.4.12+** — 将 `upstream/master` tip `fa0305ba` 合入 `release/rikka-arsucar`：AI 流式事件归一、豆包搜索、网络设置（UA/代理）、备份可选导入与覆盖确认、工作区终端后台多 Tab、空 tool schema / ASR / JWT·R8 等修复；保留 fork 身份（`me.arsucar.rikka`、无 Firebase、`release-apk.yml`）、Clash #209、ChatInput 工具栏常驻、搜索选择器与推理刻度 UI。
  **Merge upstream rikkahub 2.4.6–2.4.12+** — Merged `upstream/master` tip `fa0305ba` into `release/rikka-arsucar`: stream-event normalization, Doubao search, network settings (UA/proxy), selective backup import with overwrite confirm, workspace terminal background/multi-tab, empty tool-schema / ASR / JWT·R8 fixes; kept fork identity (`me.arsucar.rikka`, no Firebase, `release-apk.yml`), Clash #209, always-visible ChatInput toolbar, SearchPicker, and reasoning-tick UI.

---

## v2.3.53

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.52 之后）

- **修复助手提示词页空白（#304）** — `#298` 的 `WhileSubscribed(5000)` 冷启动导致 `AssistantPromptPage` 的 `rememberTextFieldState` 捕获空默认值后不同步；添加与 `AssistantSubagentProfilePage` 一致的 inbound sync guard。
  **Fix assistant prompt page blank render (#304)** — `WhileSubscribed(5000)` cold start from #298 caused `rememberTextFieldState` to capture the empty default and never sync; added inbound sync guard matching `AssistantSubagentProfilePage`.

- **修复对话页左右抽屉同时展开（#301）** — 任一抽屉打开时互斥关闭另一个；左抽屉 `gesturesEnabled` 在右抽屉激活时禁用；外层手势门控改用 `isActive` 覆盖动画窗口。
  **Fix chat page left/right drawer simultaneous expansion (#301)** — Mutual exclusion via `LaunchedEffect` on `targetValue`; left drawer `gesturesEnabled` disabled when right drawer is active; outer gesture layer uses `isActive` to cover animation window.

- **新增酒馆角色卡世界书导入（#302）** — 导入 v2/v3 角色卡时自动检测 `data.character_book` / `data.extensions.world`，检测到附加内容时弹窗确认，用户可选择导入或跳过；导入后世界书关联到新助手。
  **Add tavern card world book import (#302)** — Detect `data.character_book` / `data.extensions.world` from v2/v3 cards; show confirmation dialog with entry count when bindings detected; imported lorebooks are associated to the new assistant via `lorebookIds`.

---

## v2.3.52

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.51 之后）

- **修复标题/模型名不更新** — `TopBar` 中 `remember { derivedStateOf { conversation.title } }` 的 `conversation` 是普通函数参数而非 Compose State，`derivedStateOf` 无法追踪参数变化，导致标题、模型名、助手名冻结在首帧值；移除 `derivedStateOf` 改为直接读取。
  **Fix title/model name not updating** — `remember { derivedStateOf { conversation.title } }` in `TopBar` used `conversation` as a plain parameter, not Compose State; `derivedStateOf` cannot track parameter changes, freezing title/model/assistant name at first-frame values; removed `derivedStateOf`, read directly.

---

## v2.3.51

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.50 之后）

- **修复启动闪退（#301）** — `Migration_52_53` 缺少 `subagent_contexts.context_completeness` 列的 `ALTER TABLE`，导致从 v52 升级的用户启动时 Room schema 校验失败闪退；同时移除 `AppDatabase` 中与手动 migration 冲突的 `AutoMigration(52→53)`。
  **Fix startup crash (#301)** — `Migration_52_53` was missing `ALTER TABLE` for `subagent_contexts.context_completeness`, causing Room schema validation failure on launch for users upgrading from v52; also removed conflicting `AutoMigration(52→53)` from `AppDatabase`.

---

## v2.3.50

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.49 之后）

- **ChatVM 拆分与乐观写入（#295, #296）** — 原 800+ 行 ChatVM 拆分为 7 个卫星 VM（ChatMessageVM / ChatGitVM / ChatHookVM / ChatMemoryTableVM / ChatDraftVM / ChatContextVM），共享 NavBackStackEntry；引入 `OptimisticWrite` 并发原语与 `OptimisticWriteCoordinator` generation 机制，标题/置顶/移动/收藏等操作乐观更新 UI 后异步持久化，失败回滚；修复 `updateTitle` persist 读取 stale `conversation.value` 的问题改用 snapshot；修复 `moveConversationToAssistant` persist 部分成功导致 session/DB 不一致的问题。
  **ChatVM split & optimistic writes (#295, #296)** — Split the 800+ line ChatVM into 7 satellite VMs sharing NavBackStackEntry; introduced `OptimisticWrite` primitive with `OptimisticWriteCoordinator` generation gating; optimistic UI updates for title/pin/move/favorite with async persist and rollback; fixed `updateTitle` stale `conversation.value` read in persist; fixed `moveConversationToAssistant` session/DB mismatch on partial persist failure.
- **设置原子写入（#267）** — `updateSettings` 改为 transform-based 原子写入，避免并发字段更新互覆盖。
  **Atomic settings transform (#267)** — `updateSettings` now takes a `(Settings) -> Settings` transform to prevent concurrent field overwrites.
- **DI 约定规范化** — 新增 `.trellis/spec/app/dependency-injection.md` spec 文档；AGENTS.md 补充 DI 速查；VM-scoped business object（`AssistantSwitchCoordinator`、`ToolConnectionStatusStore`）注释标注 Rule 5。
  **DI conventions spec** — Added dependency-injection.md spec; AGENTS.md DI cheat-sheet; annotated VM-scoped business objects per Rule 5.
- **detekt + ktlint 静态分析 CI** — 新增 `static-analysis.yml` workflow 和 `config/detekt.yml`；detekt 1.23.8 + ktlint 插件，`ignoreFailures = true` 报告模式。
  **detekt + ktlint CI** — Added static-analysis workflow and detekt config; detekt 1.23.8 + ktlint plugin in report-only mode.
- **代码健壮性修复（#283）** — 消除 `!!` 强解、`runCatching` 改用具体异常、InputStream 正确关闭、移除 `printStackTrace`。
  **Code robustness fixes (#283)** — Eliminate `!!`, specific catch, proper InputStream close, remove `printStackTrace`.
- **DB 复合索引（#288, #299）** — ConversationEntity 新增 4 个复合索引（assistantId/isPinned/updateAt 等），加速会话列表查询。
  **DB composite indexes (#288, #299)** — Added 4 composite indexes to ConversationEntity for faster list queries.
- **魔数提取为命名常量（#293, #300）** — 散落魔数统一提取为 companion object 常量。
  **Extract magic numbers to named constants (#293, #300)**.
- **SharingStarted.Eagerly → WhileSubscribed（#291, #298）** — 减少 ViewModel 不必要的上游订阅。
  **Replace SharingStarted.Eagerly with WhileSubscribed (#291, #298)**.
- **Modifier 顺序修复（#289, #297）** — clickable 包含 padding，点击区域正确。
  **Modifier order fix (#289, #297)** — clickable includes padding so tap target is correct.
- **统一 Skill 管理 UI（#265, #266）** — 三入口共用 SkillCard 组件。
  **Unified Skill management UI (#265, #266)** — Three entry points share SkillCard component.
- **runBlocking 替换（#279）** — 改用拦截器 + provider 非阻塞替代。
  **Replace runBlocking with non-blocking alternatives (#279)**.
- **磁盘扫描移出 settingsStore 锁（#280）** — skill 读取去重。
  **Move disk scan out of settingsStore lock + deduplicate skill reads (#280)**.
- **子代理预算耗尽防护（#286）** — 防止复用预算耗尽的 context，重置 tool budget。
  **Subagent budget exhaustion guard (#286)**.
- **Compose 批量性能优化（#282）** — `collectAsStateWithLifecycle` + items key。
  **Batch Compose perf (#282)** — `collectAsStateWithLifecycle` + items key.
- **磁盘 IO 包裹 Dispatchers.IO（#277）** — 避免主线程阻塞。
  **Wrap disk IO in Dispatchers.IO (#277)**.
- **流式 UI 卡顿缓解（#281）** — reasoning timer 50ms→200ms + Markdown debounce。
  **Reduce streaming UI jank (#281)** — reasoning timer 50ms→200ms + Markdown debounce.
- **MCP 传输层死代码清理（#278）**。
  **Remove dead code in MCP transport (#278)**.
- **i18n 外提** — SemanticMemory / ImgGen / Skills 页面硬编码中文提取为 stringResource。
  **i18n extraction** — Extracted hardcoded Chinese strings to stringResource in SemanticMemory / ImgGen / Skills pages.

---

## v2.3.49

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.48 之后）

- **工作区写入审批开关恢复生效** — 修复 `#258` 引入路径硬审批后，工作区详情「工具审批」关闭写入/编辑仍对任意路径弹审批的问题；开关显式关闭时完全跳过 path hard approval（含 `/skills` 等安全区外路径），默认关闭时 `/workspace`/`/tmp` 仍免审，受信目录与开关打开行为保持不变。
  **Workspace write approval switch works again** — After `#258` path hard-approval, turning off write/edit in workspace tool-approval no longer still prompts for every path; explicit OFF fully skips path hard approval (including outside free zones). Default OFF still free under `/workspace`/`/tmp`; trusted roots and switch ON unchanged.

---

## v2.3.48

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.47 之后）

- **删除独立注入双路径（#259 覆盖 #242）** — 移除全局 `modeInjections` / Reference 条目；升级与备份恢复时 Reference→Custom 内容快照，未入预设的孤儿注入并入 Default Preset；聊天扩展 5→4 tab；Web DTO/端点同步清理。
  **Remove standalone mode-injection dual path (#259 supersedes #242)** — Drops global `modeInjections` / Reference entries; upgrade and backup restore snapshot Reference→Custom and absorb orphans into Default Preset; chat extension tabs 5→4; Web DTO/routes cleaned.
- **工作区受信写入目录（#258）** — write/edit 在安全区外可「始终允许此目录」；前缀边界匹配；路径规范化拒绝 `..`；审批侧仅持久化服务端由 tool path 推导的根。
  **Trusted write roots for workspace (#258)** — Optional always-allow directory for write/edit outside free zones; boundary-safe prefixes; path normalize rejects `..`; approval persists only server-derived roots from the tool path.
- **Bind mount 真实浏览（#247）** — 文件浏览器列出/读取 `/skills`、`/upload`、`/tool_outputs`、`/skills_private` 等挂载内容；多助手私有技能入口选择；LINUX 区只读。
  **Bind-mount real browser (#247)** — Lists/reads mounted `/skills`, `/upload`, `/tool_outputs`, `/skills_private`; multi-assistant private-skills entry picker; LINUX area read-only.
- **预设 config-only 开关展示（#245）** — 内置 config-only 条目不再计入展示条数/名称，避免「可切换却不注入」误导。
  **Config-only preset switch display (#245)** — Builtin config-only entries no longer count toward display names/counts.
- **长思考流式卡顿缓解（#248）** — 推理展开不再 key 全文；流式跳过部分 `animateContentSize`；会话 UI 发布约 64ms 合并；流式路径跳过删文件 GC。
  **Streaming jank mitigation (#248)** — Reasoning expand no longer keys full text; skip some `animateContentSize` while loading; ~64ms UI publish coalesce; skip deleted-file GC on stream path.
- **统一空态/骨架/Toast（#237）** — `EmptyState`/`ErrorState`/`Shimmer`；会话列表与统计页接入；日志导出改 Sonner。
  **Unified empty/skeleton/toast (#237)** — `EmptyState`/`ErrorState`/`Shimmer`; conversation list and stats; log export uses Sonner.
- **Typography 品牌层级（#233）** — 完整 Material3 字阶；系统/品牌字体与字重、缩放偏好；实验开关保留高级项。
  **Typography hierarchy (#233)** — Full Material3 type scale; system/brand family, weight, scale prefs; experiment gate for advanced options.
- **赞助弹窗可永久关闭（#232）** — 设置项允许永久禁用 Sponsor 提示。
  **Permanently dismiss sponsor alerts (#232)**.
- **设置页可搜索直达（#234）** — 设置目的地搜索过滤，子页可直达。
  **Searchable settings destinations (#234)**.
- **检查点思考计时不串场（#238）** — 恢复检查点后不再让未闭合思考秒数继续墙钟累加。
  **Checkpoint reasoning timer isolation (#238)**.
- **429 重放关闭旧响应（#239）** — Clash 429 重放前关闭 previous response，避免 still-open 失败。
  **Close 429 responses before replay (#239)**.
- **PromptPage 死代码与 Sheet 竞态（#241,#243,#244）** — 移除零调用 ModeInjection UI；编辑取消不残留；Sheet Expanded 初始态。
  **PromptPage dead code and sheet races (#241,#243,#244)**.
- **聊天横滑手势排除（#240）** — 可横滑行注册 exclusion，减少误开抽屉。
  **Horizontal gesture exclusion on chat rows (#240)**.
- **预设名/描述失焦提交（#246）** — 缓冲编辑，失焦再落盘，避免快速返回丢字。
  **Preset name/description commit on focus loss (#246)**.
- **use_skill 返回技能目录清单（#230）** — 加载技能时附带目录文件列表。
  **use_skill returns skill directory listing (#230)**.
- **推理 OFF 不发非法 none** — 兼容路径省略无效 `reasoning_effort` 取值。
  **Reasoning OFF omits invalid effort values**.

---

## v2.3.47

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.46 之后）

- **变量宏引擎未知宏死循环修复** — 遇 `{{//}}` / `{{trim}}` / `{{newline}}` 等未知宏时不再「替换成自己」烧尽预算；ST 注释删除、`newline`→换行、`trim` 删除宏本身；全文展开预算与嵌套深度拆分（2000 / 20），大预设 50–200+ 宏可全部展开。（#226, #227）
  **Variable macro engine: stop spinning on unknown ST macros** — Unknown macros no longer self-replace and burn the expansion budget; ST comments delete, `newline`→LF, `trim` removes the macro; full-text budget split from nested depth (2000 / 20) so large presets expand fully. (#226, #227)
- **嵌套未知宏不阻断外层 setvar** — 未知宏临时换成非 `{{…}}` token，外层展开后再还原（含写入变量表的值），避免 `{{setvar::n::Hello {{char}}!}}` 永久卡住。（#226 follow-up）
  **Nested unknown macros no longer block outer setvar** — Parks passthrough macros as non-`{{…}}` tokens, restores them (including values written to the variable map) so nested `{{char}}` inside setvar no longer freezes expansion. (#226 follow-up)

---

## v2.3.46

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.45 之后）

- **推理强度 OFF 发送 none** — `mapReasoningEffort` 移除 `noneAsLow`；所有方言下 OFF 映射为 `none`，不再被 OpenAI 兼容默认改写成 `low`。（#214）
  **Reasoning OFF sends none** — Drops `noneAsLow`; OFF maps to `none` in every dialect instead of being rewritten to `low` for OpenAI-compat defaults. (#214)
- **Auto 方言 DeepSeek 识别收紧** — `resolveDialect` 去掉弱 model-id 提示；Auto 仅对官方主机（`api.deepseek.com`、`integrate.api.nvidia.com` + deepseek-v4）识别 DeepSeekMax；未知代理主机保持 level.effort 透传，需显式按模型覆盖方言。（#214）
  **Tighter Auto DeepSeek recognition** — Removes weak model-id hints; Auto recognizes DeepSeekMax only on official hosts; unknown proxies keep effort passthrough and require an explicit per-model dialect override. (#214)
- **预设开关局部写入 + 乐观 UI** — 聊天/助手扩展里的预设 Switch 走 `SettingsStore.toggleAssistantPreset`（mutex + 乐观 `settingsFlow` + 仅写 ASSISTANTS + 失败回滚），避免全量 settings 覆盖导致卡顿与丢写。（#218）
  **Preset Switch partial write + optimistic UI** — Routes the chat/assistant extension Presets Switch through `toggleAssistantPreset` (mutex, optimistic flow, ASSISTANTS-only edit, rollback) so full-settings overwrites no longer jank or drop updates. (#218)
- **实验性生成保活前台服务** — `ChatGenerationService` + 引用计数 `ChatKeepAliveController`；设置项默认关闭；与聊天通知 live-update 合并，避免双通知。（#219）
  **Experimental generation keep-alive FGS** — Adds specialUse `ChatGenerationService` with ref-counted `ChatKeepAliveController`; preference defaults off; merges with chat notification live-updates to avoid dual notifications. (#219)
- **实验性 N 步会话检查点缓存** — 生成过程中按间隔写入检查点元数据（Room 48→49），中途保存跳过 FTS；hydrate 恢复时 toast；检查点写入不覆盖 live 流式状态。（#220）
  **Experimental N-step conversation checkpoint cache** — Mid-generation checkpoint metadata (Room 48→49) with interval settings, skip-FTS mid-gen saves, recovery toast on hydrate; checkpoint writers do not overwrite live streaming state. (#220)
- **会话变量系统（ST 宏 + MVU）** — 助手门控 `enableVariableSystem`；`Conversation.variables` / `MessageNode.variableSnapshots`；VariableMacro / UpdateVariable 变换器（PromptInjection→Macro→Placeholder）；Room 49→50；抽屉 CRUD；支持 ST `<JSONPatch>` XML 外壳解析。（#216, #217）
  **Conversation variables (ST macros + MVU)** — Assistant-gated `enableVariableSystem`, conversation/node variable snapshots, VariableMacro/UpdateVariable transformers (PromptInjection→Macro→Placeholder), Room 49→50, drawer CRUD, and ST `<JSONPatch>` XML shell parsing inside UpdateVariable blocks. (#216, #217)
- **实验功能注册表** — 全局/助手实验页由 `ExperimentalFeatureRegistry` 驱动；`chat_keepalive` / `checkpoint_cache` / `variable_system` 经 map+legacy 桥解析；双写旧布尔以兼容迁移。（#215）
  **Experimental features registry** — Global/Assistant experiment pages driven by `ExperimentalFeatureRegistry`; resolves `chat_keepalive`, `checkpoint_cache`, `variable_system` via map+legacy bridge with dual-write of legacy booleans for migration compat. (#215)
- **SettingClash 可序列化导航** — `Screen.SettingClash` 补 `@Serializable`，修复 Clash 设置页导航崩溃。（#222）
  **SettingClash serializable navigation** — Annotates `Screen.SettingClash` with `@Serializable` so the Clash settings route no longer crashes. (#222)
- **Clash 429 调试面板** — `ClashRetryTracer` + 拦截器埋点 + 设置页调试面板，便于排查 429 轮换过程。（#224）
  **Clash 429 debug panel** — Adds `ClashRetryTracer`, interceptor instrumentation, and a settings debug panel for inspecting 429 rotation attempts. (#224)

---

## v2.3.45

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.44 之后）

- **Clash 429 自动 IP 轮换重试** — 全新 Clash 外部控制设置页（host/port/secret/group/重试次数/切换延迟）+ 供应商品级 429 轮换开关附带风险确认对话框；AI 请求命中 429 时通过 `AIRequestInterceptor` 调用 Clash API 按轮询切换可选节点并串行重试，请求体为可重读 JSON，重试状态用 `Mutex` 互斥保护，取消异常正确传播。（#209）
  **Clash 429 auto IP-rotation retry** — Adds a global Clash external-control settings page (host/port/secret/group/retries/switch delay) and a per-provider 429-rotation toggle with a risk-confirm dialog; when an AI request hits 429, `AIRequestInterceptor` calls the Clash API to switch the selectable node round-robin and replays the request, with replayable JSON body, mutex-guarded retry state and propagated cancellation. (#209)
- **Clash `apiBaseUrl` SSRF 防护** — `ClashProxyConfig.validate()` 强制 `apiBaseUrl` 主机为 IP 字面量或 `localhost`（不触发 DNS），仅接受 loopback 与 site-local 私网地址，拒绝任意公网/域名主机，避免开启 429 轮换后向外部主机发送 PUT。（#209）
  **Clash `apiBaseUrl` SSRF guard** — `ClashProxyConfig.validate()` now requires `apiBaseUrl` host to be an IP literal or `localhost` (no DNS lookup), accepting only loopback and site-local private addresses, rejecting any public/host-name target to prevent PUTs to arbitrary external hosts once 429 rotation is enabled. (#209)
- **429 重试响应泄漏修复** — 重放 `chain.proceed` 抛 `IOException` 时改为返回最近一次未关闭的 429 响应，杜绝原代码进入外层 catch 返回已 `close()` 旧响应导致上层读取 `IOException: Closed` 与连接泄漏。（#209）
  **429 retry response-leak fix** — When the replayed `chain.proceed` throws `IOException`, the interceptor now returns the most recent still-open 429 response instead of falling through to the outer catch and returning an already-`close()`d previous response (which broke upstream reads with `IOException: Closed` and leaked a connection). (#209)
- **Clash 设置页输入越界可见提示** — `maxRetries` 与 `switchDelayMs` 输入越界时 TextField `supportingText` 显示合法范围，告别越界静默回弹旧值。（#209）
  **Clash settings range hint on invalid input** — `maxRetries` / `switchDelayMs` TextFields now show the legal range via `supportingText` when out of bounds, replacing the old silent revert-on-recompose behavior. (#209)

---

## v2.3.44

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.43 之后）

- **思考强度方言映射** — 抽象档位与 wire 词汇拆分：`ReasoningDialect`（Auto / OpenAI 扩展 / OpenAI 经典 / DeepSeek max / 仅开关）+ 模型个例可覆盖；官方 DeepSeek 与中转站手选 DeepSeekMax 时「超高」发 `max` 而非 `xhigh`；OnOffOnly 时 Chat picker 收敛为关/自动。（#207）
  **Reasoning dialect mapping** — Splits abstract effort levels from wire vocabulary via `ReasoningDialect` (Auto / OpenAI extended / OpenAI classic / DeepSeek max / on-off only) with per-model override; official DeepSeek and relay hosts set to DeepSeekMax map XHIGH→`max` not `xhigh`; OnOffOnly collapses the chat picker to off/auto. (#207)
- **语义记忆设置原子写与输入缓冲** — `updateSemanticMemoryConfig` 对配置键原子 RMW，避免滑块/连点读陈旧快照全量覆盖丢更新；阈值滑块 `onValueChangeFinished` 再持久化，数字与文本失焦提交。（#202）
  **Atomic semantic-memory config writes** — `updateSemanticMemoryConfig` does atomic RMW on the config key so slider/rapid toggles no longer lose updates via stale full-settings overwrite; threshold slider persists on finish; number/text fields commit on blur. (#202)
- **取消异常不再被吞** — `SemanticMemoryVM` / `StatsVM` 在 catch / `runCatching` 中 rethrow `CancellationException`，恢复 viewModelScope 结构化取消。（#203）
  **Do not swallow cancellation** — `SemanticMemoryVM` / `StatsVM` rethrow `CancellationException` from catch/`runCatching`, restoring structured cancellation for viewModelScope. (#203)
- **语义记忆浏览器弹窗可滚动** — 记忆编辑与清理确认对话框限高 + `verticalScroll`，按钮固定在滚动区外，长内容/多候选不再撑出屏幕。（#204）
  **Scrollable semantic-memory dialogs** — Memory edit and eviction confirm dialogs use max-height + `verticalScroll` with actions outside the scroll area so long content or many candidates stay usable. (#204)
- **预设注入去重改为组装期只读过滤** — 不再在加载期静默删除与预设重复的直连绑定；DataStore 保留双绑，发送时只读过滤防双注入；独立注入列表展示并标注「已由预设投递」，可取消勾选管理。（#205）
  **Non-destructive injection dedupe** — Stops load-time silent deletion of dual-bound direct mode injections; keeps DataStore dual binds and filters only at assembly; Independent Injections lists preset-managed items with a note and allows uncheck. (#205)
- **语义记忆导出与提示解耦** — 导出在 VM 内写完用户 URI 后用 `ExportResult` 事件通知 UI，不再用 `"Export OK"` 字符串与 3s 自动清除竞态导致文件残缺。（#206）
  **Semantic-memory export event** — Export copies to the user URI inside the VM then emits `ExportResult`; no more `"Export OK"` string races with the 3s message auto-clear that could truncate files. (#206)

---

## v2.3.43

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.42 之后）

- **工作区可读上传目录 `/upload`** — 主代理与子代理 knownMounts 均挂载 `filesDir/upload`；`workspace_read_file("/upload/<文件名>")` 与 fork 后 UUID 路径可读；工具描述声明只读；越界路径拒绝。（#199）
  **Workspace can read `/upload`** — Main and subagent knownMounts bind `filesDir/upload`; `workspace_read_file("/upload/<name>")` and post-fork UUID paths resolve; tool description marks read-only; path traversal rejected. (#199)
- **上传附件注入方式偏好** — 设置→偏好/通用新增「注入附件全文」：默认仅注入文件名与 `/upload` 路径（PATH_ONLY）以省 token；可选全文注入（FULL_BODY）；工作区工具实际不可用（无 TOOL 能力或工具被权限过滤）时回退全文，避免空附件进模型。（#200）
  **Upload attachment inject mode preference** — Settings → Preferences adds “Inject Full Attachment Content”: default PATH_ONLY (name + `/upload` path stub) to save tokens; optional FULL_BODY; falls back to full body when workspace tools are not actually available (no TOOL ability or tools filtered by permissions). (#200)
- **清理模式注入双路径残留** — settings 清洗去掉与已绑定预设「实际投递」条目同 id 的直连（含 Reference 目标、legacy 生效 id）；禁用预设条目时保留直连；聊天扩展面板新增「独立注入」Tab，会话/助手级开关；预设已管理的项不在独立列表重复勾选。（#201）
  **Clear dual-path mode-injection residue** — Settings sanitize drops assistant direct bindings that match effectively delivered bound-preset entries (including Reference targets and legacy effective ids); keeps direct binds when the preset entry is disabled; chat extension panel adds an Independent Injections tab (conversation/assistant scope); preset-managed ids are filtered from that list. (#201)

---

## v2.3.42

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.41 之后）

- **合并上游 rikkahub 2.4.2–2.4.5+** — 将 `upstream/master` tip `8349ef25` 合入 `release/rikka-arsucar`：助手级网络搜索、Kimi K3、上下文阶梯截断、MCP 拆分、Workspace 预览/SAF/多图格式、备份与 WebDAV 解耦、主题网格、mermaid 内置、原生 highlight、TTS 默认倍速、AI 方言修复与 AGP/Kotlin 升级等；保留 fork 身份（`me.arsucar.rikka`、无 Firebase、`release-apk.yml`）、#59 自动压缩与分段双许可。（#197）
  **Merge upstream rikkahub 2.4.2–2.4.5+** — Merged `upstream/master` tip `8349ef25` into `release/rikka-arsucar`: per-assistant web search, Kimi K3, stepped context truncation, MCP split, Workspace preview/SAF/more image formats, backup/WebDAV decoupling, theme grid, bundled mermaid, native highlight, TTS default speed, AI dialect fixes, and AGP/Kotlin upgrades; kept fork identity (`me.arsucar.rikka`, no Firebase, `release-apk.yml`), #59 auto-compress, and segmented dual license. (#197)

---

## v2.3.41

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.40 之后）

- **统计面板 API 上游健康监控** — 记录主生成与标题/建议/草稿/压缩等路径的 API 调用结果，统计页展示成功率、平均延迟与错误分类；错误信息脱敏，避免密钥泄漏。（#191）
  **API upstream health on Stats** — Records API outcomes for main generation and title/suggestion/draft/compress paths; Stats shows success rate, average latency, and error classes with redacted error messages. (#191)
- **Stats 加载性能与消息统计缓存** — 消息 token/日活统计写入增量缓存表，会话节点保存时同步更新，避免统计页反复 `json_each` 全表扫描；首次打开按需回填。（#192, #193）
  **Stats load performance and message stats cache** — Token/daily counts use incremental cache tables updated on message-node writes, avoiding repeated full-table `json_each` scans; first open backfills on demand. (#192, #193)
- **精简 memory_tables 注入 schema** — 注入提示词只保留表/列结构字段，去掉引擎策略字段，降低 token；存储与 `list_templates` 仍保留完整 schema。（#194）
  **Slim memory_tables injection schema** — Injection prompts keep only table/column structure and drop engine policy fields to save tokens; storage and `list_templates` retain the full schema. (#194)
- **语义记忆系统** — 可选的情节记忆召回与自动摘要（默认关闭）；设置页可浏览/导入导出；注入有 caps 与超时 fail-open。（#195）
  **Semantic memory** — Optional episodic recall and auto-summarization (off by default); settings UI for browse/import/export; injection uses caps and fail-open timeout. (#195)
- **草稿上下文可配置** — 回复草稿可配置尾部消息窗口、工具/推理/媒体是否纳入上下文；仅 reply_draft 内置预设展示配置 UI，向后兼容缺省字段。（#196）
  **Configurable draft context** — Reply drafts can configure the tail message window and whether tools/reasoning/media enter context; UI only for reply_draft builtins; missing fields remain backward-compatible. (#196)

---

## v2.3.40

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.39 之后）

- **导入 SillyTavern 提示词预设** — 预设页导入 JSON 时自动识别 SillyTavern「Chat Completion Preset」格式，将 `prompts[]` + `prompt_order[]` 映射为可编辑的 `PresetEntry`：支持条目顺序、启用状态（`prompts[].enabled` 与 `prompt_order[].enabled` 取 AND）、角色归一（SYSTEM 仅用于位置判定，条目只落 USER/ASSISTANT）、注入位置/深度映射、marker 占位条目跳过，以及未映射的 ST 顶层设置写入 description 提示；自有格式导入同步修复条目 ID 重复问题。（#188）
  **Import SillyTavern prompt presets** — The preset page now auto-detects SillyTavern "Chat Completion Preset" JSON on import, mapping `prompts[]` + `prompt_order[]` to editable `PresetEntry` items: entry order, enabled state (AND of `prompts[].enabled` and `prompt_order[].enabled`), role normalization (SYSTEM used only for positioning; entries only carry USER/ASSISTANT), injection position/depth mapping, marker placeholder skipping, unmapped ST top-level settings written into the description; native-format import also fixed to re-randomize entry IDs. (#188)
- **可编辑预设条目** — 预设从 ID 容器升级为可编辑、可开关、可拖拽排序的条目模型：内置提示词支持覆盖模板内容与位置；迁移保留全局 enabled 与旧排序；注入按 id 去重并防止宏泄漏；UI 新增条目分区计数、无效引用不再自动选中、跨组排序保持、创建/编辑唯一性校验、拖拽无障碍命令。（#182, #187）
  **Editable preset entries** — Presets upgraded from ID containers to an editable, toggleable, drag-reorderable entry model: builtin prompts support content/position overrides; migration preserves global enabled and legacy ordering; injection dedups by id with macro leakage guards; UI adds entry-aware counts, invalid Reference deselection, cross-group reorder preservation, create/edit uniqueness checks, and accessible drag move commands. (#182, #187)
- **原子化备份恢复与 SafeMode 升级** — WebDAV/S3/本地恢复统一走 BackupRestorer：先解压到临时目录校验完整性和外键，再原子替换并保留 `.restore-bak` 回退；settings 在 DB 替换成功后应用，dummy 设置不产出备份；冷启动首帧 settings 失败时升级到 SafeMode 而非静默吞掉。（#184, #189, #190）
  **Atomic backup restore and SafeMode escalation** — WebDAV/S3/local restore unified through BackupRestorer: unpack to a temp dir, validate integrity and foreign keys, then atomically swap with a `.restore-bak` fallback; settings applied only after DB swap succeeds; dummy settings no longer produce backups; cold-start first-frame settings failure now escalates to SafeMode instead of being silently swallowed. (#184, #189, #190)
- **Settings 流、ChatService 与备份标签页修复** — `toMutableStateFlow` 移除 `Runtime.halt(1)`，改有限重试后保留现值；ChatService 工厂移除 `runBlocking`，改 suspend 路径；WebDAV/S3 测试连接与远端删除迁入 BackupTaskCoordinator，离页不中断。（#183, #185, #186）
  **Settings flow, ChatService, and backup tab fixes** — `toMutableStateFlow` drops `Runtime.halt(1)` in favor of bounded retry with value retention; ChatService factory removes `runBlocking` for suspend paths; WebDAV/S3 test-connection and remote delete moved into BackupTaskCoordinator so leaving the page no longer interrupts them. (#183, #185, #186)
- **消息操作菜单可滚动** — 当操作项超出屏幕高度时，底部操作表现在可垂直滚动，确保所有操作可达。（#179）
  **Scrollable message actions sheet** — The bottom action sheet now scrolls vertically when actions exceed screen height, keeping all options reachable. (#179)
- **编辑模式草稿回复与 Git 状态缓存** — 编辑自己的消息时仍可触发"写回复草稿"并显示成功提示；Git 状态抽屉对同一 Workspace/CWD 重开时复用上次结果，手动刷新走 forceRefresh。（#180, #181）
  **Draft reply in edit mode and Git status cache** — "Write reply draft" is now allowed while editing your own message with a success toast; the Git status drawer reuses the last result for the same workspace/CWD on reopen, with forceRefresh for manual refresh. (#180, #181)

---

## v2.3.39

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.38 之后）

- **标题总结与 Hook 占位符正则修复** — 修复 `applyPlaceholders` 中未正确转义的 `}`，消除 Android/ICU 在编译 `\{([^{}]+)}` 时的 `Syntax error in regexp pattern`；恢复标题生成、Hook 提示词替换及其他共用该工具函数的能力。
  **Title summary and Hook placeholder regex fix** — Escape the closing `}` in `applyPlaceholders` so Android/ICU no longer rejects `\{([^{}]+)}` with a pattern syntax error; restores title generation, Hook prompt substitution, and other shared callers.

---

## v2.3.38

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.37 之后）

- **备份卡住与数据库一致性修复** — WebDAV / S3 / 本地导入导出统一任务生命周期：运行阶段可见、可取消、超时与异常不再永久转圈；备份改用 `VACUUM INTO` 单文件数据库快照并清理旧 WAL/SHM，校验走 Requery + simple 以兼容 FTS；新增 44→45 防御迁移幂等补齐 `compress_hidden_count`。
  **Backup stall and database consistency fixes** — WebDAV / S3 / local import-export share a unified task lifecycle with visible stages, cancellation, and terminal states for timeouts and failures; backups now use a `VACUUM INTO` single-file DB snapshot with old WAL/SHM cleanup and Requery+simple integrity checks for FTS compatibility; Migration 44→45 idempotently adds missing `compress_hidden_count`.
- **共享工具初始化崩溃防护** — 避免共享 Kotlin 工具 facade 在静态初始化失败后导致整类不可用的连锁崩溃。
  **Shared utility initialization crash hardening** — Prevent shared Kotlin utility facades from cascading into process-wide linkage failures after a static initializer error.

---

## v2.3.37

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.36 之后）

- **草稿回复支持附加用户意图** — 点击“写回复草稿”时会捕获输入框现有文本，去除首尾空白后作为可选指令引导生成；空白输入保持原行为，取消或失败仍可恢复原文，用户/ASR 编辑继续优先于晚到的流式片段，同时避免历史消息中的占位符文本被二次展开。（#177）
  **Draft replies support additional user intent** — The Draft Reply action now captures existing composer text and uses its trimmed value as an optional generation instruction; blank input keeps the previous behavior, cancellation or failure still restores the original text, user/ASR edits continue to win over late stream chunks, and placeholder-like text in chat history is no longer expanded recursively. (#177)
- **嵌套 Workspace Git 仓库识别与差异修复** — Git 状态和 diff 现在遵循会话 CWD、助手默认 CWD、`/workspace` 的优先级，并从有效目录解析实际仓库根；Workspace 根不是仓库时也能正确读取嵌套仓库，同时保留路径/符号链接校验和工作目录、权限、超时等结构化错误分类。（#178）
  **Nested Workspace Git repository status and diff fixes** — Git status and diffs now follow the conversation CWD, assistant default CWD, then `/workspace`, resolving the actual repository root from the effective directory; nested repositories work even when the Workspace root is not a repository, while path/symlink validation and structured working-directory, permission, and timeout errors remain intact. (#178)

---

## v2.3.36

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.35 之后）

- **助手 Workspace Git 状态抽屉** — 聊天页右侧抽屉现在可查看当前助手绑定 Workspace 的分支、暂存/未暂存/未跟踪文件和受限 diff，并支持手动刷新、截断提示及结构化错误状态；功能保持只读，不会将项目内容注入模型上下文。（#174）
  **Assistant Workspace Git status drawer** — The chat-side drawer can now show the branch, staged, unstaged, and untracked files, and bounded diffs for the current assistant's bound Workspace, with manual refresh, truncation notices, and structured error states; the feature remains read-only and does not inject project content into model context. (#174)
- **记忆表横屏沉浸式编辑** — 记忆表文档编辑器新增横竖屏切换；横屏隐藏顶部栏、标签栏和状态栏，以透明的返回/保存操作释放更多编辑空间，同时保留当前编辑模式和草稿，并在输入法显示时隐藏方向按钮。
  **Immersive landscape memory-table editing** — The memory-table document editor now supports explicit portrait/landscape switching; landscape hides the app bar, tabs, and status bar, uses transparent Back/Save actions to free more editing space, preserves the current mode and draft, and hides the orientation action while the keyboard is visible.
- **Web 会话流式更新优化** — Web 会话 SSE 在流式回复期间合并过时更新，并仅在发送时转换变化节点，减少更新积压和重复序列化。
  **Web conversation streaming optimization** — Web conversation SSE now conflates stale updates during streaming and converts only changed nodes when sending, reducing update backlog and repeated serialization.

---

## v2.3.35

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.34 之后）

- **AI 回复草稿** — 聊天输入框新增回复草稿按钮，可根据最近对话以用户口吻流式生成可编辑草稿；支持取消和恢复原输入，用户编辑或发送会安全终止生成，且仅在已有完整助手回复时可用。（#169，PR #171）
  **AI reply drafts** — Add a chat-composer action that streams an editable reply draft from the user's perspective using recent context; cancellation restores the original input, user edits or sending stop generation safely, and the action is available only after a completed assistant reply. (#169, PR #171)
- **记忆表并发写入保护** — 记忆表工具读写结果补充文档、模板、revision 和行键元数据，写操作支持可选 `expected_revision`；revision 冲突会明确返回并拒绝覆盖，同时兼容原始 JSON 与编码字符串参数。（#170）
  **Memory-table concurrent-write protection** — Memory-table tool responses now include document, template, revision, and resolved row-key metadata; writes accept an optional `expected_revision`, reject stale updates with an explicit conflict, and accept either raw JSON or encoded JSON parameters. (#170)
- **子代理传递上下文可视化** — Spawn 工具详情现在展示实际传给子代理的任务、系统提示、Profile 约束、工具、Skills/MCP 和运行元数据，并显示记忆表的实际注入状态、跳过原因及模板名称。（#166）
  **Subagent transferred-context visibility** — Spawn tool details now show the task, subagent system prompt, profile constraints, actual tools, Skills/MCP, and runtime metadata sent to the child, including actual memory-table injection status, skip reasons, and template names. (#166)
- **更新信息与工具诊断布局修复** — 侧栏更新卡中的长更新日志现在可以滚动；工具诊断摘要在窄屏下不再被操作按钮挤成逐字竖排。（#172，PR #165 #173）
  **Update-card and tool-diagnostics layout fixes** — Long changelogs in the sidebar update card are now scrollable, and tool diagnostic summaries no longer collapse into one-character-wide vertical text on narrow screens. (#172, PR #165 #173)
- **聊天与 AI 运行效率优化** — 缓存 Markdown Web 模板和 API Key 轮询状态，复用图片加载请求，并为聊天抽屉列表使用稳定实体标识，减少重复文件读取、重组工作和无效图片加载。
  **Chat and AI runtime efficiency** — Cache the Markdown Web template and API-key roulette state, reuse image requests across recompositions, and use stable entity keys in chat-drawer lists to reduce repeated file reads, recomposition work, and redundant image loads.

---

## v2.3.34

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.33 之后）

- **助手工具页体验回归修复** — 恢复设置优先的赋能工具页：用户向计数、长按多选、折叠分组、MCP 默认已配置、诊断/预设收进高级区、内置预设中文名，以及放宽权限时的确认与反馈。（#163）
  **Assistant tools page UX regression fix** — Restore a settings-first empowerment tools page with user-facing counts, long-press multi-select, collapsible groups, MCP configured-only default, diagnostics/presets under Advanced, localized built-in presets, and confirmation/feedback for permission relaxation. (#163)
- **子代理记忆表实例注入** — 子代理 Profile 可多选父助手记忆表文档实例，在首次 spawn 时只读注入；遵循对话 isolation；失效 id 静默跳过，且不授予写入工具。（#164）
  **Subagent memory-table document injection** — Subagent profiles can multi-select parent memory-table document instances for read-only injection on first spawn, honor conversation isolation, skip missing ids safely, and never grant write tools. (#164)
- **助手工具权限、诊断与连接状态** — 工具支持 INHERIT/ALLOW/ASK/DENY、策略诊断、MCP 安全连接测试、workspace 状态展示和脱敏摘要。（#154 #155 #156）
  **Assistant tool permissions, diagnostics, and connection status** — Add INHERIT/ALLOW/ASK/DENY policies, policy-aware diagnostics, safe MCP connection probes, workspace status display, and redacted summaries. (#154 #155 #156)
- **工具权限预设与批量操作** — 支持预设保存、重命名、删除、diff 预览、跨助手复制和批量四态设置，并跳过未知工具与私有资源绑定。（#157）
  **Tool permission presets and batch operations** — Add preset save, rename, delete, diff preview, cross-assistant copy, and batch four-state editing while skipping unknown tools and private resource bindings. (#157)
- **跨层工具回归矩阵** — 增加能力目录、生成装配、持久化兼容、子代理权限和 MCP fake probe 测试。（#152）
  **Cross-layer tool regression matrix** — Add capability catalog, generation assembly, persistence compatibility, subagent policy, and fake MCP probe coverage. (#152)
- **统一助手工具能力目录与策略** — 稳定工具 ID、配置/可用/生效区分与纯快照，供工具页与生成边界共用。（#153）
  **Unified assistant tool capability catalog and policy** — Stable tool IDs, configured/available/effective distinctions, and pure snapshots shared by the tools page and generation boundary. (#153)
- **助手 Workspace 需显式选择** — 绑定 workspace 时要求明确选择，避免误绑。（#151）
  **Explicit assistant workspace selection** — Require an explicit workspace choice when binding, avoiding accidental bindings. (#151)
- **版本化 Assistant Hook 事件** — 增加稳定 eventId、Room v43 事件身份、最终文本关键词预筛、工具/Shell 最终失败与子代理结束事件，并复用现有记忆表同步 cursor 保证 sourceEventId 幂等。（#158 #159 #160 #161 #162）
  **Versioned Assistant Hook events** — Add stable event IDs, Room v43 event identity, final-text keyword prefiltering, terminal tool/Shell failure and subagent completion events, while reusing memory-table sync cursors for sourceEventId idempotency. (#158 #159 #160 #161 #162)
- **终态事件审计与安全收口** — Room v44 持久化事件 payload；工具错误改用结构化 envelope 和稳定 operation hash，取消/后续成功会抑制；子代理严格映射四终态与上下文完整性；错误经验使用受限结构化评估、本地同义键查询和现有 cursor/事务实现新增、更新、跳过及 sourceEventId 幂等。（#160 #161 #162）
  **Terminal event audit and secure finalization** — Persist event payloads in Room v44; use structured tool envelopes and stable operation hashes with cancellation/later-success suppression; strictly map four subagent terminal states and context completeness; evaluate error experiences through a constrained schema, local equivalent-key lookup, and existing cursors/transactions for insert, update, skip, and sourceEventId idempotency. (#160 #161 #162)

---

## v2.3.33

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.32 之后）

- **Hook 标签管理编辑器重构** — 将「添加会话标签 / 转换标签」收敛为统一的「标签管理」动作：一份 allowlist + 评估提示词策略；动作类型改为下拉选择（标签管理 / 同步记忆表）；提示词默认折叠；编辑页移除启用开关（列表页开关保留）；旧 Add/Transition 配置可迁移加载与保存，运行时改为 multi-op 标签变更且去掉 Issue 证据硬门控。（#150）
  **Hook tag-management editor refactor** — Unify “add conversation tag” and “transition tags” into a single Tag management action with one allowlist plus evaluation-prompt strategy; action type uses a Select (Tag management / Sync memory table); the prompt is collapsed by default; the editor enable switch is removed (list toggle remains); legacy Add/Transition configs migrate for edit/save, and runtime uses multi-op tag changes without a hard Issue-evidence gate. (#150)

## v2.3.32

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.31 之后）

- **会话持久化内存峰值优化** — 降低 Room 事务路径上的内存峰值，减少大型会话写入时 Java heap OOM 风险。（#144）
  **Conversation persistence memory peaks** — Reduce memory peaks on Room transaction paths to lower Java heap OOM risk when persisting large conversations. (#144)
- **记忆表模板作用域迁移** — 支持将已有模板在 GLOBAL 与 ASSISTANT 作用域间迁移，并在 UI 与 `memory_table_tool` 中显式暴露 scope。（#145）
  **Memory-table template scope migration** — Existing templates can migrate between GLOBAL and ASSISTANT scopes, with explicit scope support in the UI and `memory_table_tool`. (#145)
- **记忆表文档回收站** — 删除记忆表文档时先移入回收站，支持按助手查看、恢复和永久删除；普通读取、注入和写入会隔离已删除文档，并在助手、会话或模板清理时保持快照与所有者生命周期一致。（#146）
  **Memory-table document trash** — Deleting a memory-table document now moves it to trash with assistant-scoped restore and permanent purge; ordinary reads, injection, and writes exclude trashed documents, while Assistant, Conversation, and template cleanup keeps snapshots and ownership lifecycle consistent. (#146)
- **Assistant Hook 自动同步记忆表** — Hook 动作扩展为通用处理框架，可选择现有助手级或当前会话级记忆表，在最终回复成功后以有界上下文生成严格 operations；支持手动预览、立即运行、失败重试和脱敏历史，并通过 revision CAS、冻结 schema、幂等 cursor 与单事务审计防止并发覆盖和部分提交。（#147）
  **Assistant Hook memory-table synchronization** — Hooks now use an action-specific framework that can synchronize an existing Assistant or current-Conversation memory table after a final successful reply using bounded context and strict operations; manual preview, run-now, failed-attempt retry, and sanitized history are included, with revision CAS, frozen-schema checks, durable idempotency cursors, and single-transaction audit preventing concurrent overwrite or partial commit. (#147)
- **GitHub Issue 完成标签联动** — Assistant Hook 新增受限标签转换动作：仅当当前最终回复同时包含严格 GitHub Issue 引用和明确创建成功语义时，才原子移除“进行中”并添加“完成”标签；证据筛选、模型决定、标签变更与历史审计均采用 fail-closed 边界。（#148）
  **GitHub Issue completion tag transition** — Assistant Hooks can now atomically remove an in-progress tag and add a completed tag only when the current final response contains both a strict GitHub Issue reference and explicit creation-success wording, with fail-closed evidence filtering, model decisions, tag writes, and structured history audit. (#148)

## v2.3.31

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.30 之后）

- **会话归档移除与数据保留** — 移除低频的会话归档页面、入口和操作；升级后原归档会话会作为普通会话重新可见，标题、消息、标签、文件夹等数据保持不变，助手归档功能不受影响。（#136）
  **Conversation archive removal with data preservation** — Remove the low-use conversation archive page and actions; previously archived conversations become visible as regular conversations after upgrading, with titles, messages, tags, folders, and other data preserved. Assistant archiving is unchanged. (#136)
- **普通记忆与记忆表独立控制** — 普通记忆和结构化记忆表现在可独立启用；关闭普通记忆不再阻断记忆表注入或工具，全局记忆表开关关闭时也会保留助手级偏好并显示明确原因。（#137）
  **Independent memory and memory-table controls** — Ordinary memory and structured memory tables can now be enabled independently; disabling ordinary memory no longer blocks memory-table injection or tools, while assistant-level preferences are preserved when the global memory-table switch is off. (#137)
- **记忆表文档与模板管理重构** — 助手记忆页改为以已创建文档为主，新增表格通过模板选择器完成；支持创建、编辑和管理私有/全局模板，阻止同名冲突，并明确区分删除文档与级联删除模板。（#140）
  **Memory-table document and template workflow** — The assistant memory page now focuses on created documents, with new tables added through a template picker; private and global templates can be created, edited, and managed with duplicate-name protection and clear document-versus-template deletion behavior. (#140)
- **Assistant Hooks 配置体验优化** — Hook 卡片支持整卡编辑，删除移入确认菜单；编辑页按基础、运行、规则和动作分区，并为无效模型、提示词及标签配置显示具体校验错误。（#141）
  **Assistant Hooks configuration improvements** — Hook cards now open directly for editing, deletion moves to a confirmation menu, and the editor is organized into Basic, Runtime, Rules, and Action sections with explicit validation for models, prompts, and tag configuration. (#141)
- **记忆表版本历史与可靠回滚** — 补齐结构化记忆快照的生产接线和可视化历史页，可查看版本时间、JSON 内容及与当前版本的差异，并在确认后将旧版本恢复为新的 revision。（#142）
  **Memory-table revision history and reliable rollback** — Complete the production snapshot wiring and add a revision-history UI for viewing timestamps, JSON payloads, and diffs against the current version, with confirmed rollback recorded as a new revision. (#142)
- **备份离页续跑与恢复安全性** — WebDAV、S3 和本地导入导出在切换 Tab 或离开备份页后仍可继续，并保留成功、失败或取消状态；同时修正成功时间记录、临时文件清理和备份恢复路径校验。（#143）
  **Background backup continuity and safer restore** — WebDAV, S3, and local import/export tasks now continue when switching tabs or leaving the backup page, retaining success, failure, or cancellation state; success-time recording, temporary-file cleanup, and restore-path validation are also hardened. (#143)

## v2.3.30

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.29 之后）

- **助手级网络搜索与 Workspace 媒体预览** — 网络搜索开关迁移到 Assistant 并保留旧配置的一次性迁移；Workspace 支持图片预览、视频及其他文件安全外部打开，并让 LINUX 区保持只读。（#135）
  **Assistant-level web search and Workspace media preview** — Web search settings are now persisted per Assistant with a one-time migration of legacy preferences; Workspace adds image preview, safe external opening for videos and other files, and read-only behavior for the LINUX area. (#135)
- **普通聊天文件类型白名单恢复** — 普通文件附件恢复 MIME/扩展名校验，不支持的媒体、压缩包和未知二进制不会进入文档附件链路。（#134）
  **Restore the regular-chat file type allowlist** — MIME/extension validation is restored for document attachments so unsupported media, archives, and unknown binaries stay out of the document flow. (#134)
- **会话标签与 Assistant Hooks** — 支持会话标签管理及 Assistant 级 Hook 配置与执行，保持对话关系和运行状态隔离。（#132、#133）
  **Conversation tags and Assistant Hooks** — Add conversation tag management and Assistant-level Hook configuration/execution while preserving isolated conversation relationships and runtime state. (#132, #133)
- **Provider 对话框布局修复** — 优化 Provider 配置对话框布局，改善小屏幕下的可用性。（#131）
  **Provider dialog layout fix** — Improve the Provider configuration dialog layout for better usability on small screens. (#131)

## v2.3.29

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.28 之后）

- **助手与记忆隔离完善** — 记忆表模板和搜索沉淀记忆严格按助手隔离；新增助手归档/恢复，并保留配置、对话与记忆。（#122、#125、#126）
  **Assistant lifecycle and memory isolation** — Memory-table templates and search-derived memories are now strictly isolated per assistant; assistants can also be archived and restored without losing configuration, conversations, or memories. (#122, #125, #126)
- **模型与 Skill 列表体验修复** — Skill frontmatter 多行描述可正确解析；模型搜索隐藏无命中的供应商分组并同步快捷导航。（#123、#124）
  **Model and Skill list fixes** — Multi-line Skill frontmatter descriptions now parse correctly, and model search hides unmatched provider groups while keeping quick navigation in sync. (#123, #124)
- **子代理恢复与并发解耦** — 子代理完整上下文持久化到 Room，可在进程重启后恢复；普通工具并行关闭时，多子代理仍按独立并发上限运行。（#127、#128）
  **Subagent recovery and concurrency separation** — Full subagent contexts are persisted in Room for process-restart recovery, while multiple subagents can still honor their own concurrency limit when ordinary tool parallelism is disabled. (#127, #128)
- **工作区图片识别链路回归保护** — 工作区读取的 JPG/PNG 等图片会作为多模态工具结果传给 Claude、OpenAI Chat/Responses 与 Google，并为非视觉模型提供明确降级。（#129、#130）
  **Workspace image recognition regression coverage** — Workspace JPG/PNG reads are delivered as multimodal tool results to Claude, OpenAI Chat/Responses, and Google, with an explicit fallback for non-vision models. (#129, #130)

## v2.3.28

### 新功能与修复 / Features & Fixes（本 Fork，v2.3.27 之后）

- **最近模型菜单显示提供商** — 在最近使用的模型菜单中显示模型提供商，便于快速区分同名模型。
  **Show providers in recent-model menu** — Recent-model entries now include the provider for easier identification. 
- **聊天体验与上下文压缩改进** — 重新打开最近助手会话；支持自动压缩过大上下文并记住压缩偏好；编辑模式粘贴文本保持行内显示。
  **Chat and context improvements** — Reopen the latest assistant conversation; automatically compress oversized contexts and remember preferences; keep pasted text inline in edit mode.
- **AI 与子代理稳定性增强** — 清理流式 HTTP 错误，强化文档/工具负载解析，并串行化子代理限制以复用上下文。
  **AI and subagent reliability** — Sanitize streaming HTTP errors, harden document/tool payload parsing, and serialize subagent limits for context reuse.
- **记忆隔离与注入预算** — 按助手隔离记忆表，并支持配置记忆注入预算。
  **Memory isolation and injection budgets** — Isolate memory tables per assistant and add configurable injection budgets.

## v2.3.27

### 新功能 / Features（本 Fork，v2.3.26 之后）

- **聊天附件支持任意文件类型** — 普通文件上传不再按扩展名或 MIME 白名单拒绝文件；ZIP、APK、无扩展名及未知类型文件均可保存为附件，未知 MIME 使用 `application/octet-stream`。（#111）
  **Arbitrary chat attachment types** — General file upload no longer rejects files through extension or MIME allowlists; ZIP, APK, extensionless, and unknown file types can be attached, with unknown MIME values using `application/octet-stream`. (#111)

## v2.3.26

### 新功能 / Features（本 Fork，v2.3.25 之后）

- **记忆表只读写入门控** — `updatePolicy.enabled = false` 现在会阻止 AI 对对应表执行新增、修改或删行，并保持批量操作原子性；缺省策略仍兼容为可写。（#107）
  **Read-only memory-table write gate** — `updatePolicy.enabled = false` now prevents AI row inserts, updates, and deletions on the affected table while preserving atomic batch behavior; missing policies remain writable for compatibility. (#107)

- **`~dm` 默认模型命令** — 在聊天输入框完整输入 `~dm` / `～dm` 可将当前生效模型设为该助手的默认模型，并提供明确操作反馈。（#108）
  **`~dm` default-model command** — Entering the exact `~dm` / `～dm` command in chat sets the currently effective model as the assistant default and provides clear feedback. (#108)

- **最终对话上下文预览** — 右侧抽屉新增上下文检查器，可只读查看下一次请求经过组装、截断和 transformer 处理后实际发送给模型的消息，并支持统计与复制。（#109）
  **Final conversation context inspector** — A new drawer inspector previews the read-only messages that would actually be sent after assembly, truncation, and transformer processing, with summary statistics and copy support. (#109)

### 修复 / Fixes（本 Fork，v2.3.25 之后）

- **日志详情 JsonTree 崩溃** — JsonTree 展开状态改用 Bundle 可保存格式，修复打开请求日志详情时因 Saver 返回裸 Map 导致的闪退。（#105）
  **Log-detail JsonTree crash** — JsonTree expansion state now uses a Bundle-saveable representation, fixing the crash caused by its Saver returning a raw Map when opening request-log details. (#105)

- **Shell 与子代理文件 chip 缺失** — 工作区工具现在报告 shell 命令产生的新增/修改文件，并强化路径规范化与差异合并，让主代理及子代理生成的文件可靠显示在消息下方。（#106）
  **Missing shell and subagent file chips** — Workspace tools now report files created or modified by shell commands and harden path normalization and diff merging so main-agent and subagent outputs reliably appear below messages. (#106)

- **子代理 context 复用诊断** — `CONTEXT_SCOPE_MISMATCH` 现在列出具体不匹配的 scope 字段，并明确缓存 context 与本次请求的比较方向，同时避免泄露权限指纹等原值。（#110）
  **Subagent context-reuse diagnostics** — `CONTEXT_SCOPE_MISMATCH` now identifies the mismatched scope fields and clearly distinguishes cached context from the current request without exposing permission fingerprints or other raw values. (#110)

## v2.3.25

### 新功能 / Features（本 Fork，v2.3.24 之后）

- **子代理上下文缓存与复用** — 子代理在正常完成、失败、中断或停止后保留完整上下文；`spawn_subagent` 可通过 `reuse_context_id` 追加指令并继续已有历史，同时提供滑动 TTL、LRU 淘汰、并发租约与作用域/权限校验。（#104）
  **Subagent context caching and reuse** — Subagents retain their complete context after completion, failure, interruption, or cancellation; `spawn_subagent` can append instructions and continue existing history via `reuse_context_id`, with sliding TTL, LRU eviction, concurrency leases, and scope/permission validation. (#104)

### 修复 / Fixes（本 Fork，v2.3.24 之后）

- **Markdown 表格横滑手势冲突** — 聊天消息中的宽 Markdown/HTML 表格左右滚动时不再误触右侧抽屉，并保持表格外抽屉手势、纵向滚动及非溢出表格行为不变。（#102）
  **Markdown table swipe gesture conflict** — Horizontally scrolling wide Markdown/HTML tables in chat no longer accidentally opens the right drawer, while drawer gestures outside tables, vertical scrolling, and non-overflowing tables remain unchanged. (#102)

## v2.3.24

### 上游同步 / Upstream Sync（本 Fork，v2.3.23 之后）

- **合并上游 rikkahub v2.4.1** — 合入上游 v2.4.1 及其后续提交，保留 Fork 定制（release-apk 工作流、去 Firebase），图片生成参数保留 Fork 细分设计（宽高比/质量/格式/背景/审核），新增每日构建工作流。（#68）
  **Merge upstream rikkahub v2.4.1** — Merged upstream v2.4.1 and later commits while preserving fork customizations (release-apk workflow, Firebase removal); image generation keeps the fork's fine-grained parameters (aspect ratio/quality/format/background/moderation); added a daily-build workflow. (#68)

- **Fish Audio 与 MiMo TTS** — 新增 Fish Audio、MiMo 语音合成提供商。
  **Fish Audio and MiMo TTS** — Added Fish Audio and MiMo text-to-speech providers.

- **TTS 不朗读括号内容** — 新增开关，启用后 TTS 跳过括号内的内容（可与引号过滤叠加）。
  **TTS skips bracketed content** — New toggle to skip content inside brackets during TTS (stacks with quote filtering).

- **文件夹分组与会话 API** — 会话侧栏支持文件夹分组，新增文件夹与事件路由。
  **Folder grouping and conversation APIs** — Conversation sidebar supports folder grouping, with new folder and event routes.

- **HEIF/HEIC 图片支持** — 新增 HEIF/HEIC 图片格式处理。
  **HEIF/HEIC image support** — Added handling for HEIF/HEIC image formats.

### 修复 / Fixes（本 Fork，v2.3.23 之后）

- **前台服务崩溃拦截** — 拦截部分 OEM 因拒绝前台服务（FGS）权限导致 Web 服务器启动闪退的问题。
  **Foreground service crash guard** — Catches startup crashes on some OEMs that deny foreground-service (FGS) permission for the web server.

- **WebView 预览大数据崩溃** — WebView 预览改用本地文件缓存传输，避免数据量过大导致进程崩溃。
  **WebView large-payload crash** — WebView preview now transfers via local file cache to avoid process crashes on large payloads.

- **备份恢复目录缺失** — 备份恢复时对缺失目录标记为 BROKEN，不再直接删除对应记录。
  **Backup restore missing directory** — Restore marks missing directories as BROKEN instead of deleting the records.

- **SSE 流式丢字** — 修复流式响应偶发丢字问题。
  **SSE streaming dropped characters** — Fixed occasional character loss in streaming responses.

- **聊天头部与输入框细节** — 模型名称支持双行显示避免斜杠截断；输入框在键盘收起时增加底部呼吸间距。
  **Chat header and input polish** — Model names can wrap to two lines to avoid slash truncation; the input field adds bottom spacing when the keyboard collapses.

## v2.3.23

### 新功能 / Features（本 Fork，v2.3.22 之后）

- **对话级记忆表抽屉** — 聊天页顶栏新增记忆表入口，打开右侧抽屉查看/编辑当前对话的记忆表；助手级与全局文档可「同步到对话级」并跟随来源，也可断开跟随独立编辑，fork/迁移对话时记忆表随行。（#89 #84）
  **Conversation-scoped memory table drawer** — A memory table entry in the chat top bar opens a right-side drawer to view/edit the current conversation's tables; assistant-level and global documents can be synced to conversation scope and follow their source, or detach to be edited independently, and tables travel with the conversation when forking/moving. (#89 #84)

- **记忆表快照与历史回滚** — 记忆表文档每次改动都会留存快照，可按 revision 回滚到旧版本。（#96）
  **Memory table snapshots and rollback** — Each memory table document edit stores a snapshot so you can roll back to an earlier revision. (#96)

- **记忆表 JSON 导出/导入** — 记忆表模板与文档支持打包导出/导入，冲突可按 SKIP/OVERWRITE/DUPLICATE 策略处理。（#100）
  **Memory table JSON export/import** — Memory table templates and documents can be exported/imported as a bundle, with SKIP/OVERWRITE/DUPLICATE conflict strategies. (#100)

- **记忆表工具增强** — `memory_table_tool` 新增 query 按表/列/值过滤、apply_ops 批量原子写、delete_row 按行删除、update_template/delete_template，read 支持列出全部文档并按 scope 过滤。（#97 #98 #92 #95 #88）
  **Memory table tool enhancements** — `memory_table_tool` adds query filtering by table/column/value, atomic batch writes via apply_ops, row-level delete_row, update_template/delete_template, and read now lists all documents filtered by scope. (#97 #98 #92 #95 #88)

- **记忆表注入控制** — 每张表可单独开关注入（per-table 门控），trigger-send 表仅注入与近期对话相关的行，系统提示可用 `{{memory_tables}}` 宏控制注入位置。（#93 #94 #99）
  **Memory table injection control** — Per-table injection toggles, trigger-send tables inject only rows relevant to recent turns, and the `{{memory_tables}}` macro controls injection placement in the system prompt. (#93 #94 #99)

- **记忆表默认模板多列化** — 新建记忆表默认改为多列结构化示例，避免误判「表格只能存 key-value」。（#81）
  **Multi-column default memory table** — New memory tables default to a multi-column structured example instead of implying tables are key-value only. (#81)

- **预设系统增强** — 新增默认全量预设，快速注入（ModeInjection）按预设隔离显示；点击扩展条目直接弹出对应编辑弹窗；子代理配置支持关联预设进行 prompt 注入。（#73 #75 #74）
  **Prompt preset enhancements** — Added a default full preset with ModeInjection scoped per preset, clicking an extension entry opens its editor directly, and subagent profiles can associate presets for prompt injection. (#73 #75 #74)

- **子代理工具可观测性** — 子代理工具卡片显示 token 计数，弹窗可展开查看传输给子代理的上下文。（#76 #79）
  **Subagent tool observability** — Subagent tool cards show token counts, and the dialog can expand to show the context transferred to the subagent. (#76 #79)

- **Provider 单独限速** — 每个提供商可单独配置 RPM/TPM，客户端自动延迟以避免触发服务端上限。（#82）
  **Per-provider rate limiting** — Each provider can be configured with its own RPM/TPM, and the client auto-delays to stay under server-side limits. (#82)

- **供应商标签管理** — 筛选区渲染全部用户自建/已挂载标签，并可在「管理标签」中新增标签。（#77）
  **Provider tag management** — The filter area renders all user-created/attached tags and lets you add new tags in the tag manager. (#77)

- **对话交互** — 点击对话流中的助手头像直接进入助手配置页；分享多选折叠视图每条正文限 3 行以降低列表高度。（#70 #80）
  **Chat interactions** — Tapping an assistant avatar in the chat opens its settings page, and the share multi-select collapsed view limits each body to 3 lines. (#70 #80)

### 修复 / Fixes（本 Fork，v2.3.22 之后）

- **记忆表数据安全** — upsert_rows 改为原子写并校验 payloadJson，避免残缺数据覆盖旧文档；patch 行级合并按表主键（explicit→primaryKey→key）而非硬编码 `key`；模板写入校验 schemaJson；工具错误封装为可读结构而非抛原始异常；delete_rows 不再误删整份文档。（#85 #86 #83 #87 #90）
  **Memory table data safety** — upsert_rows is now atomic and validates payloadJson to avoid overwriting with partial data; patch merges by table primary key (explicit→primaryKey→key) instead of a hardcoded `key`; template writes validate schemaJson; tool errors return a readable structure instead of raw exceptions; delete_rows no longer deletes the whole document. (#85 #86 #83 #87 #90)

- **注入文案** — `MemoryTableInjectionTransformer` 明确为限制文档数（maxDocuments），修正原 maxRows 误导文案。（#91）
  **Injection wording** — `MemoryTableInjectionTransformer` now clearly limits document count (maxDocuments), fixing the misleading maxRows wording. (#91)

- **预设单选** — 扩展管理中预设改为同一时间只能启用一个。（#72）
  **Single active preset** — Only one preset can be enabled at a time in extension management. (#72)

- **搜索框换行** — 模型列表搜索框限制单行，提示文字过长不再换行变高。（#71）
  **Search field wrapping** — The model list search field is single-line and no longer grows when the placeholder is long. (#71)

- **编辑预设弹窗** — 编辑预设 BottomSheet 禁用 PartiallyExpanded，初始全屏展开不再弹跳。（#78）
  **Edit preset sheet** — The edit preset BottomSheet disables PartiallyExpanded and opens fully expanded without bouncing. (#78)

- **压缩后滚动定位** — 压缩聊天切换 UI 窗口后，列表定位到可见底部而非隐藏消息。（#101）
  **Scroll position after compaction** — After compacting a chat and switching windows, the list scrolls to the visible bottom instead of a hidden message. (#101)

## v2.3.22

### 新功能 / Features（本 Fork，v2.3.21 之后）

- **提示词预设** — 新增「预设」提示词类型，可将多条提示词条目组合成预设，并作为独立注入挂载到助手，提示词管理和扩展选择器均支持查看与启用。
  **Prompt presets** — Added a "preset" prompt type that groups multiple prompt entries into a preset and mounts it to assistants as an independent injection, viewable and toggleable from both prompt management and the extension selector.

- **从 OpenCode 导入供应商** — 供应商设置支持从 OpenCode 配置导入供应商信息，快速迁移已有配置。
  **Import providers from OpenCode** — Provider settings can now import provider information from an OpenCode configuration for quick migration of existing setups.

## v2.3.21

### 新功能 / Features（本 Fork，v2.3.20 之后）

- **助手可管理记忆表模板** — 助手现在能查看已有记忆表模板并按需新建模板，长期记忆整理更省心。（#60）
  **Assistant-managed memory table templates** — Assistants can now view existing memory table templates and create new ones on demand, making long-term memory easier to organize. (#60)

- **助手可创建与更新技能** — 助手现在能在对话中直接创建或更新技能以扩展自身能力，操作执行前会先请求确认。（#61）
  **Assistant-created skills** — Assistants can now create or update skills directly during a chat to extend their own capabilities, with confirmation before applying. (#61)

- **赋能工具统一管理入口** — 助手设置新增「赋能工具」页面，集中展示各类能力的开关状态并支持一处启用/关闭。（#62）
  **Unified tools management** — Assistant settings now include a "tools" page that shows the on/off state of every capability in one place for quick toggling. (#62)

## v2.3.20

### 新功能 / Features（本 Fork，v2.3.19 之后）

- **记忆表格文档全屏编辑** — 记忆表格文档支持全屏表格/JSON 双模式查看与编辑，并修复 `patch_rows` 合并语义，避免静默丢失已有行。（#49 #50）
  **Fullscreen memory table documents** — Memory table documents now support fullscreen table/JSON viewing and editing, and `patch_rows` uses merge semantics to avoid silently dropping existing rows. (#49 #50)

- **助手私有 Skill 工作区挂载** — 助手私有 Skills 会挂载到工作区工具、主会话与子代理运行环境，便于脚本执行和运行时迭代。（#52）
  **Assistant-private Skill workspace mounts** — Assistant-private Skills are mounted into workspace tools, main chat, and subagent runtimes for script execution and runtime iteration. (#52)

- **聊天模型快速切换** — 聊天页模型图标支持长按打开最近使用模型菜单，模型切换会记录有效的最近使用列表。（#54）
  **Recent chat model switching** — Long-pressing the chat model icon opens a recent-model menu, and valid model switches are recorded in a recent list. (#54)

- **模型搜索与提供商标签管理** — 模型选择搜索支持匹配提供商名与模型名，收藏分组过滤行为保持一致；提供商标签支持建议/已用标签统一聚合、重命名、删除和排序。（#55 #56）
  **Model search and provider tag management** — Model search matches both provider and model names with consistent favorite filtering; provider tags now combine suggested/used tags and support rename, delete, and reorder. (#55 #56)

- **上游功能同步** — 合入上游搜索 Ollama fetch、WebView/Web UI 化学公式渲染、Compose/Material/Navigation 依赖更新，以及文件选择、搜索面板和聊天滚动相关改进。
  **Upstream feature sync** — Merged upstream Ollama fetch search, chemistry rendering in WebView/Web UI, Compose/Material/Navigation dependency updates, plus file picker, search panel, and chat scrolling improvements.

### 修复 / Fixes（本 Fork，v2.3.19 之后）

- **Markdown 与 JSON 查看状态** — 消息文件 chip 打开 `.md` 时使用 Markdown 渲染；日志页 JSON 树进入复制面板再返回后保留展开状态。（#51 #53）
  **Markdown and JSON view state** — Message file chips render `.md` files as Markdown, and log-page JSON trees preserve expansion state after entering and returning from the copy panel. (#51 #53)

- **上游修复同步** — 合入上游工具结果图片回传、`mhchem.mjs` 导入、残留内置搜索状态空白、搜索预览跳转与附件菜单对齐等修复。
  **Upstream fix sync** — Merged upstream fixes for tool-result image inputs, `mhchem.mjs` imports, stale built-in search empty states, search preview jumps, and attachment menu alignment.

### 文档 / Documentation

- **README 与上游同步规则** — README 统一 RikkaRs 品牌、补充 fork 与上游差异说明，并记录合并上游时保留压缩上下文分段选数的冲突规则。（#58 #59）
  **README and upstream sync rules** — README now uses RikkaRs branding, documents fork/upstream differences, and records the conflict rule for keeping the segmented keep-recent selector when merging upstream. (#58 #59)

---

## v2.3.19

### 新功能 / Features（本 Fork，v2.3.18 之后）

- **Skills 目录与聊天模型入口优化** — 聊天输入栏移除低频的模型清除按钮，Skills 目录卡片与空状态优化为更易扫读的层级与标签展示。（#43）
  **Skills directory and chat model controls** — Removed the low-frequency model clear button from chat input and improved Skills directory cards/empty state with clearer hierarchy and metadata chips. (#43)

- **工作区文本预览增强** — 工作区文件菜单支持常见 dotfile、配置文件与无后缀文本文件；只读 Markdown 查看改为渲染预览，编辑模式仍保留源码编辑器。（#44）
  **Workspace text preview enhancements** — Workspace file actions now cover common dotfiles, config files, and extensionless text files; read-only Markdown opens as rendered preview while edit mode keeps source editing. (#44)

### 修复 / Fixes（本 Fork，v2.3.18 之后）

- **Shell 工具步骤可读性** — `workspace_shell` 内联摘要稳定展示命令输入，长输出截断后的 `/tool_outputs/...` 续读会标记为 continuation，子代理 transcript 不再像空 shell。（#45 #47）
  **Shell tool step readability** — `workspace_shell` inline summaries now show command input, `/tool_outputs/...` follow-up reads are labeled as continuations, and subagent transcripts no longer look like empty shell steps. (#45 #47)

- **子代理并行开关恢复** — 主会话重新尊重助手的「并行执行工具」设置，多个同轮 `spawn_subagent` 可按配置并行执行并在工具描述中提示。（#46）
  **Subagent parallel setting restored** — Main chat now respects the assistant parallel tool execution setting, allowing same-response `spawn_subagent` calls to run concurrently when enabled and advertising that behavior in the tool description. (#46)

- **OpenAI Responses 工具 schema** — OpenAI 兼容 Responses / Chat Completions 请求会将无参工具序列化为空 object schema，避免 `parameters: null` 触发 schema 校验失败。（#48）
  **OpenAI Responses tool schema** — OpenAI-compatible Responses / Chat Completions requests serialize no-argument tools as an empty object schema instead of `parameters: null`, avoiding schema validation failures. (#48)

---

## v2.3.18

### 新功能 / Features（本 Fork，v2.3.17 之后）

- **会话模型覆盖** — 聊天页可为单个会话选择模型，并可一键清回助手默认；新会话不会继承旧会话覆盖。（#31）
  **Conversation model override** — Chat can override the model per conversation and clear back to the assistant default; new conversations do not inherit old overrides. (#31)

- **工作区全屏文本编辑器** — 工作区文件支持对 `.md`、`.json`、`.kt`、`.log` 等文本文件进行全屏查看和编辑，二进制文件不暴露文本编辑入口。（#40）
  **Workspace fullscreen text editor** — Workspace files can open `.md`, `.json`, `.kt`, `.log`, and other text files in fullscreen view/edit mode; binary files do not expose text editing. (#40)

- **记忆作用域与记忆表** — 记忆支持助手私有/全局作用域切换；新增默认关闭的记忆表管理能力，支持模板、文档、作用域控制和零侵入禁用状态。（#39 #41）
  **Memory scopes and memory tables** — Memories can switch between assistant-private and global scopes; added disabled-by-default memory table management with templates, documents, scope controls, and zero-intrusion disabled behavior. (#39 #41)

- **助手私有 Skill 管理** — 全局 Skill 与助手私有 Skill 的可见性和复制路径更清晰，助手扩展页支持管理私有副本。（#36）
  **Assistant-private Skill management** — Global and assistant-private Skills have clearer visibility and copy paths; assistant extension pages can manage private copies. (#36)

### 修复 / Fixes（本 Fork，v2.3.17 之后）

- **工作区 Git pack 可靠性** — 工作区终端和 AI shell 不再对 git pack 文件启用 `link2symlink`，并在风险存储模式下显示提示，减少 git clone/pull 损坏。（#33 #35）
  **Workspace git pack reliability** — Workspace terminal and AI shell no longer use `link2symlink` for git pack files, and risky storage modes show warnings to reduce git clone/pull corruption. (#33 #35)

- **Skill 文件访问作用域** — Skill 读取路径限制到启用 Skill 及其允许的子文件/安全符号链接，避免越权读取。（#34 #37 #38）
  **Skill file access scope** — Skill file reads are limited to enabled Skills and approved subfiles/safe symlinks, preventing unauthorized path access. (#34 #37 #38)

- **子代理工具策略** — `finish_work` 仅提供给子代理，主会话不再暴露该工具，工具权限与运行时身份保持一致。（#42）
  **Subagent tool policy** — `finish_work` is now only available to subagents; the main session no longer exposes it, keeping tool permissions aligned with runtime identity. (#42)

- **备份与聊天生成细节** — 备份规则包含外部工作区文件；后台生成和时间提醒转换器行为与前台聊天保持一致。
  **Backup and chat generation details** — Backup rules now include external workspace files; background generation and time reminder behavior align with foreground chat.

---

## v2.3.17

### 改进 / Improvements（本 Fork，v2.3.16 之后）

- **子代理步数预算** — 统一子代理 `maxSteps` 与主会话步数扣减逻辑，避免预算不一致导致提前结束或超额执行。
  **Subagent step budget** — Align subagent `maxSteps` with the main session step budget so runs stop and charge steps consistently.

- **子代理档案字段精简** — 简化子代理档案配置与 `spawn_subagent` 展示字段，保留单一预算控制入口，设置页与扩展页同步调整。
  **Subagent profile simplification** — Streamline subagent profile fields and spawn UI; single budget control; settings and extension pages updated.

---

## v2.3.16

### 新功能 / Features（本 Fork，v2.3.15 之后）

- **工作区项目文件支持外部存储目录** — 新增全局设置，可将工作区项目文件切换到应用专属外部存储目录（`Android/data/<包名>/files/workspaces/`），用户可通过文件管理器、USB 或 PC 直接编辑文件，无需每次导出导入。切换时自动迁移（复制→校验→删除源文件），迁移失败自动回滚；迁移期间锁定工作区操作防止数据不一致。工作区详情页展示当前项目文件的设备路径。（#30）
  **Workspace project files external storage** — New global setting to store workspace project files in app-specific external storage (`Android/data/<pkg>/files/workspaces/`), accessible via file manager, USB, or PC. One-tap migration with copy→verify→delete-source and automatic rollback on failure; workspace operations locked during migration. Workspace detail page shows the project files device path. (#30)

---

## v2.3.15

### 修复 / Fixes（本 Fork，v2.3.14 之后）

- **压缩上下文后消息错位** — 存在 `hidden` 节点时，`updateCurrentMessages` 按可见下标映射物理节点会写错槽位；改为按可见下标与 `messageNodes` 映射更新，并补充单元测试。（#28）
  **Message order after compress context** — With `hidden` nodes, visible indices no longer map 1:1 to `messageNodes` slots; updates use visible-index mapping; unit tests added. (#28)

- **合并后 Room 与启动崩溃** — 修正 AutoMigration 8→9、补全 24→25 / 25→26 / 26→27 迁移；fork 旧 v26 库可升到 v27，避免 identity hash 校验闪退。
  **Post-merge Room startup crash** — Fix AutoMigration 8→9, add manual migrations 24→25 / 25→26 / 26→27; legacy fork v26 databases upgrade to v27 without identity-hash crash.

### 新功能 / Features（合并 upstream/master）

- **会话文件夹** — 按助手分组管理会话，侧栏新建/重命名/删除文件夹，会话可归入文件夹。
  **Conversation folders** — Per-assistant folder groups in the drawer; create, rename, delete folders; assign conversations to folders.

- **MCP OAuth 2.1** — MCP 服务器支持 OAuth 授权（PKCE、动态注册、令牌刷新），设置页与回调 Activity 接入。
  **MCP OAuth 2.1** — OAuth for MCP servers (PKCE, DCR, token refresh); settings and callback activity.

- **助手头像裁剪** — 设置助手头像时可裁剪图片。
  **Assistant avatar crop** — Crop image when setting assistant avatar.

- **Workspace `/tmp` 免审批** — 工作区工具写入 `/tmp` 不再强制走审批流程。
  **Workspace /tmp writes** — Workspace tool writes under `/tmp` skip forced approval.

### 改进 / Improvements（合并 upstream/master）

- **OpenAI Chat Completions** — 多模态工具调用路径增强；工具参数 JSON 归一化，避免残缺 JSON 发往模型。
  **OpenAI Chat Completions** — Multimodal tool-call path; normalized tool-call JSON to avoid sending broken payloads.

- **Google API** — 工具响应中的多媒体内容解析。
  **Google API** — Multimedia content in tool responses.

- **S3/COS 同步** — 修复下载丢数据及腾讯云 COS endpoint 兼容。
  **S3/COS sync** — Fix incomplete downloads and Tencent COS endpoint compatibility.

### 修复 / Fixes（合并 upstream/master）

- **Markdown 粗体** — 使用 Bold 字重，修复部分 OEM 字体 `fontWeight` 加粗不生效。
  **Markdown bold** — Use Bold weight for OEM fonts where synthetic bold fails.

- **聊天页渐变背景** — 修复动态 Mesh 渐变动画循环跳变。
  **Chat gradient background** — Fix mesh gradient animation loop jump.

- **MCP** — 工具调用前刷新 OAuth 令牌并按需重连。
  **MCP** — Refresh OAuth tokens before tool calls and reconnect when needed.

## v2.3.14

### 新功能 / Features

- **NewAPI 渠道 JSON 导入** — 提供商设置页支持从剪贴板粘贴 `newapi_channel_conn` JSON，并与现有 `ai-provider:v1:` 格式自动分流；可映射为 OpenAI 兼容提供商并命名保存。（#19 #20）
  **NewAPI channel JSON import** — Provider settings can paste `newapi_channel_conn` JSON from the clipboard, routed alongside existing `ai-provider:v1:` payloads; maps to an OpenAI-compatible provider with a name dialog. (#19 #20)

### 改进 / Improvements

- **子代理档案提示** — `spawn_subagent` 系统提示中的可用档案列表改为输出 `workspace_access`，移除硬编码的 explore/coder 用途说明。（#27）
  **Subagent profile hints** — Available profiles in `spawn_subagent` system prompt now expose `workspace_access` instead of hardcoded explore/coder usage hints. (#27)

## v2.3.13

### 修复 / Fixes

- **FTS 搜索列名歧义崩溃** — `MessageSearchSort.orderBy` 中 `update_at` 用 `m.update_at` 限定表别名，FTS 查询不再报 ambiguous column。（#26）
  **FTS search ambiguous column crash** — `MessageSearchSort.orderBy` now qualifies `update_at` with table alias `m.update_at`, resolving the ambiguous column crash in FTS queries. (#26)

- **重复默认助手** — `DEFAULT_ASSISTANTS` 第二个条目 name 改为「Template Assistant」，新用户不再出现两个同名默认助手。（#23）
  **Duplicate default assistant** — Second `DEFAULT_ASSISTANTS` entry renamed to "Template Assistant"; new users no longer see two identically-named default assistants. (#23)

- **Shell /dev/ 重定向误拒** — Shell 策略正则使用负向前瞻，`2>/dev/null` 等安全重定向不再被误判为违规。（#16）
  **Shell /dev/ redirect false positive** — Shell policy regex now uses negative lookahead; safe redirects like `2>/dev/null` are no longer falsely blocked. (#16)

- **压缩 hidden 消息统计与正文泄漏** — `compressHiddenCount` 作为 `MessageNode` 独立属性存储，不再写入模型可见正文；`FilesPicker`/`ChatSizeChecker` 改用 `count{ !it.hidden }` 计算可见消息条数。（#24 #25）
  **Compress hidden count leak + stats mismatch** — `compressHiddenCount` is now a separate `MessageNode` property, not embedded in model-visible text; `FilesPicker`/`ChatSizeChecker` use `count{ !it.hidden }` for visible message count. (#24 #25)

- **子代理强制停止后仍在后台运行** — `ChatService` 新增 `activeSubagents` 注册表，`stopGeneration()` 委托至 `requestCancel()` 取消所有活跃子代理；`finishInterruptedPendingTools` 处理流式子代理未完成工具调用。（#18）
  **Subagent keeps running after force-stop** — `ChatService` tracks active subagents; `stopGeneration()` delegates to `requestCancel()` to cancel all running subagents; `finishInterruptedPendingTools` handles streaming subagent pending tool calls. (#18)

- **子代理语义混淆与累计上限** — `tool_loop_steps`/`transcript_size` 语义明确化；slim JSON 新增 `tool_call_count` 与 `usage` 字段；标题显示工具调用次数 + token 消耗；`SubagentProfile` 新增 `maxToolCalls` 累计工具次数上限。（#21）
  **Subagent semantic confusion + cumulative limit** — Clarified `tool_loop_steps`/`transcript_size` semantics; slim JSON now includes `tool_call_count` and `usage`; card title shows tool calls count + token usage; added `maxToolCalls` limit on `SubagentProfile`. (#21)

- **子代理工具调用 UI 与主代理不一致** — Transcript 行通过 `ToolUIRegistry` 复用主代理 ToolUI 图标/标题逻辑，点击可查看工具调用详情 Preview；内嵌工具输出默认折叠 10 行可展开；`buildTranscript` 默认 `truncateToolOutput` 提升至 2000 字符。（#22）
  **Subagent tool call UI inconsistent with main agent** — Transcript rows now use `ToolUIRegistry` for consistent icon/title with main agent ToolUI; click-through Preview shows tool call details; inline tool output with 10-line collapse/expand; `buildTranscript` default `truncateToolOutput` raised to 2000 chars. (#22)

## v2.3.12

### 新功能 / Features

- **日志长按多选导出** — 日志页长按条目进入选择模式，底部 toolbar 全选/确认/取消；确认仅导出选中项（redacted）。`truncateLogEntry` 抽到 `common` 层供 `LogsTool` 复用。（#15）
  **Log long-press multi-select export** — Long-press an entry to enter selection mode with a bottom toolbar (select all / confirm / cancel); confirm exports only selected entries (redacted). `truncateLogEntry` extracted to `common` for reuse by `LogsTool`. (#15)

- **助手级持久化工作目录** — 每个助手可配置独立 CWD，路径规范化后持久化；补全提供器与 i18n 的「默认目录」按钮均使用 effective CWD。
  **Assistant-level persistent workspace CWD** — Per-assistant configurable CWD with path normalization; completion provider and i18n default-directory button both use the effective CWD.

- **子代理 steps 字段拆分** — `steps` 拆分为 `tool_loop_steps`（最大循环轮次）与 `transcript_size`（最大 transcript 字符数），语义更清晰。
  **Subagent steps field split** — `steps` split into `tool_loop_steps` (max loop rounds) and `transcript_size` (max transcript chars) for clearer semantics.

- **finish_work 元工具** — 新增 `finish_work` meta-tool，供子代理在收敛时主动结束 agent loop，避免无效循环。
  **finish_work meta-tool** — New `finish_work` meta-tool allows subagents to proactively end the agent loop on convergence, avoiding wasted cycles.

### 修复 / Fixes

- **世界书/条目编辑全屏化** — 世界书与 Regex 条目编辑改为与聊天全屏编辑器同款的 `BasicAlertDialog` 全屏铺平，不再使用 BottomSheet 叠 Dialog，避免编辑时底层对话输入框 IME 抖动。（#17）
  **Lorebook entry fullscreen edit** — Lorebook and regex entry editing use the same fullscreen `BasicAlertDialog` pattern as chat input, replacing stacked bottom sheets/dialogs that caused bottom chat input jitter. (#17)

- **流式错误体多段 JSON** — `parseErrorDetailFromResponseBody` 容错解析 SSE/粘连 body；Claude、ChatCompletions、Response、Google 流式 `onFailure` 统一使用。（#12）
  **Streaming error body with extra JSON** — `parseErrorDetailFromResponseBody` tolerates multi-segment bodies; all streaming `onFailure` paths use it. (#12)

- **检查更新 GitHub API 403** — 可选 `github.api.token` → `GITHUB_API_TOKEN`、Bearer 与 API 版本头；403 显示明确限流提示。（#10）
  **Update check GitHub 403** — Optional token in BuildConfig, auth headers, clearer rate-limit message. (#10)

- **日志 JSON 字符串选择与复制** — 单 Sheet 内详情/「选择复制」切换，保留详情滚动位置，复制成功 Snackbar；不再嵌套 Sheet 闪退。（#9）
  **Log JSON select-and-copy** — Single bottom sheet with detail/copy states, scroll preservation, in-sheet snackbar; fixes nested sheet crash. (#9)

- **日志导出空选全量** — 选择模式确认时若选中列表为空，不再意外导出全部日志。（#15 附带修复）
  **Log export empty-selection bug** — Confirming export with empty selection no longer falls back to exporting all logs. (bundled with #15)

- **工作区 shell /dev/ 重定向误拒** — 工作区 shell 策略对 `2>/dev/null` 等安全重定向误判为违规，现已修复。（#16）
  **Workspace shell /dev/ redirect false positive** — Shell policy incorrectly flagged safe redirects like `2>/dev/null`; now fixed. (#16)

## v2.3.11

### 新功能 / Features

- **消息隐藏（软删除）** — 新增「隐藏消息」操作：被隐藏的消息以半透明 + 左侧指示条显示，标注「已隐藏 · 不在上下文」，不再计入上下文与 token；压缩上下文时默认改为隐藏旧消息而非删除，并在摘要前注入 `[已隐藏 N 条消息]` 提示。
  **Message hiding (soft delete)** — New "Hide" action: hidden messages render at low opacity with a side marker and "Hidden · not in context" label, excluded from context and token count; compress-context now hides old messages instead of deleting, with a `[N messages hidden]` prefix in the summary.

- **子代理最大并发配置** — 助手新增 `subagentMaxConcurrent`（1–5），通过 Semaphore 限流 `spawn_subagent` 并发执行，超出的子代理自动排队；助手详情页提供滑块配置。
  **Max concurrent subagents** — New per-assistant `subagentMaxConcurrent` (1–5) limits parallel `spawn_subagent` execution via a Semaphore; excess subagents queue. Configurable via a slider on the assistant detail page.

### 修复 / Fixes

- **子代理嵌套深度 off-by-one** — 统一 depth 边界条件为 `depth > maxDepth` 与 `(depth+1) <= maxDepth`：`maxDepth=1` 时主代理可正常派生首层子代理，`maxDepth=2` 时允许深度 2 的子代理存在但不再继续嵌套。补充单元测试覆盖三类边界。
  **Subagent nesting depth off-by-one** — Unified depth bounds to `depth > maxDepth` and `(depth+1) <= maxDepth`: `maxDepth=1` now correctly allows the main agent to spawn one layer; `maxDepth=2` permits depth-2 subagents but blocks further nesting. Added unit tests for three boundary cases.

- **关闭 spawn 后子代理仍可继承调用** — `SubagentPermissionBuilder` 现从 `inheritTools` 工具集中剥离 `SUBAGENT_TOOL_NAMES`（`spawn_subagent` / `ask_btw` / `manage_subagent_profile`）；`spawn_subagent` 只能通过 `spawnToolBuilder` 按 depth 条件显式注入。
  **Spawn still callable via inheritance when disabled** — `SubagentPermissionBuilder` now strips `SUBAGENT_TOOL_NAMES` (`spawn_subagent` / `ask_btw` / `manage_subagent_profile`) from inherited tool sets; `spawn_subagent` is only injected explicitly via `spawnToolBuilder` when depth allows.

- **子代理工作区文件操作不显示 chip** — 父消息文件芯片现聚合子代理 transcript 中所有 `workspace_write_file` / `workspace_edit_file` 调用路径，与顶层工具结果合并去重后统一展示。
  **Subagent workspace file chips missing** — Parent message file chips now aggregate `workspace_write_file` / `workspace_edit_file` paths from subagent transcripts, merged and deduped with top-level tool results.

### 其他 / Other

- **数据库迁移** — `AppDatabase` v25 → v26：`MessageNodeEntity` 新增 `hidden` 列（默认 0），通过自动迁移升级。
  **Database migration** — `AppDatabase` v25 → v26: added `hidden` column (default 0) to `MessageNodeEntity` via auto-migration.


## v2.3.10

### 新功能 / Features

- **会话归档** — 支持将对话归档，便于整理长期会话列表。
  **Conversation archive** — Archive conversations to keep the main list manageable.

- **子代理能力移植** — 从子 fork 移植并行/委托与额外本地工具，并调整子代理相关导航结构。
  **Subagent port** — Parallel/delegate flows, extra local tools, and navigation updates ported from the sub fork.

### 修复 / Fixes

- **屏幕使用时间** — 改用事件配对计算时长，修正应用名解析，并排除桌面启动器统计。
  **Screen time** — Event-pair duration, app label resolution, and launcher exclusion.

- **后台文本生成** — 默认使用 AUTO 推理级别。
  **Background text generation** — Default reasoning level set to AUTO.

- **技能扩展面板** — 打开时清理已删除技能的残留引用。
  **Skills panel** — Drop stale references to removed skills on open.

### 其他 / Other

- **日历工具** — 新增查询与创建日历事件工具。
  **Calendar tools** — Query and create calendar events.

- **合并上游** — 合并 `upstream/master` 至 `release/rikka-arsucar`。
  **Upstream merge** — Merged `upstream/master` into `release/rikka-arsucar`.


## v2.3.9

### 重构 / Refactor

- **更新 App 图标与应用名称** — 更新整套自适应图标以及非自适应 fallback 图标，同时将应用显示名称重命名为 "RikkaRs"。
  **App Icon and Name Update** — Updated the complete set of adaptive and non-adaptive launcher icons, and renamed the application display name to "RikkaRs".


## v2.3.8

### 修复 / Fixes

- **切换会话丢失生成内容** — 流式生成中（含子代理运行）切换到其它会话再切回时，不再用 DB 旧快照覆盖内存中的实时流式内容（文本 / reasoning / 子代理 transcript），避免出现「空消息 + 加载图标」。
  **Switch-away content loss** — While streaming (including subagent runs), switching to another conversation and back no longer overwrites in-memory streaming content (text / reasoning / subagent transcript) with a stale DB snapshot, fixing the "empty message + loading indicator" regression.

- **子代理启用前置条件** — 移除 `assistantHasSpawnableProfile` 的 `canSpawn` 前置条件与阻断弹窗；ModelListSheet 展开时移除 200dp 高度上限、隐藏拖拽条并将面板高度提至 90%，改善大屏与长列表体验。
  **Subagent enablement & sheet layout** — Removed the `canSpawn` prerequisite and blocking dialog from `assistantHasSpawnableProfile`; ModelListSheet no longer caps expanded height at 200dp, hides the drag handle, and raises sheet height to 90% for better large-screen / long-list UX.

### 重构 / Refactor

- **子代理工具刷新与运行时测试** — 重构 `ChatService` 中子代理工具的刷新逻辑并补强运行时测试覆盖。
  **Subagent tool refresh & runtime tests** — Refactored subagent tool refresh in `ChatService` and expanded runtime test coverage.


## v2.3.7

### 修复 / Fixes

- **子代理流式一致性** — 流式结束/失败/取消后，同步清理 `spawn_subagent` 工具 JSON `text` 与 metadata 的 `streaming` 字段，避免消费者误判为仍在流式。
  **Subagent streaming consistency** — On stream end/failure/cancel, the JSON `streaming` field in `spawn_subagent` tool `text` is now cleaned alongside `metadata.subagent_streaming`, preventing stale-streaming false positives.

- **会话状态写入** — 用 `synchronized(session.stateLock)` 替代 CAS 重试循环，串行化会话状态读改写；DB 加载时清理残留的子代理流式标记并写回；`syncMessageNodes` 改为按 id diff（删孤儿 + REPLACE upsert），避免中断导致整段消息丢失。
  **Conversation state writes** — Replace CAS retry loops with `synchronized(session.stateLock)` to serialize state read-modify-write; stale subagent streaming flags are cleaned on DB load and persisted back; `syncMessageNodes` now uses id-diff (delete orphans + REPLACE upsert) to avoid losing all messages on interrupted saves.

- **Workspace shell 加固** — 启发式拦截更多破坏命令（`rm -rf /*` / `~` / `$HOME`、fork bomb、`chmod -R 777 /`、关机重启等）；**非强隔离**，真实隔离依赖 workspace cwd 限制。
  **Workspace shell hardening** — Heuristic denylist extended (more `rm -rf` root variants, fork bombs, `chmod -R 777 /`, power commands); **not a security boundary** — real isolation relies on workspace cwd limits.

- **LogPage 性能** — 列表卡片脱敏结果 memoize，避免每次重组重复计算。
  **LogPage performance** — Memoize redacted URL in list card to avoid recomputation on every recomposition.


## v2.3.6

### 修复 / Fixes

- **LogPage 脱敏** — 请求详情与列表 URL 与导出/get_logs 使用相同脱敏规则（含 URL query 中的密钥）。
  **LogPage redaction** — Request detail sheet and list URLs use the same redaction as export and get_logs (including sensitive query params).

- **子代理审批** — 子代理工具链不再强制清除 `needsApproval`，与工作区审批配置一致。
  **Subagent approval** — Subagent tools no longer unconditionally clear `needsApproval`; workspace approval settings apply.

- **Web 服务默认** — 新默认仅监听 localhost；关闭 localhost 且未启用 JWT 时需确认并显示警告。
  **Web server defaults** — New installs default to localhost-only; LAN without JWT requires confirmation and shows a warning.

- **Workspace shell** — 应用层启发式拦截高危 shell 命令（如访问 /data/data、破坏性 `rm -rf /`、fork bomb、关机重启等）；**非强隔离**，真实隔离依赖 workspace cwd 限制。
  **Workspace shell** — App-layer heuristic denylist blocks high-risk shell commands (sensitive paths, `rm -rf /`, fork bombs, power commands); **not a security boundary** — real isolation relies on workspace cwd limits.

- **子代理流式一致性** — 流式结束/失败/取消后，同步清理 `spawn_subagent` 工具 JSON `text` 与 metadata 的 `streaming` 字段，避免消费者误判为仍在流式。
  **Subagent streaming consistency** — On stream end/failure/cancel, the JSON `streaming` field in `spawn_subagent` tool `text` is now cleaned alongside `metadata.subagent_streaming`, preventing stale-streaming false positives.

- **会话状态写入** — 用 `synchronized(session.stateLock)` 替代 CAS 重试循环，串行化会话状态读改写；DB 加载时清理残留的子代理流式标记并写回。
  **Conversation state writes** — Replace CAS retry loops with `synchronized(session.stateLock)` to serialize state read-modify-write; stale subagent streaming flags are cleaned on DB load and persisted back.

## v2.3.5

### 新功能 / New Features

- **日志脱敏** — AI 通过 `get_logs` 工具读取日志时，敏感标头（Authorization、API Key、Cookie）和请求体中的密钥字段自动替换为 `***REDACTED***`；LogPage 中的原始日志不受影响。
  **Log Redaction** — Sensitive headers (Authorization, API keys, cookies) and body secrets are automatically redacted to `***REDACTED***` when the AI reads logs via the `get_logs` tool. Raw logs in LogPage remain unredacted for the user.

- **`get_logs` AI 工具** — 新增本地工具让 AI 读取应用运行日志（HTTP 请求 + 文本日志），支持 `type` 过滤（all/request/text）和 `limit` 参数（1–32）。每条 body/hea- der 截断至 2KB，总 JSON 控制在 16KB 以内，防止 GenerationHandler 全局截断。
  **`get_logs` AI Tool** — New local tool that lets the AI read app runtime logs (HTTP request logs + text logs). Supports `type` filter (all/request/text) and `limit` param (1–32). Per-entry body/header truncation (2KB) + total payload cap (16KB) prevent GenerationHandler global truncation.

- **日志导出** — LogPage 新增导出按钮，通过系统文件选择器保存日志为 JSON 文件，导出内容已脱敏。
  **Log Export** — New export button on the Logs page saves logs as JSON via the system file picker. Exported content is redacted (no API keys in file).

- **子智能体系统** — 完整子智能体 MVP：数据模型 + 设置 UI（Phase A）、权限层（Phase B）、运行时引擎（Phase C）、聊天工具卡片 UI（Phase D）。子智能体配置页的本地工具列表移除了 AskUser，新增日志工具。
  **Subagent System** — Full subagent MVP: data model + settings UI (Phase A), permission layer (Phase B), runtime engine (Phase C), and chat tool cards UI (Phase D). Subagent profile page now lists Logs instead of AskUser.

- **斜杠命令技能补全** — 在聊天输入框输入 `/` 即可发现和应用技能库中的技能提示词。
  **Slash Command Skill Completion** — Type `/` in chat input to discover and apply skill prompts from the skills library.

- **搜索结果图片** — 网页搜索结果现在包含图片，显示在 AI 消息和可展开的 Sheet 中。
  **Search Results with Images** — Web search results now include images, shown in AI messages and expandable sheet.

- **屏幕使用时间工具** — 新增本地工具，用户授予使用情况访问权限后，AI 可读取设备屏幕使用时间。
  **Screen Time Tool** — New local tool that lets the AI read the device's screen usage stats after the user grants Usage Access permission.

- **图片生成全屏预览** — 全屏预览现在显示模型名称，并提供「复制提示词」按钮。
  **Image Generation Preview** — Fullscreen preview now shows the model name and a "Copy prompt" button.

### 修复 / Fixes

- **请求日志持久化** — 「记录请求」开关状态现在会持久化到 DataStore，重启应用后保留。
  **Request Logging Persistence** — The "Record requests" toggle state is now persisted to DataStore across app restarts.

- **子智能体配置页** — 从本地工具列表中移除了误导性的 AskUser 选项。
  **SubagentProfilePage** — Removed misleading AskUser option from local tools list.

### Notes

- Git tag 2.3.5 was built with embedded ersionName **2.3.4** (ersionCode 167). Use the in-app version or this note when matching APKs to tags.
  Git 标签 2.3.5 对应构建内嵌版本为 **2.3.4**（ersionCode 167），核对 APK 时请以此为准。


---

## v2.3.4

### 修复 / Fixes

- **收藏 CI 构建** — 将 `ImageFavoriteAdapter`、`FavoriteMeta` 及相关设置纳入 Release 构建，修复运行时类缺失错误。
  **Favorites CI Build** — Include `ImageFavoriteAdapter`, `FavoriteMeta`, and settings in the release build to fix missing-class runtime errors.

---

## v2.3.3

### 修复 / Fixes

- **图片生成** — 修复并发槽位管理、收藏分组及审查反馈问题。
  **Image Generation** — Fix concurrency slot management, favorites grouping, and review feedback issues.

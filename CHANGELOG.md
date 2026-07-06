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

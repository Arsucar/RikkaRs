# 审计报告：ListCard / dedup / search / web / workspace / speech

- **仓库路径**: `D:\2026Code\Group_android\rikkahub`
- **HEAD**: `3ebfbca45893deefb8c61768a4bdb0e01255a5f6` (`3ebfbca4 chore(task): archive 06-28-06-28-fix-review-issues`)
- **近期变更规模（参考）**: 近 20 个提交约 `215 files changed, 18289 insertions(+), 680 deletions(-)`（含任务归档与文档）
- **审计日期**: 2026-06-28
- **范围**: 只读代码审查（子代理 G）

---

## 1. ListCard / Reorderable / Swipe 抽象一致性

### 1.1 命名与抽象现状

仓库内 **不存在** 名为 `BaseListCard`、`ReorderableSwipeableItem` 或 `*ListCard.kt` 的共享抽象类型（全库 `*.kt` 检索无匹配）。列表场景以 **页面内私有 Composable** + **第三方 `sh.calvin.reorderable`** + **Material3 `SwipeToDismissBox`** 组合实现。

### 1.2 可重排序列表（ReorderableItem）分布

| 页面 / 组件 | 文件:行 | 行内卡片 Composable | 侧滑删除 |
|-------------|---------|---------------------|----------|
| AI Provider 列表 | `SettingProviderPage.kt:223-256` | `ProviderItem` (L617) | 无 |
| AI Provider 内模型 | `SettingProviderDetailPage.kt:531+` | `ModelCard` + SwipeToDismiss | 有 (L1340) |
| 搜索 Provider | `SettingSearchPage.kt:129-160` | `SearchProviderCard` (L270) | 无（菜单删除） |
| TTS / ASR | `SettingSpeechPage.kt:313-361, 390+` | `TTSProviderItem` / `ASRProviderItem` | 无（`onDelete` 菜单） |
| 助手 / 标签 | `AssistantPage.kt:198+` | `AssistantItem` | 无 |
| Prompt 扩展 | `PromptPage.kt:237+` | 内联 + SwipeToDismiss | 有 |
| 模型选择器收藏 | `ModelList.kt:637+` | `ModelItem` | 无 |
| MCP | `SettingMcpPage.kt:245+` | `McpServerItem` | 有 |

**P2** — **模式未统一**：任务描述中的「11 个 ListCard」在代码中对应 **多套独立 `*Item` / `*Card`**，拖拽手柄、缩放 `isDragging`、间距、删除交互（侧滑 vs 溢出菜单 vs 详情页）不一致；**无**跨模块共享组件可保证行为一致。

**P3** — **搜索列表无 SwipeToDismiss**：`SettingSearchPage.kt` 仅 `ReorderableItem` + 卡片内菜单删除（L138-147），与 `HistoryPage` / `FavoritePage` / `SettingMcpPage` 的侧滑删除路径不同。

**P3** — **语音列表删除非侧滑**：`TTSProviderItem` / `ASRProviderItem` 通过下拉/按钮 `onDelete`（`SettingSpeechPage.kt:349-358`），与设置里其他可删列表的侧滑范式不一致。

---

## 2. `copyProvider` 扩展与 sealed 覆盖

### 2.1 AI `ProviderSetting`

- 定义：`ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt:42-52`（含 `tags`）
- 实现：`OpenAI` L94-116、`Google` L162-184、`Claude` L226-248；`Types` 仅三类（L251-258）

**结论**：当前 sealed 子类 **均** 实现 `copyProvider` 且传递 `tags`；无遗漏子类。

### 2.2 Speech

- `speech/.../TTSProviderSetting.kt:12-15` — 各子类 `copyProvider` 仅 `id`/`name`（多实现至 L247）
- `speech/.../ASRProviderSetting.kt:12-15` — 五子类均覆盖（OpenAIRealtime、DashScope、Volcengine、MiMo、Step）

**P3** — **与 AI 不对称**：TTS/ASR 的 `copyProvider` 不复制 API key、URL 等字段以外的「重命名」场景足够，但 **无** `tags` 等与 AI Provider 对齐的扩展字段（设计差异，非编译缺口）。

### 2.3 Search `SearchServiceOptions`

- `search/.../SearchService.kt:133+` — sealed `SearchServiceOptions`，**无** `copyProvider` 抽象
- 列表复制/新增：`SettingSearchPage.kt:180-186` 使用 `settings.copy(searchServices = ...)`；添加实例 `selectedType.primaryConstructor!!.callBy`（L254-255）

**P2** — **search 模块未 dedup `copyProvider` 模式**：与 AI/speech 的「复制 provider 配置」API 不一致；若 fork 目标包含统一 Provider 复制契约，search 仍为 **data class + Settings.copy** 路径。

### 2.4 调用点抽样

- `PreferencesStore.kt:290,296,312,838` — `copyProvider()` / `models = emptyList()`
- `SettingProviderPage.kt:133,140` — 复制时 `Uuid.random()`
- `ShareSheet.kt:96` — 分享前 `copyProvider(models = emptyList())`

未发现 AI sealed 子类 **未实现** `copyProvider` 的情况。

---

## 3. Search：Provider 数量、scrape / query schema

### 3.1 Provider 注册

`SearchService.getService`（`SearchService.kt:46-66`）分支 **18** 个 `SearchServiceOptions` 子类型；`TYPES` map（L142-161）与之一致（含 `CustomJsOptions`）。

模块内 `search/src/main/java/me/rerere/search/*.kt` 服务实现文件 **19** 个（含 `SearchService.kt`）。

### 3.2 `scrapingParameters` / `scrape` 一致性

| 类型 | scrapingParameters | scrape 行为 |
|------|-------------------|-------------|
| Tavily | `InputSchema` 单字段 `url` (L65-74) | HTTP `/extract` (L129+) |
| Exa | `null` (L63) | `Result.failure("not supported")` (L118-123) |
| Zhipu | `null` (L51) | 同上 (L101-106) |
| Custom JS | 有 (L37+) | QuickJS `scrape(urls)` (L68+) |
| Firecrawl / Jina / LinkUp / Tinyfish / RikkaHub 等 | 部分非 null | 各自 HTTP 或脚本 |

**P3** — **「stub」语义一致**：不支持 scrape 的 provider 统一 `scrapingParameters == null` + `scrape` 返回 failure 或空实现；**非** dashscope（DashScope 属于 **ASR**，见 `ASRProviderSetting.DashScope`，非 search provider）。

**P2** — **HTTP 调用风格分裂**：例如 `TavilySearchService.kt:105` 使用 `httpClient.newCall(request).await()`（`SearchService.kt:343-358` 模块内 `internal`）；`ExaSearchService.kt:88`、`ZhipuSearchService.kt:73` 使用 **阻塞** `execute()`。行为与线程模型不一致，dedup 后仍留 **双轨**。

**P3** — **默认 JS scrape 模板**：`SearchService.kt:308-322` `DEFAULT_SCRAPE_SCRIPT` 与 Custom JS 文档字符串一致；仅作用于 `CustomJsOptions`。

### 3.3 `query` / `parameters()` schema

多数服务 `parameters()` 要求 `query`（如 Tavily L62、Exa L60、Zhipu L48）；Tavily 额外校验 `topic` 枚举（L86-88）。**无**跨 provider 的共享 schema 构建器，各文件内联 `InputSchema.Obj`。

---

## 4. `common/` 与 cross-module dedup

### 4.1 `common` 包结构

`common/src/main/java/me/rerere/common/`：**无** `provider/` 子包；主要为 `http/`、`cache/`、`android/`、`js/QuickJSFetch.kt`。

### 4.2 已抽取能力

- `common/http/Request.kt:11-27` — `Call.await()`
- `common/http/SSE.kt` — `sseFlow`（TTS MiMo/MiniMax 等使用）
- `common/js/QuickJSFetch.kt` — `injectFetch`（`CustomJsSearchService.kt:14`）

### 4.3 重复 / 遗漏

**P2** — **`Call.await()` 重复定义**：`search/SearchService.kt:343-358` 与 `common/http/Request.kt:11-27` **逻辑相同**；search 模块未统一使用 `me.rerere.common.http.await`（Tavily 等已用 search 内部版）。

**P3** — **JSON 辅助**：`jsonPrimitiveOrNull` / `jsonObjectOrNull` 等在 `common/http/Json.kt`，ai/app 广泛使用；search 服务内大量 `buildJsonObject` + 本地 DTO，**无**明显第二套 Json 工具，但 **错误处理**（`println` + `error()`）在 Exa/Zhipu 等处重复。

**P3** — **无 `common/provider`**：AI / speech / search 三套 Provider 配置模型独立序列化，fork「第二轮 dedup」在 **类型层** 仍未合并。

---

## 5. Web 模块（Ktor + 静态前端）

### 5.1 启动与绑定

- `web/Entry.kt:18-42` — `embeddedServer(CIO)`，`host` 默认 `"0.0.0.0"`（L20）
- `WebServerManager.kt:23-24,63` — `localhostOnly` 时 `127.0.0.1`，否则 `0.0.0.0`；LAN 时 mDNS（L83-98）
- `WebServerService.kt:49-52,109` — Intent extra `EXTRA_LOCALHOST_ONLY` 与通知 host 文案

### 5.2 CORS / 静态资源

- `Entry.kt:25-31` — `CORS`：`anyHost()`、`anyMethod()`，允许 `Content-Type` / `Authorization`
- 静态：`staticResources("/", "static")` + SPA（L35-39）；`web-ui/` 由 Gradle preBuild 打入（见 `AGENTS.md`）

### 5.3 API 鉴权

- `WebApiModule.kt:65,86-178` — `webServerJwtEnabled` 为 false 时 **`/api` 下 conversation/settings/files/assets 无 JWT 包裹**（L173-177）
- JWT 启用时：HMAC256，密码来自 `webServerAccessPassword`；支持 `Authorization: Bearer` 与 query `access_token`（L100-104,44）
- 密码为空且 JWT 开启：verifier 使用随机 secret + validate 返回 null（L94-96,109-111）— 路由保持关闭

**P1** — **默认暴露面**：JWT **默认关闭**（`PreferencesStore.kt:622` `webServerJwtEnabled = false`）；非 localhost 绑定 `0.0.0.0` 时，同一局域网可访问 **未认证** 的聊天/设置/文件 API（若用户开启 Web 服务且未开 JWT）。

**P2** — **CORS `anyHost()`**（`Entry.kt:29`）：与 JWT 关闭组合时，浏览器任意源可跨域调用 API（仍受网络可达性限制）。

**P3** — **Query token**：`access_token` 可能进入日志/Referer（`WebApiModule.kt:44,103`）；属常见权衡，需在文档中说明。

### 5.4 `updateAssistant`

- Web API：`web/routes/SettingsRoutes.kt:38,58,71,90,123` — `settingsStore.updateAssistant*` 系列
- 与 app 内 `PreferencesStore.kt:498+` 同源持久化路径

---

## 6. Workspace 沙箱

### 6.1 路径逃逸

- `WorkspaceFileSystem.kt:174-190` — `canonicalFile` + 前缀校验 `targetPath.startsWith(rootPath + separator)`；拒绝 NUL（L181）

**结论**：文件工具路径 **有** 根目录约束。

### 6.2 Shell

- `WorkspaceManager.kt:123-147` — `executeCommand`：`command` 非空白即可；`cwd` 须在 workspace 内且为目录
- `HostShellRunner.kt:26-27` — `ProcessBuilder(shell, "-c", context.command)`：**无** 命令白名单/黑名单
- `ProotShellRunner`（未全文展开）同样接收原始 `command` 字符串

**P1** — **Shell 无命令白名单**：AI 工具若传入 `executeCommand`，在 workspace 目录上下文中可执行 **任意** shell 字符串（受 Android 权限与 proot 环境限制，但应用层未过滤）。

**P2** — **超时与输出**：`DEFAULT_COMMAND_TIMEOUT_MS = 30_000`（L178）；`MAX_OUTPUT_CHARS = 128KiB`（`WorkspaceShellRunner.kt:38`）防止 OOM — 设计合理。

### 6.3 Workspace 根名

- `ROOT_NAME_REGEX = [A-Za-z0-9._-]+`（`WorkspaceManager.kt:179`）限制 workspace id，与文件路径校验互补。

---

## 7. Speech：TTS / ASR 控制流

### 7.1 DashScope ASR（与「dashscope stub」澄清）

- `DashScopeASRController.kt:44-115` — 长生命周期：`CoroutineScope(SupervisorJob)`、WebSocket、`AudioRecord`、`recorderJob`
- 队列背压：`MAX_WEBSOCKET_QUEUE_BYTES = 100_000`（L42）
- 配置：`ASRProviderSetting.DashScope`（`ASRProviderSetting.kt:45-64`）与 UI `ASRProviderConfigure.kt:185+`

**结论**：DashScope 为 **完整 ASR 实现**，非 search scrape stub。

### 7.2 其它 ASR 模式

- **MiMo / Step**：HTTP 分段 PCM（注释 `ASRProviderSetting.kt:88-137`）；Step 使用 SSE（`common/http/sseFlow`）
- **OpenAI Realtime / Volcengine**：WebSocket 类 DashScope

**P2** — **生命周期**：`DashScopeASRController` `stop()`/`onClosed` 释放 recorder（L117+）；需在 UI 层保证 `Chat` 离开页面时调用 `stop`（`ui/hooks/ASR.kt:114-116` 按 provider 构造 controller）— 未见单一全局 `AsrController` 单例名，但 **每 provider 实例** 仍可能长驻直至 `stop`。

### 7.3 TTS 流式 PCM

- `MiMoTTSProvider.kt` / `MiniMaxTTSProvider.kt` — `sseFlow` 流式事件
- `QwenTTSProvider.kt:47` — `X-DashScope-SSE` 头

**P3** — 流式 TTS 与 ASR 分属不同 provider 类，**无**统一 streaming 抽象接口文档（实现分散在 `speech/.../providers/`）。

---

## 8. AI 模块其它核查项

| 项 | 现状 |
|----|------|
| Provider sealed | 仅 OpenAI / Google / Claude（`ProviderSetting.kt`） |
| JS scrape stubs | 在 **search** `CustomJsOptions` 默认脚本，非 ai 模块 |
| query schema | `InputSchema` 在 ai/search 工具层分散定义 |
| BackupZipper | **无** 此类名；备份 ZIP 在 `WebDavSync.kt` / `S3Sync.kt` / `ImportExportTab.kt` |
| `updateAssistant` | `PreferencesStore.kt:498+`，Web `SettingsRoutes.kt`，`ChatService.kt:336` |

---

## 9. 发现汇总（按优先级）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| P1 | G-01 | `WebApiModule.kt:173-177` + `PreferencesStore.kt:622` | JWT 默认关闭时 LAN 绑定 `0.0.0.0` 暴露完整 Web API |
| P1 | G-02 | `WorkspaceManager.kt:123-147` + `HostShellRunner.kt:26-27` | Shell 执行无应用层命令白名单 |
| P2 | G-03 | 全 app UI 列表 | 无 `BaseListCard`；reorder/swipe/删除模式分裂 |
| P2 | G-04 | `SearchService.kt:343-358` vs `common/http/Request.kt:11-27` | 重复 `Call.await()` |
| P2 | G-05 | `ExaSearchService.kt:88` 等 vs Tavily `await()` | search HTTP 同步/异步混用 |
| P2 | G-06 | `SearchServiceOptions` | 无 `copyProvider`，与 AI/speech dedup 目标不一致 |
| P2 | G-07 | `Entry.kt:29` | CORS `anyHost()` 放大跨源访问面（JWT 关时） |
| P2 | G-08 | `DashScopeASRController` + `ASR.kt` | 长生命周期 WebSocket/录音需严格 `stop` 绑定页面生命周期 |
| P3 | G-09 | `SettingSearchPage.kt` | 搜索 provider 列表无侧滑删除 |
| P3 | G-10 | scrape 不支持类 | Exa/Zhipu 等 failure 文案/模式一致，可文档化 |
| P3 | G-11 | TTS/ASR `copyProvider` | 仅 id/name，与 AI `tags` 能力不对称 |

---

## 明早 Top 5

1. **P1 Web 暴露面**：核对用户默认路径（`webServerJwtEnabled`、`webServerLocalhostOnly`）与 `WebApiModule` 未认证路由；优先在设置页/启动服务时强制提示或默认 localhost + JWT。
2. **P1 Workspace shell**：盘点 AI 工具传入 `executeCommand` 的调用链，评估是否需要命令过滤或仅允许 proot 内路径。
3. **P2 `Call.await` dedup**：search 模块改为 `import me.rerere.common.http.await`，删除 `SearchService.kt` 底部重复实现。
4. **P2 列表 UI 技术债**：若产品仍要求「11 个 ListCard」统一，需新建共享 Composable（reorder + 可选 swipe + 菜单）并迁移 `ProviderItem` / `SearchProviderCard` / `TTSProviderItem` 等。
5. **P2 JWT 关闭 + CORS**：威胁模型写清；若面向公网/LAN 分享，默认应 **JWT on** 或 **仅 127.0.0.1**（与 `WebServerManager.localhostOnly` 联动）。

---

## 附录：关键文件索引

- 列表 UI：`SettingProviderPage.kt`, `SettingSearchPage.kt`, `SettingSpeechPage.kt`, `SettingMcpPage.kt`, `HistoryPage.kt`, `ModelList.kt`
- Provider 复制：`ai/.../ProviderSetting.kt`, `speech/.../TTSProviderSetting.kt`, `speech/.../ASRProviderSetting.kt`
- Search：`search/SearchService.kt`, `*SearchService.kt`, `CustomJsSearchService.kt`
- Common dedup：`common/http/Request.kt`, `common/js/QuickJSFetch.kt`
- Web：`web/Entry.kt`, `app/.../web/WebServerManager.kt`, `WebApiModule.kt`, `SettingWebPage.kt`
- Workspace：`workspace/WorkspaceFileSystem.kt`, `WorkspaceManager.kt`, `WorkspaceShellRunner.kt`
- Speech：`speech/.../DashScopeASRController.kt`, `ui/hooks/ASR.kt`, `MiMoTTSProvider.kt`
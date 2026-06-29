# Research: 日历工具缺失与 local 工具体系移植点

- **Query**: rikkahub fork 日历工具现状 + local 工具 UI/配置/持久化/权限模式，为移植 upstream `d677707d` 做准备
- **Scope**: internal（代码库）+ upstream commit 只读对照
- **Date**: 2026-06-29

## Findings

### 1. 确认日历工具是否完全缺失

| 检查项 | 结果 |
|--------|------|
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/` 下 calendar/Calendar | **无匹配文件** |
| `CalendarTool.kt` | **不存在**（upstream `d677707d` 新增 `app/.../local/CalendarTool.kt`） |
| `LocalToolOption` | **无** `Calendar` 枚举项（fork 止于 `Logs`，`LocalToolOption.kt:6-35`） |
| `LocalTools.getTools` | **无** calendar 分支（`LocalTools.kt:22-45`） |
| `AndroidManifest.xml` | **无** `READ_CALENDAR` / `WRITE_CALENDAR`（仅有 `PACKAGE_USAGE_STATS` 等特殊权限，`AndroidManifest.xml:15-17`） |
| 全 app `calendar` 残留 | 仅 `Greeting.kt:10,19`（`java.util.Calendar` 问候语）、emoji 资源、**任务 PRD**（`.trellis/tasks/`），**与日历工具无关** |

**结论：日历 local 工具在 fork 中完全缺失**（无半文件、无 manifest 权限、无工具名注册）。

**Upstream `d677707d` 变更面（对照用）**：13 文件，+597 行；核心为 `CalendarTool.kt`（`calendar_query` + `calendar_create`）、`LocalToolOption.Calendar`（`@SerialName("calendar")`）、manifest 两条权限、设置页 + `PermissionManager` 日历申请、两条 Tool UI 注册、多语言 strings。

---

### 2. local/ 工具体系的 UI 渲染模式

**目录**：`app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/`

| 文件 | 作用 |
|------|------|
| `ToolUI.kt` | `ToolUIContext`（入参/输出 JSON、`loading`）、`ToolUIRenderer` 接口、`ToolUIRegistry` 按 `toolName` 映射、`DefaultToolPreview`（JSON 高亮详情） |
| `BuiltinToolUIs.kt` | 内置/local 相关渲染器（如 `GetTimeInfoToolUI`、`GetScreenTimeToolUI`、`GetLogsToolUI`） |
| `WorkspaceToolUIs.kt` / `SubagentToolUIs.kt` | 其他工具族 |

**注册与分发**（`ToolUI.kt:91-113`）：

- 在 `ToolUIRegistry` 的 `listOf(...)` 中增加 `object : ToolUIRenderer` 实例，`associateBy { it.toolName }`。
- 聊天侧 `ChatMessageTools.kt:76-91`：`ToolUIRegistry.resolve(tool.toolName)` → 构造 `ToolUIContext`（`loading` 来自步骤是否在生成中）。
- **例外**：`ask_user` 不走注册表，专用 `AskUserToolStep`（`ChatMessageTools.kt:70-73`）。

**折叠步骤通用态**（`ChatMessageTools.kt:104-119`）：

- **Loading**：`DotLoading` 替代图标。
- **图标/标题**：`renderer.icon` / `renderer.title`。
- **摘要**：`renderer.hasSummary` + `Summary`；可选 `Modifier.shimmer(isLoading = context.loading)`（见 `BuiltinToolUIs.kt` 多处）。
- **详情**：BottomSheet 内 `renderer.Preview`；未定制则 `DefaultToolPreview`。
- **错误展示**：无全局 error 组件；各工具解析 JSON `error` 字段（例：`GetScreenTimeToolUI` `NO_PERMISSION`，`BuiltinToolUIs.kt:433-447`）。

**新增日历工具 UI 需改**：

1. `BuiltinToolUIs.kt`：增加 `CalendarQueryToolUI`（`toolName = "calendar_query"`）、`CalendarCreateToolUI`（`calendar_create`）；upstream 含事件列表摘要（`events.take(3)` + 条数）。
2. `ToolUI.kt`：`ToolUIRegistry` 列表追加上述两个 object（upstream 在 `GetScreenTimeToolUI` 附近注册）。
3. `app/src/main/res/values*/strings.xml`：`chat_message_tool_calendar_query`、`chat_message_tool_calendar_create` 等（upstream 还有 `permission_calendar_*`、`assistant_page_local_tools_calendar_*`）。

**可复用**：`ToolUIContext`、`shimmer`、`DefaultToolPreview`、`getStringContent` 扩展（`ToolUI.kt:116-117`）、`MaterialTheme` 摘要样式（与 `GetScreenTimeToolUI` / `SearchWebToolUI` 一致）。

**合并后问题迹象（UI）**：

- **无统一 error/permission 卡片**：Screen Time 在 Summary 标红；Calendar 若仅返回 JSON `NO_PERMISSION` 且 UI 未像 Screen Time 处理，用户只看到默认 JSON（upstream query UI 未单独做 permission Summary，依赖设置页先申请权限）。
- **工具粒度 vs 开关粒度**：一个 `LocalToolOption.Calendar` 对应 **两个** `toolName`，UI 需注册两个 renderer（upstream 如此）。
- **AskUser / CalendarCreate**：`calendar_create` 在 upstream 设 `needsApproval = { true }`（工具审批流），与 `PermissionManager` 是两条线。

---

### 3. local/ 工具的配置页 UI

**文件**：`AssistantLocalToolPage.kt`

**模式**（`AssistantLocalToolPage.kt:104-203`）：

- `CardGroup` + 多个 `item(headline, supporting, trailingContent = Switch)`。
- `toggleLocalTool(option, enabled)` → `assistant.copy(localTools = ± option)` → `onUpdate` → `AssistantDetailVM.update` → `SettingsStore`（见 §6）。
- **Screen Time 特例**（`AssistantLocalToolPage.kt:81-87`）：开开关时若无 Usage Stats → Toast + `openUsageAccessSettings()`，**仍写入开关**（不 `return` 阻止保存）。

**Upstream 日历开关**（`d677707d` 的 `AssistantLocalToolPage.kt`）：

- `rememberPermissionState` + `PermissionManager` 包裹 READ/WRITE_CALENDAR（`PermissionInfo` + `permission_calendar_*` strings）。
- 开 `LocalToolOption.Calendar` 时：若 `!calendarPermissionState.allPermissionsGranted` → `requestPermissions()` 并 **`return`（不更新 localTools）**，与 Screen Time 行为不一致。

**新增日历开关需改**：

1. `AssistantLocalToolPage.kt`：import 权限组件、日历 `PermissionState`、`toggleLocalTool` 分支、新 `CardGroup` item。
2. `values/strings.xml`（及 ja/ko/ru/zh/zh-rTW）：`assistant_page_local_tools_calendar_title/desc`、`permission_calendar_read/write` + `_desc`。
3. **建议同步**：`AssistantSubagentProfilePage.kt` 的 `localToolOptions` 列表（`727-734`）与 `localToolLabel` when（`824-831`）——当前 **缺 `AskUser`**，也 **无 Calendar**；移植日历时应一并补齐，否则子代理配置与主助手页不一致。

---

### 4. LocalToolOption 枚举与持久化

**文件**：`LocalToolOption.kt:6-35`

| 项 | Fork 现状 |
|----|-----------|
| 枚举 | `JavascriptEngine`, `TimeInfo`, `Clipboard`, `Tts`, `AskUser`, `ScreenTime`, `Logs` |
| 序列化 | `@Serializable` sealed class + 各子类 `@SerialName("snake_case")`（**非 ordinal**） |
| 未知类型 | Kotlinx 反序列化 `Assistant.localTools` 时，**新增** `@SerialName` 可向后兼容；**删除**枚举会导致旧数据里多出来的 serial name 解码失败（需注意迁移） |

**新增 Calendar 步骤**：

1. `LocalToolOption.kt`：`data object Calendar` + `@SerialName("calendar")`（与 upstream 一致）。
2. `LocalTools.kt`：lazy `calendarQueryTool` / `calendarCreateTool` + `getTools` 中 `Calendar` 时 add 两个 `Tool`。
3. `SubagentTools.kt`：`toLocalToolOption()` 增加 `"calendar" -> ...`（`294-302`，当前无 calendar，spawn 参数 `local_tools` 无法选日历）。
4. `AssistantSubagentProfilePage.kt`：列表 + `localToolLabel`（见 §3）。

**工具实现挂载**：`ChatService.kt:661` `localTools.getTools(assistant.localTools)`，无需改调用点，只要 `LocalTools` 扩展即可。

---

### 5. PermissionManager 框架

**Compose 权限 UI**（非业务单例类）：

| 文件 | 职责 |
|------|------|
| `ui/components/ui/permission/PermissionManager.kt:20-44` | 根据 `PermissionState` 显示 `PermissionRationaleDialog` |
| `PermissionState.kt` | `requestPermissions()`、`allPermissionsGranted`、永久拒绝 → 设置页 |
| `RememberPermissionState.kt` / `PermissionTypes.kt` | 声明权限集合 |

**使用场景（fork）**：相机、通知、Web、ASR 等页面级 `PermissionManager(permissionState)`（`ChatPage.kt:527`、`ChatInput.kt:162` 等）。**local 工具里仅 upstream 日历在 `AssistantLocalToolPage` 使用**；fork 当前 **Screen Time 不用 PermissionManager**，用 `ContextUtil.hasUsageStatsPermission()` + `AppEvent.OpenUsageAccessSettings`（`ScreenTimeTool.kt:78-88`）。

| 权限类型 | 申请路径 |
|----------|----------|
| READ/WRITE_CALENDAR | **标准运行时权限** → upstream 用 `PermissionManager` + `Manifest.permission.*` |
| PACKAGE_USAGE_STATS | **特殊权限** → `openUsageAccessSettings()` / `AppOpsManager`，**不走** PermissionManager |

**工具执行时拒权**（Screen Time 模式，`ScreenTimeTool.kt:78-88`）：

- 返回 JSON `{ "error": "NO_PERMISSION", "message": "..." }`；
- 可选 `eventBus.emit` 打开系统页（Screen Time 发 `OpenUsageAccessSettings`）。

**Upstream 日历执行时**（`CalendarTool.kt` 片段）：

- `ContextCompat.checkSelfPermission`；
- 无权限 → 同上 `NO_PERMISSION` JSON + 文案提示用户在助手本地工具设置中授权（**不在工具内弹 PermissionManager**）。

**`calendar_create`**：`needsApproval = { true }` → 走聊天工具 **审批 UI**（`ChatMessageTools` 的 approve/deny），与读日历权限无关。

**合并后问题迹象（权限）**：

- **双轨**：日历（运行时 PermissionManager）vs 屏幕时间（Usage Access 专用）vs 创建事件（Tool approval）——移植需三种路径各接对，不能假设「都走 PermissionManager」。
- **开关与权限不同步**：upstream 日历开关联动 `requestPermissions` 且未授权则不保存开关；Screen Time 开关可先开、用时再报错——产品行为不统一。

---

### 6. 数据保存模式

| 层级 | 说明 |
|------|------|
| 模型 | `Assistant.localTools: List<LocalToolOption>`，默认 `[TimeInfo]`（`Assistant.kt:39`） |
| 作用域 | **Per-assistant**，非全局 Settings 单字段 |
| 子代理 | `SubagentProfile.localTools`（`SubagentProfile.kt:77`）；合并时 `SubagentHost.kt:257-261` 继承父助手/配置 |
| 持久化 | `Settings.assistants` → DataStore `PreferencesStore` key `assistants` JSON（`PreferencesStore.kt:123,215,455,599`） |
| UI 写入 | `AssistantDetailVM.update` → `settingsStore.update` 替换对应 `Assistant`（`AssistantDetailVM.kt:164-179`） |

**链路**：Switch → `copy(localTools)` → `SettingsStore` → 下次 `ChatService` 构建 tools 列表时 `getTools(assistant.localTools)`。

**序列化**：`LocalToolOption` 作为 `Assistant` 的一部分由 `JsonInstant` 编解码；新增 `calendar` serial name 对旧数据安全；若用户从未选过 Calendar，列表中不会出现该项。

---

## 移植日历工具需触碰的文件清单

| 类别 | 路径 |
|------|------|
| 工具实现 | **新建** `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/CalendarTool.kt` |
| 枚举/聚合 | `LocalToolOption.kt`, `LocalTools.kt` |
| Manifest | `app/src/main/AndroidManifest.xml`（READ/WRITE_CALENDAR） |
| 消息 UI | `BuiltinToolUIs.kt`, `ToolUI.kt` |
| 助手设置 | `AssistantLocalToolPage.kt` |
| 子代理（建议） | `AssistantSubagentProfilePage.kt`, `SubagentTools.kt`（`toLocalToolOption`） |
| 字符串 | `app/src/main/res/values/strings.xml` + `values-ja`, `values-ko-rKR`, `values-ru`, `values-zh`, `values-zh-rTW` |
| 运行时（无需改调用点） | `ChatService.kt` 已通过 `LocalTools.getTools` 注入 |

**无需改**：`ChatMessageTools.kt`（除非要为 calendar 做类似 ask_user 的特例）、`Assistant.kt` 字段结构（已有 `localTools`）。

---

## 合并后问题迹象（汇总）

1. **配置页三处枚举重复**：`AssistantLocalToolPage` 手写 7 项、`AssistantSubagentProfilePage` 手写 6 项（缺 AskUser）、`LocalTools.getTools` / `LocalToolOption` / `SubagentTools.toLocalToolOption` 四处需同步——日历移植易漏子代理或 spawn 字符串映射。
2. **权限框架不统一**：PermissionManager（日历）/ Usage Access（屏幕时间）/ Tool approval（calendar_create）/ 无权限 JSON（执行期）。
3. **UI 无统一错误态**：依赖各 `ToolUIRenderer` 自行解析 `error`；Logs/GetTimeInfo 等仅默认 JSON。
4. **反序列化**：`SubagentTools` 用 `mapNotNull { toLocalToolOption() }` 静默丢弃未知项（`272-273`），与 DataStore 里 Assistant 列表直接解码行为略有差别。

---

## Related Specs

- 任务 PRD：`.trellis/tasks/06-29-calendar-tools/prd.md`
- 批次说明：`.trellis/tasks/06-29-backport-batch-2/prd.md`（单文件 `LocalTools.kt` 风格）

## Caveats / Not Found

- Fork **未**包含 upstream 的 `permission_calendar_*` 与 calendar 相关 strings（已用 `git show d677707d` 核对）。
- `CalendarTool.kt` 完整 438 行未写入本报告；实现细节以 cherry-pick / 对照 `d677707d` 为准。
- `AssistantLocalToolPage` upstream 在权限授予后是否自动重试开开关需读完整 diff（当前片段显示未授权时 `return` 不保存）。
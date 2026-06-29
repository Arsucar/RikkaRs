# PRD: Calendar query & create tools

**Parent:** `06-29-backport-batch-2`
**Source:** upstream `d677707d`（sub `e35c1f73` 移植版本仅作参考，我们采用**上游结构**）
**Complexity:** 中等（新工具 + 权限 + UI）
**集成顺序建议:** 本批第 2 个（与 ScreenTime / Search 正交）

## 背景

上游 `d677707d` 新增日历查询与创建工具，使 AI 可读写用户日历事件：
- `calendar_query`：用 `CalendarContract.Instances` 查询事件（支持重复事件展开、跨天事件），可按时间范围 / 关键词筛选。
- `calendar_create`：创建事件，需用户确认。
- 开关开启时通过 `PermissionManager` 框架申请 `READ_CALENDAR` / `WRITE_CALENDAR` 权限。

sub fork `e35c1f73` 移植时特意标注"按 fork 单文件 `LocalTools.kt` 风格落地，不采用上游 `local/` 包拆分"。但**我们 fork 已经建立了 `local/` 子包风格**（`ScreenTimeTool.kt`、`AskUserTool.kt`、`LogsTool.kt` 都在 `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/` 下），所以**采用上游结构**（新建 `local/CalendarTool.kt`），不照搬 sub 的塞进 `LocalTools.kt` 做法。

## 范围（更新版：回流 + 枚举去重落地）

### In Scope

0. **枚举去重落地（日历部分，问题 D）**
   - 本 child 在新增日历工具时，**就地实践**枚举单一 source of truth：新增 `LocalToolOption.Calendar` 后，配置页（`AssistantLocalToolPage`/`AssistantSubagentProfilePage`）和 `SubagentTools.toLocalToolOption` 应通过遍历 `LocalToolOption.entries` 派生，而非手写新的 when 分支。
   - 如果配置页当前是手写 when（调研确认是），本 child **先把日历项加进去**，完整的去重重构由 `06-29-local-tool-ui-consolidation` child 统一做（避免本 child 改太大）。
   - **触及文件**：`AssistantLocalToolPage.kt`、`AssistantSubagentProfilePage.kt`、`SubagentTools.kt`（加日历项）。

1. **新工具实现**（`app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/CalendarTool.kt`，上游 438 行）
   - `calendar_query`：参数 `time_start` / `time_end`（毫秒，必填）、`keyword`（可选，匹配事件标题/描述）；返回事件列表（标题、时间、位置、描述）。
   - `calendar_create`：参数 `title`、`time_start`、`time_end`、`location?`、`description?`；创建前通过 `PermissionManager` 框架弹确认。
   - 重复事件展开：用 `CalendarContract.Instances` 的 `BEGIN` / `END` + `EVENT_TIMEZONE` 正确处理跨天 / 跨时区事件。

2. **权限声明**（`AndroidManifest.xml`）
   - 增加 `<uses-permission android:name="android.permission.READ_CALENDAR" />`
   - 增加 `<uses-permission android:name="android.permission.WRITE_CALENDAR" />`

3. **工具开关注册**（`LocalToolOption.kt` + `LocalTools.kt`）
   - `LocalToolOption.kt`：新增 `CALENDAR_QUERY` / `CALENDAR_CREATE` 枚举项（参照上游 +4 行）。
   - `LocalTools.kt`：把新工具挂进 local tools 列表（参照上游 +8 行）。

4. **工具 UI 渲染**（`BuiltinToolUIs.kt` + `ToolUI.kt`）
   - `BuiltinToolUIs.kt`：新增日历查询结果 / 创建确认卡片（上游 +55 行）。
   - `ToolUI.kt`：注册新工具的 UI 类型（上游 +2 行）。

5. **工具配置页**（`AssistantLocalToolPage.kt`）
   - 增加日历工具的开关行（上游 +42/-行）。

6. **字符串资源**（6 个 `strings.xml`：values / values-zh / values-zh-rTW / values-ja / values-ko-rKR / values-ru，各 +8 行）
   - 工具名、描述、确认弹窗文案等。

### Out of Scope

- 不做日历的 `update` / `delete` 工具（上游也只有 query + create）。
- 不改 `PermissionManager` 框架本身（沿用现有实现）。
- 不做日历账户选择 UI（用系统默认日历账户）。

## 验收标准

- [ ] `CalendarTool.kt` 存在且实现 `calendar_query` / `calendar_create` 两个工具。
- [ ] `AndroidManifest.xml` 含 READ/WRITE_CALENDAR 权限声明。
- [ ] `LocalToolOption` / `LocalTools` 注册了新工具。
- [ ] `BuiltinToolUIs.kt` / `ToolUI.kt` 能正确渲染日历工具调用与结果。
- [ ] 6 个 `strings.xml` 都有对应 key（无硬编码字符串漏网）。
- [ ] `.\gradlew :app:compileDebugKotlin --no-daemon` 通过。
- [ ] `.\gradlew :app:installDebug --no-daemon` 成功装到设备。
- [ ] 真机验证：已授权日历权限后，AI 能查询今日事件、能创建新事件并弹确认。

## 约束

- **采用上游 `local/CalendarTool.kt` 结构**，不照搬 sub 的 `LocalTools.kt` 单文件塞入。
- 创建事件必须经过用户确认（`PermissionManager` 框架），不静默写入。
- 查询返回的事件时间用用户本地时区显示，不强行 UTC。
- 权限被拒时工具返回友好错误（"日历权限未授予"），不崩。

## 风险

- **`PermissionManager` 框架差异**：上游和 sub 的 `PermissionManager` 实现可能不同；移植时核对我们 fork 的 `PermissionManager` 接口，必要时适配。
- **CalendarProvider 不可用**：少数设备无系统日历（无账户），`CalendarContract` 查询返回空 —— 工具应返回"无日历账户"提示而非崩。
- **strings 多语言**：上游的日/韩/俄文文案可直接用；若有新增 key 需要补译，参照已有风格。

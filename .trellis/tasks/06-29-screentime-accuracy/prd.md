# PRD: ScreenTime event-pairing & launcher exclusion

**Parent:** `06-29-backport-batch-2`
**Source:** upstream `5b46c8de` (event-pairing) + `40b613eb` (launcher exclusion)
**Complexity:** 中等（算法重写 + manifest）
**集成顺序建议:** 本批第 1 个（最小、最独立）

## 背景

我们 fork 的 `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/ScreenTimeTool.kt:133` 仍用 `usageStatsManager.queryAndAggregateUsageStats(startMs, endMs)`，该 API 基于统计桶聚合，存在两个已知偏差：
1. **统计桶溢出** —— 长前台会话跨多个桶时聚合结果偏高。
2. **前台时段重叠** —— 多桶重叠期被重复计数。

upstream 在 2.3.4 版本用两笔提交修正：
- `5b46c8de`：改用 `queryEvents` 的全局单一前台模型，逐事件配对 + 向前回看 + 区间裁剪计算前台时长，并声明 LAUNCHER 查询以解析应用名。
- `40b613eb`：通过 HOME intent 识别桌面 launcher 应用，在前台时长统计中排除（用户停在桌面不应算作 App 前台），与系统屏幕使用时间口径一致；manifest 增加 HOME 查询声明适配 Android 11+ 包可见性。

两笔强耦合（都改 `ScreenTimeTool.kt` + `AndroidManifest.xml`），合并实现。

## 范围（更新版：回流 + UI 优化）

### In Scope

0. **ScreenTime UI 错误/空态优化**（问题 C 的 ScreenTime 部分，调研见 `research/screentime-ui-and-persistence.md`）
   - 现状：`GetScreenTimeToolUI`（`BuiltinToolUIs.kt:419`）只处理 `NO_PERMISSION`；`INVALID_TIME`/`INVALID_RANGE` 走默认 JSON Preview；`apps` 空时无友好提示；Preview 最多 50 条无折叠。
   - 优化：
     - `INVALID_TIME`/`INVALID_RANGE` 显示友好错误文案（红字卡片），不走默认 JSON。
     - 成功但 `apps` 空（查询区间无使用记录）显示空态文案（"该时间段无屏幕使用记录"）。
     - Preview 数据量大时（>10 条）折叠 + "显示全部"展开。
   - 注：通用统一错误组件（`LocalToolErrorCard`）由 `06-29-local-tool-ui-consolidation` child 统一抽；本 child 先就地优化 ScreenTime，后续该 child 统一抽取时迁移。
   - **触及文件**：`BuiltinToolUIs.kt`（GetScreenTimeToolUI + ScreenTimePreview）、`strings.xml`（新增错误/空态文案）。

1. **事件配对计算算法**（`5b46c8de` 核心改动）
   - `ScreenTimeTool.kt`：替换 `queryAndAggregateUsageStats` 调用，改用 `usageStatsManager.queryEvents(startMs, endMs)` 遍历 `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` 事件。
   - 实现全局单一前台模型：按时间顺序逐事件配对，`MOVE_TO_FOREGROUND` 到下一个 `MOVE_TO_BACKGROUND`（或查询窗口边界）之间计为该包前台时长。
   - 区间裁剪：只统计落在 `[startMs, endMs]` 内的部分。
   - 向前回看：若查询窗口开始时某 App 已在前台（无对应 MOVE_TO_FOREGROUND），向更早的时间查询一次以找到进入前台的事件。

2. **桌面 launcher 排除**（`40b613eb` 核心改动）
   - `ScreenTimeTool.kt`：通过 `packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)` 获取所有桌面应用包名集合，前台时长统计时跳过这些包。
   - `AndroidManifest.xml`：增加 `<queries>` 声明以适配 Android 11+ 包可见性限制：
     ```xml
     <intent>
         <action android:name="android.intent.action.MAIN" />
         <category android:name="android.intent.category.HOME" />
     </intent>
     <intent>
         <action android:name="android.intent.action.MAIN" />
         <category android:name="android.intent.category.LAUNCHER" />
     </intent>
     ```

3. **应用名解析**（`5b46c8de` 附带）
   - manifest 声明 LAUNCHER 查询后，`packageManager.getApplicationLabel` 才能正确解析各 App 名（Android 11+ 包可见性）。

### Out of Scope

- 不改 ScreenTime 工具的权限申请流程（已有 `PACKAGE_USAGE_STATS` 权限与 `LocalToolOption` 开关）。
- 不改 ScreenTime 的 UI 渲染（`BuiltinToolUIs.kt` 里的 ScreenTime 卡片）。
- 不做算法性能基准测试（事件配对比聚合略慢但在查询窗口内可接受）。

## 验收标准

- [ ] `ScreenTimeTool.kt` 不再调用 `queryAndAggregateUsageStats`，改用 `queryEvents` 事件配对。
- [ ] `AndroidManifest.xml` 含 HOME + LAUNCHER `<queries>` 声明。
- [ ] 前台时长统计排除桌面 launcher 包（通过 `queryIntentActivities(HOME intent)` 获取）。
- [ ] `.\gradlew :app:compileDebugKotlin --no-daemon` 通过。
- [ ] `.\gradlew :app:installDebug --no-daemon` 成功装到设备。
- [ ] 真机验证：打开某 App 停留 1 分钟、回桌面停留 30 秒、再开另一 App 1 分钟，查询今日屏幕使用时间 —— 桌面 30 秒不计入，两个 App 各 1 分钟计入，总量不跨桶翻倍。

## 约束

- 严格按 upstream `5b46c8de` + `40b613eb` 的实现思路，避免引入 sub fork 的额外差异（sub 没有这两笔修复）。
- 应用名解析失败时回退到包名（不崩），与现有行为一致。
- 事件配对的向前回看窗口不要无限往前查（设合理上限，如再往前查一个等长窗口仍无 MOVE_TO_FOREGROUND 则视为窗口开始时就在前台）。

## 风险

- **事件缺失**：某些 OEM/ROM 可能不记录 `MOVE_TO_BACKGROUND` 事件（设备重启后状态丢失），导致配对悬空。upstream 实现里应该有兜底（用下一个 MOVE_TO_FOREGROUND 或窗口边界收尾），移植时核对并保留。
- **包可见性**：Android 11+ 即使声明了 `<queries>`，第三方 App 名解析仍可能受限；这与 upstream 行为一致，不是回归。

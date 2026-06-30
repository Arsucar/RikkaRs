# Implement: 日志导出精准选择与清洗

## Pre-flight

- [x] `prd.md` — 收敛完成
- [x] `design.md` — 已写
- [x] `implement.md` — 本文件
- [x] `implement.jsonl` — 已填 spec 引用
- [x] `check.jsonl` — 已填 spec 引用

---

## Execution Checklist

### Step 1: 提取 truncateLogEntry 到 common 层

- [ ] 1.1 新建 `common/src/main/java/me/rerere/common/android/LogTruncation.kt`
  - 将 `LogsTool.kt:20-37` 的 `MAX_BODY_CHARS`、`MAX_HEADER_VALUE_CHARS`、`truncateLogEntry()` 搬入
  - 函数设为 `internal fun`（同模块内可见，配合 common 的 export 配置）
  - 常量保持 `private const val`
- [ ] 1.2 修改 `app/.../tools/local/LogsTool.kt`
  - 删除内联的 `MAX_BODY_CHARS`、`MAX_HEADER_VALUE_CHARS`、`truncateLogEntry()`
  - import `me.rerere.common.android.truncateLogEntry`
  - 验证 `selectLogsForPayload` 行为不变

**验证**: `.\gradlew :common:test --no-daemon` 通过

### Step 2: LogPage 状态扩展

- [ ] 2.1 在 `LogPage.kt` 新增 `LogTypeFilter` enum
- [ ] 2.2 在 `LogPage` composable 顶部新增状态变量:
  - `logTypeFilter`、`searchQuery`、`selecting`、`selectedIds`
- [ ] 2.3 新增 `logEntryMatches()` private function
- [ ] 2.4 新增 `visibleLogs` derived state（替代 `sortedLogs`）
- [ ] 2.5 新增 `exportEntries` derived state
- [ ] 2.6 将 `UnifiedLogList` 参数从 `logs` → `visibleLogs`，并新增 `selecting`、`selectedIds`、`onSelectionChange` 参数

**验证**: 编译通过 `.\gradlew :app:compileDebugKotlin --no-daemon`

### Step 3: FilterChip 区域

- [ ] 3.1 在 `LazyColumn` 中 `RequestLoggingSwitchCard` 下方新增 FilterChip row item
  - `All` / `Text` / `Request` 三个 `FilterChip`
  - 点击切换 `logTypeFilter`
- [ ] 3.2 搜索栏: TopBar 新增搜索 icon，点击展开/收起 `OutlinedTextField`
  - 参照 `SettingProviderPage.kt` 搜索模式
  - `searchQuery` 绑定到 TextField value

**验证**: 编译通过，UI 过滤生效

### Step 4: 搜索高亮

- [ ] 4.1 将 `ChatList.kt` 的 `buildHighlightedText()` 复制到 `LogPage.kt` 为 private function（或提取为 shared util — check.jsonl 标记了此项，实现时可评估）
- [ ] 4.2 在 `RequestLogCard`（URL 文本）和 `TextLogCard`（message 文本）上应用搜索高亮
  - `searchQuery.isNotBlank()` 时用 `buildHighlightedText(text, searchQuery, highlightColor)` 替代纯 `Text(text)`

**验证**: 编译通过，搜索时高亮可见

### Step 5: 多选模式 UI

- [ ] 5.1 `RequestLogCard` / `TextLogCard` 外部包裹 `ListSelectableItem`
  - `enabled = selecting`
  - `key = log.id`，`selectedKeys = selectedIds`
  - `onSelectChange` toggle `selectedIds`
- [ ] 5.2 长按进入选择模式: 在 card Modifier 上加 `combinedClickable`
  - `onLongClick`: `selecting = true; selectedIds.add(log.id)`
  - `selecting = true` 时 `onClick` 抑制 detail sheet，仅 toggle select
- [ ] 5.3 底部 `HorizontalFloatingToolbar`:
  - Cancel: `selecting = false; selectedIds.clear()`
  - Select All: toggle 全选/全取消 `visibleLogs`
  - Confirm: 触发导出
- [ ] 5.4 选择模式下 TopBar actions 调整:
  - 隐藏 Search / Filter / Download / Delete
  - 或保持不变（toolbar 在底部互不冲突）—— 实现时评估

**验证**: 编译通过，长按进入选择、toggle、全选、confirm 导出

### Step 6: 导出管线改造

- [ ] 6.1 修改 `createDocumentLauncher` 回调:
  - 数据源: `Logging.getRecentLogs()` → `exportEntries`
  - 管线: `.map { it.redacted() }` → `.map { truncateLogEntry(it.redacted()) }`
  - import `me.rerere.common.android.truncateLogEntry`
- [ ] 6.2 选择模式 confirm 后触发 `createDocumentLauncher.launch(...)`
  - confirm 时: `selecting = false` 并记录 `pendingExportEntries`，或直接在 confirm 回调中调用 launcher
- [ ] 6.3 导出后清理: `selectedIds.clear()`

**验证**: 全量导出（无选择无过滤）→ JSON 含 `...[truncated]` 标记；选择导出 → 仅包含选中项

### Step 7: 字符串资源

- [ ] 7.1 `strings.xml` 新增:
  - `log_page_filter_all` / `log_page_filter_text` / `log_page_filter_request`
  - `log_page_search_hint` (搜索框 placeholder)
  - 复用已有: `common_select_all`、`chat_list_clear_selection`、`chat_list_confirm`（如已有则直接用）

**验证**: 编译通过，无 missing resource

---

## Validation Commands

```bash
# 单元测试
.\gradlew :common:test --no-daemon

# 编译验证
.\gradlew :app:compileDebugKotlin --no-daemon

# 完整 debug 构建
.\gradlew :app:assembleDebug --no-daemon
```

---

## Review Gates

| After Step | Gate |
|-----------|------|
| Step 1 | common 单元测试通过 |
| Step 2 | 编译通过，状态正确 |
| Step 3-4 | UI 过滤+搜索高亮可见（设备验证） |
| Step 5 | 多选 + toolbar 交互正常（设备验证） |
| Step 6 | 导出 JSON 内容正确（选中项 + truncated 标记） |
| Step 7 | 无 missing string resource |

---

## Rollback Points

| Step | Rollback |
|------|----------|
| Step 1 | revert `LogTruncation.kt` + 恢复 `LogsTool.kt` 内联版本 |
| Step 2 | 移除新增状态变量，恢复 `logs → UnifiedLogList` 原参数 |
| Step 3-4 | 删除 FilterChip/search UI 代码，`visibleLogs` 简化为 `logs` |
| Step 5 | 删除 `ListSelectableItem` 包裹、toolbar、`combinedClickable` |
| Step 6 | 导出回调 revert 为 `Logging.getRecentLogs().map { it.redacted() }` |
| Step 7 | 删除新增 string 资源 |

# Design: 日志导出精准选择与清洗 (v2)

## Overview

回退 v1 的 FilterChip / 搜索框 / 多选 toolbar，改为：
- **单条导出**：每条日志卡片加一个图标按钮，点击直接 SAF 导出该条（redacted）
- **全量导出 + 清洗面板**：TopBar 下载按钮改为先弹 BottomSheet，用户勾选清洗开关后导出全量；选项持久化到 Settings

---

## Architecture Decisions

### AD-1: 不引入 ViewModel，状态精简

LogPage 仅保留：
- `logs` snapshot（已有）
- `showExportSheet`（控制清洗面板）
- `settings` 中的 `logExportOptions`（持久化清洗选项）

砍掉：`logTypeFilter`、`searchQuery`、`selecting`、`selectedIds`、`visibleLogs`、`exportEntries`、底部 toolbar、`ListSelectableItem` 包裹、搜索高亮、`buildHighlightedText`、`logEntryMatches`。

### AD-2: 清洗选项持久化到 Settings

新增 `LogExportOptions` data class（默认全 false = 原样导出）：

```kotlin
@Serializable
data class LogExportOptions(
    val stripRequestBody: Boolean = false,
    val stripResponseHeaders: Boolean = false,
    val stripRequestHeaders: Boolean = false,
    val truncateLongFields: Boolean = false,
)
```

- 挂在 `Settings.logExportOptions`（默认 `LogExportOptions()`）
- `PreferencesStore` 增加对应的 `LOG_EXPORT_OPTIONS` key（JSON 序列化）
- LogPage 通过 `settingsStore.settingsFlow` 读取，BottomSheet 修改时调 `settingsStore.update { it.copy(logExportOptions = ...) }`

### AD-3: 清洗管线 = redacted → 用户选项 → 序列化

导出时：
```kotlin
val cleaned = entries
    .map { it.redacted() }                           // 始终 redact
    .map { applyLogExportOptions(it, options) }      // 按用户选项剥/截
```

`applyLogExportOptions` 放在 `common/.../LogExport.kt`（与 `LogTruncation.kt` 同包，可合并），处理：
- `stripRequestBody` → `RequestLog.copy(requestBody = null)`
- `stripResponseHeaders` → `RequestLog.copy(responseHeaders = emptyMap())`
- `stripRequestHeaders` → `RequestLog.copy(requestHeaders = emptyMap())`
- `truncateLongFields` → 现有 `truncateLogEntry` 逻辑

### AD-4: 单条导出 = 直接触发 SAF

每条日志卡片右上角加 `IconButton`（图标 `Download01` 或 `Share01`），点击：
```kotlin
val single = listOf(log.redacted())  // 单条不应用清洗选项，保持原貌
// 触发 SAF createDocumentLauncher，序列化 single
```

**为何单条不应用清洗选项**：单条导出本身就是"精准"，用户主动选了这条，再剥字段反而损失信息。若用户想清洗，用全量导出 + 开选项。

---

## Component Design

### 1. LogExportOptions data class（new）

位置：`app/.../data/model/LogExportOptions.kt`（或直接放 `common/` 与 LogEntry 同包，方便 `applyLogExportOptions` 引用）。

**选 `common/`**：与 `LogEntry`、`truncateLogEntry` 同包，避免循环依赖。

```kotlin
// common/src/main/java/me/rerere/common/android/LogExport.kt
@Serializable
data class LogExportOptions(
    val stripRequestBody: Boolean = false,
    val stripResponseHeaders: Boolean = false,
    val stripRequestHeaders: Boolean = false,
    val truncateLongFields: Boolean = false,
)

fun applyLogExportOptions(entry: LogEntry, options: LogExportOptions): LogEntry {
    if (entry !is LogEntry.RequestLog) return entry
    var result = entry
    if (options.stripRequestBody) result = result.copy(requestBody = null)
    if (options.stripResponseHeaders) result = result.copy(responseHeaders = emptyMap())
    if (options.stripRequestHeaders) result = result.copy(requestHeaders = emptyMap())
    if (options.truncateLongFields) result = truncateLogEntry(result)
    return result
}
```

`LogTruncation.kt` 内容合并到 `LogExport.kt`（删 `LogTruncation.kt`），单一文件管清洗管线。

### 2. Settings 扩展

`PreferencesStore.kt`：
- 加 key `LOG_EXPORT_OPTIONS`
- `Settings` 加字段 `val logExportOptions: LogExportOptions = LogExportOptions()`
- load/save 用 `JsonInstant.encodeToString` / `decodeFromString`

### 3. LogPage 改造（大幅精简）

**移除**：FilterChip row、搜索 OutlinedTextField、`ListSelectableItem` 包裹、底部 `HorizontalFloatingToolbar`、`combinedClickable` 长按、选择模式相关状态、`buildHighlightedText`、`logEntryMatches`、`LogTypeFilter` enum。

**保留**：原有 LazyColumn + `RequestLogCard` / `TextLogCard` + 详情 BottomSheet。

**新增**：
1. **`showExportSheet` 状态** — 控制 TopBar 下载点击后弹 BottomSheet
2. **每条卡片加导出 IconButton** — `RequestLogCard` / `TextLogCard` 增加 `onExport: () -> Unit` 参数；卡片内右上角放图标按钮
3. **导出 BottomSheet** — 4 个 Switch + 一个"导出"按钮
4. **导出管线** — 全量导出走 `applyLogExportOptions`，单条导出走 `redacted` only

### 4. 导出 BottomSheet UI

```kotlin
@Composable
private fun LogExportOptionsSheet(
    options: LogExportOptions,
    onOptionsChange: (LogExportOptions) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = spacedBy(8.dp)) {
        Text("Export options", style = titleMedium)
        SwitchRow("Strip request body", options.stripRequestBody) { onOptionsChange(options.copy(stripRequestBody = it)) }
        SwitchRow("Strip response headers", options.stripResponseHeaders) { onOptionsChange(options.copy(stripResponseHeaders = it)) }
        SwitchRow("Strip request headers", options.stripRequestHeaders) { onOptionsChange(options.copy(stripRequestHeaders = it)) }
        SwitchRow("Truncate long fields (>2048)", options.truncateLongFields) { onOptionsChange(options.copy(truncateLongFields = it)) }
        Button(onClick = onExport, modifier = fillMaxWidth) { Text("Export all") }
    }
}
```

### 5. TopBar 行为

- 点击下载 → `showExportSheet = true`（不直接 launch SAF）
- BottomSheet 内 "Export all" → `showExportSheet = false; launchExport()`
- 清空按钮不变

---

## Key Files & Changes

| File | Change | Size |
|------|--------|------|
| `common/.../LogExport.kt` | **New** — `LogExportOptions` + `applyLogExportOptions`（吸收原 `LogTruncation.kt`）| M |
| `common/.../LogTruncation.kt` | **Delete** — 合并到 LogExport.kt | — |
| `app/.../data/datastore/PreferencesStore.kt` | 加 `logExportOptions` 字段 + load/save | S |
| `app/.../ui/pages/log/LogPage.kt` | **大改** — 删筛选/搜索/多选，加单条导出按钮 + 导出 BottomSheet | L |
| `app/.../data/ai/tools/local/LogsTool.kt` | 改 import `truncateLogEntry` → `me.rerere.common.android.truncateLogEntry`（路径不变，文件改名但函数签名不变）| XS |
| `app/.../res/values/strings.xml` | 删 `log_page_filter_*` / `log_page_search_hint`，加 `log_page_export_*` 开关文案 | S |

---

## Compatibility & Rollback

- **默认行为不变**：`LogExportOptions()` 全 false → `applyLogExportOptions` 等价于 identity → 全量导出 = 全量 redacted（与 v1 之前一致）
- **redacted 始终生效**：清洗管线 `redacted → applyOptions`，redacted 永不跳过
- **单条导出原貌**：单条不应用清洗选项，保留诊断信息
- **Rollback**：revert LogPage 即可；LogExport.kt / Settings 字段可保留（无副作用）

---

## Tradeoffs

| Decision | Pro | Con |
|----------|-----|-----|
| 砍多选改单条导出 | 极简，符合"看哪条导哪条"的真实场景 | 失去批量能力（用户场景不需要）|
| 清洗选项持久化 | 用户下次不用重选 | 增 Settings 一个字段 |
| 单条不应用清洗 | 单条本身就是精准选择，剥字段损信息 | 全量与单条导出语义略不同（文档化即可）|
| 清洗在 common 层 | 与 LogEntry 同包，LogsTool 可复用 | — |

---

## Migration from v1

1. 删 `LogTypeFilter`、`logEntryMatches`、`buildHighlightedText`、`visibleLogs`、`exportEntries`、`latestExportEntries`
2. 删 `selecting`、`selectedIds`、`logTypeFilter`、`searchQuery` 状态
3. 删 FilterChip row item、搜索框 item
4. 删 `ListSelectableItem` 包裹、底部 `HorizontalFloatingToolbar`、`combinedClickable`
5. 删 `RequestLogCard` / `TextLogCard` 的 `searchQuery` / `highlightColor` / `onLongClick` 参数
6. 新增 `LogExportOptions` + `applyLogExportOptions`
7. Settings 加 `logExportOptions`
8. LogPage 加 `showExportSheet`、`LogExportOptionsSheet`、卡片单条导出按钮

# Design: 记忆表格文档全屏表格编辑

## Boundaries

### 涉及模块与文件

| 层级 | 文件 | 职责 |
|------|------|------|
| 导航 | `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 新增 `Screen.AssistantMemoryTableDocumentEditor`，注册 `entry` 映射。 |
| UI 入口 | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt` | 移除旧 `AlertDialog` 编辑；点击文档/新建文档改为 `navController.navigate(...)`。 |
| 新页面 | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryTableDocumentEditorPage.kt` | 全屏编辑器 UI：TopAppBar、TabRow、表格/JSON 视图、scope 切换、保存/返回确认。 |
| 业务模型 | `app/src/main/java/me/rerere/rikkahub/data/model/MemoryTable.kt` | 默认 schema；本次不改动模型，但解析逻辑依赖其结构。 |
| 持久化 | `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryTableRepository.kt` | 复用 `upsertDocument`，不改动。 |
| VM | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt` | 复用 `upsertMemoryTableDocument` / `deleteMemoryTableDocument`，不改动。 |
| 资源 | `app/src/main/res/values/strings.xml`、`values-zh/strings.xml` | 新增/恢复页面标题、模式名称、按钮、空状态、未保存提示等。 |

## Contracts

### Navigation Key

```kotlin
@Serializable
data class AssistantMemoryTableDocumentEditor(
    val documentId: String? = null,
    val templateId: String,
    val assistantId: String,
    val scopeType: MemoryTableScopeType = MemoryTableScopeType.ASSISTANT,
) : Screen
```

- `documentId == null` 表示新建文档。
- `templateId` 必传，用于解析 schema；若本地找不到模板则使用 `DEFAULT_MEMORY_TABLE_SCHEMA_JSON`。
- `assistantId` 用于 ASSISTANT scope 回退及 scope 切换时回填。
- `scopeType` 作为新建文档的初始作用域；编辑已有文档时以文档本身为准。

### 内部状态结构

页面内部维护以下状态：

- `document: MemoryTableDocument` — 当前文档元数据（scope/templateId 等）。
- `mode: EditorMode { Table, Json }` — 当前 Tab。
- `payloadJson: String` — JSON 草稿，单一数据源。
- `tables: List<MemoryTableEditorTable>` — 表格模式从 `payloadJson + schema` 解析出的结构化数据。
- `editorError: String?` — 当前校验/解析错误提示。
- `hasUnsavedChanges: Boolean` — 用于返回确认。

### 解析与序列化

复用旧实现逻辑（可迁移为独立私有函数或页面内 private function）：

- `parseMemoryTableSchema(schemaJson: String): List<MemoryTableSchemaTable>`
- `parseMemoryTableEditorTables(schemaJson, payloadJson): Result<List<MemoryTableEditorTable>>`
- `serializeMemoryTablePayload(originalPayloadJson, tables): Result<String>`
- `validateMemoryTablePayloadJson(payloadJson): Result<Unit>`

边界约定：
- schema 不含有效表时，表格模式显示空状态文案。
- payload 不是 Object 时切换表格模式失败并提示。
- 单元格内容统一按字符串处理；非字符串 JSON 值进入编辑时调用 `jsonElementToCellText` 转为字符串。

## Data Flow

```
AssistantMemoryPage
  ├─ 点击文档/新建文档
  └─ navigate(Screen.AssistantMemoryTableDocumentEditor(...))

AssistantMemoryTableDocumentEditorPage
  ├─ LaunchedEffect: 从 VM / Repository 拉取 template & document（或构造空文档）
  ├─ 初始化 payloadJson / tables / hasUnsavedChanges = false
  ├─ 单元格编辑 / 新增行 / 删除行
  │   └─ 更新 tables → serialize → 更新 payloadJson → hasUnsavedChanges = true
  ├─ JSON 编辑
  │   └─ 更新 payloadJson → validate → hasUnsavedChanges = true
  ├─ Tab 切换 Table ← → Json
  │   └─ Table→Json: 直接显示 payloadJson
  │   └─ Json→Table: parseMemoryTableEditorTables(payloadJson) 成功才切换
  ├─ 保存
  │   └─ validate/serialize 成功 → vm.upsertMemoryTableDocument(document.copy(payloadJson)) → popBackStack
  └─ 返回
      └─ hasUnsavedChanges → 显示“放弃/保存”Dialog
```

## UI Layout

### TopAppBar

- 左侧：返回按钮。
- 标题：文档作用域 + revision 或“新建表格文档”。
- 右侧：保存按钮（仅在有效时启用）。

### TabRow

- Tab 1：表格（默认）。
- Tab 2：JSON。

### 表格模式主体

```
LazyColumn / Column(verticalScroll)
  ├─ 错误提示条（若有）
  ├─ Scope 切换行（Switch + 说明）
  ├─ 空状态文案（schema 无表时）
  └─ for each table:
       ├─ 表名标题
       ├─ HorizontalScroll
       │   └─ Column
       │       ├─ Header Row（列名）
       │       └─ for each row: TextField + Delete Icon
       └─ “新增行” TextButton
```

### JSON 模式主体

- 一个占据剩余高度的 `TextField`，显示 `payloadJson`。

### 返回确认 Dialog

- 标题：`未保存的修改`
- 正文：`离开此页面将丢失未保存的修改。`
- 按钮：`放弃`、`保存`、`取消`（可选）。

## Compatibility & Rollback

- 路由 key 新增字段不影响旧 `Screen` 子类。
- 持久化层不变，保存失败不会破坏现有数据。
- 若新页面有严重问题，可通过恢复 `AssistantMemoryPage` 的 `AlertDialog` 快速回滚，但保留导航入口。

## Open Decisions

- 是否将解析/序列化逻辑提取到 `data/model/MemoryTable.kt` 作为可测试的 public helper？
  - 推荐：暂不提取，先作为新页面内部 private function 保持最小改动；如需单元测试再迁移。

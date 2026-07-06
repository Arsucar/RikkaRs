# Implement: 优化记忆表格编辑器 UI 与新增列

## Execution Checklist

### Phase 1: 用 DataTable 重构表格渲染

1. [ ] 在 `AssistantMemoryTableDocumentEditorPage.kt` 中引入 `me.rerere.rikkahub.ui.components.table.DataTable`。
2. [ ] 重构 `MemoryTableEditableTable`：
   - 接收 `table: MemoryTableEditorTable`、`onChange`、`onAddColumn: (columnName: String, columnType: String) -> Unit`、`onDeleteColumn: (columnName: String) -> Unit`。
   - `headers`：每个列名用 `Text`；最后一个 header 嵌入“+ 列”`TextButton` 或 `IconButton`。
   - `rows`：每个单元格放 `OutlinedTextField`（透明背景/无边框可后续调优，先保持可用）。
   - 设置 `columnMinWidths` 与 `columnMaxWidths`：`string` 最小 80dp、最大 160dp；`text` 最小 120dp、最大 280dp。
   - 设置 `cellPadding = 8.dp`、`stretchToFillWidth = true`。
3. [ ] 删除旧的手动 `Row + horizontalScroll` 实现。

### Phase 2: 新增列 Dialog

4. [ ] 在 `AssistantMemoryTableDocumentEditorPage.kt` 中添加 `AddColumnDialog` composable：
   - 列名 `OutlinedTextField`。
   - 类型选择：`string` / `text`（用 `Row` + `RadioButton` 或两个 `FilterChip`）。
   - 确认/取消按钮。
5. [ ] 实现 `addColumnToTable(tableIndex, columnName, columnType)`：
   - 校验列名非空且不与现有列重名。
   - 更新 `tableState` 中对应表的 `columns` 和每一行 `rows`。
   - 序列化到 `payloadJson`。
6. [ ] 实现 `updateTemplateSchemaForColumn(tableIndex, add/remove, column)`：
   - 解析模板 `schemaJson`。
   - 修改对应 `table.columns`。
   - 调用 `vm.upsertMemoryTableTemplate(template.copy(schemaJson = updated))`。

### Phase 3: 删除列

7. [ ] 在每个列标题单元格内添加删除入口（例如列标题右侧的小 `IconButton` 或长按菜单）。
8. [ ] 实现 `deleteColumnFromTable(tableIndex, columnName)`：
   - 弹出 `RikkaConfirmDialog` 确认。
   - 更新 `tableState` 中对应表的 `columns` 和每一行 `rows`。
   - 更新模板 schema。
   - 序列化到 `payloadJson`。

### Phase 4: 字符串资源

9. [ ] 新增/更新中/英字符串：
   - `assistant_page_memory_table_add_column`
   - `assistant_page_memory_table_column_name`
   - `assistant_page_memory_table_column_type`
   - `assistant_page_memory_table_column_type_string`
   - `assistant_page_memory_table_column_type_text`
   - `assistant_page_memory_table_delete_column`
   - `assistant_page_memory_table_delete_column_confirm`
   - `assistant_page_memory_table_column_exists`

### Phase 5: 验证

10. [ ] 运行 `.\gradlew --no-daemon :app:compileDebugKotlin`。
11. [ ] 修复编译错误。
12. [ ] 连接设备，运行 `.\gradlew --no-daemon :app:installDebug`。
13. [ ] 在设备上验证：
    - 表格边框、行高一致。
    - 单元格编辑正常。
    - 新增列后模板 schema 更新，表格出现新列。
    - 删除列后 schema 和 payload 清理。

## Review Gates

- [ ] 不修改 `DataTable.kt` 内部实现。
- [ ] 所有新增字符串均来自资源文件。
- [ ] 新增/删除列时同步更新 `tableState`、`template.schemaJson`、`payloadJson` 三处状态。
- [ ] 列名重复时给用户明确提示。

## Rollback Point

- 若 `DataTable` 接入后存在严重交互问题（如焦点丢失），可回滚到手动 `Row` 实现，但保留新增/删除列功能。

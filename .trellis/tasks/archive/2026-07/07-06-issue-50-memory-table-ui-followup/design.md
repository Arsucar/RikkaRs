# Design: 优化记忆表格编辑器 UI 与新增列

## Boundaries

| 文件 | 改动 |
|------|------|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryTableDocumentEditorPage.kt` | 重构 `MemoryTableEditableTable` 使用 `DataTable`；新增列/删除列 Dialog 与逻辑。 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/table/DataTable.kt` | 不改动；直接复用。 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt` | 复用 `upsertMemoryTableTemplate` 保存 schema 变更。 |
| `app/src/main/res/values/strings.xml`、`values-zh/strings.xml` | 新增列相关文案。 |

## Contracts

### `DataTable` 适配

`DataTable` API：
```kotlin
fun DataTable(
    headers: List<@Composable () -> Unit>,
    rows: List<List<@Composable () -> Unit>>,
    modifier: Modifier = Modifier,
    cellPadding: Dp = 4.dp,
    cellBorder: BorderStroke? = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    headerBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    zebraStriping: Boolean = false,
    columnMinWidths: List<Dp> = emptyList(),
    columnMaxWidths: List<Dp> = emptyList(),
    cellAlignment: Alignment = Alignment.CenterStart,
    outerBorder: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape: Shape = MaterialTheme.shapes.small,
    stretchToFillWidth: Boolean = true,
)
```

适配方式：
- `headers`：每个列标题为 `@Composable { Text(column.name) }`，最后一个 header 单元格可嵌入“+ 列”按钮。
- `rows`：每行是 `List<@Composable () -> Unit>`，每个单元格内放 `OutlinedTextField` 或 `Text`。
- `columnMinWidths` / `columnMaxWidths`：根据列类型设置，`text` 类型列最大宽度更大；或统一最小宽度保证可点击。

### 新增列

1. 用户点击表头末尾“+ 列”。
2. 弹出 `AlertDialog`：输入列名（`TextField`），选择类型（`string` / `text`，用 Row + RadioButton 或 ExposedDropdownMenuBox）。
3. 点击确认：
   - 校验列名非空、不重复。
   - 解析模板 `schemaJson` 为 `JsonObject`。
   - 在对应 `table.columns` 末尾添加 `{ "name": "...", "type": "..." }`。
   - 调用 `vm.upsertMemoryTableTemplate(template.copy(schemaJson = updatedSchema))`。
   - 在当前 `tableState` 对应表的所有行 `Map<String, String>` 中追加新列键值为空字符串。
   - 序列化 `tableState` 到 `payloadJson`。

### 删除列

1. 用户在列标题上长按或点击菜单图标，选择“删除列”。
2. 确认 Dialog。
3. 确认后：
   - 从模板 schema 对应 `table.columns` 移除该列。
   - 调用 `vm.upsertMemoryTableTemplate(...)`。
   - 从当前 `tableState` 对应表的所有行 Map 中移除该键。
   - 序列化到 `payloadJson`。

## Data Flow

```
MemoryTableEditableTable
  ├─ headers: 列名 Text + 末尾“+列”按钮
  ├─ rows: OutlinedTextField（单元格编辑）
  └─ onCellChange / onAddColumn / onDeleteColumn

onCellChange
  └─ table.updateCell(...) → updateTables(...) → serialize → payloadJson

onAddColumn
  └─ show AddColumnDialog → validate → update template.schemaJson
      → upsertMemoryTableTemplate → update tableState rows
      → serialize → payloadJson

onDeleteColumn
  └─ show ConfirmDialog → update template.schemaJson
      → upsertMemoryTableTemplate → update tableState rows
      → serialize → payloadJson
```

## Compatibility

- `DataTable` 本身是纯展示组件，无状态，接入不需要改其内部。
- 新增/删除列会修改模板 schema，影响所有同模板文档；这是用户已确认的行为。
- 若列类型不是 `string`/`text`，统一按 `string` 处理编辑高度。

## Open Decisions

- “新增列”按钮位置：推荐放在表头行最后一个单元格内（视觉上属于表头），或放在表名标题右侧。本实现采用表头末尾单元格内嵌按钮。
- 删除列入口：推荐在列标题单元格内放一个小菜单 IconButton（`MoreVertical` / `Delete`）。

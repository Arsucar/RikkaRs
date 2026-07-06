# Issue #50 跟进：优化记忆表格编辑器 UI 与新增列

## Goal

修复当前记忆表格文档编辑器中表格行高/列宽不一致、缺乏表格线的问题，复用项目已有的 `DataTable` 组件实现类 Markdown 表格样式；同时支持在编辑器内新增列（修改模板 schema）。

## Requirements

### UI 重构

1. **复用 `DataTable`**：将 `MemoryTableEditableTable` 从手动 `Row + OutlinedTextField` 改为使用 `me.rerere.rikkahub.ui.components.table.DataTable`。
2. **样式目标**：
   - 统一行高（同一行所有单元格等高）。
   - 单元格带边框，表头有背景色，整体外观接近 Markdown 表格。
   - 支持横向滚动，列宽根据内容自适应，并可在内容较窄时拉伸铺满视口。
   - 长文本 `text` 类型单元格自动换行并撑高整行。
3. **行内编辑**：单元格内仍使用 `OutlinedTextField`（无边框或透明背景更协调），保持即点即改。

### 新增列

4. **入口**：每张表表头行末尾提供“+ 列”按钮，或在表头右上角菜单中提供“新增列”。
5. **新增列 Dialog**：输入列名、选择类型（`string` / `text`）。
6. **保存位置**：新增列写入当前模板 `schemaJson` 的对应 `table.columns` 末尾，通过 `AssistantDetailVM.upsertMemoryTableTemplate` 持久化。
7. **数据一致性**：新增列后，当前文档对应表的所有行自动追加该列的空字段；保存时 `payloadJson` 包含新列。

### 删除列（附带）

8. 每个列标题提供菜单或长按，支持“删除列”。
9. 删除列时从模板 schema 中移除该列，并从当前文档对应表的所有行中移除该字段（可选：仅 schema 移除，行中保留但不渲染；推荐一并清理）。
10. 删除前需确认 Dialog，避免误删。

### 保留功能

11. 表格/JSON Tab 切换、scope 切换、保存、未保存返回确认保持现有行为不变。

## Acceptance Criteria

- [ ] `MemoryTableEditableTable` 使用 `DataTable` 渲染，行高统一、带边框、横向滚动正常。
- [ ] 点击单元格可直接编辑，`string` 单行、`text` 多行。
- [ ] 点击“新增列”弹出 Dialog，输入列名/类型后保存，模板 schema 更新，当前表格立即出现新列。
- [ ] 新增列后保存文档，`payloadJson` 包含新列字段。
- [ ] 删除列时提示确认，删除后 schema 和当前文档 payload 同步清理。
- [ ] 编译通过：`.\gradlew --no-daemon :app:compileDebugKotlin`。
- [ ] 安装到设备并验证：`.\gradlew --no-daemon :app:installDebug`。

## Notes

- 用户已确认：新增列修改模板 schema（影响所有使用该模板的文档）。
- 现有 `DataTable` 位于 `app/src/main/java/me/rerere/rikkahub/ui/components/table/DataTable.kt`。

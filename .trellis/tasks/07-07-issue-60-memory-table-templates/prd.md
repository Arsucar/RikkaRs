# Issue 60 memory table template tool actions

## Goal

给 `memory_table_tool` 增加 `list_templates` 与 `create_template` 两个操作，让 AI 能在对话中查询已有模板并创建新模板，避免使用不存在的 `template_id` 产生孤儿文档。

来源：GitHub issue #60。

## Requirements

- 在 `memory_table_tool` 的 `action` 枚举中新增 `list_templates` 和 `create_template`。
- `list_templates`：返回所有模板的 `id` / `name` / `description` / `schemaJson`，供 AI 选择合适的 `template_id`。
- `create_template`：接收 `name`（必填）、`description`（可选）、`schema_json`（可选，缺省用 `DEFAULT_MEMORY_TABLE_SCHEMA_JSON`），创建模板并返回完整模板（含新 `id`）。
- 复用 `MemoryTableRepository.getTemplates()` / `upsertTemplate()`，不新增数据源。
- 通过给 `buildMemoryTableTools` / `buildMemoryTableToolsIfEnabled` 新增 `readTemplates` / `upsertTemplate` 回调注入仓库能力，在 `ChatService` 装配处接线。
- 工具描述更新，说明新操作及推荐流程（先 `list_templates` → 无则 `create_template` → `upsert_rows`）。

## Constraints

- 不改动数据库 schema、DAO、Entity。
- `create_template` 走 `MemoryTableRepository.upsertTemplate` 的规范化逻辑（空 name/schema 有默认值、生成 id、时间戳）。
- 保持既有 `read` / `upsert_rows` / `patch_rows` / `delete_rows` 行为不变。

## Acceptance Criteria

- [ ] `list_templates` 返回当前所有模板的结构化列表。
- [ ] `create_template` 创建模板并返回其 `id`，随后 `upsert_rows` 可引用该 id。
- [ ] `create_template` 未传 `name` 时报错要求提供 name。
- [ ] `:app:compileDebugKotlin` 编译通过。

## Notes

- 参考文件：`data/ai/tools/MemoryTableTools.kt`、`data/repository/MemoryTableRepository.kt`、`service/ChatService.kt`（~L728 装配区）。

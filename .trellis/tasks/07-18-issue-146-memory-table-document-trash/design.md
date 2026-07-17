# Issue #146：记忆表文档软删除技术设计

## Schema and migration

- `memory_table_documents` 新增 nullable `deleted_at INTEGER`、`deleted_by TEXT`，DB v40→v41；为 `deleted_at` 建 index 便于 active/trash 查询。
- Entity/model/mapping 保持字段同名语义。旧 row 的两个字段为 null。
- Schema 41 由 Room export 生成；migration test 从 schema 40 建库并验证旧 payload/revision/snapshot。

## DAO query boundary

- 默认 active API 的 SQL 明确包含 `deleted_at IS NULL`：全量、scope、effective、单文档/effective 单文档。
- 新增 including-deleted 单行读取、trash flow/list、soft-delete conditional update、restore conditional update。
- `softDelete` 仅当 `deleted_at IS NULL` 更新并返回 affected rows；重复删除返回 0，由 Repository 映射为 already deleted。
- `restore` 仅当 `deleted_at IS NOT NULL` 清空字段；不改 `updated_at/revision/payload`。
- 现有 `deleteDocumentAndSnapshots` 重命名/语义收窄为 purge，template/assistant cascade 继续调用硬清路径。

## Repository contracts

- Actor-aware `softDeleteDocument/restoreDocument/purgeDocument` 先用 including-deleted 读取并复用当前 scope 授权。
- upsert/rollback/import overwrite 等以 ID 写入的入口在 including-deleted row 存在且 deleted 时抛稳定错误。
- import 冲突规划使用 including-deleted ID 集，不能把 trash ID 当成可新建 ID。
- restore 不生成 snapshot；purge 在 Room transaction 内先删 snapshots 后删 document。
- Assistant cleanup 增加按 Conversation→assistant 联查的 conversation-scope documents，统一清 active/trash snapshots/document。

## Tool and runtime flow

- `delete_document` confirm 守卫不变，回调从硬删改为 soft delete，并返回已移入回收站/已在回收站的结构化结果。
- read/query/injection 继续走默认 effective Repository API，DAO 过滤后无需在 transformer 重复判断。
- apply/update/rollback 入口对 deleted target 失败；不允许 tool restore/purge。

## UI flow

- Assistant Memory 顶部增加回收站入口，进入独立 trash 内容页/route，列表显示 template、scope、deletedAt、deletedBy 与 document ID fallback。
- trash 页面使用显式 UiState 区分 Loading/Error/Empty/Success；支持 restore 与 purge 二次确认。
- 主列表和 Conversation drawer 的删除确认改成“移入回收站，可恢复”。
- template 删除确认补充“active 和回收站文档都会永久删除”。
- UI actor 固定 `user_ui`；assistant cascade 固定 `assistant_cascade`。

## Compatibility and rollback

- 两列 nullable，不改变现有 JSON bundle 格式；export 默认只导出 active 文档，import 用 including-deleted ID 做冲突保护。
- 若 UI 出现问题，可保留 schema/Repository 并回退 trash route；不能回退到用户删除直接 hard purge。
- #147 基于 v41 实现，提交前必须复查 target `deleted_at IS NULL`，避免同步写入 trash 文档。

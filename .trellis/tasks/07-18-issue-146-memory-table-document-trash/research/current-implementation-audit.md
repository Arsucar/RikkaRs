# 当前实现审计摘要

- 当前 DB v40，`MemoryTableDocumentEntity` / model 无删除字段；DAO 全量、scope、effective、single 查询均无 active 过滤。
- 当前 Repository 删除调用 `deleteDocumentAndSnapshots`，先删 snapshots 再删 document；UI 与 `memory_table_tool.delete_document` 都接到硬删。
- tool 已有 `confirm_document_id` 精确匹配守卫，可保留并仅替换副作用语义。
- snapshot/rollback 已完整存在，soft delete 只需保留 document row 即可保留 revision chain。
- DATABASE backup 复制 DB/WAL/SHM，不需要额外序列化字段；需要 migration 与往返验证。
- assistant cleanup 当前不能覆盖属于该 Assistant Conversation、但使用 GLOBAL template 的 conversation-scope document，#146 必须修正 active/trash 两类。
- import conflict planning 若改用默认 active list，会把 trash ID 误判为不存在；必须使用 including-deleted IDs。

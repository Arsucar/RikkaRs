# Issue #146：执行计划

- [ ] 更新 Entity/model/DB version/auto migration，生成 schema 41 与 v40→v41 migration test。
- [ ] 重构 MemoryTableDAO active/including-deleted/trash/soft-delete/restore/purge 查询，并修正 assistant cascade 对 conversation-scope global-template 文档的清理。
- [ ] 更新 MemoryTableRepository mapping、授权、deleted-write reject、import ID 冲突、soft/restore/purge API 与稳定错误。
- [ ] 将 MemoryTableTools `delete_document` 和 ChatService wiring 改为软删，保留 confirm；补 read/query/apply/rollback deleted target 回归。
- [ ] 增加 Assistant Memory trash route/UiState/list/restore/purge，更新主列表、drawer、template cascade 文案与反馈。
- [ ] 使用 locale-tui 流程更新所有现有 locale 字符串。
- [ ] 补 DAO/Repository/tool/migration/UI state 测试；验证 snapshot/revision 保留、purge、重复删除、assistant cleanup。
- [ ] 运行资源处理、聚焦 JVM/Room 测试、`:app:compileDebugKotlin`、androidTest 编译与设备安装；设备不可用时如实降级。
- [ ] 更新 app memory spec/CHANGELOG，提交推送，发布并复核中英文 Issue 评论后关闭 #146。

## Risk and rollback points

- #147 的 migration 必须从 v41 开始，禁止并行创建另一个 40→41。
- 默认 query 漏一个 `deleted_at IS NULL` 会让 trash 泄漏到注入/tool；including-deleted 滥用会让普通 UI 重新显示 trash。
- `@Insert(REPLACE)` 会复活 deleted ID，所有 ID-aware write/import 路径必须在 Repository 层集中拒绝。
- purge/template/assistant cascade 必须清 snapshots；soft/restore 不得清或新增 snapshot。

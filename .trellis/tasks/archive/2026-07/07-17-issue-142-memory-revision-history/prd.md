# Issue #142 结构化记忆 revision 历史与回滚

## Goal

让现有结构化记忆快照真正可用：用户能查看文档历史、比较快照，并在确认后回滚到指定历史版本。

## Confirmed Facts

- `MemoryTableSnapshotEntity`、`MemoryTableSnapshotDAO`、`MemoryTableRepository.getDocumentSnapshots/rollbackDocument` 已存在，但生产 Koin 构造 `MemoryTableRepository` 时漏传 snapshot DAO。
- 当前快照模型可靠提供的字段只有 document、revision、payload 和时间；不能伪造 operation/source/change summary。
- 现有 UI 只显示当前 revision，没有历史路由或回滚入口；项目已有 `DiffView`、统一 diff 工具和确认对话框可复用。

## Requirements

- 正确注入 snapshot DAO，并让缺失依赖不再静默宣称“有历史”。
- 为记忆文档提供历史列表和快照详情，展示 revision、时间、payload 差异及 Loading/Empty/Error 状态。
- 回滚前二次确认；回滚以新 revision 写入，目标旧快照不可变，成功后刷新当前文档。
- 历史查询和回滚必须沿用现有 assistant/document 访问边界；不存在或无权访问的文档不得读写快照。
- 删除模板时同步清理所属文档快照，避免孤儿 revision 数据。

## Acceptance Criteria

- [ ] 生产路径更新文档后可读到快照历史，按 revision 倒序展示。
- [ ] 可查看指定快照与相邻/当前 payload 的差异；空历史和读取失败有明确 UI。
- [ ] 回滚成功后当前文档 payload 恢复、revision 增长，原目标快照仍可再次查看。
- [ ] 回滚失败、无效 revision、越权访问不会改写当前文档，并给出错误反馈。
- [ ] 模板删除不会留下其文档对应的 `memory_table_snapshots` 行；无关文档快照保留。
- [ ] Repository/DAO/UI 回归测试覆盖生产接线、历史、回滚和删除清理。

## Out of Scope

- 远端 DTO/同步协议、分页、完整审计日志、软删除回收站、并发 ETag 和外部 deep link。

# 处理 Issue #146 记忆表文档软删除与回收站

## Goal

为结构化记忆表 document 引入可恢复的软删除、回收站和粗粒度删除审计，避免 UI 或 AI 误删整表后 payload、revision 与 snapshot 永久丢失，同时保持默认读取、查询和注入路径看不到已删除文档。

## Requirements

- `MemoryTableDocumentEntity` / model 增加 nullable `deletedAt`、`deletedBy`，数据库从 v40 迁移到 v41；旧行迁移后保持 active。
- 仅 document 软删除；template 删除继续硬级联，并明确警告其下 active/trash 文档不可从回收站恢复。
- 默认 document list、scope/effective get、read/query、注入都排除 `deletedAt != null`；内部授权/冲突/清理路径可显式读取 including-deleted。
- UI/tool 删除调用 `softDeleteDocument(id, actor)`；保留 payload、revision、follow-source 和全部 snapshots。重复软删固定返回 already-deleted/no-op，不修改原审计时间。
- restore 仅清空 `deletedAt/deletedBy`，不改 payload、revision、updatedAt，不创建 snapshot。
- purge 才在一个事务内删除 document 与 snapshots；V1 purge 只暴露给 UI，不提供永久删除 tool。
- 任何 upsert/apply/write 对已软删 ID 必须明确 reject，禁止通过 REPLACE 意外复活。
- `memory_table_tool.delete_document` 保留 `confirm_document_id` 精确匹配守卫，正确确认后 actor=`memory_table_tool` 软删；错误确认零写入。
- Assistant Memory 增回收站入口、Loading/Empty/Error/Success 状态、恢复与永久删除二次确认；Conversation drawer 删除文案改为可恢复的移入回收站。
- `removeAssistant`/`deleteDataOwnedByAssistant` 硬清该助手相关 active/trash 文档及 snapshots，包括属于该助手 Conversation 但使用 GLOBAL template 的文档。
- DATABASE 文件备份/恢复保留软删字段；无需建立跨端 trash 协议或 TTL。
- 所有新增 UI 字符串使用资源并提供真实简体中文翻译及现有 locale 更新。

## Acceptance Criteria

- [ ] v40→v41 migration 保留旧文档、payload、revision 与 snapshot，新增删除字段为 null。
- [ ] UI/tool 软删后 active list、read/query 与注入不可见，trash list 可见且包含删除时间/来源。
- [ ] restore 后 payload/revision/snapshots 与软删前一致，revision history 可继续打开。
- [ ] purge 后 document 与 snapshots 均不存在且不可恢复。
- [ ] 已软删 ID 的 upsert/apply_ops/rollback/write 被稳定错误拒绝，不会复活。
- [ ] `delete_document` 正确 confirm 软删，错误 confirm 与重复软删行为有回归测试。
- [ ] Assistant Memory 回收站支持加载、空态、恢复、永久删除和明确反馈；Conversation drawer 不再显示“不可撤销”软删文案。
- [ ] template 硬删仍清 active/trash 下属数据；助手删除硬清所有相关 scope 文档和 snapshots。
- [ ] DATABASE 备份恢复后 soft-deleted row 仍在 trash，active row 仍 active，或记录可核验的文件级往返证明。
- [ ] Repository/tool/DAO/UI 相关测试、资源处理、app 编译与可用设备安装通过，无法执行项如实记录。

## Confirmed Facts

- 当前 DB version 为 40；document/model 没有删除字段，默认查询均未过滤。
- 当前 `deleteDocumentAndSnapshots` 会硬删 snapshots 和 document，UI 与 tool 都接到该路径。
- snapshot/rollback 已存在并按 document 保留最多 20 条；软删除无需复制 payload。
- DATABASE backup 直接复制 DB/WAL/SHM，新增列会天然进入备份。
- 当前 assistant cleanup 漏掉“属于该助手 Conversation、但使用 GLOBAL template”的 conversation-scope document。

## Out of Scope

- template 软删除、行级回收站、自动 30 天 TTL、企业审计流水、跨端独立 trash 同步。
- tool 永久删除、自动清空回收站、follow-source 软删级联。

## Open Questions

- 无阻塞问题。Document 没有独立名称，V1 回收站显示 template 名 + scope + document ID fallback，不额外扩 name schema。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.

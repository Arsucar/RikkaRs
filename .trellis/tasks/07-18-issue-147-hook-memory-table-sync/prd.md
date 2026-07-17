# 处理 Issue #147 通用 Hook 后处理与记忆表同步

## Goal

把现有 Assistant Hook 从标签专用后处理扩展为 action-specific 通用框架，并新增受限的 `SYNC_MEMORY_TABLE` Action：在 logical turn 最终回复成功后冻结有界上下文，让模型只生成结构化 operations，再由本地校验与单事务 CAS 安全写入选定记忆表。

## Requirements

- 保留现有 final-success、logical-turn exactly-once、claim/lease、timeout、顺序执行和标签 Hook 兼容；不得建立平行 auto-sync Job。
- 新增 `HookActionConfig.SyncMemoryTable` / `HookActionType.SYNC_MEMORY_TABLE`，configuration hash 覆盖 target、scope、context、frequency、model/prompt 等完整配置。
- 将 Frozen request、parser、Action context/result、execution history 从 tag-specific 泛化为 action-specific；旧 `add_conversation_tag` JSON/DB 记录继续解码和展示。
- 自动执行只读取 active branch 的有界 user/assistant 窗口，绑定 logicalTurnId、截止 messageId、targetDocumentId 和 baseRevision；默认不包含 system/tool raw payload、hidden/旧分支或 pending tool。
- 后处理模型没有任意聊天工具权限，只能返回严格 JSON 的 decision/baseRevision/operations/reason；target/scope/table/column/type/PK/updatePolicy 全由本地重新校验。
- 多 operations、payload/revision/snapshot、幂等 cursor 和 execution result/diff 必须在同一 Room transaction 全成或全败。
- expected revision 使用 DAO CAS；冲突返回稳定 `REVISION_CONFLICT`，禁止 last-write-wins。提交前同时要求 #146 target active/non-deleted。
- 成功幂等键至少包含 hookId/configVersion/targetDocumentId/logicalTurnId 或截止 messageId；重放只 SKIPPED。失败 retry 仅重试未提交 attempt。
- 全局、Assistant、auto-sync、Hook enabled、scope permission 任一门控关闭时零模型调用、零写入。GLOBAL 写入 V1 禁用或要求明确高风险确认。
- UI 扩展现有 Hooks editor/list/history，支持 Action 选择、目标/上下文/频率配置、手动 preview/run、冲突/失败 retry 与脱敏审计；不落完整 prompt/消息/raw response。
- `memoryTableAutoSyncEnabled` V1 作为 memory-sync Hook 的全局 auto permission，默认 false；UI 从灰色占位改为可配置入口，不自动创建会产生费用的 enabled Hook。

## Acceptance Criteria

- [ ] Dispatcher/Registry 不按具体 Action 硬编码，标签 Hook 序列化、执行、历史和顺序回归通过。
- [ ] final-success/pending tool/重复 callback/旧分支/取消失败门禁保证每 logical turn/target 至多一次成功写入。
- [ ] Frozen input 的消息数、角色、分支和字符数有界并可测，不含未授权 system/tool/hidden 内容。
- [ ] 严格 parser 与本地 validator 拒绝任意 target/scope/tool/未知 table/column/type/PK/updatePolicy 违规，零写入。
- [ ] 多 op 中途失败完全回滚；同 revision 并发仅一方提交；payload/revision/snapshot/cursor/history/diff 同事务一致。
- [ ] #146 soft-deleted target、scope/permission 变化、lease 失效、timeout/cancel 均不写。
- [ ] 自动门控关闭时零模型调用；频率/消息/token 上限可配置并产生可审计 SKIPPED。
- [ ] 手动 preview 不写库，apply 前重查 revision；失败 retry 不重复已提交 operations。
- [ ] history 展示 action、target/scope、base/result revision、op 摘要、diff、状态、耗时与 retry relation，正文/raw response 不落库。
- [ ] DB v41→v42 migration、JVM/Room/Hook/UI 测试、编译与可用设备安装通过，或如实记录环境限制。

## Confirmed Facts

- Hook Registry/Dispatcher 已有 handler map、lease、timeout 与 final-success exactly-once；但 request/result/history/ChatService cast/UI 均被标签语义贯穿。
- MemoryTable 已有 schema/updatePolicy、revision/snapshot 和 apply_ops 内存构造，但没有 expectedRevision CAS，也不能与 execution completion/cursor 同事务。
- #146 先把 DB 升到 v41 并引入 soft-delete；本任务固定使用 v41→v42。

## Out of Scope

- 给 Hook 模型开放任意工具、建立独立后台 Job、自动启用会产生费用的 Hook。
- V1 自动创建 document、自动 GLOBAL 写入、复杂每日费用计费、跨设备执行队列。

## Open Questions

- 无阻塞问题。V1 支持显式选择现有 ASSISTANT/CONVERSATION document；GLOBAL 自动写入禁用。手动 preview/run/retry 纳入交付，富文本 diff 可用结构化摘要实现。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.

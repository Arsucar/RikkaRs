# Issue #147：通用 Hook 后处理与记忆表同步设计

## Architecture

- 复用现有 `HookFinalSuccessGate → finalizeAndCreateRunExactlyOnce → HookDispatcher → HookActionRegistry`，不新增 scheduler。
- 引入 action-specific frozen request、strict output parser、validated result 与 handler；AddTag 与 SyncMemoryTable 各自拥有 request/output/result DTO。
- ChatService 只负责冻结公共 turn/message 元数据并请求 action factory，移除对 `AddConversationTag` 的强制 cast。

## Frozen input and model boundary

- Sync config 显式 target document、scope、recent message count、role policy、max chars、frequency 与 model/prompt。
- 冻结 active branch 最近 N 条已完成 user/assistant 文本，记录 message ID/role；排除 hidden、system、tool raw、pending tool。
- provider prompt 只要求严格 `{decision, baseRevision, operations, reason}`；不注册 tool。
- parser exact-key、size/op-count 限制；模型输出 target/scope 被忽略/拒绝。

## Validation and atomic commit

- 将 MemoryTable apply_ops 的纯 payload operation/validator 提取为 Repository 可复用内部能力，验证 schema columns/types、PK、updatePolicy、actor scope。
- 新 DAO CAS API 在 `WHERE id=:id AND revision=:expected AND deleted_at IS NULL` 下更新；0 rows 映射 revision conflict/target deleted。
- 新 cursor 与 generalized execution audit schema 在 DB v42；同一 transaction 内：重查 lease/target → 校验 cursor/CAS → snapshot → payload revision update → cursor → execution result/diff/status。
- Dispatcher 对 atomic handler 只接收最终 action result；不得在 handler commit 后再用第二事务标记 success。

## Idempotency, retry, and gates

- 唯一 cursor：hook/config/target/logicalTurn/endMessage。成功存在即 SKIPPED。
- preview 生成 frozen input + parsed/validated diff，但不写 payload/cursor；apply 使用 preview baseRevision 并重查。
- retry 关联 source execution，新 attempt 仅允许 source 未提交；已成功 cursor 拒绝重复。
- global memory table、assistant table、auto permission、hook enabled、scope permission、frequency 任一失败时在 provider 前 SKIPPED。

## UI and compatibility

- Hook editor 增 Action selector；Sync 区配置目标文档、最近 N 条、角色、字符/operation 上限、最小 turn 间隔、自动/手动。
- history 使用 action-specific summary；旧 tagId 列保留 nullable，新 action audit 通过新增 nullable JSON/typed fields 兼容旧记录。
- `memoryTableAutoSyncEnabled` 保留为全局 auto gate，默认 false；设置入口跳到 Hooks 配置，不创建默认 enabled Hook。
- #146 trash 文档不出现在 target selector，commit CAS 也拒绝 deleted target。

## Migration and rollback

- 从 DB v41→v42，新增 cursor/audit/generalized execution columns；旧 tag execution 保持可读。
- 旧 Assistant JSON 的 `add_conversation_tag` serial name 不变，只新增 subtype。
- 如 Sync Action 出现问题，可通过全局 auto gate 关闭模型调用；AddTag handler 不依赖新 action。

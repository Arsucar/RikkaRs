# Issue #145：记忆表模板 Scope 迁移技术设计

## Boundary and Data Flow

完整链路：Compose 编辑/复制 → `AssistantDetailVM` → `MemoryTableRepository` → actor-scoped `MemoryTableDAO` → Room Flow 刷新；AI 路径为 tool schema/handler → ChatService actor closure → 同一 Repository API。

Repository 是 scope 语义和授权的唯一权威。UI 只做即时冲突提示和确认，tool 只解析参数；两者不得自行拼接 `scopeId`。

## Repository Contract

actor-aware upsert 使用 nullable target：

- create + `requestedScopeType = null` → ASSISTANT/current actor。
- update + null → 保留 existing scope。
- 显式 ASSISTANT → `scopeId = actorAssistantId`。
- 显式 GLOBAL → `scopeId = __global__`。
- CONVERSATION → 拒绝。

更新前先按 actor 读取有效模板，拒绝 foreign/malformed/missing；按目标 scope 执行规范化名称冲突检查并排除自身 ID。DAO 用单条 guarded UPDATE 同时写 name/description/schema/scope/updatedAt。目标与当前相同是幂等成功。

actorless import API 保持兼容，不改其显式 ownership 语义。

## Document Contract

迁移不修改 `memory_table_documents`：template ID、document ID、document scope、payload 和 revision 全部保持。标准 list/injection 先以 actor 有效模板 ID 过滤，因此 GLOBAL→ASSISTANT 后其他助手立即看不到模板及其标准列表项。

若迁移后的 ASSISTANT 模板仍关联 GLOBAL 文档，文档 scope 按 Issue 要求保持不变；当前 Repository 对后续“ASSISTANT 模板 + GLOBAL 文档”写入的限制和 known-ID 文档授权缺口作为已知边界记录，不在本任务偷偷改变。

## Tool Contract

模板 scope parser 只接受 `assistant|global`，错误信息与文档 scope parser 区分。callback 必须保留“参数是否出现”：

- create 未传 → null，由 Repository 默认 ASSISTANT。
- update 未传 → null，保留 existing。
- 显式值 → 传目标类型。

schema description 明确模板动作只支持 assistant/global，而文档 upsert 仍支持 conversation/assistant/global。

## UI Contract

- EDIT 使用局部 `scopeType` 参与表单显示和目标 namespace 冲突检查。
- scope 未变化直接普通保存；变化时显示可 loading 的 `AlertDialog`，说明可见性变化和“文档 scope 不变”。
- 成功后关闭确认并返回管理列表，Room Flow 更新徽章；失败清 loading、保留表单并显示本地化错误。
- GLOBAL 管理菜单新增“复制到本助手”。复制确认弹窗使用本地化副本名称作为可编辑初值；Repository 生成新 ID、scope 固定 ASSISTANT 并最终校验冲突，原 GLOBAL 保留。
- 迁移通过编辑表单的 scope 变化进入独立确认；复制使用单独菜单和确认弹窗，两者文案与成功提示不同。

## Compatibility and Concurrency

- 不变更 schema/version/migration。
- 无 template revision，因此并发编辑为数据库原子 UPDATE + last-write-wins；不会出现字段已更新而 scope 未更新的半成功。
- ChatService 和 UI 使用 Room/Fresh reads，无缓存迁移步骤。
- 现有 “普通 update 保持 ownership” 测试改为“未显式 target 保持”；新增显式迁移测试。

## Validation Matrix

- DAO：GLOBAL→A、A→GLOBAL、same-scope、foreign/malformed/missing、字段+scope 原子更新。
- Repository：默认 create、update null 保持、显式迁移、名称冲突、文档行不变、另一助手过滤。
- Tool：create/update assistant/global、update omitted、conversation reject、known-ID authorization。
- UI pure/state：目标 scope 冲突、确认模式、copy vs migrate、loading/success/error。
- Resources：六套 locale key 和 placeholder 一致。

## Rollback

没有 schema 变更。数据迁移本身会改变 row ownership；代码回滚不会自动恢复旧 scope。提交前需在测试中记录原/目标 scope 并证明显式反向迁移可用。

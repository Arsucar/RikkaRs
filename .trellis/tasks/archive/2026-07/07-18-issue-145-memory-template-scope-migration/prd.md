# 处理 Issue #145 记忆表模板 Scope 迁移

## Goal

允许用户和 `memory_table_tool` 显式创建或迁移 GLOBAL/当前助手 ASSISTANT 模板，治理误建的全局模板，同时保持 #122 建立的跨助手隔离、现有文档 scope 和模板 ID/文档引用稳定。

## Requirements

- 模板 create/update 必须区分“未传 scope”和“显式目标 scope”：create 未传默认当前助手 ASSISTANT；update 未传保留既有 scope；显式 `assistant|global` 按目标迁移。
- `conversation` 对模板动作无效，UI 不提供该选项，tool 必须返回明确错误。
- Repository 只根据 actor 和目标类型派生 `scopeId`，不得接受调用方伪造其他 assistant owner。
- GLOBAL 或 actor 自有 ASSISTANT 模板可原地迁移；他人 ASSISTANT 和畸形 scope 模板不可按已知 ID 读取、更新、迁移或删除。
- DAO 在一次受 actor 约束的原子 UPDATE 中同时写普通字段和 `scope_type/scope_id`，避免字段成功但 scope 失败的部分更新。
- 迁移保持模板 ID、createdAt 和关联文档行不变；文档自身 scope 不自动迁移、不删除。
- 目标 scope 下继续执行规范化名称冲突检查：ASSISTANT 与 GLOBAL+同助手私有冲突；GLOBAL 与所有模板冲突；同 ID 自身排除。
- 助手记忆页编辑模板时开放 GLOBAL/ASSISTANT 控件；scope 变化先显示确认，保存中禁止重复提交/关闭，成功刷新徽章，失败保留原持久化 scope 和编辑状态。
- GLOBAL 模板管理项明确区分“迁移”与“复制到本助手”；复制创建新 ID 且保留原 GLOBAL，迁移保留 ID 且改变可见性。
- `memory_table_tool.create_template` 和 `update_template` 读取可选 scope；list 继续只返回 GLOBAL ∪ 当前助手 ASSISTANT，迁移后其他助手立即不可见。
- 新增/修改文案必须资源化并覆盖默认英文、简中及仓库现有 locale 集合。

## Acceptance Criteria

- [x] UI 可将指定 GLOBAL 模板原地迁移为当前助手 ASSISTANT，模板 ID 与关联文档 scope 不变，另一助手的模板列表不再出现它。
- [x] UI 可将当前助手 ASSISTANT 模板显式迁移为 GLOBAL，并在确认后对其他助手可见。
- [x] 编辑不改变 scope 时保持现网普通字段更新行为，不弹迁移确认。
- [x] GLOBAL 模板同时提供独立复制入口；复制保留源模板并创建新的当前助手模板，不与迁移混淆。
- [x] tool create/update 支持 `scope=assistant|global`；update 不传 scope 保持原 owner；模板动作传 `conversation` 明确失败。
- [x] 他人 ASSISTANT、畸形 GLOBAL 和不存在模板均不能被当前 actor 迁移或改写。
- [x] DAO/Repository/tool/UI 逻辑测试覆盖 scope 原子更新、幂等、目标名称冲突、跨助手过滤和文档不变。
- [x] 不新增 Room schema migration；现有 #122 隔离、#140 管理/去重、记忆 capability 测试继续通过。
- [x] app resources、Kotlin、聚焦 JVM 测试和 androidTest 编译通过；设备 offline 与 lint 超时已如实记录，未声称安装或 lint 通过。

## Confirmed Facts

- `MemoryTableTemplate` 和 Room entity 已有 `scopeType/scopeId`，当前数据库版本 40，无需新增列。
- actor-aware Repository create 可显式 GLOBAL，但 update 强制保留 existing scope；DAO 更新 SQL 不写 scope。
- tool schema 已有通用 `scope` enum，但模板 create/update handler 完全不读取它。
- CREATE UI 已有 GLOBAL/ASSISTANT Radio；EDIT 使用既有 scope、`scopeEditable=false` 且无 scope 参数。
- Room Flow 每次变更会刷新有效模板；ChatService 和 tool 每次调用也 fresh-read，无需新增缓存失效。
- 模板文档没有数据库 FK，原地迁移保持 `templateId` 即可保留引用。

## Out of Scope

- 自动猜测历史 GLOBAL 模板应归属哪个助手。
- 自动迁移、删除或重写关联文档 scope。
- 为模板新增 revision/schema 版本；并发 scope 更新采用单次原子写和 last-write-wins。
- 在本 Issue 内全面修复文档 known-ID 授权边界；该问题若被验证存在需单独跟踪，不能伪装为模板迁移已覆盖。

## Open Questions

- 无。Issue 已明确默认策略和交互范围。

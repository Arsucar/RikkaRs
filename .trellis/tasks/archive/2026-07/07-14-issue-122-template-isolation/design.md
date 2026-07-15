# Design: Issue #122 模板所有权隔离

## Architecture

模板沿用文档现有的 scope 词汇，但模板只允许两种所有权：

```text
MemoryTableTemplate(scopeType, scopeId)
  GLOBAL    -> scopeId == "__global__"
  ASSISTANT -> scopeId == assistant UUID string
```

`CONVERSATION` 不允许作为模板 scope。模板可见性从 Room 查询开始，在 Repository 再防御过滤，并由 VM、UI、聊天注入和 AI 工具统一消费 scoped API。

## Persistence and Migration

- Room `35 -> 36`：`memory_table_templates` 增加非空 `scope_type`、`scope_id` 与联合索引。
- 迁移默认值固定为 `GLOBAL` / `__global__`。旧版本没有所有者信息，禁止根据关联文档猜测归属。
- `MemoryTableTemplateEntity` 与 schema 36 同步；迁移测试必须验证旧行、默认值及索引。
- 模板 ID 继续全局唯一；不同助手可创建同名但不同 ID 的模板。

## Model and Bundle Compatibility

- `MemoryTableTemplate` 增加带默认值的 ownership 字段，使缺字段的旧 JSON 解码为全局模板。
- memory-table bundle 升级为 v2；导入兼容 v1/v2，v1 归一化为全局，v2 保留 ownership。
- 不存在于本机的 assistant owner 仍原样保留，不能静默提升为全局。
- 普通 update 不允许改变 ownership；私有/全局转换只能通过显式复制产生新 ID。

## DAO and Repository Contracts

### Visibility

对 actor assistant A：

```text
template.scope == GLOBAL
OR template.scope == ASSISTANT AND template.scopeId == A
```

DAO 提供 effective list/flow 与 actor-scoped by-ID 查询。Repository 对 DAO 返回再次应用同一谓词。

### Mutation

- Create：助手页面和聊天工具默认 `ASSISTANT/currentAssistantId`。
- Update：先按 actor 授权读取现有行，再更新可编辑字段；保留原 ownership。
- Delete：先授权模板，再在同一事务中删除关联文档和模板。未授权时不得先删除文档。
- 禁止普通 mutation 使用 `REPLACE` 覆盖已知的外部模板 ID。
- Global 模板保持当前共享可见/可编辑语义；复制全局模板产生当前助手私有的新 ID。
- 已知 ID 的文档工具 mutation 同样校验 `GLOBAL/current assistant/current conversation`，避免上一轮 #122 的旁路。

## Runtime and UI Flow

- `AssistantDetailVM` 使用 `assistantId` 的 effective template flow；创建默认 private，删除/更新传 actor。
- `ChatVM` 随 conversation assistant 变化 `flatMapLatest` scoped template flow。
- `ChatService` 为准备请求、注入和工具 wiring 使用当前 assistant 的 effective templates；所有 mutation closure 捕获 actor assistant/conversation。
- Assistant 页面、文档编辑器、对话模板选择器只收到 scoped templates；即使错误数据进入 UI，也按 ownership 再过滤。
- 私有模板不能创建/切换为 GLOBAL 文档；UI 禁用该操作，Repository/工具端再次拒绝。
- 全局模板提供“复制到当前助手”动作：复制 schema/名称/描述等定义，生成新 ID 和当前助手 owner，不复制原文档。
- 编辑器必须区分模板仍在加载与已加载后不存在；不得把 `stateIn` 的空初值当成越权/不存在而首帧退出。
- DAO 与 Repository 对 GLOBAL 的有效性使用同一严格谓词：`scopeType == GLOBAL && scopeId == __global__`，畸形行不可见也不可按 scoped API 删除。

## Error and Edge Cases

| 场景 | 结果 |
|---|---|
| B 持有 A 私有模板 ID | get/update/delete 拒绝，数据不变 |
| B 持有 A 文档 ID | tool mutation 拒绝，数据不变 |
| 私有模板尝试保存 GLOBAL 文档 | 验证失败，不写入 |
| 删除未授权模板 | 模板及关联文档均保留 |
| 导入 v1 模板 | 作为全局模板导入 |
| 导入 v2 的未知 assistant owner | 保留 owner，默认不可见 |
| conversation 改绑另一助手 | 原助手私有模板不可见且不注入；不得静默降级 schema |

## Rollback

产品代码可回滚，但 schema 36 不能由旧二进制安全降级。备份恢复 schema 35 后由新版本正常迁移。迁移只添加字段/索引，不删除模板或文档。

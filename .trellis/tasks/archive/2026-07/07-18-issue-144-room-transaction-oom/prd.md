# 处理 Issue #144 Room/协程事务 OOM

## Goal

降低当前代码中可证明的 Room/会话持久化内存峰值，补充不泄露用户内容的事务规模与 heap 诊断信息，并在没有复现、heap dump 或业务栈证据时避免把协程 continuation 的最终崩溃点误报为真实根因。

## Requirements

- 明确主错误是 512 MiB Java heap 耗尽，`CoroutinesInternalError` 是 continuation 恢复时的次生错误；交付评论不得声称已仅凭现有堆栈定位具体 DAO。
- `recent_chats` 只返回会话 ID、标题和更新时间，不得为了摘要加载或反序列化任一 `message_node.messages`。
- 会话更新只读取既有节点 ID，不得在 diff 前用 `SELECT *` 把所有历史消息 JSON 同时载入内存。
- 会话首次保存和 fork 保存应分批编码并写入节点，单批实体数量有固定上限；不得先构造整段会话的 `List<MessageNodeEntity>`。
- 完整会话读取仍保持数据完整和既有顺序，不静默截断历史；分页读取的规模、入口、节点数、序列化字符数和 JVM heap 摘要应能以元数据日志定位，日志不得包含消息正文。
- 索引重建继续逐会话处理并报告进度；不得同时保留多个会话的完整消息树。
- 结构化记忆、Hook、标签和子代理上下文等其他 Room 候选路径完成静态风险记录和既有回归，但在无证据时不做破坏兼容性的猜测式限流。
- app 功能变更按仓库规范执行聚焦 JVM/数据库测试、编译和设备安装；无法执行的仪器测试或设备验收必须如实记录。

## Acceptance Criteria

- [x] `recent_chats` 的执行路径对最近 30 个会话也不会调用完整节点加载，返回字段和排序保持不变。
- [x] 节点同步通过轻量 ID 投影计算删除项，既有消息 JSON 不因 diff 被反序列化或复制。
- [x] 新建/分叉会话的节点编码与插入按固定批次执行，测试证明空列表、小列表和大列表的批次边界。
- [x] 完整会话与索引重建仍能还原全部节点、顺序、收藏状态和选择分支；单行过大时明确失败，不返回残缺历史。
- [x] 诊断日志只包含操作名、会话 ID、页/节点/字符计数、耗时和 heap 数值，不包含消息正文、工具参数或附件内容。
- [x] ConversationRepository 相关 JVM 测试、app 编译和 androidTest 编译通过；设备离线与固定地址连接超时已如实记录，未声称安装或 instrumentation 执行成功。
- [ ] 只有在复现、heap/allocation 证据或覆盖原数据规模的压力验证证明不再触达 heap limit 后才关闭 #144；否则发布当前改进、验证证据和仍缺的外部诊断信息，并保持 Issue 开放。

## Confirmed Facts

- 当前仅有一次 R8 后的崩溃堆栈，缺少版本、设备、操作入口、数据规模、logcat 和 heap dump。
- `loadMessageNodes` 每页查询 64 个节点，但最终会反序列化并返回该会话全部节点；分页不等于总结果有界。
- `getRecentConversations` 当前为只需标题/日期的 `recent_chats` 加载每个最近会话的完整节点，这是可以直接消除的无效大对象加载。
- `syncMessageNodes` 当前用 `getNodesOfConversation` 读取包含完整 `messages` JSON 的全部实体，但只使用 ID。
- `saveMessageNodes` 当前先构造完整实体列表再一次 `insertAll`。
- 记忆表 payload、Hook history、活跃 run、子代理 context 等路径也存在无长度上限或无查询 limit 的风险，但现有堆栈不能证明其中任一路径是本次根因。

## Out of Scope

- 在没有证据时把 OOM 归因于 Room、协程库或某个业务 DAO。
- 通过静默截断、删除或拒绝加载用户历史来制造“有界”结果。
- 本任务内重构所有 Room 事务、引入新的数据库 schema 或改变记忆表/Hook/子代理的产品容量策略。

## Open Questions

- 无阻塞产品决策。原始触发入口与真实 retained object 只能由后续复现、heap dump 或 allocation trace 回答。

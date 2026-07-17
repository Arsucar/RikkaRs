# Issue #144：Room/协程 OOM 执行计划

- [ ] 新增 `MessageNodeDAO` 的 ID-only 查询，并让 `syncMessageNodes` 不再读取完整历史实体。
- [ ] 将首次插入和 fork 的节点编码/`insertAll` 改为固定批次，提取批次策略的可测试纯函数。
- [ ] 将 `getRecentConversations` 改为轻量摘要映射，确认唯一调用者 `recent_chats` 不再加载节点。
- [ ] 为完整节点加载/保存增加元数据级规模与 heap 诊断，确保日志无消息正文且只在阈值/失败路径产生。
- [ ] 核对 `rebuildAllIndexes` 逐会话释放路径，并补充必要的回归或注释。
- [ ] 新增/更新 JVM 与 DAO 测试：ID 投影、批次边界、顺序、摘要路径、sync diff。
- [ ] 静态复核 MemoryTable、Hook、Tag、Subagent transaction 候选，记录未修改理由与剩余风险。
- [ ] 运行 `git diff --check`、聚焦测试、`:app:compileDebugKotlin`；按设备流程执行 `:app:installDebug`。
- [ ] 根据实际证据决定 Issue 状态：能证明原规模下不再 OOM才关闭，否则发布中英文进展/边界评论并保持开放。

## Risk and Rollback Points

- DAO 投影字段名必须与 Room 映射一致，避免返回顺序变化。
- 批量插入仍位于外层事务内；失败必须整体回滚，不能留下半个会话。
- 诊断不得输出用户内容，也不得在每个小会话上持续写日志。
- 不允许为了测试通过给完整会话加载增加静默上限。

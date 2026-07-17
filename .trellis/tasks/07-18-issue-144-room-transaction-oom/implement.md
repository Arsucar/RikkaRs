# Issue #144：Room/协程 OOM 执行计划

- [x] 新增 `MessageNodeDAO` 的 ID-only 查询，并让 `syncMessageNodes` 不再读取完整历史实体。
- [x] 将首次插入和 fork 的节点编码/`insertAll` 改为固定批次，提取批次策略的可测试纯函数。
- [x] 将 `getRecentConversations` 改为轻量摘要映射，并以无效节点 JSON sentinel 的真实 Repository 测试锁定不读取节点的路径。
- [x] 为完整节点加载/保存增加元数据级规模与 heap 诊断，确保日志无消息正文且只在阈值/失败路径产生。
- [x] 核对 `rebuildAllIndexes` 逐会话释放路径，并补充 65 节点跨页完整读取回归。
- [x] 新增/更新 JVM 与 DAO 测试：ID 投影/隔离、统计、批次边界、分页 fallback、顺序、摘要路径、sync diff。
- [x] 静态复核 MemoryTable、Hook、Tag、Subagent transaction 候选，记录未修改理由与剩余风险。
- [x] 运行 `git diff --check`、聚焦测试、完整 app JVM 测试、`:app:compileDebugKotlin` 和 androidTest 编译；设备 offline 且重连 10060，无法执行安装/仪器测试。
- [ ] 发布中英文进展/边界评论并保持 Issue 开放；缺少原始复现、heap/allocation 证据和真实数据规模压力验证。

## Risk and Rollback Points

- DAO 投影字段名必须与 Room 映射一致，避免返回顺序变化。
- 批量插入仍位于外层事务内；失败必须整体回滚，不能留下半个会话。
- 诊断不得输出用户内容，也不得在每个小会话上持续写日志。
- 不允许为了测试通过给完整会话加载增加静默上限。

## Validation Results (2026-07-18)

- `git diff --check`：通过。
- `:app:compileDebugKotlin`：通过。
- `:app:testDebugUnitTest --tests me.rerere.rikkahub.data.repository.ConversationRepositorySyncOpsTest`：通过。
- `:app:testDebugUnitTest`：完整 app JVM 单测通过。
- `:app:compileDebugAndroidTestKotlin`：通过；新增 DAO/Repository instrumentation 测试已编译。
- `adb devices`：`100.99.129.110:5555 offline`。
- `adb connect 100.99.129.110:5555`：失败，Windows socket 10060；未执行 `:app:installDebug` 或 instrumentation test。
- Android Lint：本任务未重跑；紧邻任务 #145 的同工作树 lint 已分别在 120 秒和 300 秒超时，未把 lint 描述为通过。
- 仍未覆盖：原 issue 数据规模的稳定复现、heap dump/allocation trace、单节点编码前的字符阈值预知、第二批 DAO 故障注入后的真实 Room rollback instrumentation。

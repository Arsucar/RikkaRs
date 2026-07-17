# Issue #147：执行计划

- [ ] 等待并基于 #146 DB v41/schema/deleted target contract；确认 migration 起点为 41。
- [ ] 泛化 Hook domain/request/parser/result/history，新增 SyncMemoryTable config/type/hash，保持 AddTag JSON/DB/UI 回归。
- [ ] 提取有界 active-branch frozen input 与 action-specific provider executor，严格限制角色、字符、operations 和 raw response 落库。
- [ ] 提取/增强 MemoryTable schema/type/PK/updatePolicy operation validator，新增 expectedRevision + active target CAS。
- [ ] 增 DB v42 cursor/audit/generalized execution schema；实现 payload/snapshot/CAS/cursor/execution result 同事务原子 commit。
- [ ] 接入 Dispatcher lease/timeout/final-success/manual preview-run/retry，不新增并行 Job；所有门控在 provider 前短路。
- [ ] 扩展 Hooks editor/list/history/detail 与 auto-sync 设置入口，使用资源和所有 locale；GLOBAL 自动写入 V1 禁用。
- [ ] 补 domain/parser/context/validator/idempotency/CAS/rollback/lease/final-success/AddTag 回归及 UI state 测试。
- [ ] 运行资源、聚焦 JVM/Room/Hook 测试、app 编译/androidTest 编译和设备安装。
- [ ] 更新 Hook/Memory code-spec、CHANGELOG，提交推送，发布并复核中英文评论后关闭 #147。

## Risk and rollback points

- handler 写表和 execution success 分两个事务会产生不可恢复审计漂移，必须阻断交付。
- 不能复用 tool 入口的 conversation-scope 禁止逻辑；应复用底层 validator/Repository 并定义 Hook actor 权限。
- provider raw response、完整 frozen messages 与 payload diff 不得落库；只存有界结构化摘要。
- AddTag 是强回归面：旧 JSON、config hash、history 与 execution status 必须保持。

# Issue #147：执行计划

- [x] 等待并基于 #146 DB v41/schema/deleted target contract；确认 migration 起点为 41。
- [x] 泛化 Hook domain/request/parser/result/history，新增 SyncMemoryTable config/type/hash，保持 AddTag JSON/DB/UI 回归。
- [x] 提取有界 active-branch frozen input 与 action-specific provider executor，严格限制角色、字符、operations 和 raw response 落库。
- [x] 提取/增强 MemoryTable schema/type/PK/updatePolicy operation validator，新增 expectedRevision + active target CAS。
- [x] 增 DB v42 cursor/audit/generalized execution schema；实现 payload/snapshot/CAS/cursor/execution result 同事务原子 commit。
- [x] 接入 Dispatcher lease/timeout/final-success/manual preview-run/retry，不新增并行 Job；所有门控在 provider 前短路。
- [x] 扩展 Hooks editor/list/history/detail 与 auto-sync 设置入口，使用资源和所有 locale；GLOBAL 自动写入 V1 禁用。
- [x] 补 domain/parser/context/validator/idempotency/CAS/rollback/lease/final-success/AddTag 回归及 UI state 测试。
- [x] 运行资源、聚焦 JVM/Room/Hook 测试、app 编译/androidTest 编译和设备安装。
- [x] 更新 Hook/Memory code-spec、CHANGELOG，提交推送，发布并复核中英文评论后关闭 #147。

## Validation evidence

- `:app:processDebugResources :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
  passed with `--no-daemon`.
- Focused Hook/hash/parser/frozen-context/operation/UI JVM tests passed; full app JVM suite passed.
- Room androidTest sources compile, including v41->v42 migration, CAS, atomic commit, replay, lease, schema-change,
  multi-operation rollback, and competing-revision tests. Connected instrumentation was not run.
- `:app:lintDebug` completed but failed on 103 existing errors, 41 warnings, and 1 hint. The refreshed report has zero
  findings for changed Kotlin filenames and zero findings for the 42 changed/added #147 localization keys.
- All 42 target resource keys parse and exist in `values`, `values-zh`, `values-zh-rTW`, `values-ja`, `values-ko-rKR`,
  and `values-ru`, with identical placeholder sets. `git diff --check` passes.
- Device `100.99.129.110:5555` remains `offline` after reconnect, so `:app:installDebug` and instrumentation execution
  were not attempted.

## Risk and rollback points

- handler 写表和 execution success 分两个事务会产生不可恢复审计漂移，必须阻断交付。
- 不能复用 tool 入口的 conversation-scope 禁止逻辑；应复用底层 validator/Repository 并定义 Hook actor 权限。
- provider raw response、完整 frozen messages 与 payload diff 不得落库；只存有界结构化摘要。
- AddTag 是强回归面：旧 JSON、config hash、history 与 execution status 必须保持。

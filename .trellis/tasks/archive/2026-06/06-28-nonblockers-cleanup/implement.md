# Implement: Non-blockers C

> 子代理派发时 prompt 必须以 `Active task: .trellis/tasks/06-28-nonblockers-cleanup` 开头。

## C-1 LogPage memoize

- [ ] **1.1** 读 `app/src/main/java/me/rerere/rikkahub/ui/pages/log/LogPage.kt`（`RequestLogCard` 约 `260-310`）。
- [ ] **1.2** 在 `RequestLogCard` 函数体顶部加 `val redacted = remember(log.id, log.url) { log.redacted() as LogEntry.RequestLog }`。
- [ ] **1.3** 将 `Text(text = log.redacted().let {...}.url, ...)` 改为 `Text(text = redacted.url, ...)`。
- [ ] **1.4** 确认 `remember` import 已存在（详情页已用）。

## C-2 状态锁审计

- [ ] **2.1** grep `session.state.value` 与 `\.state\.value\s*=` 在 `ChatService.kt`。
- [ ] **2.2** 对每条写位置判定：seed（`ConversationSession(...)` 构造，无需锁）或运行时写（必须在 `synchronized(session.stateLock)` 内）。
- [ ] **2.3** 若发现锁外运行时写 → 包进 `synchronized(session.stateLock) { ... }`。
- [ ] **2.4** 结论写入 `.trellis/tasks/06-28-nonblockers-cleanup/research/lock-audit.md`。

## C-3 syncMessageNodes 测试

- [ ] **3.1** 检查 `app/build.gradle.kts` 是否有 `androidx.room:room-testing` + `inMemoryDatabaseBuilder` 用例（grep `inMemoryDatabaseBuilder` / `RobolectricTestRunner` 在 `app/src/test`）。
- [ ] **3.2a**（有基建）：新增 `ConversationRepositorySyncTest.kt`，覆盖：同 id 覆盖、删孤儿、新增节点。
- [ ] **3.2b**（无基建，降级）：在 `ConversationRepository.kt` 抽取 `private fun computeNodeSyncOps(...)`（或 internal 便于测试），新增 `ConversationRepositorySyncOpsTest.kt` 单测纯函数；在本文档 §3.2b 注明降级理由。

**§3.2b 降级理由（已采用）**：`app/build.gradle.kts` 无 `androidx.room:room-testing`，`app/src/test` 中无 `inMemoryDatabaseBuilder` / `RobolectricTestRunner` 用例；仅存在 JVM 单元测试。故抽取 `internal fun computeNodeSyncOps` 并单测 diff 逻辑，不引入 Room 集成测试基建。
- [ ] **3.3** 运行 `.\gradlew :app:testDebugUnitTest --tests "*Sync*" --no-daemon` 通过。

## C-4 baseline 文档

- [ ] **4.1** 读 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md`，找合适段落（「FAQ」/「已知问题」/末尾）。
- [ ] **4.2** 补一段说明 baseline profile 含历史 Firebase 符号、不影响构建、后续重生时清理。
- [ ] **4.3** 不改 `startup-prof.txt`。

## 验证

- [ ] **5.1** `.\gradlew :app:compileDebugKotlin --no-daemon` 通过（若本任务是最后一个子代理；否则跳过完整编译）。
- [ ] **5.2** `.\gradlew :app:testDebugUnitTest --tests "*Sync*" --no-daemon` 通过。
- [ ] **5.3** LogPage 视觉行为不变（仅性能优化，无 UI 改动）。

## 回滚点

- C-1 / C-2 / C-3 / C-4 各自独立 commit → 可单独 revert。

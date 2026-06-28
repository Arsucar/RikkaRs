# PRD: Non-blockers C — Performance, Lock Audit, Tests, Cleanup

## 范围（4 个独立小项）

### C-1 LogPage 列表卡片 `redacted()` memoize

**问题**：`RequestLogCard` 每次重组都对 `log.url` 调 `log.redacted().let { it as LogEntry.RequestLog }.url`（`LogPage.kt:263`），无 `remember`，日志多时多余 CPU。

**验收 C-1**：
- 用 `remember(log.id, log.url) { (log.redacted() as LogEntry.RequestLog) }` 缓存。
- 列表滚动时不重复脱敏。
- 编译通过。

### C-2 ChatService 状态锁写路径审计

**问题**：`commitConversationState` / `updateConversationState` 已用 `session.stateLock`，但需确认 `ChatService` 内**所有**直接写 `session.state.value =` 的位置都在锁内（或本就不需要锁的 seed 路径）。

**验收 C-2**：
- 全文件 grep `session.state.value` 与 `state.value`，列出所有命中行；对每个写位置标注「在 synchronized 块内」或「seed 初始化（无需锁）」。
- 若发现锁外写且非 seed → 修正为进锁。
- 产出审计结论写入本任务 `research/` 或 `implement.md` 备注段。

### C-3 syncMessageNodes 单测

**问题**：`ConversationRepository.syncMessageNodes`（`ConversationRepository.kt:392-411`）用「按 id 删除孤儿 + REPLACE insert」替代整表删除，缺少测试覆盖：删分支节点、改 selectIndex、并发 update 不丢消息。

**验收 C-3**：
- 新增 `ConversationRepositoryTest`（若已存在则补充）覆盖：
  1. 更新已存在节点（同 id）→ 字段（messages / selectIndex）被覆盖。
  2. 删除 DB 中有但新列表无的节点。
  3. 新增节点被持久化。
- 若项目无 Room in-memory 测试基建，至少补 **逻辑层** 单测（抽取 sync 算法为可测函数，或在 DAO 层用 robolectric）。
- 若补 Room 集成测试成本过高，**降级**为：抽取 `syncMessageNodes` 的 id diff 逻辑为纯函数 `computeNodeDiff(existing, new): SyncOp`，单测纯函数，并在 implement.md 注明降级理由。

### C-4 baseline Firebase 残留说明

**问题**：`app/src/release/generated/baselineProfiles/startup-prof.txt` 含大量 `com/google/firebase/*` 符号（历史 baseline 未重生）。

**验收 C-4**：
- **不重生** baseline（超出范围）。
- 在 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` 或 `CHANGELOG` 「已知问题」段补一句：「baseline profile 含历史 Firebase 符号，不影响无 Firebase 构建，后续重 profile 时清理」。

## 跨项验收

- `.\gradlew :app:compileDebugKotlin --no-daemon` 通过。
- `.\gradlew :app:testDebugUnitTest --no-daemon`（含 C-3 新测试）通过。
- 不破坏现有 LogPage / ChatService 行为。

## 约束

- C-1 不改脱敏规则本身，只 memoize。
- C-2 若发现锁外写，**最小修复**（进锁），不重构锁模型。
- C-3 若 Room 测试基建缺失，允许降级为纯函数测试，但必须在 implement.md 说明。
- C-4 仅文档，不动 `startup-prof.txt`。

## 不在本任务范围

- 重生 baseline profile。
- 重写状态锁为 Mutex / Channel。
- LogPage 详情页重构。

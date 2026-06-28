# Design: Non-blockers C

## C-1 LogPage memoize

`RequestLogCard(log, onClick)` 当前：
```kotlin
Text(text = log.redacted().let { it as LogEntry.RequestLog }.url, ...)
```
改为：
```kotlin
val redacted = remember(log.id, log.url) { log.redacted() as LogEntry.RequestLog }
Text(text = redacted.url, ...)
```
详情页已用 `remember(log.id)`，保持一致。

## C-2 状态锁审计

grep 目标（在 `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`）：
- `session.state.value =` （写）
- `.state.value`（读，仅确认是否需要锁；StateFlow 读本身线程安全，无需锁）

**预期结果**：seed 初始化（`getOrCreateSession` 内 `ConversationSession(...)` 构造）无需锁；其余写应在 `synchronized(session.stateLock) {}` 内。

若发现锁外写 → 进锁（最小改动）。结果记录到本任务 `research/lock-audit.md`。

## C-3 syncMessageNodes 测试

### 降级决策树

```
有 Room in-memory 测试基建（如 androidx.room:room-testing + inMemoryDatabaseBuilder）？
├─ 有 → 写 ConversationRepositoryTest（@RunWith(RobolectricTestRunner) 或 instrumented）
│        覆盖：同 id 覆盖、删孤儿、新增
└─ 无 → 抽取纯函数：
        fun computeNodeSyncOps(existingIds: List<String>, newNodes: List<MessageNode>): Pair<List<String /* delete */>, List<MessageNode /* upsert */>>
        单测纯函数；在 implement.md 注明降级。
```

优先尝试 Room 测试；若 setup 超 30 分钟或子代理报告基建缺失 → 降级。

## C-4 baseline 文档

在 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` 「已知问题 / FAQ」段或新增段补：
> **Baseline profile 含历史 Firebase 符号**：`app/src/release/generated/baselineProfiles/startup-prof.txt` 为历史生成物，含 `com/google/firebase/*` 符号。本 fork 已移除 Firebase 依赖，这些符号不影响构建；后续重新生成 baseline profile 时会自动清理。

同时 CHANGELOG `v2.3.6` 不必为此单独加条目（属已知产物滞后）。

## 风险

- **低**：C-1 改动极小，回归风险低。
- **中**：C-3 Room 测试基建可能未配置 → 走降级路径。
- **低**：C-2 仅审计 + 最小修复。

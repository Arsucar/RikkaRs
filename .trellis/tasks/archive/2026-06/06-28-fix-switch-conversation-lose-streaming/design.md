# Design: fix-switch-conversation-lose-streaming

## 1. 问题根因（已确认）

会话1 流式生成中 → 切到会话2 → 切回会话1 时：
- `ChatVM` 按会话 ID 绑定导航栈，切回会**新建 ChatVM**（`ChatPage.kt:102-106`）。
- `ChatVM.init` 无条件调用 `chatService.initializeConversation(_conversationId)`（`ChatVM.kt:85`）。
- `initializeConversation`（`ChatService.kt:393-414`）从 DB 读快照 → `updateConversation` → `commitConversationState`（`1174-1181`）**全量替换** `session.state`。
- 流式中间内容（文本 / reasoning / 子代理 transcript）**只在内存 `session.state`**，生成成功结束才 `saveConversation` 落盘（`741-744` vs `764`）。
- 结果：内存流式内容被 DB 落后快照覆盖 → 空消息 + `conversationJob` 仍 active → 兔子 loading。

## 2. 修复方案

### 2.1 生产代码改动（`ChatService.kt`）

**改动点 1：`initializeConversation` 顶部加 guard（`ChatService.kt:393`）**

改前：
```kotlin
suspend fun initializeConversation(conversationId: Uuid) {
    val session = getOrCreateSession(conversationId) // 确保 session 存在
    val conversation = conversationRepo.getConversationById(conversationId)
    if (conversation != null) {
        val hydrated = hydrateConversationFromDb(conversation, session)
        updateConversation(conversationId, hydrated)
        if (hydrated != conversation) {
            saveConversation(conversationId, hydrated)
        }
        settingsStore.updateAssistant(conversation.assistantId)
    } else {
        // 新建对话分支...
    }
}
```

改后：
```kotlin
suspend fun initializeConversation(conversationId: Uuid) {
    val session = getOrCreateSession(conversationId) // 确保 session 存在
    if (shouldSkipInitializeOnGenerating(session)) {
        Log.d(TAG, "initializeConversation: skipped $conversationId (generating)")
        return
    }
    val conversation = conversationRepo.getConversationById(conversationId)
    if (conversation != null) {
        val hydrated = hydrateConversationFromDb(conversation, session)
        updateConversation(conversationId, hydrated)
        if (hydrated != conversation) {
            saveConversation(conversationId, hydrated)
        }
        settingsStore.updateAssistant(conversation.assistantId)
    } else {
        // 新建对话分支...
    }
}
```

**改动点 2：新增 guard 纯函数（`ChatService.kt`，紧邻 `hydrateConversationFromDb` 附近）**

```kotlin
private fun shouldSkipInitializeOnGenerating(session: ConversationSession): Boolean =
    session.isGenerating
```

**改动量**：1 处 early return（3 行）+ 1 个纯函数（2 行）。

### 2.2 测试代码改动

**测试文件：** `app/src/test/java/me/rerere/rikkahub/service/InitializeGuardTest.kt`（新建）

**测试 1：guard 纯函数**
```kotlin
class InitializeGuardTest {
    private fun makeSession(): ConversationSession = ConversationSession(
        id = Uuid.random(),
        initial = Conversation.ofId(Uuid.random(), DEFAULT_ASSISTANT_ID),
        scope = CoroutineScope(Dispatchers.Unconfined),
        onIdle = {},
    )

    @Test
    fun `shouldSkipInitializeOnGenerating returns true when job active`() {
        val session = makeSession()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch { awaitCancellation() }  // 挂起, isActive=true
        session.setJob(job)
        try {
            assertTrue(shouldSkipInitializeOnGenerating(session))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `shouldSkipInitializeOnGenerating returns false when no job`() {
        val session = makeSession()
        // session.setJob(null) 默认即为非生成
        assertFalse(shouldSkipInitializeOnGenerating(session))
    }
}
```

- 注：`shouldSkipInitializeOnGenerating` 是 private，需测试可见。两个方案：
  - (a) 用 `@VisibleForTesting` + 改为 `internal`（仓库已用 `androidx.annotation.VisibleForTesting` 吗？需确认；若无则直接 `internal`）。
  - (b) 放同一文件用 `@JvmStatic` / 顶层函数。
  - **推荐 (a)**：改为 `internal`（最小可见性提升），测试与生产同 module 可见。

**测试 2：`hydrateConversationFromDb` 补测** —— 与测试 1 同文件 `InitializeGuardTest.kt`（复用 `makeSession()` helper）

**前置重构**：`hydrateConversationFromDb` 从 `ChatService` 成员移为 **top-level `internal fun`**（放 `ChatService.kt` 类外，原位置附近），签名不变。`initializeConversation` 调用处同步改。理由：测它无需 `ChatService` 实例（15 个 final class 依赖，不可构造）。
```kotlin
@Test
fun `hydrate returns loaded as-is when generating`() {
    val session = makeSession()
    session.setJob(activeJob)
    val loaded = conversationWithSubagentStreaming()
    assertEquals(loaded, hydrateConversationFromDb(loaded, session))
}

@Test
fun `hydrate cleans stale streaming when not generating`() {
    val session = makeSession()  // 无 job
    val loaded = conversationWithSubagentStreaming()
    val hydrated = hydrateConversationFromDb(loaded, session)
    // 断言 subagent_streaming metadata 被清为 false（复用 SubagentStreamingConsistencyTest 的断言风格）
    assertNotEquals(loaded, hydrated)
}
```

- 同样需 `hydrateConversationFromDb` 改 `internal`（当前 private）。

### 2.3 调用方影响分析（7 处）

`initializeConversation` 被以下调用，guard 对所有调用方生效，逐一确认：

| # | 调用点 | 生成中时行为 | 是否安全 |
|---|--------|------------|---------|
| 1 | `ChatVM.kt:85`（`init`） | **跳过覆盖** → 修复目标 | ✅ 修复点 |
| 2 | `ConversationRoutes.kt:224`（POST injections） | 跳过 hydrate；后续 `updateConversationState` 基于 session 内存态操作 | ✅ 安全（操作基于内存态） |
| 3 | `ConversationRoutes.kt:271`（POST messages） | 同上 | ✅ |
| 4 | `ConversationRoutes.kt:290`（edit message） | 同上；生成中编辑本就不该发生 | ✅ |
| 5 | `ConversationRoutes.kt:302`（fork） | 跳过；fork 后通常独立处理 | ✅ |
| 6 | `ConversationRoutes.kt:313`（delete message） | 跳过；生成中删除本就危险，跳过更安全 | ✅ |
| 7 | `ConversationRoutes.kt:325`（select node） | 跳过；同上 | ✅ |
| 8 | `ConversationRoutes.kt:366`（SSE stream） | 跳过覆盖 → 客户端看到实时流式内容 | ✅ 正确行为 |

**结论**：所有调用方在 `isGenerating=true` 时跳过 DB 覆盖都是正确或更安全的行为，无误伤。

## 3. 不改动的部分（明确边界）

- **`hydrateConversationFromDb`（`1263-1266`）保持不变**：它已有 `isGenerating` 分支，guard 在它之前拦截后，它只在非生成时被调用。补测它的分支是为了回归保护。
- **`commitConversationState`（`1174-1181`）不加 guard**：guard 在 `initializeConversation` 入口拦截，下游 `commitConversationState` 不需要重复判断（它被多处调用，加 guard 会影响其它路径）。
- **流式期间不落盘**：保持现状（每 chunk 只写内存，生成结束才 `saveConversation`）。周期性 checkpoint 属独立议题。
- **`ConversationRepository` / `SettingsStore` 不抽 interface**：scope 外重构。
- **不引入 mockk / coroutines-test / Robolectric**：保持仓库零 mock 现状。

## 4. 风险与回滚

### 风险
- **R-1**：`isGenerating=true` 但 `session.state` 异常为空（不应发生，in-use session 不被 remove）。接受空白，不引入 merge。
- **R-2**：guard 纯函数可见性从 private 改 internal，理论上让同 module 其它代码可调（但语义清晰，风险低）。
- **R-3**：web 路由在生成中收到请求时跳过 hydrate，若某路由强依赖"DB 最新"可能行为变化。经 §2.3 分析，所有路由后续操作都基于 session 内存态，安全。

### 回滚
- 回滚 = 删除 `initializeConversation` 顶部的 3 行 guard + 删除 `shouldSkipInitializeOnGenerating` 函数。
- 测试文件可保留（guard 删除后 `shouldSkipInitializeOnGenerating` 测试编译失败，一并删；`hydrateConversationFromDb` 测试保留有价值）。

## 5. 验证清单（对应 PRD AC）

| AC | 验证方式 |
|----|---------|
| AC1 | 手动：会话1 子代理运行 → 切会话2 → 切回，内容保留 |
| AC2 | 手动：切回后生成继续，新 chunk 正常 append |
| AC3 | 手动：切走切回不取消 generationJob（日志/behavior 确认） |
| AC4 | 手动：首次进入 / 已结束会话，initializeConversation 正常加载 |
| AC5a | 自动：`InitializeGuardTest` 通过 |
| AC5b | 自动：`hydrateConversationFromDb` 补测通过 |
| AC6 | 自动：`compileDebugKotlin` + 指定测试通过 |

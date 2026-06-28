# Implement: fix-switch-conversation-lose-streaming

执行顺序严格自上而下。每步完成后勾选。验证命令见 §3。

## 前置确认

- [ ] **P0** 确认 `ConversationSession` 可在测试中直接构造（构造参数：`id: Uuid, initial: Conversation, scope: CoroutineScope, onIdle: (Uuid) -> Unit`，全部 public）。✅ 已确认（`ConversationSession.kt:18-23`）。
- [ ] **P0** 确认 `shouldSkipInitializeOnGenerating` / `hydrateConversationFromDb` 改 `internal` 即可被同 module 测试访问（app 模块内）。✅

## Step 1：生产代码改动（`ChatService.kt`）

- [ ] **1.1** 新增 top-level `internal fun shouldSkipInitializeOnGenerating`（放在 `ChatService.kt` 类外，`hydrateConversationFromDb` 附近）：
  ```kotlin
  internal fun shouldSkipInitializeOnGenerating(session: ConversationSession): Boolean =
      session.isGenerating
  ```

- [ ] **1.2** `initializeConversation`（`ChatService.kt:393`）顶部加 guard。改 `getOrCreateSession` 之后、`getConversationById` 之前：
  ```kotlin
      suspend fun initializeConversation(conversationId: Uuid) {
          val session = getOrCreateSession(conversationId) // 确保 session 存在
          if (shouldSkipInitializeOnGenerating(session)) {
              Log.d(TAG, "initializeConversation: skipped $conversationId (generating)")
              return
          }
          val conversation = conversationRepo.getConversationById(conversationId)
          // ... 其余不变
      }
  ```
  - 确认 `Log` / `TAG` 已在文件顶部 import（`ChatService.kt` 已有 `import android.util.Log` + `private const val TAG`，确认行号）。

- [ ] **1.3** `hydrateConversationFromDb`（`ChatService.kt:1263`）**从 `ChatService` 成员移为 top-level `internal fun`**（放类外，原位置附近），签名不变：
  ```kotlin
  internal fun hydrateConversationFromDb(loaded: Conversation, session: ConversationSession): Conversation {
      if (session.isGenerating) return loaded
      return loaded.cleanStaleStreamingMetadata()
  }
  ```
  - `initializeConversation`（`ChatService.kt:397`）调用处同步改：去掉 `hydrateConversationFromDb` 前的隐式 `this.`（top-level 函数直接调用）。
  - 理由：测它无需 `ChatService` 实例（15 个 final class 依赖不可构造）。同 `computeNodeSyncOps`、`cleanStaleSubagentStreaming` 等 top-level / extension 先例。
  - 同样 `shouldSkipInitializeOnGenerating` 也设为 top-level `internal fun`（对称、便于测试）。

- [ ] **1.4** 编译验证：`.\gradlew :app:compileDebugKotlin --no-daemon`
  - 失败则检查 import / 语法 / 可见性。

## Step 2：测试代码

- [ ] **2.1** 新建 `app/src/test/java/me/rerere/rikkahub/service/InitializeGuardTest.kt`：
  - 测 `shouldSkipInitializeOnGenerating`：
    - 构造 `ConversationSession`（`CoroutineScope(Dispatchers.Unconfined)`）。
    - 用例 1：`scope.launch { awaitCancellation() }` → `setJob(job)` → 断言 `shouldSkipInitializeOnGenerating(session) == true` → `finally { job.cancel() }`。
    - 用例 2：不 setJob → 断言 `shouldSkipInitializeOnGenerating(session) == false`。
  - import：`kotlinx.coroutines.*`、`kotlin.uuid.Uuid`、`me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID`、`me.rerere.rikkahub.data.model.Conversation`、`org.junit.Assert.*`、`org.junit.Test`。
  - **不引入** `kotlinx-coroutines-test`（仓库未声明）；用标准 `CoroutineScope(Dispatchers.Unconfined)` + `awaitCancellation()`（`kotlinx-coroutines-core` 已有）。

- [ ] **2.2** 为 `hydrateConversationFromDb` 补测。放同一文件 `InitializeGuardTest.kt`（复用 `makeSession()` helper）：
  - 复用 `SubagentStreamingConsistencyTest.kt` 的 `conversationWithSubagentStreaming` 构造方式（含 `subagent_streaming=true` metadata 的 Conversation）。
  - 用例 1：`session` 无 job（非生成）→ `hydrateConversationFromDb(loaded, session)` → 断言返回值 ≠ loaded（metadata 被清）+ `subagent_streaming` 变 false。
  - 用例 2：`session.setJob(activeJob)`（生成中）→ `hydrateConversationFromDb(loaded, session)` → 断言返回值 == loaded（原样）。
  - top-level `internal fun` 可直接调用，无需 `ChatService` 实例。

- [ ] **2.3** 编译验证：`.\gradlew :app:compileDebugKotlin --no-daemon` + `.\gradlew :app:compileDebugUnitTestKotlin --no-daemon`

## Step 3：跑测试

- [ ] **3.1** `.\gradlew :app:testDebugUnitTest --no-daemon --tests "*InitializeGuardTest*"`
- [ ] **3.2** 失败则读日志修复；不通过不进入 Step 4。

## Step 4：安装到设备验证（手动 AC1-AC4）

- [ ] **4.1** `adb devices` 确认至少一台设备。
- [ ] **4.2** `.\gradlew :app:installDebug --no-daemon`
- [ ] **4.3** 手动验收 AC1：会话1 触发子代理生成（或长文本流式）→ 切会话2 → 切回会话1 → 内容保留，无空消息 + loading。
- [ ] **4.4** 手动验收 AC2：切回后生成继续，新 chunk 正常 append。
- [ ] **4.5** 手动验收 AC4：首次进入新会话 / 打开已结束会话，内容正常加载。
- [ ] **4.6** 手动验收 AC3：切走切回不中断生成（生成继续到完成）。

## Step 5：完成检查

- [ ] **5.1** 所有 AC 勾选完成。
- [ ] **5.2** `git diff` 自查：只改了 `ChatService.kt` + 新增测试文件，无意外改动。
- [ ] **5.3** 准备 commit（等用户确认）。

## 验证命令汇总

```bash
.\gradlew :app:compileDebugKotlin --no-daemon
.\gradlew :app:compileDebugUnitTestKotlin --no-daemon
.\gradlew :app:testDebugUnitTest --no-daemon --tests "*InitializeGuardTest*"
adb devices
.\gradlew :app:installDebug --no-daemon
```

## 回滚点

- 任何 step 失败且无法快速修复 → `git checkout -- app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` + 删除新增测试文件。
- 回到 planning 重新评估。

## Review Gates

- **Gate 1**（Step 1.4 后）：生产代码编译通过，guard 逻辑正确。
- **Gate 2**（Step 3 后）：测试通过。
- **Gate 3**（Step 4 后）：手动验收全通过。

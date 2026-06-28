# Implement: Blocker A — Subagent Streaming JSON/Metadata Consistency

> 子代理派发时 prompt 必须以 `Active task: .trellis/tasks/06-28-blocker-a-subagent-streaming` 开头。

## 步骤

- [ ] **1.1** 读 `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`（`1202-1234` `cleanStaleStreamingMetadata`、`1133-1195` `updateSubagentProgress`、`655-671` onCompletion、`1236-1245` hydrate）。
- [ ] **1.2** 在 `cleanStaleStreamingMetadata` 的 `UIMessagePart.Text` 处理分支内，新增：解析 `text` 为 JsonObject，若含 `streaming` 键且值不为 false，改写为 false 后重新序列化；解析失败或无该键则原样返回。**仅当 metadata 标记为需要清理（`isStreamingSubagent(part)`）时才动 text**，避免误改最终摘要文本。
- [ ] **1.3** 确认 `Json` 实例来源（复用类内已有的 `json` / `JsonInstant`），保持编码风格。
- [ ] **1.4** 新增单测：`app/src/test/java/me/rerere/rikkahub/service/`（或 `data/ai/subagent/`）下新增 `SubagentStreamingConsistencyTest.kt`，构造含 `text JSON streaming:true` + `metadata subagent_streaming:true` 的 Conversation，调清理函数后断言双字段均为 false。
- [ ] **1.5** 若 `cleanStaleStreamingMetadata` 是 `Conversation` 扩展且 private，测试需可见性调整或在同包测试；优先在同包内写测试。

## 验证

- [ ] **2.1** `.\gradlew :app:compileDebugKotlin --no-daemon` 通过（若本任务是最后一个子代理才跑完整编译；否则只跑 `:app:compileDebugKotlin` 子集或交由 D 子代理）。
- [ ] **2.2** `.\gradlew :app:testDebugUnitTest --tests "*SubagentStreaming*" --no-daemon` 通过。

## 回滚点

- 改动集中在 `cleanStaleStreamingMetadata` 单函数 + 新测试文件 → revert 单 commit 即可。

## 不做

- 不改 `SubagentToolUIs`。
- 不改 DB schema。
- 不改 `updateSubagentProgress` 写入逻辑。

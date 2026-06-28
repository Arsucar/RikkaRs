# PRD: Blocker A — Subagent Streaming JSON/Metadata Consistency

## 问题

`ChatService.updateSubagentProgress`（约 `ChatService.kt:1157-1161`）写入 `partialOutputText`，其 JSON 含 `"streaming":true`；而清理路径 `cleanStaleStreamingMetadata`（`1202-1234`）与 `onCompletion` 兜底（`670`）**只**把 metadata 的 `subagent_streaming` 置 `false`，**不**更新 `text` 内 JSON。

结果：流式结束 / 失败 / 取消后，`spawn_subagent` 工具的：
- `metadata.subagent_streaming` = `false` ✅
- `text` JSON `"streaming"` = `true` ❌（残留）

若消费者只解析 `text` JSON（非 metadata），会长期误判为「仍在流式」。

## 目标

让 `text` JSON 的 `streaming` 字段与 `metadata.subagent_streaming` 在流式结束时**同步**为 `false`（或一致刷新）。

## 验收标准

1. **AC-1**：流式正常结束时，`spawn_subagent` 工具 output 内 `UIMessagePart.Text.text`（若为 JSON）的 `"streaming"` 字段为 `false`，且 `metadata.subagent_streaming` 为 `false`。
2. **AC-2**：流式失败 / 取消（`onCompletion` 兜底路径）时，同样双字段同步为 `false`。
3. **AC-3**：`hydrateConversationFromDb` 从 DB 清理 stale metadata 时，**也**同步刷新 `text` JSON 的 `streaming` 字段，并写回 DB。
4. **AC-4**：现有 `SubagentToolUIs` 的 UI 行为（loading 指示、`hasSummary`）不回归。
5. **AC-5**：新增单测覆盖：`cleanStaleStreamingMetadata` 对含 `text` JSON `streaming:true` 的工具 part 同步置 false。
6. **AC-6**：`.\gradlew :app:compileDebugKotlin --no-daemon` 通过。

## 约束

- 不改 `UIMessagePart.Text` 数据类签名。
- 不改 DB schema。
- 复用现有 `cleanStaleStreamingMetadata`，不新建第二套扫描逻辑（避免审计 P2「两套实现」复发）。
- 不破坏现有 `SubagentToolUIs` 的 `remember` 键逻辑。

## 不在本任务范围

- 重构子代理 transcript 序列化格式。
- 改 `updateSubagentProgress` 的 throttling。
- UI 层 `SubagentToolUIs` 重写（仅在验收时确认不回归）。

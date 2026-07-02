# Research: Issue #28 根因总结

- **Query**: 压缩上下文后消息排序错乱、节点意外隐藏、消息丢失
- **Scope**: internal
- **Date**: 2026-07-02

## 结论：**下标错位假设成立**

压缩改变了 `messageNodes` 的物理布局（保留 hidden 节点 + 插入摘要节点），而流式生成仍用 `updateCurrentMessages(visibleMessages)`，其按 **可见列表下标 = 物理 `messageNodes` 下标** 更新。两套坐标系在压缩后不一致，导致写错槽位、内容串位、可见上下文丢更新。

## 机制分解

### 1. 压缩后的结构

示例（`keepRecentMessages = 2`，压缩前 5 条可见）：

| 物理 index | 节点 | hidden |
|------------|------|--------|
| 0 | 旧 user A | true |
| 1 | 旧 assistant B | true |
| 2 | 旧 user C | true |
| 3 | **摘要**（新 user） | false |
| 4 | 保留 user D | false |
| 5 | 保留 assistant E（流式中） | false |

`currentMessages` = [摘要, D, E]（长度 3，可见下标 0..2）。

### 2. 流式 chunk 写回

`chunk.messages` 与上表一致。`updateCurrentMessages` 行为：

- `index 0` → 修改 `newNodes[0]`（**hidden 的 A**），而非 index 3 的摘要节点。
- `index 1` → 修改 `newNodes[1]`（**hidden 的 B**），而非 index 4 的 D。
- `index 2` → 修改 `newNodes[2]`（**hidden 的 C**），而非 index 5 的 E。

结果：assistant 流式内容进入 hidden 槽位；末尾真实 assistant 节点不更新 → **丢失/错乱**。`ChatList` 仍按物理顺序画列表 → 用户看到半透明旧位显示新内容。

### 3. 为何「发给 LLM」一度正常

`handleMessageComplete` 读取 `conversation.currentMessages`（L651），过滤 hidden，顺序对。仅**持久化/UI 状态合并**路径错误。

### 4. 触发条件

- 执行 `compressConversation` 后，同一会话继续发送/生成/regenerate/子代理进度更新。
- 任意存在 `hidden == true` 且可见消息非空的前缀（压缩或 `toggleMessageHidden`）均可触发同类 bug。

## 数据流图（简）

```
compressConversation
  → messageNodes: [hidden*, summary@insertAt, kept*]
  → currentMessages: filter !hidden

handleMessageComplete
  → GenerationHandler(messages = currentMessages)
  → Flow<GenerationChunk.Messages(chunk.messages)>  // 可见列表

collect
  → updateCurrentMessages(chunk.messages)  // BUG: index → physical node
```

## 需要修改的精确位置（实现参考）

| 文件 | 行号 | 说明 |
|------|------|------|
| `Conversation.kt` | **62–94** | **主修复**：`updateCurrentMessages` 应按 `UIMessage.id`（或可见下标→物理节点映射）更新对应 `MessageNode`，禁止 `newNodes[index]` 当可见下标 |
| `ChatService.kt` | **781–784** | 流式 collect；修复函数后此处自动正确，需回归测试 |
| `ChatService.kt` | **1337–1360** | `updateSubagentProgress` 同契约 |
| `ChatService.kt` | **163–203** | `cleanStaleSubagentStreaming` → `updateCurrentMessages` |
| `ChatService.kt` | **1045–1151** | 压缩逻辑可选加固（例如写回时物理重排仅保留可见+摘要），**非必须**若上层写回已修复 |

建议新增单元测试：`messageNodes` 含 leading hidden + 中间 summary 时，对模拟 `chunk.messages` 调用 `updateCurrentMessages`，断言更新落在正确 `node.id` / 非 hidden 槽位。

## 相关文件（研究产出）

- `research/Conversation.kt.md`
- `research/ChatService.kt.md`
- `research/ChatVM.kt.md`
- `research/ChatList.kt.md`
- `research/CompressContextDialog.kt.md`

## Caveats

- `GenerationHandler.kt` 假定传入的 `messages` 列表与写回 API 一致；改 `updateCurrentMessages` 比改 emit 形状更安全。
- 多段 `compressedSummaries` 时每个 summary 节点携带相同 `compressHiddenCount`（L1129–1132），与 #28 无关。
- `ChatList` `loading = index == lastMessageIndex` 在 hidden/摘要存在时可能指向非最后可见消息，属独立 UX 项。
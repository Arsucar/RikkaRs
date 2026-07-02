# Research: Conversation.kt

- **Query**: `currentMessages`, `updateCurrentMessages`, `MessageNode`（hidden / compressHiddenCount）
- **Scope**: internal
- **Date**: 2026-07-02

## File

`app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`

## Key types and properties

### `currentMessages` (L47–52)

```kotlin
val currentMessages
    get(): List<UIMessage> {
        return messageNodes
            .filter { !it.hidden }
            .map { node -> node.messages[node.selectIndex] }
    }
```

- LLM 上下文、压缩输入、`GenerationHandler` 入参、通知文案等，都基于**可见节点**展开后的 `UIMessage` 列表。
- **物理顺序**：`messageNodes` 仍保留 `hidden == true` 的节点；可见列表是子序列，下标与 `messageNodes` 下标**不一一对应**。

### `updateCurrentMessages` (L62–94)

```kotlin
fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
    val newNodes = this.messageNodes.toMutableList()

    messages.forEachIndexed { index, message ->
        val node = newNodes
            .getOrElse(index) { message.toMessageNode() }

        val newMessages = node.messages.toMutableList()
        var newMessageIndex = node.selectIndex
        if (newMessages.any { it.id == message.id }) {
            newMessages[newMessages.indexOfFirst { it.id == message.id }] = message
        } else {
            newMessages.add(message)
            newMessageIndex = newMessages.lastIndex
        }

        val newNode = node.copy(
            messages = newMessages,
            selectIndex = newMessageIndex
        )

        if (index > newNodes.lastIndex) {
            newNodes.add(newNode)
        } else {
            newNodes[index] = newNode
        }
    }

    return this.copy(messageNodes = newNodes)
}
```

- 入参 `messages` 在调用方（流式生成）语义上是 **`currentMessages` 的完整快照**（可见消息列表）。
- 实现却用 **`index` 当作 `messageNodes` 的物理下标** 读写节点。
- 压缩后 `messageNodes` 前段多为 `hidden` 节点，且 `insertAt` 处插入了摘要节点 → **可见下标 0 对应物理下标 0 的假设不成立**。

### `MessageNode` (L112–135)

```kotlin
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
    val hidden: Boolean = false,
    val compressHiddenCount: Int? = null,
    ...
)
```

- `hidden`：压缩或用户手动隐藏；UI 仍渲染但降透明度（见 `ChatList.kt`）。
- `compressHiddenCount`：摘要节点元数据（UI 统计用），不参与 `currentMessages` 过滤逻辑。

## Data flow

| 方向 | 说明 |
|------|------|
| 读上下文 | `messageNodes` → filter `!hidden` → `currentMessages` |
| 写流式更新 | `List<UIMessage>`（可见）→ `updateCurrentMessages` → **按物理 index 写** `messageNodes` |

## Relation to issue #28

压缩只改 `messageNodes` 布局（hidden + 插入摘要），不改编解码 `updateCurrentMessages` 的契约；流式路径仍把可见列表按下标写回全量列表 → **根因落点在本函数**。
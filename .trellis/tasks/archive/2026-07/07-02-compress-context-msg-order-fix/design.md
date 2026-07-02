# Design: updateCurrentMessages 下标错位修复

## 问题

`Conversation.updateCurrentMessages(messages: List<UIMessage>)`（Conversation.kt:62-94）将入参 `messages` 的下标 `index` 当作 `messageNodes` 的物理下标使用：

```kotlin
val node = newNodes.getOrElse(index) { message.toMessageNode() }  // L66-67
...
newNodes[index] = newNode  // L87
```

`messages` 是可见消息列表（`currentMessages` 语义，过滤了 hidden），但 `messageNodes` 含 hidden 节点和压缩插入的摘要节点，两者下标在压缩后不再对齐。

## 修复方案：按 UIMessage.id 匹配可见节点

### 核心逻辑

`updateCurrentMessages` 重写为：

1. 遍历 `messages`，对每个 `UIMessage`：
   - 先在**所有** `messageNodes`（含 hidden）中按 `id` 查找已存在的节点 → 若找到则就地更新该节点的 messages 列表。
   - 若未找到，则创建新 `MessageNode` 并追加到 `newNodes` 末尾。
2. 不再使用 `index` 作为物理下标读写。

### 伪代码

```kotlin
fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
    val newNodes = this.messageNodes.toMutableList()

    for (message in messages) {
        val nodeIndex = newNodes.indexOfFirst { node -> node.messages.any { it.id == message.id } }

        if (nodeIndex >= 0) {
            // 已有节点：就地更新
            val node = newNodes[nodeIndex]
            val newMessages = node.messages.toMutableList()
            val existingIdx = newMessages.indexOfFirst { it.id == message.id }
            if (existingIdx >= 0) {
                newMessages[existingIdx] = message
            } else {
                newMessages.add(message)
            }
            newNodes[nodeIndex] = node.copy(
                messages = newMessages,
                selectIndex = newMessages.indexOfFirst { it.id == message.id }.let { if (it >= 0) it else node.selectIndex }
            )
        } else {
            // 新节点：追加到末尾
            newNodes.add(message.toMessageNode())
        }
    }

    return this.copy(messageNodes = newNodes)
}
```

### 关键设计决策

| 决策 | 理由 |
|------|------|
| 按 `id` 匹配而非「可见下标→物理下标映射」 | 更鲁棒，不依赖任何下标对齐假设；hidden 节点也不会被误写 |
| 新节点追加末尾 | 流式生成新 assistant 消息时，它不属于任何现有节点 → 追加正确 |
| 不改 `compressConversation` | 压缩逻辑本身正确（hidden + 摘要插入），bug 在写回侧 |
| 不改 `GenerationHandler` emit 形状 | 改写回端比改 emit 更安全，调用方契约不变 |

## 受影响文件

| 文件 | 改动 | 风险 |
|------|------|------|
| `Conversation.kt` L62-94 | 重写 `updateCurrentMessages` | 低：行为对非压缩场景等价 |
| `ConversationTest.kt`（新增或已有） | 添加压缩场景测试 | 无 |

## 不受影响

- `ChatService.kt` 各调用点（L781、L1360、L203）：修复函数后自动正确，无需改动。
- `ChatList.kt`：渲染逻辑不变。
- `compressConversation`：逻辑不变。

## 回滚

如出现问题，恢复 `updateCurrentMessages` 原实现即可。影响范围仅限写回路径。

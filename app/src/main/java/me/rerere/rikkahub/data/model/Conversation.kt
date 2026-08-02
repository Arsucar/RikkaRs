package me.rerere.rikkahub.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.InstantSerializer
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Conversation(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val chatModelId: Uuid? = null,
    val title: String = "",
    val messageNodes: List<MessageNode>,
    val chatSuggestions: List<String> = emptyList(),
    val isPinned: Boolean = false,
    @Serializable(with = InstantSerializer::class)
    val createAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updateAt: Instant = Instant.now(),
    val customSystemPrompt: String? = null,
    val modeInjectionIds: Set<Uuid> = emptySet(),
    val lorebookIds: Set<Uuid> = emptySet(),
    // Absolute path inside the workspace rootfs
    val workspaceCwd: String? = null,
    // 所属文件夹（助手内分组），null 表示未归入任何文件夹
    val folderId: Uuid? = null,
    // #89: 对话级记忆表隔离开关。true = 仅注入对话级记忆表，屏蔽助手级/全局，避免重复注入。
    val memoryTableIsolation: Boolean = false,
    /** #220: last tool-step index written as a mid-generation checkpoint; null when Final. */
    val checkpointStep: Int? = null,
    /** #220: whether the durable snapshot is a mid-generation checkpoint (not a completed turn). */
    val isCheckpointSnapshot: Boolean = false,
    /**
     * #217/#216: working copy of conversation variables for the currently selected branch.
     * Branch alternatives store per-message snapshots on [MessageNode.variableSnapshots].
     */
    val variables: Map<String, String> = emptyMap(),
    @Transient
    val newConversation: Boolean = false
) {
    val files: List<Uri>
        get() = messageNodes
            .flatMap { node -> node.messages.flatMap { it.parts } }
            .collectAllParts()
            .mapNotNull { it.fileUri() }

    /**
     *  当前选中的 message
     */
    val currentMessages
        get(): List<UIMessage> {
            return messageNodes
                .filter { !it.hidden }
                .mapNotNull { node ->
                    if (node.messages.isEmpty()) null
                    else node.messages[node.selectIndex.coerceIn(0, node.messages.lastIndex)]
                }
        }

    fun getMessageNodeByMessage(message: UIMessage): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.contains(message) }
    }

    fun getMessageNodeByMessageId(messageId: Uuid): MessageNode? {
        return messageNodes.firstOrNull { node -> node.messages.any { it.id == messageId } }
    }

    fun updateCurrentMessages(messages: List<UIMessage>): Conversation {
        val newNodes = this.messageNodes.toMutableList()
        // 可见节点（!hidden）的物理下标列表，使可见下标与物理下标正确对应
        val visibleIndices = newNodes.mapIndexed { index, node ->
            index to node
        }.filter { !it.second.hidden }.map { it.first }

        messages.forEachIndexed { index, message ->
            val physicalIndex = visibleIndices.getOrNull(index)

            if (physicalIndex != null) {
                val node = newNodes[physicalIndex]
                val newMessages = node.messages.toMutableList()
                var newMessageIndex = node.selectIndex
                val existingIdx = newMessages.indexOfFirst { it.id == message.id }
                if (existingIdx >= 0) {
                    newMessages[existingIdx] = message
                    newMessageIndex = existingIdx
                } else {
                    newMessages.add(message)
                    newMessageIndex = newMessages.lastIndex
                }
                newNodes[physicalIndex] = node.copy(
                    messages = newMessages,
                    selectIndex = newMessageIndex,
                )
            } else {
                // 超出可见节点数量，追加到末尾
                newNodes.add(message.toMessageNode())
            }
        }

        return this.copy(
            messageNodes = newNodes
        )
    }

    companion object {
        fun ofId(
            id: Uuid,
            assistantId: Uuid = DEFAULT_ASSISTANT_ID,
            messages: List<MessageNode> = emptyList(),
            newConversation: Boolean = false
        ) = Conversation(
            id = id,
            assistantId = assistantId,
            messageNodes = messages,
            newConversation = newConversation,
        )
    }
}

@Serializable
data class MessageNode(
    val id: Uuid = Uuid.random(),
    val messages: List<UIMessage>,
    val selectIndex: Int = 0,
    val hidden: Boolean = false,
    val compressHiddenCount: Int? = null,
    /**
     * #217/#216: per-alternative variable snapshot keyed by message id (string).
     * Written when that assistant alternative finishes generation; restored on selectIndex change.
     */
    val variableSnapshots: Map<String, Map<String, String>> = emptyMap(),
    @Transient
    val isFavorite: Boolean = false,
) {
    val currentMessage get() = when {
        messages.isEmpty() -> throw IllegalStateException("MessageNode has no messages")
        else -> messages[selectIndex.coerceIn(0, messages.lastIndex)]
    }

    val role get() = messages.firstOrNull()?.role ?: MessageRole.USER

    companion object {
        fun of(message: UIMessage) = MessageNode(
            messages = listOf(message),
            selectIndex = 0
        )
    }
}

fun UIMessage.toMessageNode(): MessageNode {
    return MessageNode(
        messages = listOf(this),
        selectIndex = 0
    )
}

/**
 * Store a branch variable snapshot for [messageId] on the node that contains that message.
 */
fun Conversation.withVariableSnapshot(
    messageId: Uuid,
    variables: Map<String, String>,
): Conversation {
    val key = messageId.toString()
    val updatedNodes = messageNodes.map { node ->
        if (node.messages.any { it.id == messageId }) {
            node.copy(
                variableSnapshots = node.variableSnapshots + (key to variables),
            )
        } else {
            node
        }
    }
    return copy(messageNodes = updatedNodes, variables = variables)
}

/**
 * 递归展开所有 parts，包括工具调用结果中的嵌套 parts。
 */
private fun List<UIMessagePart>.collectAllParts(): List<UIMessagePart> =
    this + filterIsInstance<UIMessagePart.Tool>().flatMap { it.output.collectAllParts() }

/**
 * 提取 part 中引用的本地文件 URI，新增文件类型时只需在此处添加。
 */
private fun UIMessagePart.fileUri(): Uri? = when (this) {
    is UIMessagePart.Image -> url.takeIf { it.startsWith("file://") }?.toUri()
    is UIMessagePart.Document -> url.takeIf { it.startsWith("file://") }?.toUri()
    is UIMessagePart.Video -> url.takeIf { it.startsWith("file://") }?.toUri()
    is UIMessagePart.Audio -> url.takeIf { it.startsWith("file://") }?.toUri()
    else -> null
}

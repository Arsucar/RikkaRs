package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

@Serializable
enum class FavoriteType(val value: String) {
    @SerialName("node")
    NODE("node"),

    @SerialName("image")
    IMAGE("image"),

    // Keep old value for compatibility with existing data.
    @SerialName("message")
    MESSAGE("message");

    companion object {
        fun fromValue(value: String): FavoriteType? = entries.firstOrNull { it.value == value }
    }
}

@Serializable
data class FavoriteMeta(
    val title: String? = null,
    val subtitle: String? = null,
    val previewText: String? = null,
    val collectionId: String? = null,
)

@Serializable
data class NodeFavoriteRef(
    val conversationId: Uuid,
    val nodeId: Uuid,
)

data class NodeFavoriteTarget(
    val conversationId: Uuid,
    val conversationTitle: String,
    val nodeId: Uuid,
    val node: MessageNode,
)

@Serializable
data class ImageFavoriteRef(
    val imageId: Int,
)

@Serializable
data class ImageFavoriteSnapshot(
    val imageId: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String,
    val type: String,
    val sourcePaths: String? = null,
)

data class ImageFavoriteTarget(
    val imageId: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String,
    val type: String,
    val sourcePaths: String? = null,
    val collectionId: String? = null,
)

fun UIMessage.buildFavoritePreview(maxLength: Int = 160): String {
    val plainText = parts
        .filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text.trim() }
        .trim()
    if (plainText.isNotBlank()) {
        return plainText.take(maxLength)
    }
    return when (role) {
        MessageRole.USER -> "[User Message]"
        MessageRole.ASSISTANT -> "[Assistant Message]"
        MessageRole.SYSTEM -> "[System Message]"
        MessageRole.TOOL -> "[Tool Message]"
    }
}

fun MessageNode.buildFavoritePreview(maxLength: Int = 160): String {
    return currentMessage.buildFavoritePreview(maxLength)
}

package me.rerere.rikkahub.data.model

import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import kotlin.uuid.Uuid

data class ConversationTag(
    val id: Uuid,
    val displayName: String,
    val colorKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ConversationTagRelation(
    val conversationId: Uuid,
    val tagId: Uuid,
)

data class ConversationTagReferenceCount(
    val tagId: Uuid,
    val count: Int,
)

data class NormalizedConversationTagName(
    val displayName: String,
    val normalizedName: String,
)

object ConversationTagRules {
    const val MAX_NAME_CODE_POINTS = 40
    const val MAX_TAGS = 100
    const val MAX_TAGS_PER_CONVERSATION = 20

    val COLOR_PALETTE = listOf(
        "red",
        "orange",
        "amber",
        "green",
        "teal",
        "blue",
        "indigo",
        "purple",
        "pink",
        "gray",
    )

    private val collapsedWhitespace = Regex("[\\p{Z}\\s]+")

    fun normalizeName(value: String): NormalizedConversationTagName {
        val displayName = Normalizer.normalize(value, Normalizer.Form.NFC)
            .trim { it.isWhitespace() || Character.isSpaceChar(it) }
            .replace(collapsedWhitespace, " ")
        if (displayName.isEmpty()) {
            throw ConversationTagException(ConversationTagErrorCode.EMPTY_NAME)
        }
        if (Character.codePointCount(displayName, 0, displayName.length) > MAX_NAME_CODE_POINTS) {
            throw ConversationTagException(ConversationTagErrorCode.NAME_TOO_LONG)
        }
        return NormalizedConversationTagName(
            displayName = displayName,
            normalizedName = Normalizer.normalize(
                displayName.uppercase(Locale.ROOT).lowercase(Locale.ROOT),
                Normalizer.Form.NFC,
            ),
        )
    }

    fun requireValidColor(colorKey: String): String {
        if (colorKey !in COLOR_PALETTE) {
            throw ConversationTagException(ConversationTagErrorCode.INVALID_COLOR)
        }
        return colorKey
    }

    fun defaultColor(tagCount: Int): String = COLOR_PALETTE[tagCount.mod(COLOR_PALETTE.size)]
}

enum class ConversationTagErrorCode {
    EMPTY_NAME,
    NAME_TOO_LONG,
    INVALID_COLOR,
    DUPLICATE_NAME,
    TAG_LIMIT_REACHED,
    CONVERSATION_TAG_LIMIT_REACHED,
    TAG_NOT_FOUND,
    CONVERSATION_NOT_FOUND,
    SAME_TAG,
}

class ConversationTagException(
    val code: ConversationTagErrorCode,
) : IllegalArgumentException(code.name)

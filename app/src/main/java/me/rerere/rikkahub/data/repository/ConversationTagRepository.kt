package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.ConversationTagDAO
import me.rerere.rikkahub.data.db.dao.TagMutationResult
import me.rerere.rikkahub.data.db.entity.ConversationTagEntity
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.model.ConversationTagErrorCode
import me.rerere.rikkahub.data.model.ConversationTagException
import me.rerere.rikkahub.data.model.ConversationTagReferenceCount
import me.rerere.rikkahub.data.model.ConversationTagRelation
import me.rerere.rikkahub.data.model.ConversationTagRules
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationTagRepository(
    private val dao: ConversationTagDAO,
) {
    fun observeTags(): Flow<List<ConversationTag>> = dao.observeTags().map { entities ->
        entities.map { it.toModel() }
    }

    fun observeRelations(): Flow<List<ConversationTagRelation>> = dao.observeRelations().map { relations ->
        relations.map { relation ->
            ConversationTagRelation(
                conversationId = Uuid.parse(relation.conversationId),
                tagId = Uuid.parse(relation.tagId),
            )
        }
    }

    fun observeReferenceCounts(): Flow<List<ConversationTagReferenceCount>> =
        dao.observeReferenceCounts().map { counts ->
            counts.map { count ->
                ConversationTagReferenceCount(
                    tagId = Uuid.parse(count.tagId),
                    count = count.count,
                )
            }
        }

    fun observeTagsForConversation(conversationId: Uuid): Flow<List<ConversationTag>> =
        dao.observeTagsForConversation(conversationId.toString()).map { entities ->
            entities.map { it.toModel() }
        }

    suspend fun getTagsForConversation(conversationId: Uuid): List<ConversationTag> =
        dao.getTagsForConversation(conversationId.toString()).map { it.toModel() }

    suspend fun getTag(tagId: Uuid): ConversationTag? = dao.getTagById(tagId.toString())?.toModel()

    suspend fun findTagByName(name: String): ConversationTag? {
        val normalized = ConversationTagRules.normalizeName(name)
        return dao.getTagByNormalizedName(normalized.normalizedName)?.toModel()
    }

    suspend fun getReferenceCount(tagId: Uuid): Int {
        if (dao.getTagById(tagId.toString()) == null) throw tagError(ConversationTagErrorCode.TAG_NOT_FOUND)
        return dao.countReferencesForTag(tagId.toString())
    }

    suspend fun createTag(name: String, colorKey: String? = null): ConversationTag {
        val normalized = ConversationTagRules.normalizeName(name)
        val resolvedColor = colorKey?.let(ConversationTagRules::requireValidColor)
            ?: ConversationTagRules.defaultColor(dao.countTags())
        val now = System.currentTimeMillis()
        val entity = ConversationTagEntity(
            id = Uuid.random().toString(),
            normalizedName = normalized.normalizedName,
            displayName = normalized.displayName,
            colorKey = resolvedColor,
            createdAt = now,
            updatedAt = now,
        )
        dao.createTag(entity, ConversationTagRules.MAX_TAGS).requireChanged()
        return entity.toModel()
    }

    suspend fun renameTag(tagId: Uuid, name: String): ConversationTag {
        val normalized = ConversationTagRules.normalizeName(name)
        dao.renameTag(
            tagId = tagId.toString(),
            normalizedName = normalized.normalizedName,
            displayName = normalized.displayName,
            updatedAt = System.currentTimeMillis(),
        ).requireChanged()
        return dao.getTagById(tagId.toString())?.toModel()
            ?: throw tagError(ConversationTagErrorCode.TAG_NOT_FOUND)
    }

    suspend fun recolorTag(tagId: Uuid, colorKey: String): ConversationTag {
        val validColor = ConversationTagRules.requireValidColor(colorKey)
        dao.recolorTag(tagId.toString(), validColor, System.currentTimeMillis()).requireChanged()
        return dao.getTagById(tagId.toString())?.toModel()
            ?: throw tagError(ConversationTagErrorCode.TAG_NOT_FOUND)
    }

    suspend fun deleteTag(tagId: Uuid) {
        dao.deleteTag(tagId.toString()).requireChanged()
    }

    suspend fun mergeTag(sourceTagId: Uuid, targetTagId: Uuid) {
        dao.mergeTag(sourceTagId.toString(), targetTagId.toString()).requireChanged()
    }

    suspend fun addTag(conversationId: Uuid, tagId: Uuid): Boolean =
        dao.addTag(
            conversationId = conversationId.toString(),
            tagId = tagId.toString(),
            maxTagsPerConversation = ConversationTagRules.MAX_TAGS_PER_CONVERSATION,
        ).requireChangeOrNoOp()

    suspend fun removeTag(conversationId: Uuid, tagId: Uuid): Boolean =
        dao.removeTag(conversationId.toString(), tagId.toString()).requireChangeOrNoOp()
}

private fun ConversationTagEntity.toModel(): ConversationTag = ConversationTag(
    id = Uuid.parse(id),
    displayName = displayName,
    colorKey = colorKey,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

private fun TagMutationResult.requireChanged() {
    if (this != TagMutationResult.CHANGED) throw toException()
}

private fun TagMutationResult.requireChangeOrNoOp(): Boolean = when (this) {
    TagMutationResult.CHANGED -> true
    TagMutationResult.NO_CHANGE -> false
    else -> throw toException()
}

private fun TagMutationResult.toException(): ConversationTagException = tagError(
    when (this) {
        TagMutationResult.DUPLICATE_NAME -> ConversationTagErrorCode.DUPLICATE_NAME
        TagMutationResult.TAG_LIMIT_REACHED -> ConversationTagErrorCode.TAG_LIMIT_REACHED
        TagMutationResult.CONVERSATION_TAG_LIMIT_REACHED ->
            ConversationTagErrorCode.CONVERSATION_TAG_LIMIT_REACHED
        TagMutationResult.TAG_NOT_FOUND -> ConversationTagErrorCode.TAG_NOT_FOUND
        TagMutationResult.CONVERSATION_NOT_FOUND -> ConversationTagErrorCode.CONVERSATION_NOT_FOUND
        TagMutationResult.SAME_TAG -> ConversationTagErrorCode.SAME_TAG
        TagMutationResult.CHANGED,
        TagMutationResult.NO_CHANGE,
        -> error("Successful tag result cannot be converted to an exception")
    }
)

private fun tagError(code: ConversationTagErrorCode) = ConversationTagException(code)

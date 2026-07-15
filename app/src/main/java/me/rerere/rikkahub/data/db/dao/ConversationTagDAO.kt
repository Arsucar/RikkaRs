package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ConversationTagCrossRef
import me.rerere.rikkahub.data.db.entity.ConversationTagEntity
import me.rerere.rikkahub.data.db.entity.ConversationTagReferenceCountEntity

@Dao
interface ConversationTagDAO {
    @Query("SELECT * FROM conversation_tags ORDER BY normalized_name ASC, id ASC")
    fun observeTags(): Flow<List<ConversationTagEntity>>

    @Query("SELECT * FROM conversation_tag_cross_ref ORDER BY conversation_id ASC, tag_id ASC")
    fun observeRelations(): Flow<List<ConversationTagCrossRef>>

    @Query(
        """
        SELECT tags.id AS tag_id, COUNT(relations.conversation_id) AS count
        FROM conversation_tags AS tags
        LEFT JOIN conversation_tag_cross_ref AS relations ON relations.tag_id = tags.id
        GROUP BY tags.id
        ORDER BY tags.normalized_name ASC, tags.id ASC
        """
    )
    fun observeReferenceCounts(): Flow<List<ConversationTagReferenceCountEntity>>

    @Query(
        """
        SELECT tags.* FROM conversation_tags AS tags
        INNER JOIN conversation_tag_cross_ref AS relations ON relations.tag_id = tags.id
        WHERE relations.conversation_id = :conversationId
        ORDER BY tags.normalized_name ASC, tags.id ASC
        """
    )
    fun observeTagsForConversation(conversationId: String): Flow<List<ConversationTagEntity>>

    @Query(
        """
        SELECT tags.* FROM conversation_tags AS tags
        INNER JOIN conversation_tag_cross_ref AS relations ON relations.tag_id = tags.id
        WHERE relations.conversation_id = :conversationId
        ORDER BY tags.normalized_name ASC, tags.id ASC
        """
    )
    suspend fun getTagsForConversation(conversationId: String): List<ConversationTagEntity>

    @Query("SELECT * FROM conversation_tags WHERE id = :tagId")
    suspend fun getTagById(tagId: String): ConversationTagEntity?

    @Query("SELECT * FROM conversation_tags WHERE normalized_name = :normalizedName")
    suspend fun getTagByNormalizedName(normalizedName: String): ConversationTagEntity?

    @Query("SELECT COUNT(*) FROM conversation_tags")
    suspend fun countTags(): Int

    @Query("SELECT COUNT(*) FROM conversation_tag_cross_ref WHERE conversation_id = :conversationId")
    suspend fun countTagsForConversation(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM conversation_tag_cross_ref WHERE tag_id = :tagId")
    suspend fun countReferencesForTag(tagId: String): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM conversation_tag_cross_ref
            WHERE conversation_id = :conversationId AND tag_id = :tagId
        )
        """
    )
    suspend fun relationExists(conversationId: String, tagId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM ConversationEntity WHERE id = :conversationId)")
    suspend fun conversationExists(conversationId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagIgnore(entity: ConversationTagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelationIgnore(relation: ConversationTagCrossRef): Long

    @Query(
        """
        UPDATE conversation_tags SET
            normalized_name = :normalizedName,
            display_name = :displayName,
            updated_at = :updatedAt
        WHERE id = :tagId
        """
    )
    suspend fun updateName(
        tagId: String,
        normalizedName: String,
        displayName: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE conversation_tags SET color_key = :colorKey, updated_at = :updatedAt WHERE id = :tagId")
    suspend fun updateColor(tagId: String, colorKey: String, updatedAt: Long): Int

    @Query("DELETE FROM conversation_tags WHERE id = :tagId")
    suspend fun deleteTagById(tagId: String): Int

    @Query(
        """
        DELETE FROM conversation_tag_cross_ref
        WHERE conversation_id = :conversationId AND tag_id = :tagId
        """
    )
    suspend fun deleteRelation(conversationId: String, tagId: String): Int

    @Query(
        """
        INSERT OR IGNORE INTO conversation_tag_cross_ref(conversation_id, tag_id)
        SELECT conversation_id, :targetTagId
        FROM conversation_tag_cross_ref
        WHERE tag_id = :sourceTagId
        """
    )
    suspend fun mergeRelations(sourceTagId: String, targetTagId: String)

    @Query(
        """
        INSERT OR IGNORE INTO conversation_tag_cross_ref(conversation_id, tag_id)
        SELECT :targetConversationId, tag_id
        FROM conversation_tag_cross_ref
        WHERE conversation_id = :sourceConversationId
        """
    )
    suspend fun copyRelationsForFork(sourceConversationId: String, targetConversationId: String)

    @Transaction
    suspend fun createTag(entity: ConversationTagEntity, maxTags: Int): TagMutationResult {
        if (getTagByNormalizedName(entity.normalizedName) != null) return TagMutationResult.DUPLICATE_NAME
        if (countTags() >= maxTags) return TagMutationResult.TAG_LIMIT_REACHED
        return if (insertTagIgnore(entity) == -1L) {
            TagMutationResult.DUPLICATE_NAME
        } else {
            TagMutationResult.CHANGED
        }
    }

    @Transaction
    suspend fun renameTag(
        tagId: String,
        normalizedName: String,
        displayName: String,
        updatedAt: Long,
    ): TagMutationResult {
        val current = getTagById(tagId) ?: return TagMutationResult.TAG_NOT_FOUND
        val conflict = getTagByNormalizedName(normalizedName)
        if (conflict != null && conflict.id != current.id) return TagMutationResult.DUPLICATE_NAME
        updateName(tagId, normalizedName, displayName, updatedAt)
        return TagMutationResult.CHANGED
    }

    @Transaction
    suspend fun recolorTag(tagId: String, colorKey: String, updatedAt: Long): TagMutationResult {
        if (getTagById(tagId) == null) return TagMutationResult.TAG_NOT_FOUND
        updateColor(tagId, colorKey, updatedAt)
        return TagMutationResult.CHANGED
    }

    @Transaction
    suspend fun deleteTag(tagId: String): TagMutationResult {
        if (getTagById(tagId) == null) return TagMutationResult.TAG_NOT_FOUND
        deleteTagById(tagId)
        return TagMutationResult.CHANGED
    }

    @Transaction
    suspend fun mergeTag(sourceTagId: String, targetTagId: String): TagMutationResult {
        if (sourceTagId == targetTagId) return TagMutationResult.SAME_TAG
        if (getTagById(sourceTagId) == null || getTagById(targetTagId) == null) {
            return TagMutationResult.TAG_NOT_FOUND
        }
        mergeRelations(sourceTagId, targetTagId)
        deleteTagById(sourceTagId)
        return TagMutationResult.CHANGED
    }

    @Transaction
    suspend fun addTag(
        conversationId: String,
        tagId: String,
        maxTagsPerConversation: Int,
    ): TagMutationResult {
        if (!conversationExists(conversationId)) return TagMutationResult.CONVERSATION_NOT_FOUND
        if (getTagById(tagId) == null) return TagMutationResult.TAG_NOT_FOUND
        if (relationExists(conversationId, tagId)) return TagMutationResult.NO_CHANGE
        if (countTagsForConversation(conversationId) >= maxTagsPerConversation) {
            return TagMutationResult.CONVERSATION_TAG_LIMIT_REACHED
        }
        return if (insertRelationIgnore(ConversationTagCrossRef(conversationId, tagId)) == -1L) {
            TagMutationResult.NO_CHANGE
        } else {
            TagMutationResult.CHANGED
        }
    }

    @Transaction
    suspend fun removeTag(conversationId: String, tagId: String): TagMutationResult {
        if (!conversationExists(conversationId)) return TagMutationResult.CONVERSATION_NOT_FOUND
        if (getTagById(tagId) == null) return TagMutationResult.TAG_NOT_FOUND
        return if (deleteRelation(conversationId, tagId) == 0) {
            TagMutationResult.NO_CHANGE
        } else {
            TagMutationResult.CHANGED
        }
    }
}

enum class TagMutationResult {
    CHANGED,
    NO_CHANGE,
    DUPLICATE_NAME,
    TAG_LIMIT_REACHED,
    CONVERSATION_TAG_LIMIT_REACHED,
    TAG_NOT_FOUND,
    CONVERSATION_NOT_FOUND,
    SAME_TAG,
}

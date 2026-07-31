// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Semantic/episodic memory row (physically isolated from [MemoryEntity]).
 * embedding is a JSON float array string, e.g. "[0.12, 0.34, ...]".
 */
@Entity(
    tableName = "episodic_memory",
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["assistant_id", "is_core"]),
    ],
)
data class EpisodicMemoryEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("content")
    val content: String = "",
    @ColumnInfo("summary")
    val summary: String = "",
    @ColumnInfo("importance")
    val importance: Int = 3,
    @ColumnInfo("is_core")
    val isCore: Boolean = false,
    @ColumnInfo("embedding")
    val embedding: String? = null,
    @ColumnInfo("embedding_model")
    val embeddingModel: String? = null,
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo("updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo("last_recalled_at")
    val lastRecalledAt: Long? = null,
    @ColumnInfo("recall_count")
    val recallCount: Int = 0,
    @ColumnInfo("source_conversation_id")
    val sourceConversationId: String? = null,
    @ColumnInfo("source_message_index")
    val sourceMessageIndex: Int? = null,
)

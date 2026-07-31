// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Per-(assistant, conversation) summarize cursor for semantic memory auto-trigger. */
@Entity(
    tableName = "semantic_memory_state",
    primaryKeys = ["assistant_id", "conversation_id"],
)
data class SemanticMemoryStateEntity(
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("last_summarized_message_count")
    val lastSummarizedMessageCount: Int = 0,
    @ColumnInfo("last_summarized_at")
    val lastSummarizedAt: Long = 0,
)

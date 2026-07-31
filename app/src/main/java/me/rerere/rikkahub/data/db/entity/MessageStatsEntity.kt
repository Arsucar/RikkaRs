package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_stats",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversation_id")],
)
data class MessageStatsEntity(
    @PrimaryKey
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("message_count")
    val messageCount: Int,
    @ColumnInfo("user_message_count")
    val userMessageCount: Int,
    @ColumnInfo("token_input")
    val tokenInput: Long,
    @ColumnInfo("token_output")
    val tokenOutput: Long,
    @ColumnInfo("token_cached")
    val tokenCached: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "message_stats_daily",
    primaryKeys = ["conversation_id", "day"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("conversation_id"),
        Index("day"),
    ],
)
data class MessageStatsDailyEntity(
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("day")
    val day: String,
    @ColumnInfo("user_message_count")
    val userMessageCount: Int,
)

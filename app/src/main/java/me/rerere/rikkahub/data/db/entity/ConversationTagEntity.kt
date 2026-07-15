package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_tags",
    indices = [
        Index(value = ["normalized_name"], unique = true),
        Index(value = ["created_at"]),
    ],
)
data class ConversationTagEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("normalized_name")
    val normalizedName: String,
    @ColumnInfo("display_name")
    val displayName: String,
    @ColumnInfo("color_key")
    val colorKey: String,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "conversation_tag_cross_ref",
    primaryKeys = ["conversation_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id", "conversation_id"])],
)
data class ConversationTagCrossRef(
    @ColumnInfo("conversation_id")
    val conversationId: String,
    @ColumnInfo("tag_id")
    val tagId: String,
)

data class ConversationTagReferenceCountEntity(
    @ColumnInfo("tag_id")
    val tagId: String,
    val count: Int,
)

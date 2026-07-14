package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subagent_contexts",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
        Index(value = ["parent_assistant_id", "conversation_id"]),
    ],
)
data class SubagentContextEntity(
    @PrimaryKey @ColumnInfo(name = "context_id") val contextId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String?,
    @ColumnInfo(name = "parent_assistant_id") val parentAssistantId: String,
    @ColumnInfo(name = "scope_json") val scopeJson: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "messages_json") val messagesJson: String,
    @ColumnInfo(name = "usage_json") val usageJson: String?,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    @ColumnInfo(name = "expires_at") val expiresAtMillis: Long,
    @ColumnInfo(name = "revision") val revision: Long,
)

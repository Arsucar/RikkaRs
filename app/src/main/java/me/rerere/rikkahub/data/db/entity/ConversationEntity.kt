package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * #288: composite indexes covering the ConversationDAO queries that ORDER BY
 * is_pinned DESC, update_at DESC with various WHERE clauses, plus create_at
 * range scans. Eliminates full table scans as conversation count grows.
 *
 * Note: the table name is the class name (mixed-case "ConversationEntity"),
 * so Room generates index names with that mixed-case prefix; the migration
 * SQL must use the identical names or Room's schema validation will fail.
 */
@Entity(
    indices = [
        Index(value = ["assistant_id", "is_pinned", "update_at"]),
        Index(value = ["folder_id", "is_pinned", "update_at"]),
        Index(value = ["is_pinned", "update_at"]),
        Index(value = ["create_at"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("assistant_id", defaultValue = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
    val assistantId: String,
    @ColumnInfo("chat_model_id", defaultValue = "")
    val chatModelId: String = "",
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("nodes")
    val nodes: String,
    @ColumnInfo("create_at")
    val createAt: Long,
    @ColumnInfo("update_at")
    val updateAt: Long,
    @ColumnInfo("suggestions", defaultValue = "[]")
    val chatSuggestions: String,
    @ColumnInfo("is_pinned", defaultValue = "0")
    val isPinned: Boolean,
    @ColumnInfo("custom_system_prompt", defaultValue = "")
    val customSystemPrompt: String = "",
    @ColumnInfo("lorebook_ids", defaultValue = "[]")
    val lorebookIds: String = "[]",
    @ColumnInfo("workspace_cwd", defaultValue = "")
    val workspaceCwd: String = "",
    @ColumnInfo("folder_id", defaultValue = "")
    val folderId: String = "",
    @ColumnInfo("memory_table_isolation", defaultValue = "0")
    val memoryTableIsolation: Boolean = false,
    /** #220: last checkpoint tool step; empty string means none (Final snapshot). */
    @ColumnInfo("checkpoint_step", defaultValue = "")
    val checkpointStep: String = "",
    /** #220: 1 = mid-generation checkpoint snapshot, 0 = final/completed snapshot. */
    @ColumnInfo("is_checkpoint_snapshot", defaultValue = "0")
    val isCheckpointSnapshot: Boolean = false,
    /** #217/#216: conversation variables JSON map (string→string). */
    @ColumnInfo("variables", defaultValue = "{}")
    val variables: String = "{}",
)

package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * #96: stored revision snapshot of a memory table document payload. Written before
 * each destructive/overwriting document update so a prior revision can be restored.
 */
@Entity(
    tableName = "memory_table_snapshots",
    indices = [
        Index(value = ["document_id"]),
        Index(value = ["document_id", "revision"]),
        Index(value = ["created_at"]),
    ],
)
data class MemoryTableSnapshotEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("document_id")
    val documentId: String,
    @ColumnInfo("revision")
    val revision: Int,
    @ColumnInfo("payload_json")
    val payloadJson: String,
    @ColumnInfo("created_at")
    val createdAt: Long,
)

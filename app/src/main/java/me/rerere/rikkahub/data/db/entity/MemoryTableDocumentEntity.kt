package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_table_documents",
    indices = [
        Index(value = ["template_id"]),
        Index(value = ["scope_type", "scope_id"]),
        Index(value = ["updated_at"]),
    ],
)
data class MemoryTableDocumentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("template_id")
    val templateId: String,
    @ColumnInfo("scope_type")
    val scopeType: String,
    @ColumnInfo("scope_id")
    val scopeId: String,
    @ColumnInfo("payload_json")
    val payloadJson: String,
    @ColumnInfo("revision")
    val revision: Int,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
    // #89: assistant-scoped source document id this row follows (null when standalone).
    @ColumnInfo("source_document_id")
    val sourceDocumentId: String? = null,
    // #89: 1 = payload mirrors source document, 0 = detached for independent edits.
    @ColumnInfo(name = "follow_source", defaultValue = "0")
    val followSource: Boolean = false,
)

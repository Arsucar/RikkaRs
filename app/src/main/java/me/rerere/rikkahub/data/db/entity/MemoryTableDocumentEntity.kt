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
)

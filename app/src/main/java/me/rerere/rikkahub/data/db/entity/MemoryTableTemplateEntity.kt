package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_table_templates",
    indices = [
        Index(value = ["scope_type", "scope_id"]),
        Index(value = ["updated_at"]),
    ],
)
data class MemoryTableTemplateEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("name")
    val name: String,
    @ColumnInfo("description")
    val description: String,
    @ColumnInfo("schema_json")
    val schemaJson: String,
    @ColumnInfo(name = "scope_type", defaultValue = "GLOBAL")
    val scopeType: String,
    @ColumnInfo(name = "scope_id", defaultValue = "__global__")
    val scopeId: String,
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

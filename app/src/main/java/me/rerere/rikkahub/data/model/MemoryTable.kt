package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class MemoryTableTemplate(
    val id: String = Uuid.random().toString(),
    val name: String = "",
    val description: String = DEFAULT_MEMORY_TABLE_DESCRIPTION,
    val schemaJson: String = DEFAULT_MEMORY_TABLE_SCHEMA_JSON,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class MemoryTableDocument(
    val id: String = Uuid.random().toString(),
    val templateId: String,
    val scopeType: MemoryTableScopeType = MemoryTableScopeType.ASSISTANT,
    val scopeId: String,
    val payloadJson: String = "{}",
    val revision: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
enum class MemoryTableScopeType {
    CONVERSATION,
    ASSISTANT,
    GLOBAL;

    companion object {
        fun fromStorage(value: String?): MemoryTableScopeType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ASSISTANT
        }
    }
}

fun shouldEnableMemoryTable(settingsEnabled: Boolean, assistantEnabled: Boolean): Boolean {
    return settingsEnabled && assistantEnabled
}

const val DEFAULT_MEMORY_TABLE_DESCRIPTION = "General key-value facts for assistant memory lookup."

const val DEFAULT_MEMORY_TABLE_SCHEMA_JSON = """
{
  "tables": [
    {
      "name": "facts",
      "columns": [
        { "name": "key", "type": "string" },
        { "name": "value", "type": "string" }
      ],
      "injectPolicy": { "enabled": true },
      "updatePolicy": { "enabled": true }
    }
  ],
  "maxInjectTokens": 800
}
"""

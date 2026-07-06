package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool

internal fun Tool.openAIParametersSchemaElement(json: Json): JsonElement {
    val schema: InputSchema = parameters() ?: InputSchema.Obj(properties = JsonObject(emptyMap()))
    return json.encodeToJsonElement(schema)
}

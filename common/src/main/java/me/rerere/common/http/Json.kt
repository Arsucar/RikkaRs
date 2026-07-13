package me.rerere.common.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

val JsonElement.jsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

val JsonElement.jsonArrayOrNull: JsonArray?
    get() = this as? JsonArray

val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

fun JsonObject.getByKey(key: String): String {
    return evaluateJsonExpr(key, this)
}

/**
 * Prefer object; if the value is a JSON string literal, parse it as object;
 * otherwise return empty object. Used for relay APIs that stringify tool inputs.
 */
fun JsonElement?.asJsonObjectLenient(): JsonObject {
    if (this == null) return JsonObject(emptyMap())
    jsonObjectOrNull?.let { return it }
    val content = jsonPrimitiveOrNull?.content ?: return JsonObject(emptyMap())
    return runCatching {
        Json.Default.parseToJsonElement(content).jsonObjectOrNull
    }.getOrNull() ?: JsonObject(emptyMap())
}

package me.rerere.common.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class JsonTest {
    @Test
    fun `lenient object conversion keeps object instance`() {
        val original = Json.parseToJsonElement("""{"query":"kotlin"}""") as JsonObject

        assertSame(original, original.asJsonObjectLenient())
    }

    @Test
    fun `lenient object conversion parses stringified object`() {
        val encoded = JsonPrimitive("""{"query":"kotlin","limit":3}""")
        val expected = Json.parseToJsonElement("""{"query":"kotlin","limit":3}""")

        assertEquals(expected, encoded.asJsonObjectLenient())
    }

    @Test
    fun `lenient object conversion returns empty object for unsupported values`() {
        val unsupported: List<JsonElement?> = listOf(
            null,
            JsonNull,
            JsonArray(emptyList()),
            JsonPrimitive(42),
            JsonPrimitive(true),
            JsonPrimitive(""),
            JsonPrimitive("plain text"),
            JsonPrimitive("{"),
            JsonPrimitive("""["not", "an", "object"]"""),
            JsonPrimitive("null"),
        )

        unsupported.forEach { value ->
            assertEquals(JsonObject(emptyMap()), value.asJsonObjectLenient())
        }
    }
}

package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MemoryTableOperationsTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun appliesOrderedOperationsAndProducesBoundedSummaries() {
        val application = apply(
            """
            [
              {"type":"insert","table":"facts","row":{"id":"b","value":"new","score":2}},
              {"type":"update","table":"facts","row":{"id":"a","value":"updated","score":3}},
              {"type":"delete","table":"facts","row_key_value":"b"}
            ]
            """.trimIndent(),
        )
        val payload = json.parseToJsonElement(application.payloadJson).jsonObject
        val rows = payload.getValue("facts") as JsonArray

        assertEquals(3, application.operationCount)
        assertEquals(1, rows.size)
        assertEquals("updated", rows.single().jsonObject.getValue("value").toString().trim('"'))
        assertEquals("{\"insert\":1,\"update\":1,\"delete\":1}", application.operationSummaryJson)
        assertEquals(3, (json.parseToJsonElement(application.diffSummaryJson) as JsonArray).size)
    }

    @Test
    fun rejectsUnknownTableColumnTypeAndMissingPrimaryKey() {
        listOf(
            """[{"type":"insert","table":"unknown","row":{"id":"x"}}]""",
            """[{"type":"insert","table":"facts","row":{"id":"x","unknown":"value"}}]""",
            """[{"type":"insert","table":"facts","row":{"id":"x","score":"wrong"}}]""",
            """[{"type":"insert","table":"facts","row":{"value":"missing id"}}]""",
        ).forEach { operations ->
            assertThrows(MemoryTableOperationException::class.java) { apply(operations) }
        }
    }

    @Test
    fun rejectsUnsupportedOrAmbiguousSchemaAndReadOnlyTables() {
        val unsupportedType = schema.replace("\"integer\"", "\"date\"")
        val multiplePrimaryKeys = schema.replace(
            "{\"name\":\"value\",\"type\":\"string\"}",
            "{\"name\":\"value\",\"type\":\"string\",\"primaryKey\":true}",
        )
        val readOnly = schema.replace("\"enabled\":true", "\"enabled\":false")
        val operation = """[{"type":"update","table":"facts","row":{"id":"a","value":"x"}}]"""

        listOf(unsupportedType, multiplePrimaryKeys, readOnly).forEach { invalidSchema ->
            assertThrows(MemoryTableOperationException::class.java) {
                applyValidatedMemoryTableOperations(
                    json = json,
                    schemaJson = invalidSchema,
                    payloadJson = payload,
                    operations = json.parseToJsonElement(operation) as JsonArray,
                    maxOperations = 3,
                )
            }
        }
    }

    @Test
    fun rejectsExtraOperationKeysAndOperationLimit() {
        val extraKey = """
            [{"type":"delete","table":"facts","row_key_value":"a","target":"other"}]
        """.trimIndent()
        val tooMany = """
            [
              {"type":"update","table":"facts","row":{"id":"a","value":"one"}},
              {"type":"update","table":"facts","row":{"id":"a","value":"two"}}
            ]
        """.trimIndent()

        assertThrows(MemoryTableOperationException::class.java) { apply(extraKey) }
        assertThrows(MemoryTableOperationException::class.java) { apply(tooMany, maxOperations = 1) }
    }

    @Test
    fun laterFailureDoesNotExposePartiallyAppliedPayload() {
        val operations = """
            [
              {"type":"update","table":"facts","row":{"id":"a","value":"would change"}},
              {"type":"delete","table":"facts","row_key_value":"missing"}
            ]
        """.trimIndent()

        assertThrows(MemoryTableOperationException::class.java) { apply(operations) }
        val originalRows = json.parseToJsonElement(payload).jsonObject.getValue("facts") as JsonArray
        assertEquals("old", originalRows.single().jsonObject.getValue("value").toString().trim('"'))
    }

    private fun apply(operations: String, maxOperations: Int = 3): MemoryTableOperationApplication =
        applyValidatedMemoryTableOperations(
            json = json,
            schemaJson = schema,
            payloadJson = payload,
            operations = json.parseToJsonElement(operations) as JsonArray,
            maxOperations = maxOperations,
        )

    private val schema = """
        {
          "tables": [
            {
              "name": "facts",
              "columns": [
                {"name":"id","type":"string","primaryKey":true},
                {"name":"value","type":"string"},
                {"name":"score","type":"integer"}
              ],
              "updatePolicy": {"enabled":true}
            }
          ]
        }
    """.trimIndent()

    private val payload = """
        {"facts":[{"id":"a","value":"old","score":1}]}
    """.trimIndent()
}

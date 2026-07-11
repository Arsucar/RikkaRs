package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPreviewTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun copyJsonIsVersionedStableAndDoesNotExposeToolLambdas() {
        val prepared = PreparedProviderInput(
            messages = listOf(
                UIMessage.system("system"),
                UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Image("file:/image.png"))),
            ),
            tools = listOf(
                Tool(
                    name = "lookup",
                    description = "Lookup a value",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject { put("query", buildJsonObject { put("type", "string") }) },
                            required = listOf("query"),
                        )
                    },
                    execute = { emptyList() },
                ),
            ),
            sourceMessageCount = 4,
            retainedSourceMessageCount = 2,
            usedConversationSystemPrompt = true,
        )

        val copy = prepared.toContextPreview(json).toCopyJson(json)
        val root = json.parseToJsonElement(copy).jsonObject

        assertEquals(1, root.getValue("formatVersion").jsonPrimitive.content.toInt())
        assertEquals("lookup", root.getValue("tools").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(2, root.getValue("messages").jsonArray.size)
        assertEquals(
            setOf("role", "parts"),
            root.getValue("messages").jsonArray.first().jsonObject.keys,
        )
        assertTrue(root.getValue("overview").jsonObject.getValue("truncated").jsonPrimitive.content.toBoolean())
        assertFalse(copy.contains("execute"))
        assertFalse(copy.contains("Function"))
        assertFalse(copy.contains("createdAt"))
        assertFalse(copy.contains("usage"))
        assertFalse(copy.contains("metadata"))
    }

    @Test
    fun previewSnapshotsToolSchemaOnceAndReportsActualTruncationCounts() {
        var schemaCalls = 0
        val prepared = PreparedProviderInput(
            messages = listOf(UIMessage.system("system"), UIMessage.user("kept")),
            tools = listOf(
                Tool(
                    name = "lookup",
                    description = "Lookup a value",
                    parameters = {
                        schemaCalls++
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("query", buildJsonObject { put("type", "string") })
                            },
                            required = listOf("query"),
                        )
                    },
                    execute = { error("preview must not execute tools") },
                ),
            ),
            sourceMessageCount = 5,
            retainedSourceMessageCount = 2,
            usedConversationSystemPrompt = false,
        )

        val preview = prepared.toContextPreview(json)

        assertEquals(1, schemaCalls)
        assertEquals(5, preview.sourceMessageCount)
        assertEquals(2, preview.retainedSourceMessageCount)
        assertTrue(preview.truncated)
        assertEquals("lookup", preview.tools.single().name)
        assertEquals(
            "string",
            preview.tools.single().parameters!!.jsonObject
                .getValue("properties").jsonObject
                .getValue("query").jsonObject
                .getValue("type").jsonPrimitive.content,
        )
        val expectedCharacters = "system".length + "kept".length +
            "lookup".length + "Lookup a value".length + preview.tools.single().parameters.toString().length
        assertEquals(expectedCharacters, preview.characterCount)
        assertEquals(preview.characterCount, prepared.toContextPreview(Json).characterCount)
    }

    @Test
    fun preparedToolDefinitionFreezesSchemaForPreviewAndProviderReuse() {
        var schemaCalls = 0
        val tool = Tool(
            name = "lookup",
            description = "Lookup a value",
            parameters = {
                schemaCalls++
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("call", buildJsonObject { put("value", schemaCalls) })
                    },
                )
            },
            execute = { emptyList() },
        )

        val preparedTool = listOf(tool).snapshotToolDefinitions().single()
        val first = preparedTool.parameters()
        val second = preparedTool.parameters()

        assertEquals(1, schemaCalls)
        assertEquals(first, second)
    }
}

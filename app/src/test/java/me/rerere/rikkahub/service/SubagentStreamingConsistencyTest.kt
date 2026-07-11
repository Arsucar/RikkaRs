package me.rerere.rikkahub.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.ai.subagent.SubagentStatus
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SubagentStreamingConsistencyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `cleanStaleSubagentStreaming clears metadata and text streaming flag`() {
        val textJson = """{"profile_name":"x","succeeded":false,"streaming":true}"""
        val metadata = buildJsonObject {
            put("subagent_streaming", JsonPrimitive(true))
            put("subagent_context_id", JsonPrimitive("context-1"))
            put("subagent_context_status", JsonPrimitive(SubagentStatus.RUNNING.name))
        }
        val tool = UIMessagePart.Tool(
            toolCallId = "call-1",
            toolName = "spawn_subagent",
            input = "{}",
            output = listOf(UIMessagePart.Text(text = textJson, metadata = metadata)),
        )
        val assistant = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(tool))
        val conversation = Conversation(
            assistantId = DEFAULT_ASSISTANT_ID,
            messageNodes = listOf(MessageNode.of(assistant)),
        )

        val cleaned = conversation.cleanStaleSubagentStreaming(json)
        val outputText = cleaned.currentMessages.single().parts.single() as UIMessagePart.Tool
        val textPart = outputText.output.filterIsInstance<UIMessagePart.Text>().single()

        assertEquals(
            "false",
            textPart.metadata?.get("subagent_streaming")?.jsonPrimitive?.content,
        )
        val parsed = json.decodeFromString<JsonObject>(textPart.text)
        assertEquals(false, parsed["streaming"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())
        assertEquals(
            SubagentStatus.INTERRUPTED.name,
            textPart.metadata?.get("subagent_context_status")?.jsonPrimitive?.content,
        )
        assertEquals("context-1", textPart.metadata?.get("subagent_context_id")?.jsonPrimitive?.content)
    }

    @Test
    fun `cleanStaleSubagentStreaming leaves non-json summary text unchanged`() {
        val summary = "Subagent finished with a short summary."
        val metadata = buildJsonObject {
            put("subagent_streaming", JsonPrimitive(true))
        }
        val tool = UIMessagePart.Tool(
            toolCallId = "call-2",
            toolName = "spawn_subagent",
            input = "{}",
            output = listOf(UIMessagePart.Text(text = summary, metadata = metadata)),
        )
        val assistant = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(tool))
        val conversation = Conversation(
            assistantId = DEFAULT_ASSISTANT_ID,
            messageNodes = listOf(MessageNode.of(assistant)),
        )

        val cleaned = conversation.cleanStaleSubagentStreaming(json)
        val textPart = (cleaned.currentMessages.single().parts.single() as UIMessagePart.Tool)
            .output
            .filterIsInstance<UIMessagePart.Text>()
            .single()

        assertEquals(summary, textPart.text)
        assertEquals(
            "false",
            textPart.metadata?.get("subagent_streaming")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `cleanStreamingTextPayload sets streaming false in json text`() {
        val input = """{"profile_name":"x","succeeded":false,"streaming":true}"""
        val out = cleanStreamingTextPayload(input, json)
        val parsed = json.decodeFromString<JsonObject>(out)
        assertFalse(parsed["streaming"]!!.jsonPrimitive.content.toBooleanStrict())
    }
}

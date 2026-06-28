package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitializeGuardTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun makeSession(): ConversationSession = ConversationSession(
        id = Uuid.random(),
        initial = Conversation.ofId(Uuid.random(), DEFAULT_ASSISTANT_ID),
        scope = CoroutineScope(Dispatchers.Unconfined),
        onIdle = {},
    )

    private fun conversationWithSubagentStreaming(): Conversation {
        val textJson = """{"profile_name":"x","succeeded":false,"streaming":true}"""
        val metadata = buildJsonObject {
            put("subagent_streaming", JsonPrimitive(true))
        }
        val tool = UIMessagePart.Tool(
            toolCallId = "call-1",
            toolName = "spawn_subagent",
            input = "{}",
            output = listOf(UIMessagePart.Text(text = textJson, metadata = metadata)),
        )
        val assistant = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(tool))
        return Conversation(
            assistantId = DEFAULT_ASSISTANT_ID,
            messageNodes = listOf(MessageNode.of(assistant)),
        )
    }

    @Test
    fun `shouldSkipInitializeOnGenerating returns true when job active`() {
        val session = makeSession()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job: Job = scope.launch { awaitCancellation() }
        session.setJob(job)
        try {
            assertTrue(shouldSkipInitializeOnGenerating(session))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `shouldSkipInitializeOnGenerating returns false when no job`() {
        val session = makeSession()
        assertFalse(shouldSkipInitializeOnGenerating(session))
    }

    @Test
    fun `hydrate returns loaded as-is when generating`() {
        val session = makeSession()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job: Job = scope.launch { awaitCancellation() }
        session.setJob(job)
        val loaded = conversationWithSubagentStreaming()
        try {
            val result = hydrateConversationFromDb(loaded, session, json)
            assertEquals(loaded, result)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `hydrate cleans stale streaming when not generating`() {
        val session = makeSession()
        val loaded = conversationWithSubagentStreaming()
        val result = hydrateConversationFromDb(loaded, session, json)
        assertNotEquals(loaded, result)
        val tool = result.currentMessages.single().parts.single() as UIMessagePart.Tool
        val textPart = tool.output.filterIsInstance<UIMessagePart.Text>().single()
        assertEquals(
            "false",
            textPart.metadata?.get("subagent_streaming")?.jsonPrimitive?.content,
        )
    }
}
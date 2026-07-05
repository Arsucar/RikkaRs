package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class ChatModelResolutionTest {
    @Test
    fun conversationModelOverridesAssistantAndGlobalModel() {
        val globalModelId = Uuid.random()
        val assistantModelId = Uuid.random()
        val conversationModelId = Uuid.random()
        val assistant = Assistant(chatModelId = assistantModelId)
        val settings = Settings.dummy().copy(
            chatModelId = globalModelId,
            assistantId = assistant.id,
            assistants = listOf(assistant),
        )
        val conversation = Conversation(
            assistantId = assistant.id,
            chatModelId = conversationModelId,
            messageNodes = emptyList(),
        )

        assertEquals(conversationModelId, settings.resolveChatModelId(conversation))
    }

    @Test
    fun assistantModelOverridesGlobalModelWhenConversationHasNoOverride() {
        val globalModelId = Uuid.random()
        val assistantModelId = Uuid.random()
        val assistant = Assistant(chatModelId = assistantModelId)
        val settings = Settings.dummy().copy(
            chatModelId = globalModelId,
            assistantId = assistant.id,
            assistants = listOf(assistant),
        )
        val conversation = Conversation(
            assistantId = assistant.id,
            messageNodes = emptyList(),
        )

        assertEquals(assistantModelId, settings.resolveChatModelId(conversation))
    }

    @Test
    fun globalModelIsUsedWhenConversationAndAssistantHaveNoOverride() {
        val globalModelId = Uuid.random()
        val assistant = Assistant()
        val settings = Settings.dummy().copy(
            chatModelId = globalModelId,
            assistantId = assistant.id,
            assistants = listOf(assistant),
        )
        val conversation = Conversation(
            assistantId = assistant.id,
            messageNodes = emptyList(),
        )

        assertEquals(globalModelId, settings.resolveChatModelId(conversation))
    }
}

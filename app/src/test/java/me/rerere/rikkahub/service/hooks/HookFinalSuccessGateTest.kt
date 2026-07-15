package me.rerere.rikkahub.service.hooks

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

class HookFinalSuccessGateTest {
    @Test
    fun acceptsOnlyFinishedNonEmptyAssistantText() {
        val message = assistant(" final answer ")
        val conversation = conversation(message)

        val snapshot = evaluateHookFinalSuccess(conversation)

        assertEquals(message.id, snapshot?.messageId)
        assertEquals("final answer", snapshot?.text)
    }

    @Test
    fun rejectsUserStreamingAndEmptyMessages() {
        val user = assistant("answer").copy(role = MessageRole.USER)
        val streaming = assistant("answer").copy(finishedAt = null)
        val empty = assistant("  ")

        assertNull(evaluateHookFinalSuccess(conversation(user)))
        assertNull(evaluateHookFinalSuccess(conversation(streaming)))
        assertNull(evaluateHookFinalSuccess(conversation(empty)))
    }

    private fun assistant(text: String) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        finishedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

    private fun conversation(message: UIMessage) = Conversation(
        assistantId = Uuid.random(),
        messageNodes = listOf(message.toMessageNode()),
    )
}

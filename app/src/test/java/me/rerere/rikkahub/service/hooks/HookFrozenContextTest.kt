package me.rerere.rikkahub.service.hooks

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class HookFrozenContextTest {
    @Test
    fun freezesOnlySelectedVisibleUserAndAssistantBranchThroughCutoff() {
        val hidden = message("hidden", MessageRole.USER)
        val oldAlternative = message("old alternative", MessageRole.ASSISTANT)
        val selectedAlternative = message("selected answer", MessageRole.ASSISTANT)
        val system = message("system secret", MessageRole.SYSTEM)
        val tool = message("tool payload", MessageRole.TOOL)
        val cutoff = message("latest user", MessageRole.USER)
        val afterCutoff = message("future answer", MessageRole.ASSISTANT)
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode(messages = listOf(hidden), hidden = true),
                MessageNode(messages = listOf(oldAlternative, selectedAlternative), selectIndex = 1),
                MessageNode(messages = listOf(system)),
                MessageNode(messages = listOf(tool)),
                MessageNode(messages = listOf(cutoff)),
                MessageNode(messages = listOf(afterCutoff)),
            ),
        )

        val frozen = freezeMemoryTableHookMessages(
            conversation = conversation,
            cutoffMessageId = cutoff.id,
            config = config(recentMessageCount = 10, maxContextChars = 1_000),
        )

        assertEquals(listOf(selectedAlternative.id, cutoff.id), frozen.map { it.messageId })
        assertFalse(frozen.any { it.text.contains("hidden") || it.text.contains("old alternative") })
        assertFalse(frozen.any { it.text.contains("system") || it.text.contains("tool") })
        assertFalse(frozen.any { it.text.contains("future") })
    }

    @Test
    fun rolePolicyAndRecentMessageLimitAreAppliedBeforeCharacterBound() {
        val first = message("first", MessageRole.USER)
        val second = message("second", MessageRole.ASSISTANT)
        val cutoff = message("third", MessageRole.USER)
        val conversation = conversation(first, second, cutoff)

        val assistantOnly = freezeMemoryTableHookMessages(
            conversation,
            cutoff.id,
            config(
                recentMessageCount = 1,
                includeUserMessages = false,
                includeAssistantMessages = true,
                maxContextChars = 1_000,
            ),
        )

        assertEquals(listOf(second.id), assistantOnly.map { it.messageId })
    }

    @Test
    fun unicodeCharacterLimitKeepsWholeCodePointsFromNewestMessages() {
        val older = message("older", MessageRole.USER)
        val cutoff = message("ab😀cd", MessageRole.ASSISTANT)

        val frozen = freezeMemoryTableHookMessages(
            conversation(older, cutoff),
            cutoff.id,
            config(recentMessageCount = 2, maxContextChars = 3),
        )

        assertEquals(1, frozen.size)
        assertEquals("😀cd", frozen.single().text)
        assertEquals(3, Character.codePointCount(frozen.single().text, 0, frozen.single().text.length))
        assertFalse(frozen.single().text.endsWith("\uD83D"))
    }

    @Test
    fun inactiveAlternativeCannotBeUsedAsCutoff() {
        val inactive = message("inactive", MessageRole.ASSISTANT)
        val active = message("active", MessageRole.ASSISTANT)
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(MessageNode(messages = listOf(inactive, active), selectIndex = 1)),
        )

        val error = assertThrows(HookOutputException::class.java) {
            freezeMemoryTableHookMessages(conversation, inactive.id, config())
        }

        assertEquals(HookErrorCode.SOURCE_MESSAGE_NOT_ACTIVE, error.code)
        assertTrue(conversation.currentMessages.single().id == active.id)
    }

    private fun message(text: String, role: MessageRole) = UIMessage(
        id = Uuid.random(),
        role = role,
        parts = listOf(UIMessagePart.Text(text)),
    )

    private fun conversation(vararg messages: UIMessage) = Conversation(
        assistantId = Uuid.random(),
        messageNodes = messages.map { MessageNode(messages = listOf(it)) },
    )

    private fun config(
        recentMessageCount: Int = 8,
        includeUserMessages: Boolean = true,
        includeAssistantMessages: Boolean = true,
        maxContextChars: Int = 12_000,
    ) = HookActionConfig.SyncMemoryTable(
        targetDocumentId = "document",
        recentMessageCount = recentMessageCount,
        includeUserMessages = includeUserMessages,
        includeAssistantMessages = includeAssistantMessages,
        maxContextChars = maxContextChars,
    )
}

package me.rerere.rikkahub.data.ai.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionPromptTest {
    @Test
    fun inputDraftPromptIncludesTrimmedInstructionOnce() {
        val prompt = buildInputDraftPrompt(
            locale = "English",
            content = "assistant: Please decide.",
            userInstruction = "  Reply politely  ",
        )

        assertEquals(1, Regex("<user_instruction>").findAll(prompt).count())
        assertEquals(1, Regex("</user_instruction>").findAll(prompt).count())
        assertTrue(prompt.contains("用户对本次回复的附加要求/意图：\nReply politely"))
        assertTrue(prompt.contains("请在生成回复草稿时严格遵循上述要求。"))
    }

    @Test
    fun inputDraftPromptOmitsInstructionBlockForBlankInput() {
        val prompt = buildInputDraftPrompt(
            locale = "English",
            content = "assistant: Please decide.",
            userInstruction = " \n\t ",
        )

        assertFalse(prompt.contains("<user_instruction>"))
        assertFalse(prompt.contains("用户对本次回复的附加要求/意图"))
        assertFalse(prompt.contains("{user_instruction}"))
    }

    @Test
    fun inputDraftPromptPreservesPlaceholderTextInsideConversationContent() {
        val prompt = buildInputDraftPrompt(
            locale = "English",
            content = "assistant: The literal token is {user_instruction}.",
            userInstruction = "Reply politely",
        )

        assertTrue(prompt.contains("The literal token is {user_instruction}."))
        assertEquals(1, Regex("<user_instruction>").findAll(prompt).count())
    }
}

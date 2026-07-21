package me.rerere.rikkahub.ui.pages.chat

import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.ui.components.ai.canGenerateInputDraft
import me.rerere.rikkahub.ui.components.ai.requireInputDraftText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDraftPolicyTest {
    @Test
    fun generationRequiresConversationOutsideEditModeWithIdleAsr() {
        assertFalse(
            canGenerateInputDraft(
                conversationHasMessages = false,
                isEditing = false,
                asrStatus = ASRStatus.Idle,
            )
        )
        assertFalse(
            canGenerateInputDraft(
                conversationHasMessages = true,
                isEditing = true,
                asrStatus = ASRStatus.Idle,
            )
        )
        assertTrue(
            canGenerateInputDraft(
                conversationHasMessages = true,
                isEditing = false,
                asrStatus = ASRStatus.Idle,
            )
        )
        assertTrue(
            canGenerateInputDraft(
                conversationHasMessages = true,
                isEditing = false,
                asrStatus = ASRStatus.Error,
            )
        )
    }

    @Test
    fun generationIsBlockedWhileAsrIsActive() {
        listOf(ASRStatus.Connecting, ASRStatus.Listening, ASRStatus.Stopping).forEach { status ->
            assertFalse(
                canGenerateInputDraft(
                    conversationHasMessages = true,
                    isEditing = false,
                    asrStatus = status,
                )
            )
        }
    }

    @Test
    fun completedDraftMustContainVisibleText() {
        assertEquals("Draft", requireInputDraftText("Draft", "empty"))

        val error = assertThrows(IllegalArgumentException::class.java) {
            requireInputDraftText(" \n\t", "empty")
        }
        assertEquals("empty", error.message)
    }
}

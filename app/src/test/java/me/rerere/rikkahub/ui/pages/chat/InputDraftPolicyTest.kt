package me.rerere.rikkahub.ui.pages.chat

import me.rerere.asr.ASRStatus
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.ui.components.ai.canGenerateInputDraft
import me.rerere.rikkahub.ui.components.ai.hasInputDraftReplyTarget
import me.rerere.rikkahub.ui.components.ai.requireInputDraftText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDraftPolicyTest {
    @Test
    fun replyTargetRequiresCompletedAssistantTurn() {
        assertFalse(hasInputDraftReplyTarget(latestMessageRole = null, mainGenerationActive = false))
        assertFalse(hasInputDraftReplyTarget(MessageRole.USER, mainGenerationActive = false))
        assertFalse(hasInputDraftReplyTarget(MessageRole.ASSISTANT, mainGenerationActive = true))
        assertTrue(hasInputDraftReplyTarget(MessageRole.ASSISTANT, mainGenerationActive = false))
    }

    @Test
    fun generationRequiresReplyTargetOutsideEditModeWithIdleAsr() {
        assertFalse(
            canGenerateInputDraft(
                hasReplyTarget = false,
                isEditing = false,
                asrStatus = ASRStatus.Idle,
            )
        )
        assertFalse(
            canGenerateInputDraft(
                hasReplyTarget = true,
                isEditing = true,
                asrStatus = ASRStatus.Idle,
            )
        )
        assertTrue(
            canGenerateInputDraft(
                hasReplyTarget = true,
                isEditing = false,
                asrStatus = ASRStatus.Idle,
            )
        )
        assertTrue(
            canGenerateInputDraft(
                hasReplyTarget = true,
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
                    hasReplyTarget = true,
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

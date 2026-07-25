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
    fun generationRequiresReplyTargetWithIdleAsr() {
        assertFalse(
            canGenerateInputDraft(
                hasReplyTarget = false,
                asrStatus = ASRStatus.Idle,
            )
        )
        assertTrue(
            canGenerateInputDraft(
                hasReplyTarget = true,
                asrStatus = ASRStatus.Idle,
            )
        )
        assertTrue(
            canGenerateInputDraft(
                hasReplyTarget = true,
                asrStatus = ASRStatus.Error,
            )
        )
    }

    // #181: 编辑态不再禁用「写回复草稿」，只要有回复目标且 ASR 空闲即可生成。
    @Test
    fun generationIsAllowedRegardlessOfEditMode() {
        assertTrue(
            canGenerateInputDraft(
                hasReplyTarget = true,
                asrStatus = ASRStatus.Idle,
            )
        )
    }

    @Test
    fun generationIsBlockedWhileAsrIsActive() {
        listOf(ASRStatus.Connecting, ASRStatus.Listening, ASRStatus.Stopping).forEach { status ->
            assertFalse(
                canGenerateInputDraft(
                    hasReplyTarget = true,
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

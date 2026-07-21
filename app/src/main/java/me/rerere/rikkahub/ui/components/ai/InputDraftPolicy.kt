package me.rerere.rikkahub.ui.components.ai

import me.rerere.asr.ASRStatus
import me.rerere.ai.core.MessageRole

internal fun hasInputDraftReplyTarget(
    latestMessageRole: MessageRole?,
    mainGenerationActive: Boolean,
): Boolean = !mainGenerationActive && latestMessageRole == MessageRole.ASSISTANT

internal fun canGenerateInputDraft(
    hasReplyTarget: Boolean,
    isEditing: Boolean,
    asrStatus: ASRStatus,
): Boolean =
    hasReplyTarget &&
        !isEditing &&
        (asrStatus == ASRStatus.Idle || asrStatus == ASRStatus.Error)

internal fun requireInputDraftText(draft: String, emptyMessage: String): String {
    require(draft.isNotBlank()) { emptyMessage }
    return draft
}

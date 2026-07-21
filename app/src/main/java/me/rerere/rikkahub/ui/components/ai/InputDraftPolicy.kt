package me.rerere.rikkahub.ui.components.ai

import me.rerere.asr.ASRStatus

internal fun canGenerateInputDraft(
    conversationHasMessages: Boolean,
    isEditing: Boolean,
    asrStatus: ASRStatus,
): Boolean =
    conversationHasMessages &&
        !isEditing &&
        (asrStatus == ASRStatus.Idle || asrStatus == ASRStatus.Error)

internal fun requireInputDraftText(draft: String, emptyMessage: String): String {
    require(draft.isNotBlank()) { emptyMessage }
    return draft
}

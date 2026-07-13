package me.rerere.rikkahub.ui.components.ai

internal fun shouldConvertPastedTextToFile(
    enabled: Boolean,
    isEditing: Boolean,
    textLength: Int,
    threshold: Int,
): Boolean = enabled && !isEditing && textLength > threshold

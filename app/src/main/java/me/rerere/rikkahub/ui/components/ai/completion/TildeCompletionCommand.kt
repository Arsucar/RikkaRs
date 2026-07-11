package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange

internal const val DEFAULT_MODEL_COMMAND_QUERY = "dm"

internal data class TildeCompletionCommand(
    val query: String,
    val range: TextRange,
)

internal fun findTildeCompletionCommand(text: String, cursor: Int): TildeCompletionCommand? {
    if (cursor < 0 || cursor > text.length) return null
    val prefix = text.substring(0, cursor)
    val startHalf = prefix.lastIndexOf('~')
    val startFull = prefix.lastIndexOf('～')
    if (startHalf < 0 && startFull < 0) return null
    val useFullWidth = startFull > startHalf
    val start = if (useFullWidth) startFull else startHalf
    if (start > 0 && !text[start - 1].isTildeCompletionBoundary(useFullWidth)) return null

    val query = prefix.substring(start + 1)
    if (query.any { it.isWhitespace() }) return null
    return TildeCompletionCommand(
        query = query,
        range = TextRange(start, cursor),
    )
}

private fun Char.isTildeCompletionBoundary(fullWidth: Boolean): Boolean =
    if (fullWidth) true
    else isWhitespace() || this in "([{<\"'"

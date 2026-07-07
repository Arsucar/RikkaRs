package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.rikkahub.data.model.Preset
import kotlin.math.max

class PresetCompletionProvider(
    private val presets: List<Preset>,
) : ChatCompletionProvider {
    override val id: String = "preset_switch"

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection || presets.isEmpty()) return null
        val command = findPresetCommand(context.text, context.cursor) ?: return null
        val query = command.query
        val items = presets
            .asSequence()
            .mapNotNull { preset ->
                val score = preset.matchScore(query) ?: return@mapNotNull null
                ChatCompletionItem(
                    label = "~${preset.name.ifBlank { "preset" }}",
                    detail = preset.description.ifBlank { null },
                    insertText = "",
                    icon = HugeIcons.MagicWand01,
                    sortScore = score,
                    presetId = preset.id,
                )
            }
            .sortedWith(
                compareByDescending<ChatCompletionItem> { it.sortScore }
                    .thenBy { it.label.length }
                    .thenBy { it.label.lowercase() }
            )
            .take(MAX_COMPLETION_ITEMS)
            .toList()

        if (items.isEmpty()) return null
        return ChatCompletionList(
            providerId = id,
            replacementRange = command.range,
            items = items,
        )
    }

    private fun Preset.matchScore(query: String): Int? {
        val normalizedQuery = query.lowercase()
        if (normalizedQuery.isBlank()) return 1
        return max(
            name.lowercase().fuzzyScore(normalizedQuery) ?: -1,
            description.lowercase().fuzzyScore(normalizedQuery) ?: -1,
        ).takeIf { it >= 0 }
    }

    private fun String.fuzzyScore(query: String): Int? {
        if (this == query) return 1000
        if (startsWith(query)) return 900 - length.coerceAtMost(200)
        val containsIndex = indexOf(query)
        if (containsIndex >= 0) return 800 - containsIndex.coerceAtMost(200)

        var queryIndex = 0
        var firstMatch = -1
        var lastMatch = -1
        forEachIndexed { index, char ->
            if (queryIndex < query.length && char == query[queryIndex]) {
                if (firstMatch < 0) firstMatch = index
                lastMatch = index
                queryIndex++
            }
        }
        if (queryIndex != query.length) return null

        val span = (lastMatch - firstMatch + 1).coerceAtLeast(query.length)
        return 500 - span.coerceAtMost(300) - firstMatch.coerceAtLeast(0).coerceAtMost(100)
    }

    private data class PresetCommand(
        val query: String,
        val range: TextRange,
    )

    private fun findPresetCommand(text: String, cursor: Int): PresetCommand? {
        if (cursor < 0 || cursor > text.length) return null
        val prefix = text.substring(0, cursor)
        val start = prefix.lastIndexOf('~')
        if (start < 0) return null
        if (start > 0 && !text[start - 1].isPresetBoundary()) return null

        val query = prefix.substring(start + 1)
        if (query.any { it.isWhitespace() }) return null
        return PresetCommand(
            query = query,
            range = TextRange(start, cursor),
        )
    }

    private fun Char.isPresetBoundary(): Boolean =
        isWhitespace() || this in "([{<\"'"

    companion object {
        private const val MAX_COMPLETION_ITEMS = 8
    }
}

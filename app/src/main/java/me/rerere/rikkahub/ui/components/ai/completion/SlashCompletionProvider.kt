package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Book02
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.files.skillsAvailableForSlash
import kotlin.math.max

class SlashCompletionProvider(
    private val enabledSkills: Set<String>,
    private val skillManager: SkillManager,
) : ChatCompletionProvider {
    override val id: String = "slash_skills"

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection) return null
        val slash = findSlashCommand(context.text, context.cursor) ?: return null
        val query = slash.query

        val available = skillManager.skillsAvailableForSlash(enabledSkills)

        val items = available
            .asSequence()
            .mapNotNull { skill ->
                val score = skill.matchScore(query) ?: return@mapNotNull null
                ChatCompletionItem(
                    label = "/${skill.name}",
                    detail = skill.description,
                    insertText = "",
                    icon = HugeIcons.Book02,
                    sortScore = score,
                    skillName = skill.name.ifBlank { skill.skillDir.name },
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
            replacementRange = slash.range,
            items = items,
        )
    }

    private fun SkillMetadata.matchScore(query: String): Int? {
        val normalizedName = name.lowercase()
        val normalizedDesc = description.lowercase()
        val normalizedQuery = query.lowercase()

        if (normalizedQuery.isBlank()) return 1

        return max(
            normalizedName.fuzzyScore(normalizedQuery) ?: -1,
            normalizedDesc.fuzzyScore(normalizedQuery) ?: -1,
        ).takeIf { it >= 0 }
    }

    private fun String.fuzzyScore(query: String): Int? {
        if (query.isBlank()) return 1
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

    private data class SlashCommand(
        val query: String,
        val range: TextRange,
    )

    private fun findSlashCommand(text: String, cursor: Int): SlashCommand? {
        if (cursor < 0 || cursor > text.length) return null
        val prefix = text.substring(0, cursor)
        val start = prefix.lastIndexOf('/')
        if (start < 0) return null
        if (start > 0 && !text[start - 1].isSlashBoundary()) return null

        val query = prefix.substring(start + 1)
        if (query.any { it.isWhitespace() }) return null
        return SlashCommand(
            query = query,
            range = TextRange(start, cursor),
        )
    }

    private fun Char.isSlashBoundary(): Boolean =
        isWhitespace() || this in "([{<\"'"

    companion object {
        private const val MAX_COMPLETION_ITEMS = 8
    }
}

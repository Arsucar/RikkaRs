package me.rerere.rikkahub.data.files

object SkillFrontmatterParser {
    private val frontmatterEndRegex = Regex("""\r?\n---(?:\r?\n|$)""")

    fun parse(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!content.startsWith("---")) return result
        val endRange = findFrontmatterEndRange(content) ?: return result
        val yaml = content.substring(3, endRange.first)
            .removePrefix("\r\n")
            .removePrefix("\n")
        val lines = if (yaml.isEmpty()) emptyList() else yaml.lines()
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val rawValue = line.substring(colonIdx + 1).trim()
                val blockScalar = BlockScalarIndicator.parse(rawValue)
                val value = if (blockScalar == null) {
                    rawValue.removeSurrounding("\"")
                } else {
                    val parentIndent = line.leadingWhitespaceCount()
                    val blockLines = mutableListOf<String>()
                    var nextLineIndex = lineIndex + 1
                    while (nextLineIndex < lines.size) {
                        val candidate = lines[nextLineIndex]
                        if (candidate.isNotBlank() && candidate.leadingWhitespaceCount() <= parentIndent) {
                            break
                        }
                        blockLines += candidate
                        nextLineIndex++
                    }
                    lineIndex = nextLineIndex - 1
                    parseBlockScalar(
                        lines = blockLines,
                        parentIndent = parentIndent,
                        indicator = blockScalar,
                    )
                }
                if (key.isNotBlank() && value.isNotBlank()) {
                    result[key] = value
                }
            }
            lineIndex++
        }
        return result
    }

    fun extractBody(content: String): String {
        if (!content.startsWith("---")) return content
        val endRange = findFrontmatterEndRange(content) ?: return content
        return content.substring(endRange.last + 1).trimStart('\r', '\n')
    }

    private fun findFrontmatterEndRange(content: String): IntRange? {
        if (!content.startsWith("---")) return null
        return frontmatterEndRegex.find(content, startIndex = 3)?.range
    }

    private fun parseBlockScalar(
        lines: List<String>,
        parentIndent: Int,
        indicator: BlockScalarIndicator,
    ): String {
        if (lines.isEmpty()) return ""
        val contentIndent = indicator.indent?.let { parentIndent + it }
            ?: lines.asSequence()
                .filter { it.isNotBlank() }
                .map { it.leadingWhitespaceCount() }
                .minOrNull()
            ?: (parentIndent + 1)
        val normalizedLines = lines.map { line ->
            if (line.isBlank()) {
                BlockScalarLine(text = "", moreIndented = false)
            } else {
                val lineIndent = line.leadingWhitespaceCount()
                BlockScalarLine(
                    text = line.drop(contentIndent.coerceAtMost(lineIndent)),
                    moreIndented = lineIndent > contentIndent,
                )
            }
        }
        val rawValue = when (indicator.style) {
            '|' -> normalizedLines.joinToString("\n") { it.text } + "\n"
            '>' -> foldBlockScalarLines(normalizedLines) + "\n"
            else -> error("Unsupported block scalar style: ${indicator.style}")
        }
        return when (indicator.chomping) {
            '-' -> rawValue.trimEnd('\n')
            '+' -> rawValue
            else -> rawValue.trimEnd('\n') + "\n"
        }
    }

    private fun foldBlockScalarLines(lines: List<BlockScalarLine>): String = buildString {
        lines.forEachIndexed { index, line ->
            append(line.text)
            val next = lines.getOrNull(index + 1) ?: return@forEachIndexed
            val separator = when {
                line.moreIndented || next.moreIndented -> '\n'
                line.text.isNotEmpty() && next.text.isNotEmpty() -> ' '
                line.text.isNotEmpty() && next.text.isEmpty() -> '\n'
                line.text.isEmpty() && next.text.isEmpty() -> '\n'
                else -> null
            }
            separator?.let(::append)
        }
    }

    private fun String.leadingWhitespaceCount(): Int = indexOfFirst { !it.isWhitespace() }
        .let { if (it == -1) length else it }

    private data class BlockScalarLine(
        val text: String,
        val moreIndented: Boolean,
    )

    private data class BlockScalarIndicator(
        val style: Char,
        val chomping: Char?,
        val indent: Int?,
    ) {
        companion object {
            fun parse(value: String): BlockScalarIndicator? {
                val style = value.firstOrNull()?.takeIf { it == '|' || it == '>' } ?: return null
                val indicators = value.drop(1)
                if (indicators.length > 2) return null
                val chomping = indicators.singleOrNull { it == '+' || it == '-' }
                val indent = indicators.singleOrNull { it in '1'..'9' }?.digitToInt()
                val valid = indicators.all { it == '+' || it == '-' || it in '1'..'9' } &&
                    indicators.count { it == '+' || it == '-' } <= 1 &&
                    indicators.count { it in '1'..'9' } <= 1
                return if (valid) BlockScalarIndicator(style, chomping, indent) else null
            }
        }
    }
}

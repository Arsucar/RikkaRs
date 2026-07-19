package me.rerere.rikkahub.service.hooks

import java.net.URI
import java.util.Locale

enum class GitHubIssueEvidenceType {
    ISSUE_URL,
    ISSUE_NUMBER,
}

data class GitHubIssueEvidence(
    val type: GitHubIssueEvidenceType,
    val issueNumber: Long,
    val normalizedUrl: String? = null,
)

data class ConfiguredHookKeywordEvidence(
    val matchedPattern: String,
    val matchedText: String,
    val matchType: String,
    val normalizedEvidence: String,
    val normalizedUrl: String? = null,
)

/** Bounded local pre-filter used by KEYWORD_MATCHED; it never calls a provider. */
fun detectConfiguredHookKeyword(text: String, keyword: String): String? =
    detectConfiguredHookKeywordEvidence(text, keyword)?.normalizedEvidence

fun detectConfiguredHookKeywordEvidence(text: String, keyword: String): ConfiguredHookKeywordEvidence? {
    val candidate = keyword.trim()
    if (candidate.isEmpty() || text.isEmpty()) return null
    if (candidate.equals("issue", ignoreCase = true) || candidate.equals("issues", ignoreCase = true)) {
        val match = Regex(
            "(?i)https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/issues/\\d+/?|(?<![\\p{L}\\p{N}_])#\\d+\\b|\\bissues?\\b"
        ).find(text) ?: return null
        val matched = match.value
        if (matched.startsWith("http", ignoreCase = true)) {
            val uri = runCatching { URI(matched.trimEnd('/', '.', ',', ';', ':')) }.getOrNull() ?: return null
            val normalizedUrl = URI(
                "https",
                null,
                uri.host.lowercase(Locale.ROOT),
                -1,
                uri.rawPath.trimEnd('/').lowercase(Locale.ROOT),
                uri.rawQuery,
                uri.rawFragment,
            ).toASCIIString().trimEnd('/')
            return ConfiguredHookKeywordEvidence(
                matchedPattern = candidate.take(200),
                matchedText = matched.take(200),
                matchType = "ISSUE_URL",
                normalizedEvidence = normalizedUrl,
                normalizedUrl = normalizedUrl,
            )
        }
        val normalized = matched.lowercase(Locale.ROOT)
        return ConfiguredHookKeywordEvidence(
            matchedPattern = candidate.take(200),
            matchedText = matched.take(200),
            matchType = if (normalized.startsWith('#')) "ISSUE_NUMBER" else "KEYWORD",
            normalizedEvidence = normalized.take(200),
        )
    }
    val index = text.indexOf(candidate, ignoreCase = true)
    if (index < 0) return null
    val end = index + candidate.length
    val wordLike = candidate.any { it.isLetterOrDigit() }
    if (wordLike) {
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(end)
        if (before?.isLetterOrDigit() == true || after?.isLetterOrDigit() == true) return null
    }
    return ConfiguredHookKeywordEvidence(
        matchedPattern = candidate.take(200),
        matchedText = text.substring(index, end).take(200),
        matchType = "KEYWORD",
        normalizedEvidence = candidate.lowercase(Locale.ROOT).take(200),
    )
}

private data class EvidenceCandidate(
    val start: Int,
    val endExclusive: Int,
    val evidence: GitHubIssueEvidence,
)

private data class TextRange(
    val start: Int,
    val endExclusive: Int,
)

private val clauseSeparator = Regex("(?:\\r?\\n|[。！？；]+|[.?!;](?=\\s|$))+")
private val urlCandidate = Regex("(?i)https://[^\\s<>()\\[\\]{}]+")
private val issueNumberCandidate = Regex("(?<![\\p{L}\\p{N}_/])#([1-9][0-9]*)(?![\\p{L}\\p{N}_-])")
private val shorthandConflictMarker = Regex(
    "(?i)(?<![\\p{L}\\p{N}_])(?:pr|pull\\s+request|pull|build|order|commit|release)(?![\\p{L}\\p{N}_])"
)
private val englishSuccess = Regex(
    "(?i)(?<![\\p{L}\\p{N}_])(?:published\\s+successfully|created|submitted|filed)(?![\\p{L}\\p{N}_])"
)
private val englishNegative = Regex(
    "(?i)(?<![\\p{L}\\p{N}_])(?:not\\s+created|could(?:n['’]t|\\s+not)\\s+create|failed|error|draft|plan|suggest|waiting|confirm)(?![\\p{L}\\p{N}_])"
)
private val chineseSuccess = listOf("已创建", "创建成功", "已提交", "提交成功", "已登记")
private val chineseNegative = listOf(
    "未创建",
    "创建失败",
    "报错",
    "草稿",
    "计划",
    "建议",
    "等待",
    "确认",
    "尚未执行",
)
private const val MAX_EVIDENCE_DISTANCE_CODE_POINTS = 80

fun detectGitHubIssueCompletionEvidence(text: String): GitHubIssueEvidence? {
    val sanitized = stripMarkdownEvidenceExclusions(text)
    return clauseSeparator.split(sanitized)
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull(::detectClauseEvidence)
        .firstOrNull()
}

private fun detectClauseEvidence(clause: String): GitHubIssueEvidence? {
    val lowerClause = clause.lowercase(Locale.ROOT)
    if (englishNegative.containsMatchIn(lowerClause) || chineseNegative.any(lowerClause::contains)) {
        return null
    }
    val successRanges = buildList {
        englishSuccess.findAll(clause).forEach { add(TextRange(it.range.first, it.range.last + 1)) }
        chineseSuccess.forEach { phrase ->
            clause.indicesOf(phrase).forEach { start -> add(TextRange(start, start + phrase.length)) }
        }
    }
    if (successRanges.isEmpty()) return null

    val urlMatches = urlCandidate.findAll(clause).toList()
    val urlEvidence = urlMatches.asSequence()
        .mapNotNull { match -> parseIssueUrlCandidate(match.value, match.range.first) }
        .firstOrNull { candidate -> successRanges.any { withinEvidenceWindow(clause, candidate, it) } }
    if (urlEvidence != null) return urlEvidence.evidence

    if (shorthandConflictMarker.containsMatchIn(clause)) return null
    return issueNumberCandidate.findAll(clause)
        .filter { match -> urlMatches.none { match.range.first in it.range } }
        .mapNotNull { match ->
            val numberText = match.groupValues[1]
            val issueNumber = numberText.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            EvidenceCandidate(
                start = match.range.first,
                endExclusive = match.range.last + 1,
                evidence = GitHubIssueEvidence(GitHubIssueEvidenceType.ISSUE_NUMBER, issueNumber),
            )
        }
        .firstOrNull { candidate -> successRanges.any { withinEvidenceWindow(clause, candidate, it) } }
        ?.evidence
}

private fun parseIssueUrlCandidate(rawCandidate: String, start: Int): EvidenceCandidate? {
    val candidate = rawCandidate.trimEnd('.', ',', '!', ';', ':', '。', '，', '！', '；', '：')
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (!uri.host.equals("github.com", ignoreCase = true)) return null
    if (uri.userInfo != null || uri.port != -1 || '%' in uri.rawPath) return null
    val segments = uri.rawPath.split('/').filter(String::isNotEmpty)
    if (segments.size != 4 || segments[2] != "issues") return null
    if (segments[0].isBlank() || segments[1].isBlank()) return null
    val numberText = segments[3]
    val issueNumber = numberText.toLongOrNull()
        ?.takeIf { it > 0 && numberText.firstOrNull() != '0' }
        ?: return null
    val normalized = URI(
        "https",
        null,
        "github.com",
        -1,
        "/${segments[0]}/${segments[1]}/issues/$issueNumber",
        uri.rawQuery,
        uri.rawFragment,
    ).toASCIIString()
    return EvidenceCandidate(
        start = start,
        endExclusive = start + candidate.length,
        evidence = GitHubIssueEvidence(GitHubIssueEvidenceType.ISSUE_URL, issueNumber, normalized),
    )
}

private fun withinEvidenceWindow(clause: String, candidate: EvidenceCandidate, success: TextRange): Boolean {
    val gapStart: Int
    val gapEnd: Int
    if (candidate.endExclusive <= success.start) {
        gapStart = candidate.endExclusive
        gapEnd = success.start
    } else if (success.endExclusive <= candidate.start) {
        gapStart = success.endExclusive
        gapEnd = candidate.start
    } else {
        return true
    }
    return clause.codePointCount(gapStart, gapEnd) <= MAX_EVIDENCE_DISTANCE_CODE_POINTS
}

private fun stripMarkdownEvidenceExclusions(text: String): String {
    var fenceMarker: String? = null
    val withoutBlocksAndQuotes = text.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        val openingMarker = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (fenceMarker != null) {
            if (openingMarker == fenceMarker) fenceMarker = null
            ""
        } else if (openingMarker != null) {
            fenceMarker = openingMarker
            ""
        } else if (trimmed.startsWith('>')) {
            ""
        } else {
            maskInlineCode(line)
        }
    }
    return withoutBlocksAndQuotes
}

private fun maskInlineCode(line: String): String {
    if ('`' !in line) return line
    val result = StringBuilder(line)
    var index = 0
    while (index < line.length) {
        if (line[index] != '`') {
            index++
            continue
        }
        val delimiterStart = index
        while (index < line.length && line[index] == '`') index++
        val delimiter = line.substring(delimiterStart, index)
        val closingStart = line.indexOf(delimiter, startIndex = index)
        val maskedEnd = if (closingStart >= 0) closingStart + delimiter.length else line.length
        for (position in delimiterStart until maskedEnd) result.setCharAt(position, ' ')
        index = maskedEnd
    }
    return result.toString()
}

private fun String.indicesOf(needle: String): Sequence<Int> = sequence {
    var start = indexOf(needle)
    while (start >= 0) {
        yield(start)
        start = indexOf(needle, start + needle.length)
    }
}

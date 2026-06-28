package me.rerere.common.android

private val SENSITIVE_HEADER_NAMES = setOf(
    "authorization",
    "proxy-authorization",
    "x-api-key",
    "api-key",
    "x-goog-api-key",
    "anthropic-api-key",
    "cookie",
    "set-cookie",
    "x-amz-security-token",
)

private const val REDACTED = "***REDACTED***"

fun redactHeaders(headers: Map<String, String>): Map<String, String> =
    headers.mapValues { (key, value) ->
        if (key.lowercase() in SENSITIVE_HEADER_NAMES) REDACTED else value
    }

private val SENSITIVE_BODY_PATTERNS = listOf(
    Regex(
        """("(?:api[_-]?key|apikey|authorization|token|secret|access[_-]?token)"\s*:\s*)"[^"]*""",
        RegexOption.IGNORE_CASE,
    ),
)

private val SENSITIVE_QUERY_PARAM_PATTERN = Regex(
    """([?&](?:api[_-]?key|apikey|token|secret|access[_-]?token)=)([^&\s"#]*)""",
    RegexOption.IGNORE_CASE,
)

fun redactSecrets(text: String): String {
    var result = text
    for (pattern in SENSITIVE_BODY_PATTERNS) {
        result = pattern.replace(result) { match ->
            match.groupValues[1] + "\"" + REDACTED + "\""
        }
    }
    result = SENSITIVE_QUERY_PARAM_PATTERN.replace(result) { match ->
        match.groupValues[1] + REDACTED
    }
    return result
}

fun LogEntry.redacted(): LogEntry = when (this) {
    is LogEntry.TextLog -> this
    is LogEntry.RequestLog -> copy(
        url = redactSecrets(url),
        requestHeaders = redactHeaders(requestHeaders),
        responseHeaders = redactHeaders(responseHeaders),
        requestBody = requestBody?.let(::redactSecrets),
    )
}

package me.rerere.common.android

private const val MAX_BODY_CHARS = 2048
private const val MAX_HEADER_VALUE_CHARS = 2048

fun truncateLogEntry(entry: LogEntry): LogEntry = when (entry) {
    is LogEntry.TextLog -> entry
    is LogEntry.RequestLog -> entry.copy(
        requestHeaders = entry.requestHeaders.mapValues { (_, v) ->
            if (v.length > MAX_HEADER_VALUE_CHARS) v.take(MAX_HEADER_VALUE_CHARS) + "...[truncated]" else v
        },
        requestBody = entry.requestBody?.let { body ->
            if (body.length > MAX_BODY_CHARS) body.take(MAX_BODY_CHARS) + "...[truncated]" else body
        },
        responseHeaders = entry.responseHeaders.mapValues { (_, v) ->
            if (v.length > MAX_HEADER_VALUE_CHARS) v.take(MAX_HEADER_VALUE_CHARS) + "...[truncated]" else v
        },
    )
}
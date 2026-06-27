package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.common.android.redacted
import me.rerere.rikkahub.utils.JsonInstant

private const val MAX_BODY_CHARS = 2048
private const val MAX_HEADER_VALUE_CHARS = 2048
private const val MAX_PAYLOAD_CHARS = 16 * 1024

private fun truncateLogEntry(entry: LogEntry): LogEntry = when (entry) {
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

private fun buildPayloadJson(
    logs: List<LogEntry>,
    totalAvailable: Int,
): String = buildJsonObject {
    put("count", logs.size)
    put("totalAvailable", totalAvailable)
    put("requestLoggingEnabled", Logging.isRequestLoggingEnabled())
    put(
        "logs",
        JsonInstant.encodeToJsonElement(ListSerializer(LogEntry.serializer()), logs),
    )
}.toString()

private fun selectLogsForPayload(
    allLogs: List<LogEntry>,
    limit: Int,
): List<LogEntry> {
    val prepared = allLogs.map { truncateLogEntry(it.redacted()) }
    val overhead = buildPayloadJson(emptyList(), allLogs.size).length
    var remaining = MAX_PAYLOAD_CHARS - overhead
    val included = mutableListOf<LogEntry>()
    for (entry in prepared) {
        if (included.size >= limit) break
        val entryJson = JsonInstant.encodeToJsonElement(LogEntry.serializer(), entry).toString()
        val comma = if (included.isNotEmpty()) 1 else 0
        val cost = comma + entryJson.length
        if (remaining < cost) break
        remaining -= cost
        included.add(entry)
    }
    return included
}

internal fun buildLogsTool(): Tool = Tool(
    name = "get_logs",
    description = """
        Retrieve the app's recent runtime logs, including AI HTTP request logs and text logs.
        Use this to inspect the requests the app made to AI providers (URL, method, status code,
        duration, errors) and general app log messages — helpful for debugging issues the user
        is experiencing. Sensitive headers (Authorization / API keys / cookies) are redacted.
        Optional 'type' filters logs: "all" (default), "request", or "text".
        Optional 'limit' caps the number of returned entries (default 20, max 32).
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("type", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("all")
                        add("request")
                        add("text")
                    })
                    put("description", "Filter logs by type: all (default), request, or text")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max number of log entries to return (default 20, max 32)")
                })
            }
        )
    },
    execute = {
        val params = it.jsonObject
        val type = params["type"]?.jsonPrimitive?.contentOrNull ?: "all"
        val limit = params["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?.coerceIn(1, 32) ?: 20

        val allLogs: List<LogEntry> = when (type) {
            "request" -> Logging.getRequestLogs()
            "text" -> Logging.getTextLogs()
            else -> Logging.getRecentLogs()
        }
        val selected = selectLogsForPayload(allLogs, limit)
        listOf(UIMessagePart.Text(buildPayloadJson(selected, allLogs.size)))
    }
)

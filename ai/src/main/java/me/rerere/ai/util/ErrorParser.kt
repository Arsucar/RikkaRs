package me.rerere.ai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

class HttpException(
    message: String
) : RuntimeException(message)

private val errorFields = listOf("error", "detail", "message", "description")
private const val MAX_HTTP_REASON_LENGTH = 100
const val MAX_ERROR_BODY_LOG_PREVIEW_LENGTH = 500

fun JsonElement.parseErrorDetail(): HttpException {
    return when (this) {
        is JsonObject -> {
            val foundField = errorFields.firstOrNull { this[it] != null }

            if (foundField != null) {
                this[foundField]!!.parseErrorDetail()
            } else {
                HttpException(Json.encodeToString(JsonElement.serializer(), this))
            }
        }

        is JsonArray -> {
            if (this.isEmpty()) {
                HttpException("Unknown error: Empty JSON array")
            } else {
                this.first().parseErrorDetail()
            }
        }

        is JsonPrimitive -> HttpException(this.jsonPrimitive.content)

        else -> HttpException(Json.encodeToString(JsonElement.serializer(), this))
    }
}

fun httpStatusException(
    statusCode: Int,
    reasonPhrase: String? = null
): HttpException = HttpException(formatHttpStatus(statusCode, reasonPhrase))

fun parseHttpErrorResponse(
    response: Response,
    bodyRaw: String?
): HttpException {
    return parseErrorDetailFromResponseBody(
        bodyRaw = bodyRaw,
        statusCode = response.code,
        reasonPhrase = response.message,
        contentType = response.body.contentType()?.toString()
    ) ?: httpStatusException(response.code, response.message)
}

fun parseErrorDetailFromResponseBody(
    bodyRaw: String?,
    statusCode: Int? = null,
    reasonPhrase: String? = null,
    contentType: String? = null
): HttpException? {
    val status = statusCode?.let { formatHttpStatus(it, reasonPhrase) }
    val trimmed = bodyRaw?.trim().orEmpty()
    if (trimmed.isBlank()) return status?.let(::HttpException)

    parseJsonError(trimmed)?.let { return combineStatusAndDetail(status, it) }

    if (isHtmlResponse(trimmed, contentType)) {
        return HttpException(withStatus(status, "Upstream returned an unexpected HTML response"))
    }

    parseJsonSequenceError(trimmed)?.let { return combineStatusAndDetail(status, it) }

    return HttpException(withStatus(status, "Upstream returned an unexpected non-JSON response"))
}

fun errorBodyLogPreview(bodyRaw: String?): String {
    val normalized = bodyRaw
        ?.replace(Regex("[\\r\\n\\t]+"), " ")
        ?.trim()
        .orEmpty()
    if (normalized.isEmpty()) return "<empty>"
    return normalized.take(MAX_ERROR_BODY_LOG_PREVIEW_LENGTH)
}

private fun parseJsonError(value: String): HttpException? {
    return runCatching { Json.parseToJsonElement(value).parseErrorDetail() }.getOrNull()
}

private fun parseJsonSequenceError(bodyRaw: String): HttpException? {
    val lines = bodyRaw.lineSequence()
        .map { it.trim().removePrefix("data:").trim() }
        .filter { it.isNotBlank() && it != "[DONE]" }

    var firstParsed: HttpException? = null
    for (line in lines) {
        val candidates = extractJsonValues(line).ifEmpty { listOf(line) }
        for (candidate in candidates) {
            val element = runCatching { Json.parseToJsonElement(candidate) }.getOrNull() ?: continue
            val detail = element.parseErrorDetail()
            if (element is JsonObject && element.containsKey("error")) return detail
            if (firstParsed == null) firstParsed = detail
        }
    }
    return firstParsed
}

private fun extractJsonValues(value: String): List<String> {
    val values = mutableListOf<String>()
    var start = -1
    var depth = 0
    var inString = false
    var escaped = false

    value.forEachIndexed { index, character ->
        if (start < 0) {
            if (character == '{' || character == '[') {
                start = index
                depth = 1
            }
            return@forEachIndexed
        }

        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
            return@forEachIndexed
        }

        when (character) {
            '"' -> inString = true
            '{', '[' -> depth++
            '}', ']' -> {
                depth--
                if (depth == 0) {
                    values += value.substring(start, index + 1)
                    start = -1
                }
            }
        }
    }

    return values
}

private fun isHtmlResponse(bodyRaw: String, contentType: String?): Boolean {
    if (contentType?.contains("html", ignoreCase = true) == true) return true

    val prefix = bodyRaw.take(512).lowercase()
    return prefix.startsWith("<!doctype html") ||
        prefix.startsWith("<html") ||
        prefix.contains("<head") ||
        prefix.contains("<body") ||
        prefix.contains("<script") ||
        prefix.contains("<style")
}

private fun formatHttpStatus(statusCode: Int, reasonPhrase: String?): String {
    val reason = reasonPhrase
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(MAX_HTTP_REASON_LENGTH)
        ?.takeIf { it.isNotEmpty() }
    return if (reason == null) "HTTP $statusCode" else "HTTP $statusCode $reason"
}

private fun combineStatusAndDetail(status: String?, detail: HttpException): HttpException {
    val detailMessage = detail.message?.trim().orEmpty()
    if (status == null || detailMessage.isEmpty()) return detail
    return HttpException("$status: $detailMessage")
}

private fun withStatus(status: String?, message: String): String {
    return if (status == null) message else "$status: $message"
}

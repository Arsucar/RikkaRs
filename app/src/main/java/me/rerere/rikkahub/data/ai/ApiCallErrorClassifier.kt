package me.rerere.rikkahub.data.ai

import me.rerere.ai.util.HttpException
import me.rerere.common.android.redactSecrets
import me.rerere.rikkahub.data.db.entity.ApiCallErrorType
import me.rerere.rikkahub.data.db.entity.ApiCallStatus
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

data class ClassifiedApiError(
    val errorType: String,
    val errorCode: String?,
    val errorMessage: String?,
    /** Room status: TIMEOUT vs ERROR */
    val status: String = ApiCallStatus.ERROR,
)

object ApiCallErrorClassifier {
    private const val MAX_MESSAGE_LEN = 200
    private val HTTP_CODE = Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE)
    /** OpenAI-style / bare key material often echoed in 401 bodies. */
    private val API_KEY_TOKEN = Regex(
        """(?i)\b(?:sk-|sk-proj-|sk-ant-|rk-)[A-Za-z0-9_\-]{8,}""",
    )
    private val BEARER_TOKEN = Regex(
        """(?i)\b(Bearer\s+)([A-Za-z0-9\-._~+/]+=*)""",
    )
    private val KEY_ASSIGNMENT = Regex(
        """(?i)\b((?:api[_-]?key|access[_-]?token|secret|token)\s*[:=]\s*)(\S+)""",
    )

    fun classify(throwable: Throwable): ClassifiedApiError {
        val root = unwrap(throwable)
        val message = root.message.orEmpty()
        val httpCode = extractHttpCode(message)
        val lower = message.lowercase()

        val typeAndStatus = when {
            isTimeout(root, lower) -> ApiCallErrorType.TIMEOUT to ApiCallStatus.TIMEOUT
            isNetwork(root) -> ApiCallErrorType.NETWORK to ApiCallStatus.ERROR
            httpCode == 401 || httpCode == 403 || isAuthMessage(lower) ->
                ApiCallErrorType.AUTH to ApiCallStatus.ERROR
            httpCode == 429 || lower.contains("rate limit") || lower.contains("too many requests") ->
                ApiCallErrorType.RATE_LIMIT to ApiCallStatus.ERROR
            isContentFilter(lower) -> ApiCallErrorType.CONTENT_FILTER to ApiCallStatus.ERROR
            httpCode != null && httpCode in 500..599 -> ApiCallErrorType.SERVER to ApiCallStatus.ERROR
            root is HttpException && httpCode == null && lower.contains("server") ->
                ApiCallErrorType.SERVER to ApiCallStatus.ERROR
            else -> ApiCallErrorType.UNKNOWN to ApiCallStatus.ERROR
        }

        return ClassifiedApiError(
            errorType = typeAndStatus.first,
            errorCode = httpCode?.let { "HTTP $it" },
            errorMessage = sanitizeMessage(message),
            status = typeAndStatus.second,
        )
    }

    fun sanitizeMessage(raw: String?): String? {
        val trimmed = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (trimmed.isEmpty()) return null
        var safe = redactSecrets(trimmed)
        safe = KEY_ASSIGNMENT.replace(safe) { it.groupValues[1] + "***REDACTED***" }
        safe = BEARER_TOKEN.replace(safe) { it.groupValues[1] + "***REDACTED***" }
        safe = API_KEY_TOKEN.replace(safe, "***REDACTED***")
        return safe.take(MAX_MESSAGE_LEN)
    }

    private fun unwrap(throwable: Throwable): Throwable {
        var current: Throwable = throwable
        var depth = 0
        while (current.cause != null &&
            current.cause !== current &&
            depth < 6 &&
            (current.message.isNullOrBlank() || current is RuntimeException)
        ) {
            val cause = current.cause ?: break
            // Prefer HttpException / IOException leaves when present
            if (cause is HttpException || cause is IOException || cause is TimeoutException) {
                return cause
            }
            current = cause
            depth++
        }
        // Walk for first HttpException
        var walk: Throwable? = throwable
        depth = 0
        while (walk != null && depth < 8) {
            if (walk is HttpException || walk is SocketTimeoutException || walk is TimeoutException) {
                return walk
            }
            walk = walk.cause
            depth++
        }
        return throwable
    }

    private fun extractHttpCode(message: String): Int? =
        HTTP_CODE.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun isTimeout(root: Throwable, lower: String): Boolean =
        root is SocketTimeoutException ||
            root is TimeoutException ||
            lower.contains("timeout") ||
            lower.contains("timed out")

    private fun isNetwork(root: Throwable): Boolean =
        root is UnknownHostException ||
            root is ConnectException ||
            root is SSLException ||
            (root is IOException && root !is SocketTimeoutException)

    private fun isAuthMessage(lower: String): Boolean =
        lower.contains("unauthorized") ||
            lower.contains("invalid api key") ||
            lower.contains("invalid_api_key") ||
            lower.contains("authentication") ||
            lower.contains("auth failed") ||
            lower.contains("incorrect api key")

    private fun isContentFilter(lower: String): Boolean =
        lower.contains("content_filter") ||
            lower.contains("content filter") ||
            lower.contains("content policy") ||
            (lower.contains("safety") && lower.contains("block")) ||
            lower.contains("moderation") ||
            lower.contains("responsibleai")
}

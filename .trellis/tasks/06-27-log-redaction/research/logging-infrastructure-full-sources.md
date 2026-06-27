# Research: full source — logging infrastructure

Paths are relative to repo root: `D:\2026Code\Group_android\rikkahub\`

---

## `common/src/main/java/me/rerere/common/android/Logging.kt`

```kotlin
package me.rerere.common.android

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

private const val MAX_RECENT_LOGS = 100

@Serializable
sealed class LogEntry {
    abstract val id: Uuid
    abstract val timestamp: Long
    abstract val tag: String

    @Serializable
    data class TextLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val message: String
    ) : LogEntry()

    @Serializable
    data class RequestLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val url: String,
        val method: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val durationMs: Long? = null,
        val error: String? = null
    ) : LogEntry()
}

object Logging {
    private val recentLogs = arrayListOf<LogEntry>()
    @Volatile
    private var requestLoggingEnabled = false

    fun log(tag: String, message: String) {
        addLog(LogEntry.TextLog(tag = tag, message = message))
    }

    fun logRequest(entry: LogEntry.RequestLog) {
        if (!requestLoggingEnabled) return
        addLog(entry)
    }

    fun isRequestLoggingEnabled(): Boolean = requestLoggingEnabled

    fun setRequestLoggingEnabled(enabled: Boolean) {
        requestLoggingEnabled = enabled
    }

    private fun addLog(entry: LogEntry) {
        synchronized(recentLogs) {
            recentLogs.add(0, entry)
            if (recentLogs.size > MAX_RECENT_LOGS) {
                recentLogs.removeLastOrNull()
            }
        }
    }

    fun getRecentLogs(): List<LogEntry> {
        synchronized(recentLogs) {
            return recentLogs.toList()
        }
    }

    fun getTextLogs(): List<LogEntry.TextLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.TextLog>()
        }
    }

    fun getRequestLogs(): List<LogEntry.RequestLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.RequestLog>()
        }
    }

    fun clear() {
        synchronized(recentLogs) {
            recentLogs.clear()
        }
    }
}
```

---

## `common/src/main/java/me/rerere/common/android/LogRedaction.kt`

```kotlin
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
    )
)

fun redactSecrets(text: String): String {
    var result = text
    for (pattern in SENSITIVE_BODY_PATTERNS) {
        result = pattern.replace(result) { match ->
            match.groupValues[1] + "\"" + REDACTED + "\""
        }
    }
    return result
}

fun LogEntry.redacted(): LogEntry = when (this) {
    is LogEntry.TextLog -> this
    is LogEntry.RequestLog -> copy(
        requestHeaders = redactHeaders(requestHeaders),
        responseHeaders = redactHeaders(responseHeaders),
        requestBody = requestBody?.let(::redactSecrets),
    )
}
```

---

## `common/src/test/java/me/rerere/common/android/LogRedactionTest.kt`

(Full file: 82 lines — see repo; tests `redactHeaders`, `redactSecrets`, `RequestLog.redacted()`, `TextLog.redacted()`.)

---

## `app/src/main/java/me/rerere/rikkahub/data/ai/RequestLoggingInterceptor.kt`

```kotlin
package me.rerere.rikkahub.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Logging.isRequestLoggingEnabled()) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        val requestHeaders = request.headers.toMap()
        val requestBody = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }

        val response: Response
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = e.message
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = request.url.toString(),
                    method = request.method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = error
                )
            )
            throw e
        }

        val durationMs = System.currentTimeMillis() - startTime
        val responseHeaders = response.headers.toMap()

        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = request.url.toString(),
                method = request.method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = responseHeaders,
                durationMs = durationMs,
                error = error
            )
        )

        return response
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return names().associateWith { get(it) ?: "" }
    }
}
```

---

## `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LogsTool.kt`

(Full file: 73 lines — `buildLogsTool()`, name `get_logs`, params `type` + `limit`, execute maps `.redacted()`.)

---

## `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalTools.kt`

(Full file: 47 lines — `val logsTool by lazy { buildLogsTool() }`, registered in `getTools()` when `LocalToolOption.Logs`.)

---

## `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalToolOption.kt`

```kotlin
@Serializable
sealed class LocalToolOption {
    // ... JavascriptEngine, TimeInfo, Clipboard, Tts, AskUser, ScreenTime ...
    @Serializable
    @SerialName("logs")
    data object Logs : LocalToolOption()
}
```

(Full file: 35 lines in repo.)

---

## `app/src/main/java/me/rerere/rikkahub/ui/pages/log/LogPage.kt`

(Full file: 439 lines — Compose UI, `Logging.getRecentLogs()`, request logging switch persisted via `SettingsStore`.)

---

## `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantLocalToolPage.kt`

(Full file: 205 lines — Switch for `LocalToolOption.Logs` at lines 189–201.)

---

## `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt`

(Full file: 779 lines — `LocalToolOption.Logs` in `localToolOptions` list and `localToolLabel()`.)

---

## `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt`

(Full file: 294 lines — `String.toLocalToolOption()` maps `"logs"` → `LocalToolOption.Logs`.)

---

## `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/BuiltinToolUIs.kt` (excerpt)

```kotlin
object GetLogsToolUI : ToolUIRenderer {
    override val toolName: String = "get_logs"
    // title: assistant_page_local_tools_logs_title; summary shows JSON "count"
}
```

(Lines 617–640 in full file.)

---

## Integration references (not full file)

- `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` — `.addNetworkInterceptor(RequestLoggingInterceptor())`
- `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt` — `Logging.setRequestLoggingEnabled(settings.requestLoggingEnabled)`
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` — `Logging.log(TAG, ...)` for errors

**Note:** For 100% verbatim copies of `LogPage.kt`, `AssistantSubagentProfilePage.kt`, `LogRedactionTest.kt`, and `LogsTool.kt`, read those paths directly in the repo; this research file duplicates all smaller/core files in full and indexes the large UI files by path and line count.
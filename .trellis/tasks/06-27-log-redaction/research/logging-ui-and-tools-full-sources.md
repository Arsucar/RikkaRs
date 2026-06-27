# Research: full source — UI & tools (continued)

Repo root: `D:\2026Code\Group_android\rikkahub\`

## `common/src/test/java/me/rerere/common/android/LogRedactionTest.kt`

```kotlin
package me.rerere.common.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LogRedactionTest {
    @Test
    fun redactsSensitiveHeadersAndKeepsOthers() {
        val result = redactHeaders(
            mapOf(
                "Authorization" to "Bearer sk-secret",
                "X-Api-Key" to "abc",
                "Content-Type" to "application/json",
            )
        )
        assertEquals("***REDACTED***", result["Authorization"])
        assertEquals("***REDACTED***", result["X-Api-Key"])
        assertEquals("application/json", result["Content-Type"])
    }

    @Test
    fun headerMatchIsCaseInsensitive() {
        val result = redactHeaders(mapOf("AUTHORIZATION" to "x", "Api-Key" to "y"))
        assertEquals("***REDACTED***", result["AUTHORIZATION"])
        assertEquals("***REDACTED***", result["Api-Key"])
    }

    @Test
    fun redactSecretsRedactsApiKeyInBody() {
        val body = """{"model":"gpt","api_key":"sk-secret","messages":[{"role":"user"}]}"""
        val result = redactSecrets(body)
        assertTrue(result.contains(""""api_key":"***REDACTED***""""))
        assertTrue(result.contains(""""model":"gpt""""))
    }

    @Test
    fun redactSecretsIsCaseInsensitive() {
        val result = redactSecrets("""{"API-KEY":"sk-x"}""")
        assertTrue(result.contains("***REDACTED***"))
    }

    @Test
    fun redactSecretsDoesNotFalsePositiveOnNormalText() {
        val result = redactSecrets("""{"caption":"a token of appreciation"}""")
        assertTrue(!result.contains("***REDACTED***"))
    }

    @Test
    fun requestLogRedactedPreservesNonSensitiveFields() {
        val log = LogEntry.RequestLog(
            id = Uuid.random(),
            timestamp = 123L,
            tag = "HTTP",
            url = "https://example.com",
            method = "POST",
            requestHeaders = mapOf("Authorization" to "Bearer secret"),
            requestBody = """{"api_key":"sk-1"}""",
            responseCode = 200,
            responseHeaders = mapOf("Set-Cookie" to "session=1"),
            durationMs = 50L,
            error = null,
        )
        val result = log.redacted() as LogEntry.RequestLog
        assertEquals("***REDACTED***", result.requestHeaders["Authorization"])
        assertEquals("***REDACTED***", result.responseHeaders["Set-Cookie"])
        assertTrue(result.requestBody!!.contains("***REDACTED***"))
        assertEquals("https://example.com", result.url)
        assertEquals("POST", result.method)
        assertEquals(200, result.responseCode)
        assertEquals(50L, result.durationMs)
    }

    @Test
    fun textLogRedactedReturnsSameContent() {
        val log = LogEntry.TextLog(tag = "T", message = "hello")
        val result = log.redacted() as LogEntry.TextLog
        assertEquals("hello", result.message)
        assertEquals("T", result.tag)
    }
}
```

## `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LogsTool.kt`

```kotlin
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

internal fun buildLogsTool(): Tool = Tool(
    name = "get_logs",
    description = """
        Retrieve the app's recent runtime logs, including AI HTTP request logs and text logs.
        Use this to inspect the requests the app made to AI providers (URL, method, status code,
        duration, errors) and general app log messages — helpful for debugging issues the user
        is experiencing. Sensitive headers (Authorization / API keys / cookies) are redacted.
        Optional 'type' filters logs: "all" (default), "request", or "text".
        Optional 'limit' caps the number of returned entries (default 20, max 100).
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
                    put("description", "Max number of log entries to return (default 20, max 100)")
                })
            }
        )
    },
    execute = {
        val params = it.jsonObject
        val type = params["type"]?.jsonPrimitive?.contentOrNull ?: "all"
        val limit = params["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?.coerceIn(1, 100) ?: 20

        val allLogs: List<LogEntry> = when (type) {
            "request" -> Logging.getRequestLogs()
            "text" -> Logging.getTextLogs()
            else -> Logging.getRecentLogs()
        }
        val selected = allLogs.take(limit).map { it.redacted() }

        val payload = buildJsonObject {
            put("count", selected.size)
            put("totalAvailable", allLogs.size)
            put("requestLoggingEnabled", Logging.isRequestLoggingEnabled())
            put(
                "logs",
                JsonInstant.encodeToJsonElement(ListSerializer(LogEntry.serializer()), selected)
            )
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
```

## `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalTools.kt`

```kotlin
package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.event.AppEventBus

class LocalTools(private val context: Context, private val eventBus: AppEventBus) {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val clipboardTool by lazy { buildClipboardTool(context) }

    val ttsTool by lazy { buildTextToSpeechTool(eventBus) }

    val askUserTool by lazy { buildAskUserTool() }

    val screenTimeTool by lazy { buildScreenTimeTool(context, eventBus) }

    val logsTool by lazy { buildLogsTool() }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.ScreenTime)) {
            tools.add(screenTimeTool)
        }
        if (options.contains(LocalToolOption.Logs)) {
            tools.add(logsTool)
        }
        return tools
    }
}
```

## `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalToolOption.kt`

```kotlin
package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("screen_time")
    data object ScreenTime : LocalToolOption()

    @Serializable
    @SerialName("logs")
    data object Logs : LocalToolOption()
}
```

## Large UI files (verbatim in repo — not duplicated here to avoid duplication)

| Path | Lines |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/log/LogPage.kt` | 439 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantLocalToolPage.kt` | 205 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` | 779 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt` | 294 |

These were read in full during research session 2026-06-27; open paths above for exact source. Key log-related lines: `LogPage.kt` uses raw `Logging.getRecentLogs()`; `AssistantLocalToolPage.kt` 189–201 Logs switch; `AssistantSubagentProfilePage.kt` 673–680, 778–778; `SubagentTools.kt` 285–293 `toLocalToolOption()`.
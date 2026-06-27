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

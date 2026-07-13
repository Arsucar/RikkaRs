package me.rerere.ai.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorParserTest {
    @Test
    fun `html error keeps status without exposing response body`() {
        val exception = parseErrorDetailFromResponseBody(
            bodyRaw = """
                <!doctype html>
                <html><head><script>secretGatewayPayload()</script></head><body>edge failure</body></html>
            """.trimIndent(),
            statusCode = 504,
            reasonPhrase = "Gateway Timeout",
            contentType = "text/html; charset=utf-8"
        )

        val message = requireNotNull(exception).message.orEmpty()
        assertTrue(message.contains("504"))
        assertTrue(message.contains("upstream", ignoreCase = true))
        assertFalse(message.contains("<html", ignoreCase = true))
        assertFalse(message.contains("<head", ignoreCase = true))
        assertFalse(message.contains("secretGatewayPayload"))
        assertFalse(message.contains("edge failure"))
    }

    @Test
    fun `plain text error keeps status without exposing response body`() {
        val exception = parseErrorDetailFromResponseBody(
            bodyRaw = "private proxy diagnostic token",
            statusCode = 403,
            reasonPhrase = "Forbidden",
            contentType = "text/plain"
        )

        val message = requireNotNull(exception).message.orEmpty()
        assertTrue(message.contains("403"))
        assertTrue(message.contains("non-JSON", ignoreCase = true))
        assertFalse(message.contains("private proxy diagnostic token"))
    }

    @Test
    fun `empty error body falls back to status exception`() {
        val exception = parseErrorDetailFromResponseBody(
            bodyRaw = "  ",
            statusCode = 502,
            reasonPhrase = "Bad Gateway",
            contentType = null
        )

        assertEquals("HTTP 502 Bad Gateway", requireNotNull(exception).message)
    }

    @Test
    fun `json error keeps status and provider detail`() {
        val exception = parseErrorDetailFromResponseBody(
            bodyRaw = """{"error":{"message":"rate limit exceeded"}}""",
            statusCode = 429,
            reasonPhrase = "Too Many Requests",
            contentType = "application/json"
        )

        val message = requireNotNull(exception).message.orEmpty()
        assertTrue(message.contains("429"))
        assertTrue(message.contains("rate limit exceeded"))
    }

    @Test
    fun `multiline and concatenated SSE json prefers error field`() {
        val exception = parseErrorDetailFromResponseBody(
            bodyRaw = """
                event: error
                data: {"message":"less useful"}{"error":{"message":"quota exhausted"}}
                data: [DONE]
            """.trimIndent(),
            statusCode = 400,
            reasonPhrase = "Bad Request",
            contentType = "text/event-stream"
        )

        val message = requireNotNull(exception).message.orEmpty()
        assertTrue(message.contains("400"))
        assertTrue(message.contains("quota exhausted"))
        assertFalse(message.contains("less useful"))
    }

    @Test
    fun `response helper carries response metadata into parsed error`() {
        val request = Request.Builder().url("https://example.test/v1/stream").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body(
                """{"error":{"message":"slow down"}}"""
                    .toResponseBody("application/json".toMediaType())
            )
            .build()

        val exception = parseHttpErrorResponse(response, """{"error":{"message":"slow down"}}""")

        assertNotNull(exception)
        assertTrue(exception.message.orEmpty().contains("HTTP 429 Too Many Requests"))
        assertTrue(exception.message.orEmpty().contains("slow down"))
    }

    @Test
    fun `error body log preview is bounded`() {
        val preview = errorBodyLogPreview("x".repeat(MAX_ERROR_BODY_LOG_PREVIEW_LENGTH + 100))

        assertEquals(MAX_ERROR_BODY_LOG_PREVIEW_LENGTH, preview.length)
    }
}

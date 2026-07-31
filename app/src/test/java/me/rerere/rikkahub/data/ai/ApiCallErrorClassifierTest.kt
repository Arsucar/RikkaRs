package me.rerere.rikkahub.data.ai

import me.rerere.ai.util.HttpException
import me.rerere.rikkahub.data.db.entity.ApiCallErrorType
import me.rerere.rikkahub.data.db.entity.ApiCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiCallErrorClassifierTest {
    @Test
    fun `classifies HTTP 401 as AUTH`() {
        val result = ApiCallErrorClassifier.classify(HttpException("HTTP 401 Unauthorized: invalid api key"))
        assertEquals(ApiCallErrorType.AUTH, result.errorType)
        assertEquals("HTTP 401", result.errorCode)
        assertEquals(ApiCallStatus.ERROR, result.status)
    }

    @Test
    fun `classifies HTTP 429 as RATE_LIMIT`() {
        val result = ApiCallErrorClassifier.classify(HttpException("HTTP 429 Too Many Requests"))
        assertEquals(ApiCallErrorType.RATE_LIMIT, result.errorType)
        assertEquals("HTTP 429", result.errorCode)
    }

    @Test
    fun `classifies HTTP 500 as SERVER`() {
        val result = ApiCallErrorClassifier.classify(HttpException("HTTP 500 Internal Server Error"))
        assertEquals(ApiCallErrorType.SERVER, result.errorType)
    }

    @Test
    fun `classifies socket timeout as TIMEOUT status`() {
        val result = ApiCallErrorClassifier.classify(SocketTimeoutException("timeout"))
        assertEquals(ApiCallErrorType.TIMEOUT, result.errorType)
        assertEquals(ApiCallStatus.TIMEOUT, result.status)
    }

    @Test
    fun `classifies unknown host as NETWORK`() {
        val result = ApiCallErrorClassifier.classify(UnknownHostException("api.example.com"))
        assertEquals(ApiCallErrorType.NETWORK, result.errorType)
    }

    @Test
    fun `classifies content filter message`() {
        val result = ApiCallErrorClassifier.classify(HttpException("content_filter triggered"))
        assertEquals(ApiCallErrorType.CONTENT_FILTER, result.errorType)
    }

    @Test
    fun `unknown errors fall back to UNKNOWN`() {
        val result = ApiCallErrorClassifier.classify(IllegalStateException("something odd"))
        assertEquals(ApiCallErrorType.UNKNOWN, result.errorType)
    }

    @Test
    fun `sanitizes long messages`() {
        val long = "x".repeat(500)
        val sanitized = ApiCallErrorClassifier.sanitizeMessage(long)
        assertEquals(200, sanitized!!.length)
    }

    @Test
    fun `redacts api keys echoed in auth errors`() {
        val result = ApiCallErrorClassifier.classify(
            HttpException("HTTP 401 Unauthorized: Incorrect API key provided: sk-proj-ABCDEFGHijklmnop"),
        )
        assertEquals(ApiCallErrorType.AUTH, result.errorType)
        assertTrue(result.errorMessage!!.contains("***REDACTED***"))
        assertTrue(!result.errorMessage!!.contains("sk-proj-"))
    }

    @Test
    fun `redacts bearer and json api_key fields`() {
        val sanitized = ApiCallErrorClassifier.sanitizeMessage(
            """Bearer tokensecret {"api_key":"sk-secretvalue"}""",
        )
        assertTrue(sanitized!!.contains("***REDACTED***"))
        assertTrue(!sanitized.contains("tokensecret"))
        assertTrue(!sanitized.contains("sk-secretvalue"))
    }

    @Test
    fun `unwraps cause HttpException`() {
        val wrapped = RuntimeException("wrapper", HttpException("HTTP 403 Forbidden"))
        val result = ApiCallErrorClassifier.classify(wrapped)
        assertEquals(ApiCallErrorType.AUTH, result.errorType)
        assertTrue(result.errorMessage!!.contains("403"))
    }
}

package me.rerere.ai.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SSEEventSourceTest {
    @Test
    fun `non-success response reports status exception and original response`() {
        val listener = RecordingEventSourceListener()
        val request = Request.Builder().url("https://example.test/v1/stream").build()
        val response = response(
            request = request,
            code = 504,
            message = "Gateway Timeout",
            body = "<html><body>gateway failure</body></html>",
            contentType = "text/html"
        )
        val eventSource = SSEEventSource(request, listener)

        eventSource.processResponse(response)

        assertSame(response, listener.failedResponse)
        assertEquals(504, listener.failedResponse?.code)
        assertNotNull(listener.failure)
        assertTrue(listener.failure?.message.orEmpty().contains("HTTP 504 Gateway Timeout"))
    }

    @Test
    fun `successful event stream still opens emits and closes`() {
        val listener = RecordingEventSourceListener()
        val request = Request.Builder().url("https://example.test/v1/stream").build()
        val response = response(
            request = request,
            code = 200,
            message = "OK",
            body = "data: hello\n\n",
            contentType = "text/event-stream"
        )
        val eventSource = SSEEventSource(request, listener)

        eventSource.processResponse(response)

        assertTrue(listener.opened)
        assertEquals(listOf("hello"), listener.events)
        assertTrue(listener.closed)
        assertNull(listener.failure)
    }

    private fun response(
        request: Request,
        code: Int,
        message: String,
        body: String,
        contentType: String
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()
    }

    private class RecordingEventSourceListener : EventSourceListener() {
        var opened = false
        var closed = false
        var failure: Throwable? = null
        var failedResponse: Response? = null
        val events = mutableListOf<String>()

        override fun onOpen(eventSource: EventSource, response: Response) {
            opened = true
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            events += data
        }

        override fun onClosed(eventSource: EventSource) {
            closed = true
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            failure = t
            failedResponse = response
        }
    }
}

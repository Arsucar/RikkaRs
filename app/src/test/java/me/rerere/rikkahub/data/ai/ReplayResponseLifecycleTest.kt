package me.rerere.rikkahub.data.ai

import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayResponseLifecycleTest {
    @Test
    fun `replaced responses close while handed off response stays open`() {
        val first = trackedResponse(429)
        val second = trackedResponse(429)
        val final = trackedResponse(200)
        val lifecycle = ReplayResponseLifecycle(first.response)

        lifecycle.closeBeforeReplay()
        lifecycle.accept(second.response)
        lifecycle.closeBeforeReplay()
        lifecycle.accept(final.response)
        lifecycle.handOff()
        lifecycle.closeIfNotHandedOff()

        assertTrue(first.body.closed)
        assertTrue(second.body.closed)
        assertFalse(final.body.closed)
    }

    @Test
    fun `owned response closes when replay is cancelled or fails`() {
        val tracked = trackedResponse(429)
        val lifecycle = ReplayResponseLifecycle(tracked.response)

        lifecycle.closeIfNotHandedOff()

        assertTrue(tracked.body.closed)
    }

    @Test
    fun `final 429 remains open after handoff`() {
        val tracked = trackedResponse(429)
        val lifecycle = ReplayResponseLifecycle(tracked.response)

        lifecycle.handOff()
        lifecycle.closeIfNotHandedOff()

        assertFalse(tracked.body.closed)
    }

    private fun trackedResponse(code: Int): TrackedResponse {
        val body = TrackingBody()
        val response = Response.Builder()
            .request(Request.Builder().url("http://localhost").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body)
            .build()
        return TrackedResponse(response, body)
    }

    private data class TrackedResponse(val response: Response, val body: TrackingBody)

    private class TrackingBody : ResponseBody() {
        var closed = false
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 0
        override fun source(): BufferedSource = Buffer()
        override fun close() {
            closed = true
            super.close()
        }
    }
}

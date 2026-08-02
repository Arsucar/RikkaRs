package me.rerere.rikkahub.data.ai.clash

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClashApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ClashApiClient
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
        client = ClashApiClient(json = Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    @Test
    fun `getSelectableNodes parses group nodes and excludes DIRECT REJECT`() = runBlocking {
        enqueueJson(
            """{"proxies":{"GLOBAL":{"all":["🇺🇸 US","DIRECT","REJECT","🇯🇵 JP"],"now":"🇺🇸 US"}}}"""
        )

        val nodes = client.getSelectableNodes(baseUrl, "GLOBAL")

        assertEquals(listOf("🇺🇸 US", "🇯🇵 JP"), nodes)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/proxies", request.path)
    }

    @Test
    fun `getCurrentNode returns the now field`() = runBlocking {
        enqueueJson(
            """{"proxies":{"GLOBAL":{"all":["🇺🇸 US","🇯🇵 JP"],"now":"🇺🇸 US"}}}"""
        )

        val current = client.getCurrentNode(baseUrl, "GLOBAL")

        assertEquals("🇺🇸 US", current)
    }

    @Test
    fun `switchNode sends correct PUT and returns true`() = runBlocking {
        enqueueJson("""{"result":"success"}""", code = 200)

        val switched = client.switchNode(baseUrl, "GLOBAL", "🇯🇵 JP")

        assertTrue(switched)
        val request: RecordedRequest = server.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path?.contains("/proxies/") == true)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"name\""))
        assertTrue(body.contains("JP"))
    }

    @Test
    fun `switchNode JSON-encodes special characters in node name`() = runBlocking {
        enqueueJson("""{"result":"success"}""", code = 200)

        client.switchNode(baseUrl, "GLOBAL", """node "quoted" \ path""")

        val body = server.takeRequest().body.readUtf8()
        assertEquals("""{"name":"node \"quoted\" \\ path"}""", body)
    }

    @Test
    fun `switchNode throws on non 200`() = runBlocking {
        enqueueJson("""{"error":"boom"}""", code = 500)

        assertThrows(IllegalStateException::class.java) {
            runBlocking<Unit> { client.switchNode(baseUrl, "GLOBAL", "🇯🇵 JP") }
        }
    }

    @Test
    fun `getSelectableNodes throws when group missing`() = runBlocking {
        enqueueJson("""{"proxies":{}}""", code = 200)

        assertThrows(IllegalStateException::class.java) {
            runBlocking<Unit> { client.getSelectableNodes(baseUrl, "GLOBAL") }
        }
    }
}
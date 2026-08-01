package me.rerere.rikkahub.data.ai.clash

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClashProxyConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `defaults are applied`() {
        val config = ClashProxyConfig()
        assertEquals("http://127.0.0.1:9090", config.apiBaseUrl)
        assertEquals("GLOBAL", config.groupName)
        assertEquals(2, config.maxRetries)
        assertEquals(500L, config.switchDelayMs)
        assertNull(config.validate())
    }

    @Test
    fun `validate rejects maxRetries out of range`() {
        assertEquals("maxRetries must be in 1..5", ClashProxyConfig(maxRetries = 0).validate())
        assertEquals("maxRetries must be in 1..5", ClashProxyConfig(maxRetries = 6).validate())
        assertNull(ClashProxyConfig(maxRetries = 1).validate())
        assertNull(ClashProxyConfig(maxRetries = 5).validate())
    }

    @Test
    fun `validate rejects switchDelayMs out of range`() {
        assertEquals("switchDelayMs must be in 100..2000", ClashProxyConfig(switchDelayMs = 99).validate())
        assertEquals("switchDelayMs must be in 100..2000", ClashProxyConfig(switchDelayMs = 2001).validate())
        assertNull(ClashProxyConfig(switchDelayMs = 100).validate())
        assertNull(ClashProxyConfig(switchDelayMs = 2000).validate())
    }

    @Test
    fun `serialization round trip preserves fields`() {
        val config = ClashProxyConfig(
            apiBaseUrl = "http://10.0.0.2:9090",
            groupName = "PROXY",
            maxRetries = 4,
            switchDelayMs = 800,
        )
        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<ClashProxyConfig>(encoded)
        assertEquals(config, decoded)
    }

    @Test
    fun `decoding legacy data fills defaults for missing fields`() {
        val decoded = json.decodeFromString<ClashProxyConfig>("""{"apiBaseUrl":"http://x"}""")
        assertEquals("http://x", decoded.apiBaseUrl)
        assertEquals("GLOBAL", decoded.groupName)
        assertEquals(2, decoded.maxRetries)
        assertEquals(500L, decoded.switchDelayMs)
    }
}
package me.rerere.rikkahub.data.sync.importer

import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.ui.components.ui.encodeForShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderImportDecoderTest {
    @Test
    fun decodeProviderImportText_routesV1ToComplete() {
        val encoded = ProviderSetting.OpenAI(name = "Shared", apiKey = "sk-x").encodeForShare()
        val result = decodeProviderImportText(encoded)

        assertTrue(result is ProviderImportResult.Complete)
        val setting = (result as ProviderImportResult.Complete).setting
        assertTrue(setting is ProviderSetting.OpenAI)
        assertEquals("Shared", setting.name)
    }

    @Test
    fun decodeProviderImportText_routesNewApiToNeedsName() {
        val json = """{"_type":"newapi_channel_conn","key":"sk-a","url":"https://gw.test"}"""
        val result = decodeProviderImportText(json)

        assertTrue(result is ProviderImportResult.NeedsName)
        val setting = (result as ProviderImportResult.NeedsName).setting as ProviderSetting.OpenAI
        assertEquals("sk-a", setting.apiKey)
        assertEquals("https://gw.test/v1", setting.baseUrl)
    }

    @Test
    fun decodeProviderImportText_routesOpenCodeToMultipleProviders() {
        val json = """
            {
              "${'$'}schema": "https://opencode.ai/config.json",
              "provider": {
                "anthropic": {
                  "npm": "@ai-sdk/anthropic",
                  "options": {
                    "baseURL": "https://claude.gateway/v1",
                    "apiKey": "sk-claude"
                  }
                },
                "deepseek": {
                  "npm": "@ai-sdk/openai-compatible",
                  "options": {
                    "baseURL": "https://deepseek.gateway/v1",
                    "apiKey": "sk-deepseek"
                  }
                }
              }
            }
        """.trimIndent()

        val result = decodeProviderImportText(json)

        assertTrue(result is ProviderImportResult.Multiple)
        val settings = (result as ProviderImportResult.Multiple).settings
        assertEquals(2, settings.size)
        val claude = settings[0] as ProviderSetting.Claude
        val openai = settings[1] as ProviderSetting.OpenAI
        assertEquals("Anthropic", claude.name)
        assertEquals("https://claude.gateway/v1", claude.baseUrl)
        assertEquals("sk-claude", claude.apiKey)
        assertEquals("Deepseek", openai.name)
        assertEquals("https://deepseek.gateway/v1", openai.baseUrl)
        assertEquals("sk-deepseek", openai.apiKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeProviderImportText_rejectsOpenCodeWithNoImportableProviders() {
        decodeProviderImportText("""{"provider":{"empty":{"options":{}}}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeProviderImportText_rejectsGarbage() {
        decodeProviderImportText("not-json-not-v1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeProviderImportText_rejectsJsonArray() {
        decodeProviderImportText("[1,2,3]")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeProviderImportText_rejectsWrongType() {
        decodeProviderImportText("""{"_type":"other","key":"k","url":"https://x.com"}""")
    }
}

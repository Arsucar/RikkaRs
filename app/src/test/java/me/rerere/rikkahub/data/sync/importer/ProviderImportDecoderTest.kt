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

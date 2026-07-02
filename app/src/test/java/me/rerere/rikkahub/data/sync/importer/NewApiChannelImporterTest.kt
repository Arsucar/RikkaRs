package me.rerere.rikkahub.data.sync.importer

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.ai.provider.ProviderSetting

class NewApiChannelImporterTest {
    @Test
    fun parseChannelConn_mapsKeyAndUrl() {
        val json = buildJsonObject {
            put("_type", JsonPrimitive(NewApiChannelImporter.TYPE))
            put("key", JsonPrimitive("sk-test"))
            put("url", JsonPrimitive("https://api.example.com/"))
        }

        val setting = NewApiChannelImporter.parseChannelConn(json)

        assertTrue(setting is ProviderSetting.OpenAI)
        val openAi = setting
        assertEquals("sk-test", openAi.apiKey)
        assertEquals("https://api.example.com/v1", openAi.baseUrl)
        assertEquals("NewAPI", openAi.name)
        assertTrue(openAi.models.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseChannelConn_rejectsWrongType() {
        val json = buildJsonObject {
            put("_type", JsonPrimitive("other"))
            put("key", JsonPrimitive("sk-test"))
            put("url", JsonPrimitive("https://api.example.com"))
        }
        NewApiChannelImporter.parseChannelConn(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseChannelConn_rejectsBlankKey() {
        val json = buildJsonObject {
            put("_type", JsonPrimitive(NewApiChannelImporter.TYPE))
            put("key", JsonPrimitive("   "))
            put("url", JsonPrimitive("https://api.example.com"))
        }
        NewApiChannelImporter.parseChannelConn(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseChannelConn_rejectsBlankUrl() {
        val json = buildJsonObject {
            put("_type", JsonPrimitive(NewApiChannelImporter.TYPE))
            put("key", JsonPrimitive("sk-test"))
            put("url", JsonPrimitive(""))
        }
        NewApiChannelImporter.parseChannelConn(json)
    }
}

package me.rerere.rikkahub.data.sync.importer

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderSetting

object NewApiChannelImporter {
    const val TYPE = "newapi_channel_conn"

    fun parseChannelConn(element: JsonElement): ProviderSetting.OpenAI {
        val obj = element.jsonObject
        val type = obj["_type"]?.jsonPrimitive?.contentOrNull
        require(type == TYPE) { "Unsupported import type: $type" }

        val key = obj["key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(key.isNotBlank()) { "API key is required" }

        val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(url.isNotBlank()) { "Gateway URL is required" }

        return ProviderSetting.OpenAI(
            name = "NewAPI",
            models = emptyList(),
            apiKey = key,
            baseUrl = normalizeGatewayBaseUrl(url),
        )
    }
}

internal fun normalizeGatewayBaseUrl(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    if (trimmed.isBlank()) return ProviderSetting.OpenAI().baseUrl
    return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
}

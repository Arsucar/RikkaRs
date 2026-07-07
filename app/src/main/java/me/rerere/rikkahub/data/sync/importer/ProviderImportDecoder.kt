package me.rerere.rikkahub.data.sync.importer

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.http.jsonObjectOrNull
import kotlin.io.encoding.Base64
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.utils.JsonInstant

sealed class ProviderImportResult {
    data class Complete(val setting: ProviderSetting) : ProviderImportResult()
    data class NeedsName(val setting: ProviderSetting) : ProviderImportResult()
    data class Multiple(val settings: List<ProviderSetting>) : ProviderImportResult()
}

fun decodeProviderSetting(value: String): ProviderSetting {
    require(value.startsWith("ai-provider:v1:")) { "Invalid provider setting string" }

    val base64Str = value.removePrefix("ai-provider:v1:")
    val jsonBytes = Base64.decode(base64Str)
    val jsonStr = jsonBytes.decodeToString()

    return JsonInstant.decodeFromString<ProviderSetting>(jsonStr)
}

fun decodeProviderImportText(raw: String): ProviderImportResult {
    val trimmed = raw.trim()
    if (trimmed.startsWith("ai-provider:v1:")) {
        return ProviderImportResult.Complete(decodeProviderSetting(trimmed))
    }

    val element = runCatching { JsonInstant.parseToJsonElement(trimmed) }
        .getOrElse { throw IllegalArgumentException("Invalid import format") }

    val obj = element.jsonObjectOrNull
        ?: throw IllegalArgumentException("Invalid import format")
    val type = obj["_type"]?.jsonPrimitive?.contentOrNull
    if (type == NewApiChannelImporter.TYPE) {
        return ProviderImportResult.NeedsName(NewApiChannelImporter.parseChannelConn(element))
    }

    if (OpenCodeProviderImporter.matches(obj)) {
        val settings = OpenCodeProviderImporter.importProviders(obj)
        if (settings.isEmpty()) throw IllegalArgumentException("Invalid import format")
        return ProviderImportResult.Multiple(settings)
    }

    throw IllegalArgumentException("Invalid import format")
}

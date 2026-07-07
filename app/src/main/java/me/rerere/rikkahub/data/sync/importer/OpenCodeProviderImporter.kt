package me.rerere.rikkahub.data.sync.importer

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.http.jsonObjectOrNull

/**
 * 导入 opencode.ai 风格的提供商配置 (见 issue #63)。
 *
 * opencode 的 config.json 以 `provider` 对象聚合多个提供商, 每个条目形如:
 * ```json
 * {
 *   "provider": {
 *     "anthropic": {
 *       "options": { "baseURL": "https://gateway.example/v1", "apiKey": "sk-..." },
 *       "npm": "@ai-sdk/anthropic"
 *     }
 *   },
 *   "$schema": "https://opencode.ai/config.json"
 * }
 * ```
 * 每个条目按 provider key / npm 包名映射到对应的 [ProviderSetting] 子类, 支持一次导入多个。
 * 第三方网关多为 OpenAI 兼容端点, 无法识别的类型统一兜底到 [ProviderSetting.OpenAI]。
 */
object OpenCodeProviderImporter {
    private const val SCHEMA_SUFFIX = "opencode.ai/config.json"

    /** opencode 配置判定: 含 `provider` 对象, 或 `${'$'}schema` 指向 opencode */
    fun matches(root: JsonObject): Boolean {
        if (root["provider"]?.jsonObjectOrNull != null) return true
        val schema = root["\$schema"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return schema.endsWith(SCHEMA_SUFFIX)
    }

    /** 将 opencode 配置中的每个 provider 条目转换为 [ProviderSetting], 空/无效条目会被跳过 */
    fun importProviders(root: JsonObject): List<ProviderSetting> {
        val providers = root["provider"]?.jsonObjectOrNull ?: return emptyList()
        return providers.mapNotNull { (key, value) ->
            val config = value.jsonObjectOrNull ?: return@mapNotNull null
            parseProvider(key, config)
        }
    }

    private fun parseProvider(key: String, config: JsonObject): ProviderSetting? {
        val options = config["options"]?.jsonObjectOrNull
        val baseUrl = options?.get("baseURL")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val apiKey = options?.get("apiKey")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        // 既无地址也无密钥的空条目没有导入价值
        if (baseUrl.isBlank() && apiKey.isBlank()) return null

        val npm = config["npm"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
        val name = key.replaceFirstChar { it.uppercase() }

        return when (classify(key.lowercase(), npm)) {
            ProviderKind.CLAUDE -> ProviderSetting.Claude(
                name = name,
                apiKey = apiKey,
                baseUrl = baseUrl.ifBlank { ProviderSetting.Claude().baseUrl },
            )

            ProviderKind.GOOGLE -> ProviderSetting.Google(
                name = name,
                apiKey = apiKey,
                baseUrl = baseUrl.ifBlank { ProviderSetting.Google().baseUrl },
            )

            ProviderKind.OPENAI -> ProviderSetting.OpenAI(
                name = name,
                apiKey = apiKey,
                baseUrl = baseUrl.ifBlank { ProviderSetting.OpenAI().baseUrl },
            )
        }
    }

    private enum class ProviderKind { CLAUDE, GOOGLE, OPENAI }

    private fun classify(key: String, npm: String): ProviderKind = when {
        key == "anthropic" || key == "claude" || npm.contains("anthropic") -> ProviderKind.CLAUDE
        key == "google" || key.startsWith("google-vertex") || key == "vertex" ||
            npm.contains("google") -> ProviderKind.GOOGLE
        // openai 及各类 OpenAI 兼容网关 (deepseek / qwen / ollama 等) 统一兜底
        else -> ProviderKind.OPENAI
    }
}

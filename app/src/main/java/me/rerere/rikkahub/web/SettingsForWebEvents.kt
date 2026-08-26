package me.rerere.rikkahub.web

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpOAuthState
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting

fun Settings.forWebEvents(): Settings = copy(
    providers = providers.map { it.withoutSecrets() },
    searchServices = searchServices.map { it.withoutSecrets() },
    mcpServers = mcpServers.map { it.withoutSecrets() },
    webDavConfig = webDavConfig.withoutSecrets(),
    s3Config = s3Config.withoutSecrets(),
    ttsProviders = ttsProviders.map { it.withoutSecrets() },
    asrProviders = asrProviders.map { it.withoutSecrets() },
    webServerAccessPassword = "",
    assistants = assistants.map { assistant ->
        assistant.copy(
            customHeaders = assistant.customHeaders.map { header ->
                header.copy(value = "")
            }
        )
    },
)

private fun ProviderSetting.withoutSecrets(): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(apiKey = "", models = models.map { it.withoutSecrets() })
    is ProviderSetting.Google -> copy(apiKey = "", privateKey = "", models = models.map { it.withoutSecrets() })
    is ProviderSetting.Claude -> copy(apiKey = "", models = models.map { it.withoutSecrets() })
}

private fun Model.withoutSecrets(): Model = copy(
    customHeaders = customHeaders.map { it.copy(value = "") },
    providerOverwrite = providerOverwrite?.withoutSecrets(),
)

private fun SearchServiceOptions.withoutSecrets(): SearchServiceOptions = when (this) {
    is SearchServiceOptions.BingLocalOptions -> this
    is SearchServiceOptions.DoubaoOptions -> copy(apiKey = "")
    is SearchServiceOptions.ZhipuOptions -> copy(apiKey = "")
    is SearchServiceOptions.TavilyOptions -> copy(apiKey = "")
    is SearchServiceOptions.ExaOptions -> copy(apiKey = "")
    is SearchServiceOptions.SearXNGOptions -> copy(password = "")
    is SearchServiceOptions.LinkUpOptions -> copy(apiKey = "")
    is SearchServiceOptions.BraveOptions -> copy(apiKey = "")
    is SearchServiceOptions.MetasoOptions -> copy(apiKey = "")
    is SearchServiceOptions.OllamaOptions -> copy(apiKey = "")
    is SearchServiceOptions.PerplexityOptions -> copy(apiKey = "")
    is SearchServiceOptions.FirecrawlOptions -> copy(apiKey = "")
    is SearchServiceOptions.JinaOptions -> copy(apiKey = "")
    is SearchServiceOptions.BochaOptions -> copy(apiKey = "")
    is SearchServiceOptions.RikkaHubOptions -> copy(apiKey = "")
    is SearchServiceOptions.GrokOptions -> copy(apiKey = "")
    is SearchServiceOptions.TinyfishOptions -> copy(apiKey = "")
    is SearchServiceOptions.SerperOptions -> copy(apiKey = "")
    is SearchServiceOptions.CustomJsOptions -> this
}

private fun McpServerConfig.withoutSecrets(): McpServerConfig {
    val redactedOptions = commonOptions.withoutSecrets()
    return clone(commonOptions = redactedOptions)
}

private fun McpCommonOptions.withoutSecrets(): McpCommonOptions = copy(
    headers = headers.map { (name, _) -> name to "" },
    oauth = oauth?.withoutSecrets(),
)

private fun McpOAuthState.withoutSecrets(): McpOAuthState = copy(
    clientSecret = null,
    accessToken = null,
    refreshToken = null,
)

private fun WebDavConfig.withoutSecrets(): WebDavConfig = copy(password = "")

private fun S3Config.withoutSecrets(): S3Config = copy(
    accessKeyId = "",
    secretAccessKey = "",
)

private fun TTSProviderSetting.withoutSecrets(): TTSProviderSetting = when (this) {
    is TTSProviderSetting.OpenAI -> copy(apiKey = "")
    is TTSProviderSetting.Gemini -> copy(apiKey = "")
    is TTSProviderSetting.SystemTTS -> this
    is TTSProviderSetting.MiniMax -> copy(apiKey = "")
    is TTSProviderSetting.Qwen -> copy(apiKey = "")
    is TTSProviderSetting.Groq -> copy(apiKey = "")
    is TTSProviderSetting.XAI -> copy(apiKey = "")
    is TTSProviderSetting.MiMo -> copy(apiKey = "")
    is TTSProviderSetting.ElevenLabs -> copy(apiKey = "")
    is TTSProviderSetting.Step -> copy(apiKey = "")
    is TTSProviderSetting.FishAudio -> copy(apiKey = "")
}

private fun ASRProviderSetting.withoutSecrets(): ASRProviderSetting = when (this) {
    is ASRProviderSetting.OpenAIRealtime -> copy(apiKey = "")
    is ASRProviderSetting.DashScope -> copy(apiKey = "")
    is ASRProviderSetting.Volcengine -> copy(apiKey = "")
    is ASRProviderSetting.MiMo -> copy(apiKey = "")
    is ASRProviderSetting.Step -> copy(apiKey = "")
}

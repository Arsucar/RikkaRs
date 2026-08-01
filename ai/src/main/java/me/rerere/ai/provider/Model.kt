package me.rerere.ai.provider

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningDialect
import kotlin.uuid.Uuid

@Immutable
@Serializable
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    val id: Uuid = Uuid.random(),
    val type: ModelType = ModelType.CHAT,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    val tools: Set<BuiltInTools> = emptySet(),
    val providerOverwrite: ProviderSetting? = null,
    /** Optional output dim for embedding APIs that support truncation (e.g. OpenAI `dimensions`). */
    val embeddingDimensions: Int? = null,
    /**
     * Per-model override for reasoning effort wire vocabulary (#207).
     * Default [ReasoningDialect.Auto] follows host / model-id recognition so official
     * DeepSeek works out of the box; set [ReasoningDialect.DeepSeekMax] explicitly for
     * proxy / mid-station baseUrls whose host is not `api.deepseek.com`.
     */
    val reasoningDialect: ReasoningDialect = ReasoningDialect.Auto,
)

@Serializable
enum class ModelType {
    CHAT,
    IMAGE,
    EMBEDDING,
}

@Serializable
enum class Modality {
    TEXT,
    IMAGE,
}

@Serializable
enum class ModelAbility {
    TOOL,
    REASONING,
}

// 模型(提供商)提供的内置工具选项
@Serializable
sealed class BuiltInTools {
    // https://ai.google.dev/gemini-api/docs/google-search?hl=zh-cn
    @Serializable
    @SerialName("search")
    data object Search : BuiltInTools()

    // https://ai.google.dev/gemini-api/docs/url-context?hl=zh-cn
    @Serializable
    @SerialName("url_context")
    data object UrlContext : BuiltInTools()

    @Serializable
    @SerialName("image_generation")
    data object ImageGeneration : BuiltInTools()
}




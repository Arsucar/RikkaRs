// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.memory.semantic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider

private const val TAG = "EmbeddingService"

/** Online embedding via configured providers + ModelType.EMBEDDING. */
class EmbeddingService(
    private val providerManager: ProviderManager,
) {
    suspend fun embed(
        texts: List<String>,
        settings: Settings,
        config: SemanticMemoryConfig,
    ): Result<List<List<Float>>> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty input"))
        }

        val model = config.embeddingModelId?.let { settings.findModelById(it) }
            ?: return@withContext Result.failure(IllegalStateException("No embedding model selected"))
        if (model.type != ModelType.EMBEDDING) {
            return@withContext Result.failure(IllegalStateException("Selected model is not embedding"))
        }
        val providerSetting = model.findProvider(settings.providers)
            ?: return@withContext Result.failure(
                IllegalStateException("Provider not found for embedding model ${model.modelId}"),
            )

        runCatching {
            SemanticMemoryStats.update { it.copy(embeddingCallCount = it.embeddingCallCount + 1) }
            Log.i(TAG, "embed: calling ${model.modelId} for ${texts.size} texts")

            val provider = providerManager.getProviderByType(providerSetting)
            val result = provider.generateEmbedding(
                providerSetting = providerSetting,
                params = EmbeddingGenerationParams(
                    model = model,
                    input = texts,
                    dimensions = model.embeddingDimensions,
                ),
            )

            val dim = result.embeddings.firstOrNull()?.size
            SemanticMemoryStats.update {
                it.copy(
                    embeddingSuccessCount = it.embeddingSuccessCount + 1,
                    lastEmbeddingDim = dim,
                    lastEmbeddingError = null,
                )
            }
            Log.i(TAG, "embed: success, dim=$dim, count=${result.embeddings.size}")
            result.embeddings
        }.onFailure { err ->
            SemanticMemoryStats.update {
                it.copy(
                    embeddingFailCount = it.embeddingFailCount + 1,
                    lastEmbeddingError = err.message,
                )
            }
            Log.e(TAG, "embed: failed", err)
        }
    }

    suspend fun embed(text: String, settings: Settings, config: SemanticMemoryConfig): Result<List<Float>> {
        return embed(listOf(text), settings, config).map { it.first() }
    }

    suspend fun testConnection(settings: Settings, config: SemanticMemoryConfig): Result<TestConnectionResult> {
        val label = resolveEmbeddingModelLabel(settings, config) ?: "unknown"
        return embed("hello", settings, config).map { embedding ->
            TestConnectionResult(
                success = true,
                dimensions = embedding.size,
                model = label,
            )
        }
    }

    data class TestConnectionResult(
        val success: Boolean,
        val dimensions: Int,
        val model: String,
    )

    companion object {
        private val embeddingJson = Json { ignoreUnknownKeys = true }

        /**
         * Label stored on [me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity.embeddingModel].
         * Prefer provider modelId; fall back to displayName.
         */
        fun resolveEmbeddingModelLabel(settings: Settings, config: SemanticMemoryConfig): String? {
            val model = config.embeddingModelId?.let { settings.findModelById(it) } ?: return null
            return model.modelId.takeIf { it.isNotBlank() }
                ?: model.displayName.takeIf { it.isNotBlank() }
        }

        fun encodeEmbedding(arr: FloatArray): String {
            return embeddingJson.encodeToString(arr.toList())
        }

        fun decodeEmbedding(str: String): FloatArray {
            return embeddingJson.decodeFromString<List<Float>>(str).toFloatArray()
        }
    }
}

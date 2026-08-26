// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.memory.semantic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity

private const val TAG = "MemorySummarizer"

@Serializable
private data class MemoryExtraction(
    val content: String = "",
    val summary: String = "",
    val importance: Int = 3,
    val isCore: Boolean = false,
    val date: String? = null,
)

data class SummarizeResult(
    val newMemories: Int = 0,
    val updatedMemories: Int = 0,
    val totalProcessed: Int = 0,
    val totalExtracted: Int = 0,
    val aiResponseLength: Int = 0,
    val newSummaries: List<String> = emptyList(),
    val mergedSummaries: List<String> = emptyList(),
    val discardedSummaries: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

/** AI-driven memory extraction + embedding + cos>0.85 merge. */
class MemorySummarizer(
    private val providerManager: ProviderManager,
    private val embeddingService: EmbeddingService,
    private val repository: SemanticMemoryRepository,
    private val recallService: RecallService,
    private val json: Json,
) {
    suspend fun summarize(
        messages: List<UIMessage>,
        assistantId: String,
        conversationId: String,
        settings: Settings,
        config: SemanticMemoryConfig,
    ): SummarizeResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "summarize: start, ${messages.size} messages for $assistantId")

        val model = config.summarizeModelId?.let { settings.findModelById(it) }
            ?: settings.findModelById(settings.chatModelId)
            ?: return@withContext SummarizeResult(errors = listOf("No model available for summarization"))

        val provider = model.findProvider(settings.providers)
            ?: return@withContext SummarizeResult(errors = listOf("Provider not found for model ${model.modelId}"))
        val providerImpl = providerManager.getProviderByType(provider)

        val conversationText = formatConversation(messages)
        if (conversationText.isBlank()) {
            return@withContext SummarizeResult()
        }

        val prompt = buildSummarizePrompt(conversationText, config)
        val requestMessages = listOf(UIMessage.user(prompt))

        val responseText = runCatching {
            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = requestMessages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                ),
            )
            chunk.message.toText()
        }.getOrElse {
            Log.e(TAG, "summarize: AI call failed", it)
            return@withContext SummarizeResult(errors = listOf("AI call failed: ${it.message}"))
        }

        val extractions = parseMemoryExtractionsInternal(responseText)
        Log.i(
            TAG,
            "summarize: AI response length=${responseText.length}, extracted ${extractions.size}",
        )
        if (extractions.isEmpty()) {
            return@withContext SummarizeResult(
                aiResponseLength = responseText.length,
                totalExtracted = 0,
            )
        }

        val limited = extractions.take(config.maxMemoriesPerSummary)
        val discarded = extractions.drop(config.maxMemoriesPerSummary)
            .map { it.summary.ifBlank { it.content.take(40) } }

        val contents = limited.map { it.content }
        val embeddingResult = embeddingService.embed(contents, settings, config)
        val embeddings: List<List<Float>?> = if (embeddingResult.isSuccess) {
            embeddingResult.getOrThrow().map { it }
        } else {
            Log.w(TAG, "summarize: embedding failed, storing without embeddings")
            limited.map { null }
        }
        val embeddingModelLabel = EmbeddingService.resolveEmbeddingModelLabel(settings, config)

        val existingMemories = repository.getMemoriesWithEmbedding(assistantId)
        var newCount = 0
        var updatedCount = 0
        val newSummaries = mutableListOf<String>()
        val mergedSummaries = mutableListOf<String>()

        limited.forEachIndexed { index, extraction ->
            val newEmbedding = embeddings.getOrNull(index)
            val newEmbeddingArr = newEmbedding?.toFloatArray()
            val duplicate = findDuplicate(newEmbeddingArr, existingMemories)

            if (duplicate != null) {
                val merged = duplicate.copy(
                    content = if (extraction.content.length > duplicate.content.length) {
                        extraction.content
                    } else {
                        duplicate.content
                    },
                    summary = extraction.summary.ifBlank { duplicate.summary },
                    importance = maxOf(duplicate.importance, extraction.importance),
                    isCore = duplicate.isCore || extraction.isCore,
                    updatedAt = System.currentTimeMillis(),
                )
                val withEmbedding = newEmbeddingArr?.let {
                    merged.copy(
                        embedding = EmbeddingService.encodeEmbedding(it),
                        embeddingModel = embeddingModelLabel,
                    )
                } ?: merged
                repository.updateMemory(withEmbedding)
                updatedCount++
                mergedSummaries += (extraction.summary.ifBlank { extraction.content.take(40) })
                Log.d(TAG, "summarize: merged memory #${duplicate.id}")
            } else {
                val contentWithDate = extraction.date?.let { d -> "[$d] ${extraction.content}" }
                    ?: extraction.content
                val entity = EpisodicMemoryEntity(
                    assistantId = assistantId,
                    content = contentWithDate,
                    summary = extraction.summary,
                    importance = extraction.importance.coerceIn(2, 5),
                    isCore = extraction.isCore,
                    embedding = newEmbeddingArr?.let { EmbeddingService.encodeEmbedding(it) },
                    embeddingModel = newEmbeddingArr?.let { embeddingModelLabel },
                    sourceConversationId = conversationId,
                )
                repository.addMemory(entity)
                newCount++
                newSummaries += (extraction.summary.ifBlank { extraction.content.take(40) })
            }
        }

        if (config.autoEvictionEnabled) {
            val count = repository.getMemoryCount(assistantId)
            if (count > config.maxMemoriesPerAssistant) {
                Log.w(
                    TAG,
                    "summarize: memory count $count exceeds limit ${config.maxMemoriesPerAssistant}",
                )
            }
        }

        recallService.invalidateCache(assistantId)

        SummarizeResult(
            newMemories = newCount,
            updatedMemories = updatedCount,
            totalProcessed = limited.size,
            totalExtracted = extractions.size,
            aiResponseLength = responseText.length,
            newSummaries = newSummaries,
            mergedSummaries = mergedSummaries,
            discardedSummaries = discarded,
        ).also {
            Log.i(TAG, "summarize: done, new=$newCount updated=$updatedCount")
        }
    }

    private fun formatConversation(messages: List<UIMessage>): String {
        return messages
            .filter { it.role != MessageRole.SYSTEM }
            .mapNotNull { msg ->
                val role = when (msg.role) {
                    MessageRole.USER -> "User"
                    MessageRole.ASSISTANT -> "Assistant"
                    else -> msg.role.name
                }
                val text = msg.toText().take(300)
                if (text.isBlank()) null else "$role: $text"
            }
            .takeLast(50)
            .joinToString("\n")
    }

    companion object {
        const val CONVERSATION_PLACEHOLDER = "{{conversation}}"
        const val DEDUPE_COSINE_THRESHOLD = 0.85f

        val DEFAULT_PROMPT = buildString {
            appendLine("你是一个记忆提取助手, 负责从用户与AI角色的长期角色扮演对话中提取有价值的记忆。")
            appendLine()
            appendLine("请分析以下对话片段, 提取值得长期记住的人物、事件、关系、偏好等记忆。")
            appendLine()
            appendLine("每条记忆必须包含以下字段:")
            appendLine("- \"content\": 简洁描述发生了什么事 (15-80字, 用对话语言)")
            appendLine("- \"summary\": 简短标题 (3-15字)")
            appendLine("- \"importance\": 2-5 (2=日常闲聊, 3=值得注意的日常事件, 4=重要事件/里程碑, 5=重大关系里程碑)")
            appendLine("- \"isCore\": true 仅限真正定义性的时刻 (初次见面、重大承诺、人生转折), 其余一律 false")
            appendLine("- \"date\": 事件日期 (如 \"2025-07-14\"), 对话中未明确日期时留空 null")
            appendLine()
            appendLine("提取重点: 人物/事件/情感/计划/偏好/事实/日期")
            appendLine("跳过: 无意义的打招呼、重复内容、filler对话")
            appendLine()
            appendLine("只输出JSON数组, 不要其他文字:")
            appendLine("[{\"content\":\"...\",\"summary\":\"...\",\"importance\":4,\"isCore\":false,\"date\":\"2025-07-14\"}]")
            appendLine()
            appendLine("如果没有值得记忆的内容, 输出: []")
            appendLine()
            appendLine("对话内容:")
            appendLine("<conversation>")
            appendLine(CONVERSATION_PLACEHOLDER)
            appendLine("</conversation>")
        }

        fun extractJsonArray(text: String): String? {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1)
            }
            return null
        }

        fun shouldMergeByCosine(sim: Float): Boolean = sim > DEDUPE_COSINE_THRESHOLD

        fun parseMemoryExtractionsForTest(text: String, json: Json): List<MemoryExtractionPublic> {
            val jsonStr = extractJsonArray(text) ?: return emptyList()
            return runCatching {
                json.decodeFromString(ListSerializer(MemoryExtraction.serializer()), jsonStr)
                    .map {
                        MemoryExtractionPublic(
                            content = it.content,
                            summary = it.summary,
                            importance = it.importance,
                            isCore = it.isCore,
                            date = it.date,
                        )
                    }
            }.getOrElse { emptyList() }
        }
    }

    private fun buildSummarizePrompt(conversation: String, config: SemanticMemoryConfig): String {
        val template = config.summarizePrompt ?: DEFAULT_PROMPT
        return template.replace(CONVERSATION_PLACEHOLDER, conversation)
    }

    private fun parseMemoryExtractionsInternal(text: String): List<MemoryExtraction> {
        val jsonStr = extractJsonArray(text) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(MemoryExtraction.serializer()), jsonStr)
        }.getOrElse {
            Log.w(TAG, "parseMemoryExtractions: failed to parse JSON: ${it.message}")
            emptyList()
        }
    }

    private fun findDuplicate(
        newEmbedding: FloatArray?,
        existing: List<EpisodicMemoryEntity>,
    ): EpisodicMemoryEntity? {
        if (newEmbedding == null) return null
        for (mem in existing) {
            val embStr = mem.embedding ?: continue
            val emb = runCatching { EmbeddingService.decodeEmbedding(embStr) }.getOrNull() ?: continue
            if (emb.size != newEmbedding.size) continue
            val sim = RecallService.cosineSimilarity(newEmbedding, emb)
            if (shouldMergeByCosine(sim)) return mem
        }
        return null
    }
}

/** Public DTO for unit tests of JSON parse. */
data class MemoryExtractionPublic(
    val content: String = "",
    val summary: String = "",
    val importance: Int = 3,
    val isCore: Boolean = false,
    val date: String? = null,
)

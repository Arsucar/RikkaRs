package me.rerere.rikkahub.data.memory.semantic

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.db.entity.EpisodicMemoryEntity
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.uuid.Uuid

class SemanticMemoryHelpersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun cosineSimilarity_identicalVectors_isOne() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, RecallService.cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun cosineSimilarity_orthogonal_isZero() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, RecallService.cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun hybridScore_weightsCosineHighest() {
        val high = RecallService.hybridScore(
            cosine = 0.9f,
            createdAt = System.currentTimeMillis(),
            importance = 3,
            isCore = false,
        )
        val low = RecallService.hybridScore(
            cosine = 0.1f,
            createdAt = System.currentTimeMillis(),
            importance = 3,
            isCore = false,
        )
        assertTrue(high > low)
    }

    @Test
    fun extractJsonArray_stripsFence() {
        val raw = """
            Here is the result:
            ```json
            [{"content":"met Alice","summary":"Alice","importance":4,"isCore":false,"date":null}]
            ```
        """.trimIndent()
        val arr = MemorySummarizer.extractJsonArray(raw)
        assertTrue(arr != null && arr.startsWith("["))
        val parsed = MemorySummarizer.parseMemoryExtractionsForTest(raw, json)
        assertEquals(1, parsed.size)
        assertEquals("met Alice", parsed[0].content)
        assertEquals(4, parsed[0].importance)
    }

    @Test
    fun shouldMergeByCosine_threshold() {
        assertTrue(MemorySummarizer.shouldMergeByCosine(0.86f))
        assertFalse(MemorySummarizer.shouldMergeByCosine(0.85f))
        assertFalse(MemorySummarizer.shouldMergeByCosine(0.5f))
    }

    @Test
    fun encodeDecodeEmbedding_roundTrip() {
        val original = floatArrayOf(0.1f, -0.2f, 0.3f)
        val encoded = EmbeddingService.encodeEmbedding(original)
        val decoded = EmbeddingService.decodeEmbedding(encoded)
        assertEquals(original.size, decoded.size)
        original.indices.forEach { i ->
            assertTrue(abs(original[i] - decoded[i]) < 1e-5f)
        }
    }

    @Test
    fun extractKeywords_filtersShortTokens() {
        val kws = RecallService.extractKeywords("A Alice met Bob today")
        assertTrue(kws.contains("alice"))
        assertTrue(kws.contains("today"))
        assertFalse(kws.contains("a")) // length < 2 filtered
    }

    @Test
    fun cacheFingerprint_changesWhenUpdatedAtOrCountChanges() {
        val base = listOf(
            EpisodicMemoryEntity(
                id = 1,
                assistantId = "a",
                content = "x",
                updatedAt = 100L,
                recallCount = 0,
            ),
        )
        val same = listOf(
            EpisodicMemoryEntity(
                id = 1,
                assistantId = "a",
                content = "x",
                updatedAt = 100L,
                recallCount = 0,
            ),
        )
        val updated = listOf(
            EpisodicMemoryEntity(
                id = 1,
                assistantId = "a",
                content = "x",
                updatedAt = 200L,
                recallCount = 0,
            ),
        )
        val recalled = listOf(
            EpisodicMemoryEntity(
                id = 1,
                assistantId = "a",
                content = "x",
                updatedAt = 100L,
                recallCount = 1,
            ),
        )
        assertEquals(RecallService.cacheFingerprint(base), RecallService.cacheFingerprint(same))
        assertNotEquals(RecallService.cacheFingerprint(base), RecallService.cacheFingerprint(updated))
        assertNotEquals(RecallService.cacheFingerprint(base), RecallService.cacheFingerprint(recalled))
        assertNotEquals(
            RecallService.cacheFingerprint(base),
            RecallService.cacheFingerprint(base + base),
        )
    }

    @Test
    fun isGlobalMemoryOwner_detectsGlobalAndEmpty() {
        assertTrue(SemanticMemoryManager.isGlobalMemoryOwner(MemoryRepository.GLOBAL_MEMORY_ID))
        assertTrue(SemanticMemoryManager.isGlobalMemoryOwner("__global__"))
        assertTrue(SemanticMemoryManager.isGlobalMemoryOwner("GLOBAL"))
        assertTrue(SemanticMemoryManager.isGlobalMemoryOwner("  "))
        assertFalse(SemanticMemoryManager.isGlobalMemoryOwner("0950e2dc-9bd5-4801-afa3-aa887aa36b4e"))
    }

    @Test
    fun filterNewMigrationContents_isIdempotentOnDuplicates() {
        val existing = setOf(" met Alice ", "likes coffee")
        val candidates = listOf(
            "met Alice",
            " likes coffee ",
            "new fact",
            "another",
        )
        val filtered = SemanticMemoryManager.filterNewMigrationContents(existing, candidates)
        assertEquals(listOf("new fact", "another"), filtered)
        assertEquals(
            emptyList<String>(),
            SemanticMemoryManager.filterNewMigrationContents(
                existing + filtered.map { it.trim() },
                candidates,
            ),
        )
    }

    @Test
    fun semanticMemoryConfig_roundTrip_embeddingModelId() {
        val id = Uuid.parse("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val original = SemanticMemoryConfig(
            enabled = true,
            embeddingModelId = id,
            topK = 7,
            summarizeModelId = id,
            maxCoreInject = 15,
            maxInjectChars = 8000,
            maxMemoryContentLen = null,
        )
        val encoded = JsonInstant.encodeToString(original)
        val decoded = JsonInstant.decodeFromString<SemanticMemoryConfig>(encoded)
        assertEquals(true, decoded.enabled)
        assertEquals(id, decoded.embeddingModelId)
        assertEquals(7, decoded.topK)
        assertEquals(id, decoded.summarizeModelId)
        assertEquals(15, decoded.maxCoreInject)
        assertEquals(8000, decoded.maxInjectChars)
        assertNull(decoded.maxMemoryContentLen)
        assertFalse(encoded.contains("embeddingBaseUrl"))
        assertFalse(encoded.contains("embeddingApiKey"))
        assertFalse(encoded.contains("embeddingDimensions"))
    }

    @Test
    fun semanticMemoryConfig_ignoresLegacyEmbeddingApiKeys() {
        // PreferencesStore uses JsonInstant (ignoreUnknownKeys=true); old DataStore blobs
        // with embeddingBaseUrl/apiKey/model/dimensions decode cleanly to defaults.
        val legacy = """
            {
              "enabled": true,
              "embeddingBaseUrl": "https://api.siliconflow.cn/v1",
              "embeddingApiKey": "sk-legacy",
              "embeddingModel": "BAAI/bge-m3",
              "embeddingDimensions": 1024,
              "topK": 12
            }
        """.trimIndent()
        val decoded = JsonInstant.decodeFromString<SemanticMemoryConfig>(legacy)
        assertTrue(decoded.enabled)
        assertNull(decoded.embeddingModelId)
        assertEquals(12, decoded.topK)
        assertEquals(DEFAULT_SEMANTIC_MAX_CORE_INJECT, decoded.maxCoreInject)
        assertEquals(DEFAULT_SEMANTIC_MAX_INJECT_CHARS, decoded.maxInjectChars)
        assertEquals(DEFAULT_SEMANTIC_MAX_MEMORY_CONTENT_LEN, decoded.maxMemoryContentLen)
    }
}

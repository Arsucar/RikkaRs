package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.db.dao.ApiCallErrorTypeCountRow
import me.rerere.rikkahub.data.db.dao.ApiCallModelHealthRow
import me.rerere.rikkahub.data.db.entity.ApiCallErrorType
import me.rerere.rikkahub.data.db.entity.ApiCallRecordEntity
import me.rerere.rikkahub.data.db.entity.ApiCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCallHealthComputeTest {
    @Test
    fun `aggregate groups by provider and model`() {
        val records = listOf(
            success("p1", "m1", latency = 100, at = 1),
            error("p1", "m1", at = 2),
            success("p1", "m2", latency = 200, at = 3),
            pending("p1", "m1", at = 4),
        )
        val overview = ApiCallHealthCompute.aggregate(records)
        assertEquals(3, overview.totalCalls)
        assertEquals(2, overview.successCount)
        assertEquals(1, overview.errorCount)
        assertEquals(2, overview.models.size)
        val m1 = overview.models.first { it.modelId == "m1" }
        assertEquals(2, m1.totalCount)
        assertEquals(0.5f, m1.successRate, 0.001f)
        assertEquals(100L, m1.avgLatencyMs)
    }

    @Test
    fun `aggregateFromRows preserves low-frequency models without global limit`() {
        val rows = listOf(
            healthRow(
                providerId = "p1",
                modelId = "hot",
                totalCount = 9000,
                successCount = 8000,
                errorCount = 1000,
                avgLatencyMs = 120.0,
                recentStatuses = "SUCCESS,SUCCESS,SUCCESS,SUCCESS,SUCCESS",
            ),
            healthRow(
                providerId = "p2",
                modelId = "rare",
                totalCount = 3,
                successCount = 0,
                errorCount = 3,
                avgLatencyMs = null,
                lastErrorType = ApiCallErrorType.SERVER,
                recentStatuses = "ERROR,ERROR,ERROR",
            ),
        )
        val errorTypes = listOf(
            ApiCallErrorTypeCountRow("p2", "rare", ApiCallErrorType.SERVER, 3),
        )
        val overview = ApiCallHealthCompute.aggregateFromRows(rows, errorTypes)
        assertEquals(9003, overview.totalCalls)
        assertEquals(2, overview.models.size)
        assertEquals("hot", overview.models.first().modelId)
        val rare = overview.models.first { it.modelId == "rare" }
        assertEquals(3, rare.totalCount)
        assertEquals(0f, rare.successRate, 0.001f)
        assertEquals(mapOf(ApiCallErrorType.SERVER to 3), rare.errorTypeCounts)
        assertFalse(rare.almostUnavailable) // only 3 samples
    }

    @Test
    fun `aggregateFromRows marks almost unavailable from recentStatuses`() {
        val rows = listOf(
            healthRow(
                providerId = "p",
                modelId = "m",
                totalCount = 5,
                successCount = 0,
                errorCount = 5,
                recentStatuses = "ERROR,TIMEOUT,ERROR,ERROR,ERROR",
            ),
        )
        val overview = ApiCallHealthCompute.aggregateFromRows(rows)
        assertTrue(overview.models.single().almostUnavailable)
    }

    @Test
    fun `almost unavailable when last 5 all failed`() {
        val recent = (1..5).map { error("p", "m", at = it.toLong()) }
        assertTrue(ApiCallHealthCompute.isAlmostUnavailable(recent.reversed()))
    }

    @Test
    fun `almost unavailable when error rate at least 80 percent with 5 plus samples`() {
        val mixed = listOf(
            success("p", "m", at = 1),
            error("p", "m", at = 2),
            error("p", "m", at = 3),
            error("p", "m", at = 4),
            error("p", "m", at = 5),
        )
        assertTrue(ApiCallHealthCompute.isAlmostUnavailable(mixed.sortedByDescending { it.requestAt }))
    }

    @Test
    fun `not almost unavailable with few samples`() {
        val few = listOf(error("p", "m", at = 1), error("p", "m", at = 2))
        assertFalse(ApiCallHealthCompute.isAlmostUnavailable(few))
    }

    @Test
    fun `cancelled and pending excluded from rates`() {
        val records = listOf(
            success("p", "m", at = 1),
            record("p", "m", ApiCallStatus.CANCELLED, at = 2),
            pending("p", "m", at = 3),
        )
        val overview = ApiCallHealthCompute.aggregate(records)
        assertEquals(1, overview.totalCalls)
        assertEquals(1f, overview.successRate, 0.001f)
    }

    private fun healthRow(
        providerId: String,
        modelId: String,
        totalCount: Int,
        successCount: Int,
        errorCount: Int,
        avgLatencyMs: Double? = null,
        lastErrorAt: Long? = null,
        lastErrorType: String? = null,
        lastSuccessAt: Long? = null,
        recentStatuses: String? = null,
    ) = ApiCallModelHealthRow(
        providerId = providerId,
        providerName = "Provider $providerId",
        modelId = modelId,
        modelDisplayName = "Model $modelId",
        totalCount = totalCount,
        successCount = successCount,
        errorCount = errorCount,
        avgLatencyMs = avgLatencyMs,
        lastErrorAt = lastErrorAt,
        lastErrorType = lastErrorType,
        lastSuccessAt = lastSuccessAt,
        recentStatuses = recentStatuses,
    )

    private fun success(provider: String, model: String, latency: Long = 50, at: Long) =
        record(provider, model, ApiCallStatus.SUCCESS, at, latency)

    private fun error(provider: String, model: String, at: Long) =
        record(
            provider,
            model,
            ApiCallStatus.ERROR,
            at,
            errorType = ApiCallErrorType.SERVER,
        )

    private fun pending(provider: String, model: String, at: Long) =
        record(provider, model, ApiCallStatus.PENDING, at)

    private fun record(
        provider: String,
        model: String,
        status: String,
        at: Long,
        latency: Long? = null,
        errorType: String? = null,
    ) = ApiCallRecordEntity(
        id = "$provider-$model-$at",
        providerId = provider,
        providerName = "Provider $provider",
        modelId = model,
        modelDisplayName = "Model $model",
        requestAt = at,
        responseAt = at + 10,
        status = status,
        latencyMs = latency,
        errorType = errorType,
    )
}

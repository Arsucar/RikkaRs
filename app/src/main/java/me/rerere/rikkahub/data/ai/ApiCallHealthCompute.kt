package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.db.dao.ApiCallErrorTypeCountRow
import me.rerere.rikkahub.data.db.dao.ApiCallModelHealthRow
import me.rerere.rikkahub.data.db.entity.ApiCallRecordEntity
import me.rerere.rikkahub.data.db.entity.ApiCallStatus

data class ApiModelHealth(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val modelDisplayName: String,
    val totalCount: Int,
    val successCount: Int,
    val errorCount: Int,
    val successRate: Float,
    val avgLatencyMs: Long?,
    val lastErrorAt: Long?,
    val lastErrorType: String?,
    val lastSuccessAt: Long?,
    val almostUnavailable: Boolean,
    val errorTypeCounts: Map<String, Int> = emptyMap(),
)

data class ApiHealthOverview(
    val totalCalls: Int,
    val successCount: Int,
    val errorCount: Int,
    val successRate: Float,
    val avgLatencyMs: Long?,
    val models: List<ApiModelHealth>,
)

object ApiCallHealthCompute {
    /**
     * Almost unavailable when:
     * - last 5 completed calls all failed, or
     * - completed ≥ 5 and error rate ≥ 80%.
     */
    fun isAlmostUnavailable(recentCompletedNewestFirst: List<ApiCallRecordEntity>): Boolean {
        val completed = recentCompletedNewestFirst.filter { it.isCompletedOutcome() }
        return isAlmostUnavailable(
            totalCount = completed.size,
            errorCount = completed.count { it.status != ApiCallStatus.SUCCESS },
            recentStatusesNewestFirst = completed.map { it.status },
        )
    }

    fun isAlmostUnavailable(
        totalCount: Int,
        errorCount: Int,
        recentStatusesNewestFirst: List<String>,
    ): Boolean {
        if (totalCount <= 0) return false
        val last5 = recentStatusesNewestFirst.take(5)
        if (last5.size >= 5 && last5.all { it != ApiCallStatus.SUCCESS }) {
            return true
        }
        if (totalCount >= 5 && errorCount.toFloat() / totalCount >= 0.8f) {
            return true
        }
        return false
    }

    /**
     * Build overview from Room GROUP BY rows (no global row LIMIT).
     */
    fun aggregateFromRows(
        rows: List<ApiCallModelHealthRow>,
        errorTypeRows: List<ApiCallErrorTypeCountRow> = emptyList(),
    ): ApiHealthOverview {
        val errorTypesByModel = errorTypeRows
            .groupBy { it.providerId to it.modelId }
            .mapValues { (_, list) ->
                list.associate { it.errorType to it.count }
            }

        val models = rows
            .filter { it.totalCount > 0 }
            .map { row ->
                val rate = if (row.totalCount == 0) {
                    0f
                } else {
                    row.successCount.toFloat() / row.totalCount
                }
                val recent = row.recentStatuses
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                ApiModelHealth(
                    providerId = row.providerId,
                    providerName = row.providerName.orEmpty().ifBlank { row.providerId },
                    modelId = row.modelId,
                    modelDisplayName = row.modelDisplayName.orEmpty().ifBlank { row.modelId },
                    totalCount = row.totalCount,
                    successCount = row.successCount,
                    errorCount = row.errorCount,
                    successRate = rate,
                    avgLatencyMs = row.avgLatencyMs?.toLong(),
                    lastErrorAt = row.lastErrorAt,
                    lastErrorType = row.lastErrorType,
                    lastSuccessAt = row.lastSuccessAt,
                    almostUnavailable = isAlmostUnavailable(
                        totalCount = row.totalCount,
                        errorCount = row.errorCount,
                        recentStatusesNewestFirst = recent,
                    ),
                    errorTypeCounts = errorTypesByModel[row.providerId to row.modelId].orEmpty(),
                )
            }
            .sortedByDescending { it.totalCount }

        val totalCalls = models.sumOf { it.totalCount }
        val successCount = models.sumOf { it.successCount }
        val errorCount = models.sumOf { it.errorCount }
        val overallRate = if (totalCalls == 0) 0f else successCount.toFloat() / totalCalls
        val weightedLatencySum = models.sumOf { model ->
            val avg = model.avgLatencyMs ?: return@sumOf 0L
            avg * model.successCount
        }
        val overallAvg = if (successCount == 0) {
            null
        } else {
            val modelsWithLatency = models.filter { it.avgLatencyMs != null && it.successCount > 0 }
            val weight = modelsWithLatency.sumOf { it.successCount }
            if (weight == 0) null else weightedLatencySum / weight
        }

        return ApiHealthOverview(
            totalCalls = totalCalls,
            successCount = successCount,
            errorCount = errorCount,
            successRate = overallRate,
            avgLatencyMs = overallAvg,
            models = models,
        )
    }

    fun aggregate(records: List<ApiCallRecordEntity>): ApiHealthOverview {
        val completed = records.filter { it.isCompletedOutcome() }
        val successCount = completed.count { it.status == ApiCallStatus.SUCCESS }
        val errorCount = completed.size - successCount
        val overallRate = if (completed.isEmpty()) 0f else successCount.toFloat() / completed.size
        val overallAvg = avgLatency(completed.filter { it.status == ApiCallStatus.SUCCESS })

        val models = completed
            .groupBy { it.providerId to it.modelId }
            .map { (_, group) -> aggregateModel(group) }
            .filter { it.totalCount > 0 }
            .sortedByDescending { it.totalCount }

        return ApiHealthOverview(
            totalCalls = completed.size,
            successCount = successCount,
            errorCount = errorCount,
            successRate = overallRate,
            avgLatencyMs = overallAvg,
            models = models,
        )
    }

    fun aggregateModel(group: List<ApiCallRecordEntity>): ApiModelHealth {
        val completed = group.filter { it.isCompletedOutcome() }
            .sortedByDescending { it.requestAt }
        val first = group.maxByOrNull { it.requestAt } ?: completed.first()
        val successCount = completed.count { it.status == ApiCallStatus.SUCCESS }
        val errorCount = completed.size - successCount
        val rate = if (completed.isEmpty()) 0f else successCount.toFloat() / completed.size
        val lastError = completed.firstOrNull { it.status != ApiCallStatus.SUCCESS }
        val lastSuccess = completed.firstOrNull { it.status == ApiCallStatus.SUCCESS }
        val errorTypeCounts = completed
            .mapNotNull { it.errorType }
            .groupingBy { it }
            .eachCount()

        return ApiModelHealth(
            providerId = first.providerId,
            providerName = first.providerName,
            modelId = first.modelId,
            modelDisplayName = first.modelDisplayName.ifBlank { first.modelId },
            totalCount = completed.size,
            successCount = successCount,
            errorCount = errorCount,
            successRate = rate,
            avgLatencyMs = avgLatency(completed.filter { it.status == ApiCallStatus.SUCCESS }),
            lastErrorAt = lastError?.responseAt ?: lastError?.requestAt,
            lastErrorType = lastError?.errorType,
            lastSuccessAt = lastSuccess?.responseAt ?: lastSuccess?.requestAt,
            almostUnavailable = isAlmostUnavailable(completed),
            errorTypeCounts = errorTypeCounts,
        )
    }

    private fun avgLatency(successes: List<ApiCallRecordEntity>): Long? {
        val values = successes.mapNotNull { it.latencyMs }
        if (values.isEmpty()) return null
        return values.sum() / values.size
    }

    private fun ApiCallRecordEntity.isCompletedOutcome(): Boolean =
        status == ApiCallStatus.SUCCESS ||
            status == ApiCallStatus.ERROR ||
            status == ApiCallStatus.TIMEOUT
}

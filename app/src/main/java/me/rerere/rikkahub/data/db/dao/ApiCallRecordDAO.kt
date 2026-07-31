package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.rerere.rikkahub.data.db.entity.ApiCallRecordEntity

/**
 * Per (provider_id, model_id) health rollup from SQL GROUP BY.
 * Avoids loading every row into memory (old LIMIT 5000 path dropped low-freq models).
 */
data class ApiCallModelHealthRow(
    val providerId: String,
    /** Latest snapshot; subquery may be null if rows race-deleted. */
    val providerName: String?,
    val modelId: String,
    val modelDisplayName: String?,
    val totalCount: Int,
    val successCount: Int,
    val errorCount: Int,
    val avgLatencyMs: Double?,
    val lastErrorAt: Long?,
    val lastErrorType: String?,
    val lastSuccessAt: Long?,
    /** Newest-first completed statuses, up to 5, joined by comma (for almostUnavailable). */
    val recentStatuses: String?,
)

data class ApiCallErrorTypeCountRow(
    val providerId: String,
    val modelId: String,
    val errorType: String,
    val count: Int,
)

@Dao
interface ApiCallRecordDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ApiCallRecordEntity)

    @Update
    suspend fun update(record: ApiCallRecordEntity)

    /**
     * Detail list only — keep a modest LIMIT for the bottom sheet.
     * Do not use these for health aggregation (see [getHealthAggregatesSince]).
     */
    @Query(
        "SELECT * FROM api_call_records " +
            "WHERE request_at >= :sinceMs " +
            "AND status IN ('SUCCESS', 'ERROR', 'TIMEOUT') " +
            "ORDER BY request_at DESC " +
            "LIMIT :limit",
    )
    suspend fun getCompletedSince(sinceMs: Long, limit: Int = 5000): List<ApiCallRecordEntity>

    @Query(
        "SELECT * FROM api_call_records " +
            "WHERE status IN ('SUCCESS', 'ERROR', 'TIMEOUT') " +
            "ORDER BY request_at DESC " +
            "LIMIT :limit",
    )
    suspend fun getCompletedAll(limit: Int = 5000): List<ApiCallRecordEntity>

    @Query(
        """
        SELECT
            g.provider_id AS providerId,
            (
                SELECT r.provider_name FROM api_call_records r
                WHERE r.provider_id = g.provider_id AND r.model_id = g.model_id
                  AND r.status IN ('SUCCESS', 'ERROR', 'TIMEOUT')
                  AND r.request_at >= :sinceMs
                ORDER BY r.request_at DESC LIMIT 1
            ) AS providerName,
            g.model_id AS modelId,
            (
                SELECT r.model_display_name FROM api_call_records r
                WHERE r.provider_id = g.provider_id AND r.model_id = g.model_id
                  AND r.status IN ('SUCCESS', 'ERROR', 'TIMEOUT')
                  AND r.request_at >= :sinceMs
                ORDER BY r.request_at DESC LIMIT 1
            ) AS modelDisplayName,
            g.totalCount AS totalCount,
            g.successCount AS successCount,
            g.errorCount AS errorCount,
            g.avgLatencyMs AS avgLatencyMs,
            g.lastErrorAt AS lastErrorAt,
            (
                SELECT r.error_type FROM api_call_records r
                WHERE r.provider_id = g.provider_id AND r.model_id = g.model_id
                  AND r.status IN ('ERROR', 'TIMEOUT')
                  AND r.request_at >= :sinceMs
                ORDER BY r.request_at DESC LIMIT 1
            ) AS lastErrorType,
            g.lastSuccessAt AS lastSuccessAt,
            (
                SELECT GROUP_CONCAT(s.status, ',') FROM (
                    SELECT r.status AS status FROM api_call_records r
                    WHERE r.provider_id = g.provider_id AND r.model_id = g.model_id
                      AND r.status IN ('SUCCESS', 'ERROR', 'TIMEOUT')
                      AND r.request_at >= :sinceMs
                    ORDER BY r.request_at DESC LIMIT 5
                ) s
            ) AS recentStatuses
        FROM (
            SELECT
                provider_id,
                model_id,
                COUNT(*) AS totalCount,
                SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
                SUM(CASE WHEN status != 'SUCCESS' THEN 1 ELSE 0 END) AS errorCount,
                AVG(CASE WHEN status = 'SUCCESS' AND latency_ms IS NOT NULL THEN latency_ms END) AS avgLatencyMs,
                MAX(CASE WHEN status != 'SUCCESS' THEN COALESCE(response_at, request_at) END) AS lastErrorAt,
                MAX(CASE WHEN status = 'SUCCESS' THEN COALESCE(response_at, request_at) END) AS lastSuccessAt
            FROM api_call_records
            WHERE request_at >= :sinceMs
              AND status IN ('SUCCESS', 'ERROR', 'TIMEOUT')
            GROUP BY provider_id, model_id
        ) g
        """,
    )
    suspend fun getHealthAggregatesSince(sinceMs: Long): List<ApiCallModelHealthRow>

    @Query(
        """
        SELECT
            g.provider_id AS providerId,
            (
                SELECT r.provider_name FROM api_call_records r
                WHERE r.provider_id = g.provider_id AND r.model_id = g.model_id
                  AND r.status IN ('SUCCESS', 'ERROR', 'TIMEOUT')
                ORDER BY r.request_at DESC LIMIT 1
            ) AS providerName,
            g.model_id AS modelId,
            (
                SELECT r.model_display_name FROM api_call_records r
                WHERE r.provider_id = g.provider_id AND r.model_id = g.model_id
                  AND r.status IN ('SUCCESS', 'ERROR', 'TIMEOUT')
                ORDER BY r.request_at DESC LIMIT 1
            ) AS modelDisplayName,
            g.totalCount AS totalCount,
            g.successCount AS successCount,
            g.errorCount AS errorCount,
            g.avgLatencyMs AS avgLatencyMs,
            g.lastErrorAt AS lastErrorAt,
            (
                SELECT r.error_type FROM api_call_records r
                WHERE r.provider_id = g.provider_id AND r.model_id = g.model_id
                  AND r.status IN ('ERROR', 'TIMEOUT')
                ORDER BY r.request_at DESC LIMIT 1
            ) AS lastErrorType,
            g.lastSuccessAt AS lastSuccessAt,
            (
                SELECT GROUP_CONCAT(s.status, ',') FROM (
                    SELECT r.status AS status FROM api_call_records r
                    WHERE r.provider_id = g.provider_id AND r.model_id = g.model_id
                      AND r.status IN ('SUCCESS', 'ERROR', 'TIMEOUT')
                    ORDER BY r.request_at DESC LIMIT 5
                ) s
            ) AS recentStatuses
        FROM (
            SELECT
                provider_id,
                model_id,
                COUNT(*) AS totalCount,
                SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
                SUM(CASE WHEN status != 'SUCCESS' THEN 1 ELSE 0 END) AS errorCount,
                AVG(CASE WHEN status = 'SUCCESS' AND latency_ms IS NOT NULL THEN latency_ms END) AS avgLatencyMs,
                MAX(CASE WHEN status != 'SUCCESS' THEN COALESCE(response_at, request_at) END) AS lastErrorAt,
                MAX(CASE WHEN status = 'SUCCESS' THEN COALESCE(response_at, request_at) END) AS lastSuccessAt
            FROM api_call_records
            WHERE status IN ('SUCCESS', 'ERROR', 'TIMEOUT')
            GROUP BY provider_id, model_id
        ) g
        """,
    )
    suspend fun getHealthAggregatesAll(): List<ApiCallModelHealthRow>

    @Query(
        "SELECT provider_id AS providerId, model_id AS modelId, error_type AS errorType, COUNT(*) AS count " +
            "FROM api_call_records " +
            "WHERE request_at >= :sinceMs " +
            "AND status IN ('ERROR', 'TIMEOUT') " +
            "AND error_type IS NOT NULL " +
            "GROUP BY provider_id, model_id, error_type",
    )
    suspend fun getErrorTypeCountsSince(sinceMs: Long): List<ApiCallErrorTypeCountRow>

    @Query(
        "SELECT provider_id AS providerId, model_id AS modelId, error_type AS errorType, COUNT(*) AS count " +
            "FROM api_call_records " +
            "WHERE status IN ('ERROR', 'TIMEOUT') " +
            "AND error_type IS NOT NULL " +
            "GROUP BY provider_id, model_id, error_type",
    )
    suspend fun getErrorTypeCountsAll(): List<ApiCallErrorTypeCountRow>

    @Query(
        "SELECT * FROM api_call_records " +
            "WHERE provider_id = :providerId AND model_id = :modelId " +
            "AND request_at >= :sinceMs " +
            "ORDER BY request_at DESC " +
            "LIMIT :limit",
    )
    suspend fun getRecentForModel(
        providerId: String,
        modelId: String,
        sinceMs: Long,
        limit: Int = 30,
    ): List<ApiCallRecordEntity>

    @Query(
        "SELECT * FROM api_call_records " +
            "WHERE provider_id = :providerId AND model_id = :modelId " +
            "ORDER BY request_at DESC " +
            "LIMIT :limit",
    )
    suspend fun getRecentForModelAll(
        providerId: String,
        modelId: String,
        limit: Int = 30,
    ): List<ApiCallRecordEntity>

    @Query("SELECT COUNT(*) FROM api_call_records")
    suspend fun countAll(): Int
}

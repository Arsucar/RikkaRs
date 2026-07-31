package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per upstream LLM API attempt (chat generation step, title, etc.).
 * Independent of conversation/message tables so analytics survive chat deletes.
 */
@Entity(
    tableName = "api_call_records",
    indices = [
        Index(value = ["request_at"]),
        Index(value = ["provider_id", "model_id", "request_at"]),
    ],
)
data class ApiCallRecordEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,
    @ColumnInfo("provider_id")
    val providerId: String,
    @ColumnInfo("provider_name")
    val providerName: String,
    @ColumnInfo("model_id")
    val modelId: String,
    @ColumnInfo("model_display_name")
    val modelDisplayName: String,
    @ColumnInfo("request_at")
    val requestAt: Long,
    @ColumnInfo("response_at")
    val responseAt: Long? = null,
    /** PENDING / SUCCESS / ERROR / TIMEOUT / CANCELLED */
    @ColumnInfo("status")
    val status: String,
    @ColumnInfo("latency_ms")
    val latencyMs: Long? = null,
    @ColumnInfo("token_input")
    val tokenInput: Int? = null,
    @ColumnInfo("token_output")
    val tokenOutput: Int? = null,
    /** AUTH / RATE_LIMIT / TIMEOUT / CONTENT_FILTER / SERVER / NETWORK / UNKNOWN */
    @ColumnInfo("error_type")
    val errorType: String? = null,
    @ColumnInfo("error_code")
    val errorCode: String? = null,
    @ColumnInfo("error_message")
    val errorMessage: String? = null,
)

object ApiCallStatus {
    const val PENDING = "PENDING"
    const val SUCCESS = "SUCCESS"
    const val ERROR = "ERROR"
    const val TIMEOUT = "TIMEOUT"
    const val CANCELLED = "CANCELLED"
}

object ApiCallErrorType {
    const val AUTH = "AUTH"
    const val RATE_LIMIT = "RATE_LIMIT"
    const val TIMEOUT = "TIMEOUT"
    const val CONTENT_FILTER = "CONTENT_FILTER"
    const val SERVER = "SERVER"
    const val NETWORK = "NETWORK"
    const val UNKNOWN = "UNKNOWN"
}

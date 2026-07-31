package me.rerere.rikkahub.data.ai

import android.util.Log
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.db.dao.ApiCallRecordDAO
import me.rerere.rikkahub.data.db.entity.ApiCallRecordEntity
import me.rerere.rikkahub.data.db.entity.ApiCallStatus
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/**
 * Fire-and-forget persistence of upstream API call outcomes.
 * Failures writing to DB must never break generation.
 */
class ApiCallRecorder(
    private val dao: ApiCallRecordDAO,
) {
    suspend fun <T> record(
        provider: ProviderSetting,
        model: Model,
        block: suspend () -> T,
    ): T {
        val id = Uuid.random().toString()
        val requestAt = System.currentTimeMillis()
        val pending = ApiCallRecordEntity(
            id = id,
            providerId = provider.id.toString(),
            providerName = provider.name,
            modelId = model.modelId.ifBlank { model.id.toString() },
            modelDisplayName = model.displayName.ifBlank { model.modelId },
            requestAt = requestAt,
            status = ApiCallStatus.PENDING,
        )
        runCatching { dao.insert(pending) }
            .onFailure { Log.w(TAG, "insert pending api call failed", it) }

        return try {
            val result = block()
            val responseAt = System.currentTimeMillis()
            // insert REPLACE: still persists if pending insert failed (update would no-op)
            runCatching {
                dao.insert(
                    pending.copy(
                        responseAt = responseAt,
                        status = ApiCallStatus.SUCCESS,
                        latencyMs = (responseAt - requestAt).coerceAtLeast(0L),
                    ),
                )
            }.onFailure { Log.w(TAG, "persist success api call failed", it) }
            result
        } catch (e: CancellationException) {
            val responseAt = System.currentTimeMillis()
            runCatching {
                dao.insert(
                    pending.copy(
                        responseAt = responseAt,
                        status = ApiCallStatus.CANCELLED,
                        latencyMs = (responseAt - requestAt).coerceAtLeast(0L),
                    ),
                )
            }.onFailure { Log.w(TAG, "persist cancelled api call failed", it) }
            throw e
        } catch (e: Throwable) {
            val responseAt = System.currentTimeMillis()
            val classified = ApiCallErrorClassifier.classify(e)
            runCatching {
                dao.insert(
                    pending.copy(
                        responseAt = responseAt,
                        status = classified.status,
                        latencyMs = (responseAt - requestAt).coerceAtLeast(0L),
                        errorType = classified.errorType,
                        errorCode = classified.errorCode,
                        errorMessage = classified.errorMessage,
                    ),
                )
            }.onFailure { Log.w(TAG, "persist error api call failed", it) }
            throw e
        }
    }

    companion object {
        private const val TAG = "ApiCallRecorder"
    }
}

package me.rerere.rikkahub.ui.pages.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.ai.ApiCallHealthCompute
import me.rerere.rikkahub.data.ai.ApiHealthOverview
import me.rerere.rikkahub.data.ai.ApiModelHealth
import me.rerere.rikkahub.data.db.dao.ApiCallRecordDAO
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.MessageStatsDAO
import me.rerere.rikkahub.data.db.entity.ApiCallRecordEntity
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

enum class ApiHealthTimeRange {
    DAYS_7,
    DAYS_30,
    ALL,
}

data class AppStats(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastUpdatedAtMs: Long? = null,
    val loadError: Boolean = false,
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    val totalPromptTokens: Long = 0L,
    val totalCompletionTokens: Long = 0L,
    val totalCachedTokens: Long = 0L,
    val conversationsPerDay: Map<LocalDate, Int> = emptyMap(),
    val launchCount: Int = 0,
    val apiHealthTimeRange: ApiHealthTimeRange = ApiHealthTimeRange.DAYS_7,
    val apiHealth: ApiHealthOverview = emptyApiHealth(),
    val apiHealthDetail: List<ApiCallRecordEntity> = emptyList(),
    val selectedApiModelKey: Pair<String, String>? = null,
)

private fun emptyApiHealth() = ApiHealthOverview(
    totalCalls = 0,
    successCount = 0,
    errorCount = 0,
    successRate = 0f,
    avgLatencyMs = null,
    models = emptyList(),
)

class StatsVM(
    private val conversationDAO: ConversationDAO,
    private val messageStatsDAO: MessageStatsDAO,
    private val apiCallRecordDAO: ApiCallRecordDAO,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _stats = MutableStateFlow(AppStats())
    val stats = _stats.asStateFlow()

    private val loadStatsMutex = Mutex()

    init {
        viewModelScope.launch { loadStats(force = false) }
    }

    fun refresh() {
        viewModelScope.launch { loadStats(force = true) }
    }

    fun setApiHealthTimeRange(range: ApiHealthTimeRange) {
        if (_stats.value.apiHealthTimeRange == range) return
        _stats.value = _stats.value.copy(apiHealthTimeRange = range)
        viewModelScope.launch { loadApiHealthOnly() }
    }

    fun openApiModelDetail(model: ApiModelHealth) {
        val key = model.providerId to model.modelId
        _stats.value = _stats.value.copy(selectedApiModelKey = key, apiHealthDetail = emptyList())
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                loadDetail(model.providerId, model.modelId, _stats.value.apiHealthTimeRange)
            }
            if (_stats.value.selectedApiModelKey == key) {
                _stats.value = _stats.value.copy(apiHealthDetail = detail)
            }
        }
    }

    fun dismissApiModelDetail() {
        _stats.value = _stats.value.copy(selectedApiModelKey = null, apiHealthDetail = emptyList())
    }

    private suspend fun loadStats(force: Boolean) {
        loadStatsMutex.withLock {
            val hasCache = !_stats.value.isLoading && _stats.value.lastUpdatedAtMs != null
            if (hasCache) {
                _stats.value = _stats.value.copy(isRefreshing = true, loadError = false)
            } else {
                _stats.value = _stats.value.copy(isLoading = true, isRefreshing = false, loadError = false)
            }

            try {
                withContext(Dispatchers.IO) {
                    messageStatsDAO.ensureBackfilled(force = force)
                }

                val today = LocalDate.now()
                val startDate = today
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                    .minusWeeks(52)
                    .toString()
                val timeRange = _stats.value.apiHealthTimeRange

                val (totalConversations, tokenStats, conversationsPerDay, apiHealth) =
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            val conversationsDeferred = async { conversationDAO.countAll() }
                            val tokenStatsDeferred = async { messageStatsDAO.sumTokenStats() }
                            val dailyDeferred = async {
                                messageStatsDAO
                                    .getUserMessageCountPerDay(startDate)
                                    .mapNotNull { entry ->
                                        runCatching { LocalDate.parse(entry.day) to entry.count }.getOrNull()
                                    }
                                    .toMap()
                            }
                            val apiDeferred = async { loadApiHealth(timeRange) }
                            Quadruple(
                                conversationsDeferred.await(),
                                tokenStatsDeferred.await(),
                                dailyDeferred.await(),
                                apiDeferred.await(),
                            )
                        }
                    }

                val launchCount = settingsStore.settingsFlow.value.launchCount
                val now = System.currentTimeMillis()

                _stats.value = _stats.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    loadError = false,
                    lastUpdatedAtMs = now,
                    totalConversations = totalConversations,
                    totalMessages = tokenStats.totalMessages,
                    totalPromptTokens = tokenStats.promptTokens,
                    totalCompletionTokens = tokenStats.completionTokens,
                    totalCachedTokens = tokenStats.cachedTokens,
                    conversationsPerDay = conversationsPerDay,
                    launchCount = launchCount,
                    apiHealthTimeRange = timeRange,
                    apiHealth = apiHealth,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _stats.value = _stats.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    loadError = true,
                )
            }
        }
    }

    private suspend fun loadApiHealthOnly() {
        val timeRange = _stats.value.apiHealthTimeRange
        val prev = _stats.value
        _stats.value = prev.copy(isRefreshing = true, loadError = false)
        try {
            val apiHealth = withContext(Dispatchers.IO) { loadApiHealth(timeRange) }
            val selected = _stats.value.selectedApiModelKey
            val detail = if (selected != null) {
                withContext(Dispatchers.IO) {
                    loadDetail(selected.first, selected.second, timeRange)
                }
            } else {
                emptyList()
            }
            _stats.value = _stats.value.copy(
                isRefreshing = false,
                loadError = false,
                lastUpdatedAtMs = System.currentTimeMillis(),
                apiHealth = apiHealth,
                apiHealthDetail = detail,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _stats.value = _stats.value.copy(
                isRefreshing = false,
                loadError = true,
            )
        }
    }

    private suspend fun loadApiHealth(range: ApiHealthTimeRange): ApiHealthOverview {
        // SQL GROUP BY over the full window — no global LIMIT that drops low-freq models.
        val (rows, errorTypes) = when (range) {
            ApiHealthTimeRange.ALL -> {
                apiCallRecordDAO.getHealthAggregatesAll() to apiCallRecordDAO.getErrorTypeCountsAll()
            }
            else -> {
                val since = System.currentTimeMillis() - range.toDurationMs()
                apiCallRecordDAO.getHealthAggregatesSince(since) to
                    apiCallRecordDAO.getErrorTypeCountsSince(since)
            }
        }
        return ApiCallHealthCompute.aggregateFromRows(rows, errorTypes)
    }

    private suspend fun loadDetail(
        providerId: String,
        modelId: String,
        range: ApiHealthTimeRange,
    ): List<ApiCallRecordEntity> = when (range) {
        ApiHealthTimeRange.ALL -> apiCallRecordDAO.getRecentForModelAll(providerId, modelId)
        else -> apiCallRecordDAO.getRecentForModel(
            providerId = providerId,
            modelId = modelId,
            sinceMs = System.currentTimeMillis() - range.toDurationMs(),
        )
    }

    private fun ApiHealthTimeRange.toDurationMs(): Long = when (this) {
        ApiHealthTimeRange.DAYS_7 -> TimeUnit.DAYS.toMillis(7)
        ApiHealthTimeRange.DAYS_30 -> TimeUnit.DAYS.toMillis(30)
        ApiHealthTimeRange.ALL -> 0L
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )
}

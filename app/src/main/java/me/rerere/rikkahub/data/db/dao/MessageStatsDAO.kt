package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.db.entity.MessageStatsDailyEntity
import me.rerere.rikkahub.data.db.entity.MessageStatsEntity

private val messageStatsBackfillMutex = Mutex()

@Dao
interface MessageStatsDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: MessageStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyAll(rows: List<MessageStatsDailyEntity>)

    @Query("DELETE FROM message_stats_daily WHERE conversation_id = :conversationId")
    suspend fun deleteDailyByConversation(conversationId: String)

    @Transaction
    suspend fun replaceDaily(conversationId: String, rows: List<MessageStatsDailyEntity>) {
        deleteDailyByConversation(conversationId)
        if (rows.isNotEmpty()) {
            insertDailyAll(rows)
        }
    }

    @Transaction
    suspend fun replaceConversationStats(
        stats: MessageStatsEntity,
        daily: List<MessageStatsDailyEntity>,
    ) {
        upsert(stats)
        replaceDaily(stats.conversationId, daily)
    }

    @Query(
        "SELECT COALESCE(SUM(message_count), 0) AS totalMessages, " +
            "COALESCE(SUM(token_input), 0) AS promptTokens, " +
            "COALESCE(SUM(token_output), 0) AS completionTokens, " +
            "COALESCE(SUM(token_cached), 0) AS cachedTokens " +
            "FROM message_stats"
    )
    suspend fun sumTokenStats(): MessageTokenStats

    @Query(
        "SELECT day, SUM(user_message_count) AS count " +
            "FROM message_stats_daily " +
            "WHERE day >= :startDate " +
            "GROUP BY day"
    )
    suspend fun getUserMessageCountPerDay(startDate: String): List<MessageDayCount>

    @Query("SELECT COUNT(*) FROM message_stats")
    suspend fun countStats(): Int

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM message_node mn " +
            "WHERE NOT EXISTS (" +
            "SELECT 1 FROM message_stats ms WHERE ms.conversation_id = mn.conversation_id" +
            ")" +
            ")",
    )
    suspend fun hasNodeConversationsMissingStats(): Boolean

    @Query("SELECT COUNT(*) FROM message_node")
    suspend fun countMessageNodes(): Int

    @Query("DELETE FROM message_stats")
    suspend fun deleteAllStats()

    @Query("DELETE FROM message_stats_daily")
    suspend fun deleteAllDaily()

    /**
     * Seed one zero row per conversation that has nodes so empty-message conversations
     * still appear in [message_stats] (json_each alone would omit them).
     */
    @Query(
        "INSERT OR REPLACE INTO message_stats (" +
            "conversation_id, message_count, user_message_count, " +
            "token_input, token_output, token_cached, updated_at" +
            ") " +
            "SELECT DISTINCT conversation_id, 0, 0, 0, 0, 0, :updatedAt " +
            "FROM message_node"
    )
    suspend fun seedZeroStatsForAllNodeConversations(updatedAt: Long)

    @Query(
        "INSERT OR IGNORE INTO message_stats (" +
            "conversation_id, message_count, user_message_count, " +
            "token_input, token_output, token_cached, updated_at" +
            ") " +
            "SELECT DISTINCT conversation_id, 0, 0, 0, 0, 0, :updatedAt " +
            "FROM message_node"
    )
    suspend fun seedZeroStatsForMissing(updatedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO message_stats (" +
            "conversation_id, message_count, user_message_count, " +
            "token_input, token_output, token_cached, updated_at" +
            ") " +
            "SELECT mn.conversation_id, " +
            "COUNT(*) AS message_count, " +
            "COALESCE(SUM(CASE WHEN json_extract(j.value, '$.role') = 'user' THEN 1 ELSE 0 END), 0) " +
            "AS user_message_count, " +
            "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.promptTokens') AS INTEGER)), 0) " +
            "AS token_input, " +
            "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.completionTokens') AS INTEGER)), 0) " +
            "AS token_output, " +
            "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.cachedTokens') AS INTEGER)), 0) " +
            "AS token_cached, " +
            ":updatedAt AS updated_at " +
            "FROM message_node mn, json_each(mn.messages) j " +
            "GROUP BY mn.conversation_id"
    )
    suspend fun backfillStatsFromNodes(updatedAt: Long)

    @Query(
        "INSERT OR IGNORE INTO message_stats (" +
            "conversation_id, message_count, user_message_count, " +
            "token_input, token_output, token_cached, updated_at" +
            ") " +
            "SELECT mn.conversation_id, " +
            "COUNT(*) AS message_count, " +
            "COALESCE(SUM(CASE WHEN json_extract(j.value, '$.role') = 'user' THEN 1 ELSE 0 END), 0) " +
            "AS user_message_count, " +
            "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.promptTokens') AS INTEGER)), 0) " +
            "AS token_input, " +
            "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.completionTokens') AS INTEGER)), 0) " +
            "AS token_output, " +
            "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.cachedTokens') AS INTEGER)), 0) " +
            "AS token_cached, " +
            ":updatedAt AS updated_at " +
            "FROM message_node mn, json_each(mn.messages) j " +
            "GROUP BY mn.conversation_id"
    )
    suspend fun backfillStatsForMissing(updatedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO message_stats_daily (conversation_id, day, user_message_count) " +
            "SELECT mn.conversation_id, " +
            "substr(json_extract(j.value, '$.createdAt'), 1, 10) AS day, " +
            "COUNT(*) AS user_message_count " +
            "FROM message_node mn, json_each(mn.messages) j " +
            "WHERE json_extract(j.value, '$.role') = 'user' " +
            "GROUP BY mn.conversation_id, day"
    )
    suspend fun backfillDailyFromNodes()

    @Query(
        "INSERT OR IGNORE INTO message_stats_daily (conversation_id, day, user_message_count) " +
            "SELECT mn.conversation_id, " +
            "substr(json_extract(j.value, '$.createdAt'), 1, 10) AS day, " +
            "COUNT(*) AS user_message_count " +
            "FROM message_node mn, json_each(mn.messages) j " +
            "WHERE json_extract(j.value, '$.role') = 'user' " +
            "GROUP BY mn.conversation_id, day"
    )
    suspend fun backfillDailyForMissing()

    /**
     * Process-wide single-flight backfill.
     * - force=true: wipe summary tables then full rebuild (manual refresh), exclusive under mutex.
     * - force=false: when nodes exist and any conversation with nodes is missing a stats row,
     *   inserts only missing rows (INSERT OR IGNORE) so concurrent per-conversation upserts
     *   are not clobbered. Completeness is [hasNodeConversationsMissingStats] == false.
     */
    suspend fun ensureBackfilled(force: Boolean = false) {
        messageStatsBackfillMutex.withLock {
            ensureBackfilledLocked(force)
        }
    }

    @Transaction
    suspend fun ensureBackfilledLocked(force: Boolean) {
        val hasNodes = countMessageNodes() > 0
        if (force) {
            deleteAllStats()
            deleteAllDaily()
            if (!hasNodes) {
                return
            }
            runBackfill(System.currentTimeMillis(), onlyMissing = false)
            return
        }
        if (!hasNodes) {
            return
        }
        if (!hasNodeConversationsMissingStats()) {
            return
        }
        runBackfill(System.currentTimeMillis(), onlyMissing = true)
    }

    private suspend fun runBackfill(updatedAt: Long, onlyMissing: Boolean) {
        if (onlyMissing) {
            backfillStatsForMissing(updatedAt)
            seedZeroStatsForMissing(updatedAt)
            backfillDailyForMissing()
        } else {
            seedZeroStatsForAllNodeConversations(updatedAt)
            backfillStatsFromNodes(updatedAt)
            backfillDailyFromNodes()
        }
    }
}

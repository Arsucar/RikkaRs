package me.rerere.rikkahub.data.db

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.db.entity.MessageStatsDailyEntity
import me.rerere.rikkahub.data.db.entity.MessageStatsEntity
import me.rerere.rikkahub.data.model.MessageNode

data class ComputedMessageStats(
    val stats: MessageStatsEntity,
    val daily: List<MessageStatsDailyEntity>,
)

/**
 * Aggregates all messages in every node (same semantics as json_each over the full messages array).
 * Daily rows group user messages by createdAt date prefix yyyy-MM-dd.
 */
fun computeMessageStats(
    conversationId: String,
    nodes: List<MessageNode>,
    updatedAt: Long = System.currentTimeMillis(),
): ComputedMessageStats {
    var messageCount = 0
    var userMessageCount = 0
    var tokenInput = 0L
    var tokenOutput = 0L
    var tokenCached = 0L
    val dailyCounts = linkedMapOf<String, Int>()

    for (node in nodes) {
        for (message in node.messages) {
            messageCount++
            val usage = message.usage
            if (usage != null) {
                tokenInput += usage.promptTokens.toLong()
                tokenOutput += usage.completionTokens.toLong()
                tokenCached += usage.cachedTokens.toLong()
            }
            if (message.role == MessageRole.USER) {
                userMessageCount++
                val day = message.createdAt.toString().take(10)
                dailyCounts[day] = (dailyCounts[day] ?: 0) + 1
            }
        }
    }

    return ComputedMessageStats(
        stats = MessageStatsEntity(
            conversationId = conversationId,
            messageCount = messageCount,
            userMessageCount = userMessageCount,
            tokenInput = tokenInput,
            tokenOutput = tokenOutput,
            tokenCached = tokenCached,
            updatedAt = updatedAt,
        ),
        daily = dailyCounts.map { (day, count) ->
            MessageStatsDailyEntity(
                conversationId = conversationId,
                day = day,
                userMessageCount = count,
            )
        },
    )
}

package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import me.rerere.rikkahub.data.db.dao.SubagentContextDAO
import me.rerere.rikkahub.data.db.entity.SubagentContextEntity
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage

interface SubagentContextStore {
    suspend fun loadRestorable(nowMillis: Long): List<SubagentContext>
    suspend fun loadById(contextId: String): SubagentContext?
    suspend fun save(context: SubagentContext)
    suspend fun delete(contextId: String)
    suspend fun deleteExpired(nowMillis: Long)
}

class RoomSubagentContextStore(
    private val dao: SubagentContextDAO,
) : SubagentContextStore {
    override suspend fun loadRestorable(nowMillis: Long): List<SubagentContext> {
        dao.deleteExpired(nowMillis)
        return dao.getRestorable(nowMillis).mapNotNull { entity ->
            runCatching { entity.toModel() }
                .onFailure { Log.w("SubagentContextStore", "Skipping corrupt context ${entity.contextId}", it) }
                .getOrNull()
        }
    }

    override suspend fun loadById(contextId: String): SubagentContext? =
        dao.getById(contextId)?.let { entity ->
            runCatching { entity.toModel() }
                .onFailure { Log.w("SubagentContextStore", "Skipping corrupt context $contextId", it) }
                .getOrNull()
        }

    override suspend fun save(context: SubagentContext) {
        dao.upsertIfNewer(context.toEntity())
    }

    override suspend fun delete(contextId: String) = dao.deleteById(contextId)

    override suspend fun deleteExpired(nowMillis: Long) {
        dao.deleteExpired(nowMillis)
    }
}

private fun SubagentContext.toEntity() = SubagentContextEntity(
    contextId = contextId,
    conversationId = scope.conversationId?.toString(),
    parentAssistantId = scope.parentAssistantId.toString(),
    scopeJson = JsonInstant.encodeToString(scope),
    status = status.name,
    messagesJson = JsonInstant.encodeToString(messages),
    usageJson = usage?.let { JsonInstant.encodeToString(it) },
    lastError = lastError,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = lastAccessAtMillis,
    expiresAtMillis = expiresAtMillis,
    revision = revision,
)

private fun SubagentContextEntity.toModel() = SubagentContext(
    contextId = contextId,
    scope = JsonInstant.decodeFromString<SubagentContextScope>(scopeJson),
    messages = JsonInstant.decodeFromString<List<UIMessage>>(messagesJson),
    createdAtMillis = createdAtMillis,
    lastAccessAtMillis = updatedAtMillis,
    expiresAtMillis = expiresAtMillis,
    status = SubagentStatus.valueOf(status),
    usage = usageJson?.let { JsonInstant.decodeFromString<TokenUsage>(it) },
    lastError = lastError,
    revision = revision,
)

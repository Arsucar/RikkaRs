package me.rerere.rikkahub.data.ai.subagent

import android.util.Log
import java.util.LinkedHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

private const val TAG = "SubagentContextCache"
internal const val DEFAULT_SUBAGENT_CONTEXT_TTL_MILLIS = 60L * 60L * 1000L
internal const val DEFAULT_SUBAGENT_CONTEXT_CACHE_SIZE = 16

@Serializable
enum class SubagentStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    INTERRUPTED,
}

data class SubagentContextScope(
    val conversationId: Uuid?,
    val parentAssistantId: Uuid,
    val workspaceId: Uuid?,
    val workspaceCwd: String?,
    val depth: Int,
    val profileName: String,
    val workspaceAccess: WorkspaceAccess,
    val permissionFingerprint: String,
)

data class SubagentContext(
    val contextId: String,
    val scope: SubagentContextScope,
    val messages: List<UIMessage>,
    val createdAtMillis: Long,
    val lastAccessAtMillis: Long,
    val expiresAtMillis: Long,
    val status: SubagentStatus,
    val usage: TokenUsage? = null,
    val lastError: String? = null,
)

enum class SubagentContextErrorCode {
    CONTEXT_NOT_FOUND,
    CONTEXT_EXPIRED,
    CONTEXT_IN_USE,
    CONTEXT_SCOPE_MISMATCH,
    CONTEXT_TOO_LONG,
}

class SubagentContextException(
    val code: SubagentContextErrorCode,
    message: String,
) : IllegalStateException(message)

class SubagentContextCache(
    private val ttlMillis: Long = DEFAULT_SUBAGENT_CONTEXT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_SUBAGENT_CONTEXT_CACHE_SIZE,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val contexts = LinkedHashMap<String, SubagentContext>(16, 0.75f, true)
    private val expiredContextIds = LinkedHashMap<String, Long>()

    init {
        require(ttlMillis > 0) { "ttlMillis must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    suspend fun createAndAcquire(
        scope: SubagentContextScope,
        messages: List<UIMessage>,
    ): SubagentContext = mutex.withLock {
        val now = nowMillis()
        pruneExpiredLocked(now)
        val context = SubagentContext(
            contextId = Uuid.random().toString(),
            scope = scope,
            messages = snapshotMessages(messages),
            createdAtMillis = now,
            lastAccessAtMillis = now,
            expiresAtMillis = now + ttlMillis,
            status = SubagentStatus.RUNNING,
        )
        contexts[context.contextId] = context
        evictToLimitLocked()
        log("created context=${context.contextId} size=${contexts.size}")
        context.snapshot()
    }

    suspend fun acquireForReuse(
        contextId: String,
        scope: SubagentContextScope,
        messagesToAppend: List<UIMessage> = emptyList(),
    ): SubagentContext = mutex.withLock {
        val now = nowMillis()
        pruneExpiredTombstonesLocked(now)
        val existing = contexts.entries.firstOrNull { it.key == contextId }?.value ?: throw SubagentContextException(
            if (contextId in expiredContextIds) {
                SubagentContextErrorCode.CONTEXT_EXPIRED
            } else {
                SubagentContextErrorCode.CONTEXT_NOT_FOUND
            },
            if (contextId in expiredContextIds) {
                "Subagent context has expired; create a new subagent"
            } else {
                "Subagent context does not exist; create a new subagent"
            },
        )
        if (existing.status != SubagentStatus.RUNNING && existing.expiresAtMillis <= now) {
            contexts.remove(contextId)
            rememberExpiredLocked(contextId, now)
            log("expired context=$contextId size=${contexts.size}")
            throw SubagentContextException(
                SubagentContextErrorCode.CONTEXT_EXPIRED,
                "Subagent context has expired; create a new subagent",
            )
        }
        if (existing.status == SubagentStatus.RUNNING) {
            throw SubagentContextException(
                SubagentContextErrorCode.CONTEXT_IN_USE,
                "Subagent context is currently in use",
            )
        }
        if (existing.scope != scope) {
            val mismatchedFields = scopeMismatchFields(
                actual = existing.scope,
                expected = scope,
            )
            throw SubagentContextException(
                SubagentContextErrorCode.CONTEXT_SCOPE_MISMATCH,
                "Subagent context scope mismatch: actual(cached) differs from expected(requested) for fields: " +
                    mismatchedFields.joinToString(),
            )
        }
        val acquired = existing.touch(now).copy(
            messages = snapshotMessages(existing.messages + messagesToAppend),
            status = SubagentStatus.RUNNING,
            lastError = null,
        )
        contexts[contextId] = acquired
        log("reused context=$contextId size=${contexts.size}")
        acquired.snapshot()
    }

    suspend fun updateProgress(
        contextId: String,
        messages: List<UIMessage>,
        usage: TokenUsage? = null,
    ) = mutex.withLock {
        val existing = contexts[contextId] ?: return@withLock
        val now = nowMillis()
        contexts[contextId] = existing.touch(now).copy(
            messages = snapshotMessages(messages),
            usage = usage ?: existing.usage,
        )
    }

    suspend fun finish(
        contextId: String,
        status: SubagentStatus,
        messages: List<UIMessage>? = null,
        usage: TokenUsage? = null,
        error: String? = null,
    ): SubagentContext? = mutex.withLock {
        require(status != SubagentStatus.RUNNING) { "finish requires a terminal status" }
        val existing = contexts[contextId] ?: return@withLock null
        val now = nowMillis()
        val finished = existing.touch(now).copy(
            messages = messages?.let(::snapshotMessages) ?: existing.messages,
            status = status,
            usage = usage ?: existing.usage,
            lastError = error,
        )
        contexts[contextId] = finished
        pruneExpiredLocked(now)
        evictToLimitLocked()
        log("finished context=$contextId status=$status size=${contexts.size}")
        contexts[contextId]?.snapshot()
    }

    suspend fun snapshot(contextId: String): SubagentContext? = mutex.withLock {
        val now = nowMillis()
        pruneExpiredLocked(now)
        val existing = contexts[contextId] ?: return@withLock null
        val touched = existing.touch(now)
        contexts[contextId] = touched
        touched.snapshot()
    }

    suspend fun size(): Int = mutex.withLock { contexts.size }

    private fun SubagentContext.touch(now: Long): SubagentContext = copy(
        lastAccessAtMillis = now,
        expiresAtMillis = now + ttlMillis,
    )

    private fun SubagentContext.snapshot(): SubagentContext = copy(messages = snapshotMessages(messages))

    private fun scopeMismatchFields(
        actual: SubagentContextScope,
        expected: SubagentContextScope,
    ): List<String> = buildList {
        if (actual.conversationId != expected.conversationId) add("conversationId")
        if (actual.parentAssistantId != expected.parentAssistantId) add("parentAssistantId")
        if (actual.workspaceId != expected.workspaceId) add("workspaceId")
        if (actual.workspaceCwd != expected.workspaceCwd) add("workspaceCwd")
        if (actual.depth != expected.depth) add("depth")
        if (actual.profileName != expected.profileName) add("profileName")
        if (actual.workspaceAccess != expected.workspaceAccess) add("workspaceAccess")
        if (actual.permissionFingerprint != expected.permissionFingerprint) add("permissionFingerprint")
    }

    private fun snapshotMessages(messages: List<UIMessage>): List<UIMessage> = messages.map { message ->
        message.copy(
            parts = message.parts.map(::snapshotPart),
            annotations = message.annotations.toList(),
        )
    }

    private fun snapshotPart(part: UIMessagePart): UIMessagePart = when (part) {
        is UIMessagePart.Text -> part.copy(metadata = part.metadata)
        is UIMessagePart.Image -> part.copy(metadata = part.metadata)
        is UIMessagePart.Video -> part.copy(metadata = part.metadata)
        is UIMessagePart.Audio -> part.copy(metadata = part.metadata)
        is UIMessagePart.Document -> part.copy(metadata = part.metadata)
        is UIMessagePart.SlashSkill -> part.copy(metadata = part.metadata)
        is UIMessagePart.Reasoning -> part.copy(metadata = part.metadata)
        is UIMessagePart.ToolCall -> part.copy(metadata = part.metadata)
        is UIMessagePart.ToolResult -> part.copy(metadata = part.metadata)
        is UIMessagePart.Tool -> part.copy(
            output = part.output.map(::snapshotPart),
            metadata = part.metadata,
        )
        UIMessagePart.Search -> UIMessagePart.Search
    }

    private fun pruneExpiredLocked(now: Long) {
        pruneExpiredTombstonesLocked(now)
        val iterator = contexts.entries.iterator()
        while (iterator.hasNext()) {
            val context = iterator.next().value
            if (context.status != SubagentStatus.RUNNING && context.expiresAtMillis <= now) {
                iterator.remove()
                rememberExpiredLocked(context.contextId, now)
                log("expired context=${context.contextId} size=${contexts.size}")
            }
        }
    }

    private fun evictToLimitLocked() {
        while (contexts.size > maxEntries) {
            val candidate = contexts.entries.firstOrNull { it.value.status != SubagentStatus.RUNNING }
                ?: return
            contexts.remove(candidate.key)
            log("evicted context=${candidate.key} size=${contexts.size}")
        }
    }

    private fun rememberExpiredLocked(contextId: String, now: Long) {
        expiredContextIds[contextId] = now
        while (expiredContextIds.size > maxEntries.coerceAtLeast(DEFAULT_SUBAGENT_CONTEXT_CACHE_SIZE)) {
            expiredContextIds.remove(expiredContextIds.entries.first().key)
        }
    }

    private fun pruneExpiredTombstonesLocked(now: Long) {
        val iterator = expiredContextIds.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value >= ttlMillis) {
                iterator.remove()
            }
        }
    }

    private fun log(message: String) {
        runCatching { Log.i(TAG, message) }
    }
}

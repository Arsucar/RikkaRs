package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SubagentContextPersistenceTest {
    @Test
    fun restoredRunningContextIsDowngradedAndReusable() = runBlocking {
        val scope = scope()
        val stored = SubagentContext(
            contextId = "persisted",
            scope = scope,
            messages = listOf(UIMessage.user("old task")),
            createdAtMillis = 1,
            lastAccessAtMillis = 2,
            expiresAtMillis = Long.MAX_VALUE,
            status = SubagentStatus.RUNNING,
            revision = 4,
        )
        val store = FakeStore(mutableListOf(stored))
        val cache = SubagentContextCache(store = store)

        val reused = cache.acquireForReuse("persisted", scope, listOf(UIMessage.user("continue")))

        assertEquals(SubagentStatus.RUNNING, reused.status)
        assertEquals(2, reused.messages.size)
        assertTrue(store.saved.any { it.status == SubagentStatus.INTERRUPTED && it.revision == 5L })
    }

    private fun scope() = SubagentContextScope(
        conversationId = Uuid.random(),
        parentAssistantId = Uuid.random(),
        workspaceId = null,
        workspaceCwd = null,
        depth = 0,
        profileName = "default",
        workspaceAccess = WorkspaceAccess.READ_ONLY,
        permissionFingerprint = "permissions",
    )

    private class FakeStore(
        private val loaded: MutableList<SubagentContext>,
    ) : SubagentContextStore {
        val saved = mutableListOf<SubagentContext>()
        override suspend fun loadRestorable(nowMillis: Long): List<SubagentContext> = loaded.toList()
        override suspend fun loadById(contextId: String): SubagentContext? =
            loaded.firstOrNull { it.contextId == contextId }
        override suspend fun save(context: SubagentContext) { saved += context }
        override suspend fun delete(contextId: String) { loaded.removeAll { it.contextId == contextId } }
        override suspend fun deleteExpired(nowMillis: Long) = Unit
    }
}

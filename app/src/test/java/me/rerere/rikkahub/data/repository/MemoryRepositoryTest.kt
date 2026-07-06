package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.MemoryScope
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryRepositoryTest {
    @Test
    fun addGlobalMemoryStoresGlobalOwnerAndScope() = runBlocking {
        val dao = FakeMemoryDAO()
        val repository = MemoryRepository(dao)

        val memory = repository.addMemory(
            assistantId = "assistant-a",
            content = "global preference",
            scope = MemoryScope.GLOBAL,
        )

        assertEquals(MemoryScope.GLOBAL, memory.scope)
        assertEquals(MemoryRepository.GLOBAL_MEMORY_ID, dao.memories.single().assistantId)
        assertEquals(MemoryScope.GLOBAL.name, dao.memories.single().scope)
    }

    @Test
    fun updateGlobalMemoryToAssistantScopeReassignsOwner() = runBlocking {
        val dao = FakeMemoryDAO(
            MemoryEntity(
                id = 7,
                assistantId = MemoryRepository.GLOBAL_MEMORY_ID,
                content = "shared",
                scope = MemoryScope.GLOBAL.name,
            )
        )
        val repository = MemoryRepository(dao)

        val memory = repository.updateMemory(
            id = 7,
            content = "local",
            scope = MemoryScope.ASSISTANT,
            assistantId = "assistant-a",
        )

        assertEquals(MemoryScope.ASSISTANT, memory.scope)
        assertEquals("assistant-a", dao.memories.single().assistantId)
        assertEquals(MemoryScope.ASSISTANT.name, dao.memories.single().scope)
    }

    @Test
    fun effectiveMemoriesReturnsGlobalAndAssistantLocalOnly() = runBlocking {
        val dao = FakeMemoryDAO(
            MemoryEntity(
                id = 1,
                assistantId = "assistant-a",
                content = "local a",
                scope = MemoryScope.ASSISTANT.name,
            ),
            MemoryEntity(
                id = 2,
                assistantId = "assistant-b",
                content = "local b",
                scope = MemoryScope.ASSISTANT.name,
            ),
            MemoryEntity(
                id = 3,
                assistantId = MemoryRepository.GLOBAL_MEMORY_ID,
                content = "global",
                scope = MemoryScope.GLOBAL.name,
            ),
        )
        val repository = MemoryRepository(dao)

        val memories = repository.getEffectiveMemories("assistant-a")

        assertEquals(listOf(3, 1), memories.map { it.id })
    }

    private class FakeMemoryDAO(
        vararg initial: MemoryEntity,
    ) : MemoryDAO {
        val memories = initial.toMutableList()
        private var nextId = (memories.maxOfOrNull { it.id } ?: 0) + 1

        override fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>> =
            flowOf(runBlocking { getMemoriesOfAssistant(assistantId) })

        override suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity> =
            memories.filter { it.assistantId == assistantId && it.scope == MemoryScope.ASSISTANT.name }

        override fun getGlobalMemoriesFlow(): Flow<List<MemoryEntity>> =
            flowOf(runBlocking { getGlobalMemories() })

        override suspend fun getGlobalMemories(): List<MemoryEntity> =
            memories.filter { it.scope == MemoryScope.GLOBAL.name }

        override fun getEffectiveMemoriesFlow(assistantId: String): Flow<List<MemoryEntity>> =
            flowOf(runBlocking { getEffectiveMemories(assistantId) })

        override suspend fun getEffectiveMemories(assistantId: String): List<MemoryEntity> =
            memories
                .filter {
                    it.scope == MemoryScope.GLOBAL.name ||
                        (it.assistantId == assistantId && it.scope == MemoryScope.ASSISTANT.name)
                }
                .sortedWith(
                    compareBy<MemoryEntity> { if (it.scope == MemoryScope.GLOBAL.name) 0 else 1 }
                        .thenBy { it.id }
                )

        override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = flowOf(memories)

        override suspend fun getAllMemories(): List<MemoryEntity> = memories

        override suspend fun getMemoryById(id: Int): MemoryEntity? = memories.firstOrNull { it.id == id }

        override suspend fun insertMemory(memory: MemoryEntity): Long {
            val entity = memory.copy(id = nextId++)
            memories += entity
            return entity.id.toLong()
        }

        override suspend fun updateMemory(memory: MemoryEntity) {
            val index = memories.indexOfFirst { it.id == memory.id }
            require(index >= 0)
            memories[index] = memory
        }

        override suspend fun deleteMemory(id: Int) {
            memories.removeAll { it.id == id }
        }

        override suspend fun deleteMemoriesOfAssistant(assistantId: String) {
            memories.removeAll { it.assistantId == assistantId }
        }
    }
}

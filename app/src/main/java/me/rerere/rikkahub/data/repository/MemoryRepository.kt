package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryScope

class MemoryRepository(private val memoryDAO: MemoryDAO) {
    companion object {
        const val GLOBAL_MEMORY_ID = "__global__"
    }

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities ->
                entities.map { it.toAssistantMemory() }
            }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(assistantId)
            .map { it.toAssistantMemory() }
    }

    fun getGlobalMemoriesFlow(): Flow<List<AssistantMemory>> =
        memoryDAO.getGlobalMemoriesFlow()
            .map { entities ->
                entities.map { it.toAssistantMemory() }
            }

    suspend fun getGlobalMemories(): List<AssistantMemory> {
        return memoryDAO.getGlobalMemories()
            .map { it.toAssistantMemory() }
    }

    fun getEffectiveMemoriesFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getEffectiveMemoriesFlow(assistantId)
            .map { entities ->
                entities.map { it.toAssistantMemory() }
            }

    suspend fun getEffectiveMemories(assistantId: String): List<AssistantMemory> {
        return memoryDAO.getEffectiveMemories(assistantId)
            .map { it.toAssistantMemory() }
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
    }

    suspend fun updateMemory(
        id: Int,
        content: String,
        actorAssistantId: String,
        scope: MemoryScope? = null,
    ): AssistantMemory {
        val old = memoryDAO.getEffectiveMemoryById(id, actorAssistantId)
            ?: error("Memory record #$id is not available to this assistant")
        val newScope = scope ?: MemoryScope.fromStorage(old.scope)
        val ownerAssistantId = when (newScope) {
            MemoryScope.GLOBAL -> GLOBAL_MEMORY_ID
            MemoryScope.ASSISTANT -> actorAssistantId
        }
        val newMemory = old.copy(
            assistantId = ownerAssistantId,
            content = content,
            scope = newScope.name,
        )
        memoryDAO.updateMemory(newMemory)
        return newMemory.toAssistantMemory()
    }

    suspend fun updateContent(id: Int, content: String, actorAssistantId: String): AssistantMemory {
        return updateMemory(id = id, content = content, actorAssistantId = actorAssistantId)
    }

    suspend fun addMemory(
        assistantId: String,
        content: String,
        scope: MemoryScope = MemoryScope.ASSISTANT,
    ): AssistantMemory {
        val memory = AssistantMemory(
            id = 0,
            content = content,
            scope = scope,
        )
        val ownerAssistantId = if (scope == MemoryScope.GLOBAL) GLOBAL_MEMORY_ID else assistantId
        val newMemory = memory.copy(
            id = memoryDAO.insertMemory(
                MemoryEntity(
                    assistantId = ownerAssistantId,
                    content = memory.content,
                    scope = scope.name,
                )
            ).toInt()
        )
        return newMemory
    }

    suspend fun deleteMemory(id: Int, actorAssistantId: String): Boolean {
        memoryDAO.getEffectiveMemoryById(id, actorAssistantId) ?: return false
        memoryDAO.deleteMemory(id)
        return true
    }

    private fun MemoryEntity.toAssistantMemory(): AssistantMemory {
        return AssistantMemory(
            id = id,
            content = content,
            scope = MemoryScope.fromStorage(scope),
        )
    }
}

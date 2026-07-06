package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryToolsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun create_usesDefaultScopeWhenScopeMissing() = runBlocking {
        var capturedScope: MemoryScope? = null
        val tool = buildMemoryTools(
            json = json,
            defaultScope = MemoryScope.GLOBAL,
            onCreation = { content, scope ->
                capturedScope = scope
                AssistantMemory(id = 1, content = content, scope = scope)
            },
            onUpdate = { _, _, _ -> error("unexpected update") },
            onDelete = { error("unexpected delete") },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "create")
                put("content", "remember this")
            }
        )

        assertEquals(MemoryScope.GLOBAL, capturedScope)
    }

    @Test
    fun create_acceptsExplicitAssistantScope() = runBlocking {
        var capturedScope: MemoryScope? = null
        val tool = buildMemoryTools(
            json = json,
            defaultScope = MemoryScope.GLOBAL,
            onCreation = { content, scope ->
                capturedScope = scope
                AssistantMemory(id = 1, content = content, scope = scope)
            },
            onUpdate = { _, _, _ -> error("unexpected update") },
            onDelete = { error("unexpected delete") },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "create")
                put("content", "local only")
                put("scope", "assistant")
            }
        )

        assertEquals(MemoryScope.ASSISTANT, capturedScope)
    }

    @Test
    fun editPassesOptionalScopeOnlyWhenProvided() = runBlocking {
        var capturedScope: MemoryScope? = MemoryScope.GLOBAL
        val tool = buildMemoryTools(
            json = json,
            onCreation = { _, _ -> error("unexpected create") },
            onUpdate = { id, content, scope ->
                assertEquals(12, id)
                assertEquals("updated", content)
                capturedScope = scope
                AssistantMemory(id = id, content = content, scope = scope ?: MemoryScope.ASSISTANT)
            },
            onDelete = { error("unexpected delete") },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "edit")
                put("id", 12)
                put("content", "updated")
            }
        )

        assertNull(capturedScope)
    }

    @Test
    fun editAcceptsGlobalScope() = runBlocking {
        var capturedScope: MemoryScope? = null
        val tool = buildMemoryTools(
            json = json,
            onCreation = { _, _ -> error("unexpected create") },
            onUpdate = { id, content, scope ->
                capturedScope = scope
                AssistantMemory(id = id, content = content, scope = scope ?: MemoryScope.ASSISTANT)
            },
            onDelete = { error("unexpected delete") },
        ).single()

        tool.execute(
            buildJsonObject {
                put("action", "edit")
                put("id", 12)
                put("content", "updated")
                put("scope", "global")
            }
        )

        assertEquals(MemoryScope.GLOBAL, capturedScope)
    }
}

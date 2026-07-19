package me.rerere.rikkahub.data.ai.subagent

import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentMemoryTableInjectionTest {
    private val templates = listOf(
        MemoryTableTemplate(id = "t2", name = "B"),
        MemoryTableTemplate(id = "t1", name = "A"),
    )
    private val documents = listOf(
        MemoryTableDocument(
            id = "d2",
            templateId = "t2",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "a",
        ),
        MemoryTableDocument(
            id = "d1",
            templateId = "t1",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "a",
        ),
        MemoryTableDocument(
            id = "d3",
            templateId = "t1",
            scopeType = MemoryTableScopeType.CONVERSATION,
            scopeId = "c1",
        ),
        MemoryTableDocument(
            id = "d4",
            templateId = "other",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "a",
        ),
    )

    @Test
    fun emptySelectionReturnsEmpty() {
        val (t, d) = resolveSubagentMemoryTableInjection(
            selectedDocumentIds = emptySet(),
            templates = templates,
            documents = documents,
            parentMemoryTableEnabled = true,
        )
        assertTrue(t.isEmpty())
        assertTrue(d.isEmpty())
    }

    @Test
    fun parentGateOffReturnsEmpty() {
        val (t, d) = resolveSubagentMemoryTableInjection(
            selectedDocumentIds = setOf("d1"),
            templates = templates,
            documents = documents,
            parentMemoryTableEnabled = false,
        )
        assertTrue(t.isEmpty())
        assertTrue(d.isEmpty())
    }

    @Test
    fun filtersSelectedDocumentIdsAndOrders() {
        val (t, d) = resolveSubagentMemoryTableInjection(
            selectedDocumentIds = setOf("d2", "missing", "d1", "d4"),
            templates = templates,
            documents = documents,
            parentMemoryTableEnabled = true,
        )
        assertEquals(listOf("t1", "t2"), t.map { it.id })
        assertEquals(listOf("d1", "d2"), d.map { it.id })
    }

    @Test
    fun isolationKeepsConversationScopeOnly() {
        val (t, d) = resolveSubagentMemoryTableInjection(
            selectedDocumentIds = setOf("d1", "d2", "d3"),
            templates = templates,
            documents = documents,
            parentMemoryTableEnabled = true,
            memoryTableIsolation = true,
        )
        assertEquals(listOf("t1"), t.map { it.id })
        assertEquals(listOf("d3"), d.map { it.id })
        assertTrue(d.all { it.scopeType == MemoryTableScopeType.CONVERSATION })
    }

    @Test
    fun profileRoundTripKeepsInjectedDocumentIds() {
        val profile = SubagentProfile(
            name = "researcher",
            injectedMemoryTableDocumentIds = setOf("d1", "d2"),
        )
        val json = kotlinx.serialization.json.Json.encodeToString(SubagentProfile.serializer(), profile)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(SubagentProfile.serializer(), json)
        assertEquals(setOf("d1", "d2"), decoded.injectedMemoryTableDocumentIds)
    }

    @Test
    fun legacyProfileDefaultsToEmptyInjection() {
        val json = """{"name":"legacy"}"""
        val decoded = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }.decodeFromString(SubagentProfile.serializer(), json)
        assertTrue(decoded.injectedMemoryTableDocumentIds.isEmpty())
    }
}

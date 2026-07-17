package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AssistantMemoryTableTrashPageTest {
    @Test
    fun buildTrashItemsUsesTemplateNameFallbackAndStableDeletionOrder() {
        val named = document("z-doc", "named", deletedAt = 300, deletedBy = "memory_table_tool")
        val blank = document("a-doc", "blank", deletedAt = 200, deletedBy = "user_ui")
        val missing = document("b-doc", "missing", deletedAt = 200, deletedBy = "user_ui")
        val noTimestamp = document("n-doc", "named", deletedAt = null, deletedBy = null)

        val items = buildMemoryTableTrashItems(
            documents = listOf(noTimestamp, missing, blank, named),
            templates = listOf(
                MemoryTableTemplate(id = "named", name = "Named table"),
                MemoryTableTemplate(id = "blank", name = "   "),
            ),
        )

        assertEquals(listOf("z-doc", "a-doc", "b-doc", "n-doc"), items.map { it.document.id })
        assertEquals(listOf("Named table", "a-doc", "b-doc", "Named table"), items.map { it.displayName })
        assertSame(named, items[0].document)
        assertEquals("memory_table_tool", items[0].document.deletedBy)
        assertEquals(300L, items[0].document.deletedAt)
    }

    private fun document(
        id: String,
        templateId: String,
        deletedAt: Long?,
        deletedBy: String?,
    ) = MemoryTableDocument(
        id = id,
        templateId = templateId,
        scopeType = MemoryTableScopeType.ASSISTANT,
        scopeId = "assistant-a",
        payloadJson = """{"id":"$id"}""",
        revision = 4,
        deletedAt = deletedAt,
        deletedBy = deletedBy,
    )
}

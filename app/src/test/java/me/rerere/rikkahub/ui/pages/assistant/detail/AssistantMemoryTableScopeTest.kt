package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryTableScopeTest {
    @Test
    fun editorWaitsForTemplateLookupBeforeClosing() {
        val template = MemoryTableTemplate(
            id = "template",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
        )

        assertFalse(shouldCloseMemoryTableEditor(templateLookupComplete = false, template = null))
        assertFalse(shouldCloseMemoryTableEditor(templateLookupComplete = true, template = template))
        assertTrue(shouldCloseMemoryTableEditor(templateLookupComplete = true, template = null))
    }

    @Test
    fun deriveDocumentsKeepsOnlyGlobalAndCurrentAssistantScopes() {
        val template = MemoryTableTemplate(id = "template")
        val selection = deriveAssistantMemoryTableDocuments(
            templates = listOf(template),
            documents = listOf(
                document("assistant-a", MemoryTableScopeType.ASSISTANT, "assistant-a"),
                document("assistant-b", MemoryTableScopeType.ASSISTANT, "assistant-b"),
                document("global", MemoryTableScopeType.GLOBAL, "global"),
                document("conversation", MemoryTableScopeType.CONVERSATION, "conversation-a"),
            ),
            assistantId = "assistant-a",
        )

        assertEquals(listOf("assistant-a", "global"), selection.visibleDocuments.map { it.id })
        assertEquals("assistant-a", selection.primaryDocumentsByTemplate[template.id]?.id)
        assertEquals(listOf("global"), selection.extraDocuments.map { it.id })
    }

    @Test
    fun deriveDocumentsFallsBackToGlobalWhenCurrentAssistantDocumentIsMissing() {
        val template = MemoryTableTemplate(id = "template")
        val selection = deriveAssistantMemoryTableDocuments(
            templates = listOf(template),
            documents = listOf(
                document("assistant-b", MemoryTableScopeType.ASSISTANT, "assistant-b"),
                document("global", MemoryTableScopeType.GLOBAL, "global"),
            ),
            assistantId = "assistant-a",
        )

        assertEquals("global", selection.primaryDocumentsByTemplate[template.id]?.id)
        assertEquals(emptyList<MemoryTableDocument>(), selection.extraDocuments)
    }

    @Test
    fun deriveDocumentsDoesNotFallbackToAnotherAssistantOrConversation() {
        val template = MemoryTableTemplate(id = "template")
        val selection = deriveAssistantMemoryTableDocuments(
            templates = listOf(template),
            documents = listOf(
                document("assistant-b", MemoryTableScopeType.ASSISTANT, "assistant-b"),
                document("conversation", MemoryTableScopeType.CONVERSATION, "conversation-a"),
            ),
            assistantId = "assistant-a",
        )

        assertEquals(emptyList<MemoryTableDocument>(), selection.visibleDocuments)
        assertNull(selection.primaryDocumentsByTemplate[template.id])
        assertEquals(emptyList<MemoryTableDocument>(), selection.extraDocuments)
    }

    @Test
    fun deriveDocumentsKeepsAdditionalLegalDocumentsAsExtras() {
        val template = MemoryTableTemplate(id = "template")
        val selection = deriveAssistantMemoryTableDocuments(
            templates = listOf(template),
            documents = listOf(
                document("assistant-primary", MemoryTableScopeType.ASSISTANT, "assistant-a"),
                document("assistant-extra", MemoryTableScopeType.ASSISTANT, "assistant-a"),
                document("global", MemoryTableScopeType.GLOBAL, "global"),
            ),
            assistantId = "assistant-a",
        )

        assertEquals("assistant-primary", selection.primaryDocumentsByTemplate[template.id]?.id)
        assertEquals(listOf("assistant-extra", "global"), selection.extraDocuments.map { it.id })
    }

    @Test
    fun editorLookupRequiresMatchingIdTemplateAndScope() {
        val documents = listOf(
            document("assistant-a", MemoryTableScopeType.ASSISTANT, "assistant-a"),
            document("assistant-b", MemoryTableScopeType.ASSISTANT, "assistant-b"),
            document("conversation-a", MemoryTableScopeType.CONVERSATION, "conversation-a"),
            document("global", MemoryTableScopeType.GLOBAL, "legacy-global-scope"),
        )

        assertEquals(
            "assistant-a",
            findMemoryTableEditorDocument(
                documents = documents,
                documentId = "assistant-a",
                templateId = "template",
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
            )?.id,
        )
        assertNull(
            findMemoryTableEditorDocument(
                documents = documents,
                documentId = "assistant-b",
                templateId = "template",
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
            ),
        )
        assertNull(
            findMemoryTableEditorDocument(
                documents = documents,
                documentId = "assistant-a",
                templateId = "other-template",
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
            ),
        )
        assertEquals(
            "conversation-a",
            findMemoryTableEditorDocument(
                documents = documents,
                documentId = "conversation-a",
                templateId = "template",
                scopeType = MemoryTableScopeType.CONVERSATION,
                scopeId = "conversation-a",
            )?.id,
        )
        assertEquals(
            "global",
            findMemoryTableEditorDocument(
                documents = documents,
                documentId = "global",
                templateId = "template",
                scopeType = MemoryTableScopeType.GLOBAL,
                scopeId = "global",
            )?.id,
        )
    }

    @Test
    fun editorResolutionNeverReusesAnInvisibleExplicitIdForANewDraft() {
        val invisibleId = "assistant-b"
        val rejected = resolveMemoryTableEditorDocument(
            documents = listOf(
                document(invisibleId, MemoryTableScopeType.ASSISTANT, "assistant-b"),
            ),
            documentId = invisibleId,
            templateId = "template",
            scopeType = MemoryTableScopeType.ASSISTANT,
            scopeId = "assistant-a",
        )
        val newDraft = checkNotNull(
            resolveMemoryTableEditorDocument(
                documents = emptyList(),
                documentId = null,
                templateId = "template",
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
            ),
        )

        assertNull(rejected)
        assertEquals(true, newDraft.isNewDocument)
        assertNotEquals(invisibleId, newDraft.document.id)
    }

    @Test
    fun conversationEditorRequiresConversationId() {
        assertNull(
            memoryTableEditorScopeId(
                scopeType = MemoryTableScopeType.CONVERSATION,
                assistantId = "assistant-a",
                conversationId = null,
            ),
        )
        assertEquals(
            "conversation-a",
            memoryTableEditorScopeId(
                scopeType = MemoryTableScopeType.CONVERSATION,
                assistantId = "assistant-a",
                conversationId = "conversation-a",
            ),
        )
    }

    private fun document(
        id: String,
        scopeType: MemoryTableScopeType,
        scopeId: String,
    ) = MemoryTableDocument(
        id = id,
        templateId = "template",
        scopeType = scopeType,
        scopeId = scopeId,
    )
}

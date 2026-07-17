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
import kotlinx.coroutines.runBlocking

class AssistantMemoryTableScopeTest {

    @Test
    fun `template scope migration confirmation is required only when scope changes`() {
        assertFalse(
            shouldConfirmMemoryTableTemplateScopeMigration(
                currentScopeType = MemoryTableScopeType.ASSISTANT,
                targetScopeType = MemoryTableScopeType.ASSISTANT,
            )
        )
        assertTrue(
            shouldConfirmMemoryTableTemplateScopeMigration(
                currentScopeType = MemoryTableScopeType.GLOBAL,
                targetScopeType = MemoryTableScopeType.ASSISTANT,
            )
        )
        assertTrue(
            shouldConfirmMemoryTableTemplateScopeMigration(
                currentScopeType = MemoryTableScopeType.ASSISTANT,
                targetScopeType = MemoryTableScopeType.GLOBAL,
            )
        )
    }
    @Test
    fun creationPersistsTemplateBeforeDocument() = runBlocking {
        val events = mutableListOf<String>()
        val draft = MemoryTableTemplate(id = "draft")

        val document = persistMemoryTableCreation(
            template = draft,
            persistTemplate = {
                events += "template"
                it.copy(id = "persisted")
            },
            persistDocument = {
                events += "document:${it.id}"
                MemoryTableDocument(templateId = it.id, scopeId = "assistant")
            },
        )

        assertEquals(listOf("template", "document:persisted"), events)
        assertEquals("persisted", document.templateId)
    }

    @Test
    fun templateFailurePreventsDocumentPersistence() = runBlocking {
        var documentPersisted = false

        val result = runCatching {
            persistMemoryTableCreation(
                template = MemoryTableTemplate(),
                persistTemplate = { error("template failed") },
                persistDocument = {
                    documentPersisted = true
                    MemoryTableDocument(templateId = it.id, scopeId = "assistant")
                },
            )
        }

        assertTrue(result.isFailure)
        assertFalse(documentPersisted)
    }

    @Test
    fun existingTemplateStillWaitsForDocumentPersistence() = runBlocking {
        val template = MemoryTableTemplate(id = "effective")

        val result = runCatching {
            persistMemoryTableCreation(
                template = template,
                persistTemplate = null,
                persistDocument = { error("document failed") },
            )
        }

        assertTrue(result.isFailure)
    }

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
    fun deriveDocumentsBuildsPrimaryMapIndependentlyForMultipleVisibleTemplates() {
        val assistantTemplate = MemoryTableTemplate(id = "assistant-template")
        val globalTemplate = MemoryTableTemplate(id = "global-template")
        val emptyTemplate = MemoryTableTemplate(id = "empty-template")
        val selection = deriveAssistantMemoryTableDocuments(
            templates = listOf(assistantTemplate, globalTemplate, emptyTemplate),
            documents = listOf(
                document(
                    id = "assistant-global",
                    scopeType = MemoryTableScopeType.GLOBAL,
                    scopeId = "global",
                    templateId = assistantTemplate.id,
                ),
                document(
                    id = "assistant-primary",
                    scopeType = MemoryTableScopeType.ASSISTANT,
                    scopeId = "assistant-a",
                    templateId = assistantTemplate.id,
                ),
                document(
                    id = "global-primary",
                    scopeType = MemoryTableScopeType.GLOBAL,
                    scopeId = "global",
                    templateId = globalTemplate.id,
                ),
                document(
                    id = "invisible-assistant",
                    scopeType = MemoryTableScopeType.ASSISTANT,
                    scopeId = "assistant-a",
                    templateId = "invisible-template",
                ),
            ),
            assistantId = "assistant-a",
        )

        assertEquals("assistant-primary", selection.primaryDocumentsByTemplate[assistantTemplate.id]?.id)
        assertEquals("global-primary", selection.primaryDocumentsByTemplate[globalTemplate.id]?.id)
        assertTrue(selection.primaryDocumentsByTemplate.containsKey(emptyTemplate.id))
        assertNull(selection.primaryDocumentsByTemplate[emptyTemplate.id])
        assertFalse(selection.primaryDocumentsByTemplate.containsKey("invisible-template"))
        assertEquals(
            listOf("assistant-global", "assistant-primary", "global-primary"),
            selection.visibleDocuments.map { it.id },
        )
        assertEquals(listOf("assistant-global"), selection.extraDocuments.map { it.id })
    }

    @Test
    fun pickerCollapsesNormalizedDuplicateNamesWithoutDroppingDocumentProjection() {
        val first = template("first", " Shared  Name ", MemoryTableScopeType.GLOBAL, "global")
        val second = template("second", "shared name", MemoryTableScopeType.GLOBAL, "global")
        val documents = listOf(
            document("first-document", MemoryTableScopeType.GLOBAL, "global", first.id),
            document("second-document", MemoryTableScopeType.GLOBAL, "global", second.id),
        )
        val selection = deriveAssistantMemoryTableDocuments(listOf(first, second), documents, "assistant-a")

        val picker = deriveMemoryTablePickerTemplates(
            templates = listOf(first, second),
            primaryDocumentsByTemplate = selection.primaryDocumentsByTemplate,
            assistantId = "assistant-a",
        )

        assertEquals(1, picker.size)
        assertEquals(listOf("first-document", "second-document"), selection.visibleDocuments.map { it.id })
    }

    @Test
    fun pickerPrefersTemplateWithPrimaryDocument() {
        val unusedAssistant = template("assistant", "same", MemoryTableScopeType.ASSISTANT, "assistant-a")
        val addedGlobal = template("global", "SAME", MemoryTableScopeType.GLOBAL, "global")

        val picker = deriveMemoryTablePickerTemplates(
            templates = listOf(unusedAssistant, addedGlobal),
            primaryDocumentsByTemplate = mapOf(
                unusedAssistant.id to null,
                addedGlobal.id to document("added", MemoryTableScopeType.GLOBAL, "global", addedGlobal.id),
            ),
            assistantId = "assistant-a",
        )

        assertEquals("global", picker.single().id)
    }

    @Test
    fun pickerPrefersCurrentAssistantThenGlobalThenNewestAndId() {
        val otherAssistant = template("other", "same", MemoryTableScopeType.ASSISTANT, "assistant-b", 30)
        val global = template("global", "same", MemoryTableScopeType.GLOBAL, "global", 20)
        val currentAssistant = template("current", "same", MemoryTableScopeType.ASSISTANT, "assistant-a", 10)
        assertEquals(
            "current",
            deriveMemoryTablePickerTemplates(
                listOf(otherAssistant, global, currentAssistant), emptyMap(), "assistant-a"
            ).single().id,
        )

        val newestGlobal = template("z", "global-only", MemoryTableScopeType.GLOBAL, "global", 40)
        val olderGlobal = template("a", "GLOBAL-ONLY", MemoryTableScopeType.GLOBAL, "global", 10)
        assertEquals(
            "z",
            deriveMemoryTablePickerTemplates(
                listOf(olderGlobal, newestGlobal), emptyMap(), "assistant-a"
            ).single().id,
        )

        val globalBeforeOtherAssistant = template("global-a", "scope-tie", MemoryTableScopeType.GLOBAL, "global", 1)
        val newerForeignAssistant =
            template("assistant-z", "SCOPE-TIE", MemoryTableScopeType.ASSISTANT, "assistant-b", 99)
        assertEquals(
            "global-a",
            deriveMemoryTablePickerTemplates(
                listOf(newerForeignAssistant, globalBeforeOtherAssistant),
                emptyMap(),
                "assistant-a",
            ).single().id,
        )

        val stableA = template("a", "id-tie", MemoryTableScopeType.GLOBAL, "global", 10)
        val stableZ = template("z", "ID-TIE", MemoryTableScopeType.GLOBAL, "global", 10)
        assertEquals(
            "a",
            deriveMemoryTablePickerTemplates(
                listOf(stableZ, stableA),
                emptyMap(),
                "assistant-a",
            ).single().id,
        )
    }

    @Test
    fun templateNameConflictUsesScopeRulesAndExcludesEditedTemplate() {
        val global = template("global", "Shared Name", MemoryTableScopeType.GLOBAL, "global")
        val current = template("current", "Private", MemoryTableScopeType.ASSISTANT, "assistant-a")
        val other = template("other", "Other Private", MemoryTableScopeType.ASSISTANT, "assistant-b")
        val templates = listOf(global, current, other)

        assertTrue(
            hasMemoryTableTemplateNameConflict(
                " shared   name ", MemoryTableScopeType.ASSISTANT, templates, "assistant-a"
            )
        )
        assertTrue(
            hasMemoryTableTemplateNameConflict(
                "private", MemoryTableScopeType.ASSISTANT, templates, "assistant-a"
            )
        )
        assertFalse(
            hasMemoryTableTemplateNameConflict(
                "other private", MemoryTableScopeType.ASSISTANT, templates, "assistant-a"
            )
        )
        assertTrue(
            hasMemoryTableTemplateNameConflict(
                "other private", MemoryTableScopeType.GLOBAL, templates, "assistant-a"
            )
        )
        assertFalse(
            hasMemoryTableTemplateNameConflict(
                " private ", MemoryTableScopeType.ASSISTANT, templates, "assistant-a", excludedTemplateId = current.id
            )
        )
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
        templateId: String = "template",
    ) = MemoryTableDocument(
        id = id,
        templateId = templateId,
        scopeType = scopeType,
        scopeId = scopeId,
    )

    private fun template(
        id: String,
        name: String,
        scopeType: MemoryTableScopeType,
        scopeId: String,
        updatedAt: Long = 0,
    ) = MemoryTableTemplate(
        id = id,
        name = name,
        scopeType = scopeType,
        scopeId = scopeId,
        updatedAt = updatedAt,
    )
}

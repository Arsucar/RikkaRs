package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.MemoryTableDAO
import me.rerere.rikkahub.data.db.dao.MemoryTableSnapshotDAO
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableSnapshotEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableTemplateEntity
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableImportConflictPolicy
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate
import me.rerere.rikkahub.data.model.MemoryTableTemplateNameConflictException
import me.rerere.rikkahub.data.model.encodeMemoryTableBundle
import me.rerere.rikkahub.data.model.normalizeMemoryTableTemplateName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MemoryTableRepositoryTest {
    @Test
    fun templateNameNormalizationUsesTrimNfcWhitespaceCollapseAndRootLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals(
                "café i",
                normalizeMemoryTableTemplateName(" \tCafe\u0301\u00a0\nI "),
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun upsertTemplateNormalizesBlankNameAndSchema() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val template = repository.upsertTemplate(MemoryTableTemplate(name = "", schemaJson = ""))

        assertEquals("Default memory table", template.name)
        assertTrue(template.schemaJson.contains(""""name": "key""""))
        assertTrue(template.schemaJson.contains(""""name": "category""""))
        assertTrue(template.schemaJson.contains(""""name": "summary""""))
        assertEquals(1, dao.templateUpserts)
        assertEquals(template.id, dao.templates.single().id)
        assertEquals(template.schemaJson, dao.templates.single().schemaJson)
    }

    @Test
    fun upsertTemplateRejectsInvalidSchemaBeforeDaoWrite() {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.upsertTemplate(
                    MemoryTableTemplate(
                        name = "bad",
                        schemaJson = """{"tables":[{"name":"facts","columns":[]}]}""",
                    )
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("schemaJson.tables[0].columns"))
        assertEquals(0, dao.templateUpserts)
        assertTrue(dao.templates.isEmpty())
    }

    @Test
    fun deleteTemplateReturnsFalseWhenTemplateIsMissing() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val deleted = repository.deleteTemplate("missing")

        assertFalse(deleted)
    }

    @Test
    fun deleteTemplateDeletesDocumentsOnlyWhenTemplateExists() = runBlocking {
        val template = MemoryTableTemplateEntity(
            id = "template",
            name = "Template",
            description = "",
            schemaJson = """{"tables":[{"name":"facts","columns":[{"name":"key"}]}]}""",
            scopeType = "GLOBAL",
            scopeId = MemoryRepository.GLOBAL_MEMORY_ID,
            createdAt = 1,
            updatedAt = 1,
        )
        val dao = FakeMemoryTableDAO(
            templates = listOf(template),
            documents = listOf(
                doc("doc", "ASSISTANT", "assistant-a"),
                doc("other", "ASSISTANT", "assistant-a").copy(templateId = "other-template"),
            )
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val deleted = repository.deleteTemplate("template")

        assertTrue(deleted)
        assertTrue(dao.templates.isEmpty())
        assertEquals(listOf("other"), dao.documents.map { it.id })
    }

    @Test
    fun effectiveDocumentsReturnsGlobalAssistantAndConversationScopes() = runBlocking {
        val dao = FakeMemoryTableDAO(
            documents = listOf(
                doc("global", "GLOBAL", MemoryRepository.GLOBAL_MEMORY_ID),
                doc("assistant-a", "ASSISTANT", "assistant-a"),
                doc("assistant-b", "ASSISTANT", "assistant-b"),
                doc("conversation-a", "CONVERSATION", "conversation-a"),
            )
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val documents = repository.getEffectiveDocuments(
            assistantId = "assistant-a",
            conversationId = "conversation-a",
        )

        assertEquals(listOf("global", "assistant-a", "conversation-a"), documents.map { it.id })
    }

    @Test
    fun effectiveDocumentsDefensivelyFilterPollutedSuspendAndFlowResults() = runBlocking {
        val dao = FakeMemoryTableDAO(
            documents = listOf(
                doc("global", "GLOBAL", MemoryRepository.GLOBAL_MEMORY_ID),
                doc("assistant-a", "ASSISTANT", "assistant-a"),
                doc("assistant-b", "ASSISTANT", "assistant-b"),
                doc("conversation-a", "CONVERSATION", "conversation-a"),
                doc("conversation-b", "CONVERSATION", "conversation-b"),
                doc("unknown", "UNKNOWN", "assistant-a"),
            ),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val conversationSuspend = repository.getEffectiveDocuments(
            assistantId = "assistant-a",
            conversationId = "conversation-a",
        )
        val conversationFlow = repository.getEffectiveDocumentsFlow(
            assistantId = "assistant-a",
            conversationId = "conversation-a",
        ).first()
        val assistantSuspend = repository.getEffectiveDocuments(assistantId = "assistant-a")
        val assistantFlow = repository.getAssistantMemoryDocumentsFlow(assistantId = "assistant-a").first()

        val expectedConversationIds = listOf("global", "assistant-a", "conversation-a")
        val expectedAssistantIds = listOf("global", "assistant-a")
        assertEquals(expectedConversationIds, conversationSuspend.map { it.id })
        assertEquals(expectedConversationIds, conversationFlow.map { it.id })
        assertEquals(expectedAssistantIds, assistantSuspend.map { it.id })
        assertEquals(expectedAssistantIds, assistantFlow.map { it.id })
    }

    @Test
    fun getEffectiveDocumentsIfEnabledDoesNotReadDaoWhenDisabled() = runBlocking {
        val dao = FakeMemoryTableDAO(
            documents = listOf(
                doc("global", "GLOBAL", MemoryRepository.GLOBAL_MEMORY_ID),
            )
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val documents = repository.getEffectiveDocumentsIfEnabled(
            settingsEnabled = false,
            assistantEnabled = true,
            assistantId = "assistant-a",
            conversationId = "conversation-a",
        )

        assertTrue(documents.isEmpty())
        assertEquals(0, dao.effectiveDocumentReads)
    }

    @Test
    fun upsertDocumentBumpsRevisionWhenUpdating() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())
        val created = repository.upsertDocument(
            MemoryTableDocument(
                templateId = "template",
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
                payloadJson = """{"a":1}""",
            )
        )

        val updated = repository.upsertDocument(created.copy(payloadJson = """{"a":2}"""))

        assertEquals(1, updated.revision)
        assertEquals("""{"a":2}""", dao.documents.single().payloadJson)
    }

    @Test
    fun upsertDocumentRejectsInvalidPayloadBeforeDaoWrite() {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.upsertDocument(
                    MemoryTableDocument(
                        templateId = "template",
                        scopeType = MemoryTableScopeType.ASSISTANT,
                        scopeId = "assistant-a",
                        payloadJson = """["not-an-object"]""",
                    )
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("payloadJson must be a JSON object"))
        assertEquals(0, dao.documentUpserts)
        assertTrue(dao.documents.isEmpty())
    }

    @Test
    fun upsertDocumentRejectsInvalidPayloadWithoutReplacingExistingDocument() {
        val existing = doc(
            id = "doc",
            scopeType = "ASSISTANT",
            scopeId = "assistant-a",
            payloadJson = """{"valid":true}""",
            revision = 3,
        )
        val dao = FakeMemoryTableDAO(documents = listOf(existing))
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.upsertDocument(
                    MemoryTableDocument(
                        id = "doc",
                        templateId = "template",
                        scopeType = MemoryTableScopeType.ASSISTANT,
                        scopeId = "assistant-a",
                        payloadJson = """{"truncated":""",
                    )
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("payloadJson must be valid JSON"))
        assertEquals(0, dao.documentUpserts)
        assertEquals("""{"valid":true}""", dao.documents.single().payloadJson)
        assertEquals(3, dao.documents.single().revision)
    }

    @Test
    fun rollbackDocumentRestoresPriorRevision() = runBlocking {
        val dao = FakeMemoryTableDAO(
            documents = listOf(
                doc(
                    id = "doc",
                    scopeType = "ASSISTANT",
                    scopeId = "assistant-a",
                    payloadJson = """{"facts":[{"key":"name","value":"Alice"}]}""",
                    revision = 0,
                )
            ),
        )
        val snapshotDao = FakeMemoryTableSnapshotDAO()
        val repository = MemoryTableRepository(dao, snapshotDao)

        // First overwrite: revision 0's payload is snapshotted, document becomes revision 1.
        repository.upsertDocument(
            dao.documents.single().let {
                MemoryTableDocument(
                    id = it.id,
                    templateId = it.templateId,
                    scopeType = MemoryTableScopeType.ASSISTANT,
                    scopeId = it.scopeId,
                    payloadJson = """{"facts":[{"key":"name","value":"Bob"}]}""",
                )
            }
        )
        assertEquals("""{"facts":[{"key":"name","value":"Bob"}]}""", dao.documents.single().payloadJson)

        val snapshots = repository.getDocumentSnapshots("doc", actorAssistantId = "assistant-a")
        assertEquals(1, snapshots.size)
        assertEquals(0, snapshots.single().revision)

        // Rollback to revision 0's payload.
        val restored = repository.rollbackDocument(
            documentId = "doc",
            revision = 0,
            actorAssistantId = "assistant-a",
        )
        assertTrue(restored.payloadJson.contains("Alice"))
        assertTrue(dao.documents.single().payloadJson.contains("Alice"))
        assertEquals(2, restored.revision)
        assertEquals(
            listOf(1, 0),
            repository.getDocumentSnapshots("doc", actorAssistantId = "assistant-a").map { it.revision },
        )
    }

    @Test
    fun historyAndRollbackRejectForeignAssistantWithoutChangingDocument() = runBlocking {
        val original = doc(
            id = "doc",
            scopeType = "ASSISTANT",
            scopeId = "assistant-a",
            payloadJson = """{"value":"current"}""",
            revision = 3,
        )
        val dao = FakeMemoryTableDAO(documents = listOf(original))
        val snapshotDao = FakeMemoryTableSnapshotDAO().apply {
            snapshots += MemoryTableSnapshotEntity(
                id = "snapshot",
                documentId = "doc",
                revision = 2,
                payloadJson = """{"value":"old"}""",
                createdAt = 2,
            )
        }
        val repository = MemoryTableRepository(dao, snapshotDao)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.getDocumentSnapshots("doc", actorAssistantId = "assistant-b")
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.rollbackDocument(
                    documentId = "doc",
                    revision = 2,
                    actorAssistantId = "assistant-b",
                )
            }
        }

        assertEquals(original, dao.documents.single())
        assertEquals(0, dao.documentUpserts)
    }

    @Test
    fun rollbackRejectsMissingRevisionWithoutChangingDocument() = runBlocking {
        val original = doc(
            id = "doc",
            scopeType = "ASSISTANT",
            scopeId = "assistant-a",
            payloadJson = """{"value":"current"}""",
            revision = 3,
        )
        val dao = FakeMemoryTableDAO(documents = listOf(original))
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.rollbackDocument(
                    documentId = "doc",
                    revision = 99,
                    actorAssistantId = "assistant-a",
                )
            }
        }

        assertEquals(original, dao.documents.single())
        assertEquals(0, dao.documentUpserts)
    }

    @Test
    fun historyAndRollbackRejectNonHistoricalRevisionsWithoutChangingDocument() = runBlocking {
        val original = doc(
            id = "doc",
            scopeType = "ASSISTANT",
            scopeId = "assistant-a",
            payloadJson = """{"value":"current"}""",
            revision = 3,
        )
        val dao = FakeMemoryTableDAO(documents = listOf(original))
        val snapshotDao = FakeMemoryTableSnapshotDAO().apply {
            snapshots += listOf(
                MemoryTableSnapshotEntity("negative", "doc", -1, "{}", 1),
                MemoryTableSnapshotEntity("current", "doc", 3, "{}", 2),
                MemoryTableSnapshotEntity("future", "doc", 4, "{}", 3),
                MemoryTableSnapshotEntity("historical", "doc", 2, "{}", 4),
            )
        }
        val repository = MemoryTableRepository(dao, snapshotDao)

        assertEquals(
            listOf(2),
            repository.getDocumentSnapshots("doc", actorAssistantId = "assistant-a").map { it.revision },
        )
        listOf(-1, 3, 4).forEach { revision ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    repository.rollbackDocument(
                        documentId = "doc",
                        revision = revision,
                        actorAssistantId = "assistant-a",
                    )
                }
            }
        }

        assertEquals(original, dao.documents.single())
        assertEquals(0, dao.documentUpserts)
    }

    @Test
    fun effectiveTemplatesReturnGlobalAndCurrentAssistantOnly() = runBlocking {
        val dao = FakeMemoryTableDAO(
            templates = listOf(
                templateEntity("global", "GLOBAL", MemoryRepository.GLOBAL_MEMORY_ID),
                templateEntity("assistant-a", "ASSISTANT", "assistant-a"),
                templateEntity("assistant-b", "ASSISTANT", "assistant-b"),
                templateEntity("conversation", "CONVERSATION", "conversation-a"),
            )
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val templates = repository.getEffectiveTemplates("assistant-a")
        val flowTemplates = repository.getEffectiveTemplatesFlow("assistant-a").first()

        assertEquals(listOf("global", "assistant-a"), templates.map { it.id })
        assertEquals(listOf("global", "assistant-a"), flowTemplates.map { it.id })
    }

    @Test
    fun scopedTemplateCreateDefaultsToActorAndAllowsSameNamePerAssistant() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val a = repository.upsertTemplate(
            MemoryTableTemplate(name = "Shared"),
            actorAssistantId = "assistant-a",
        )
        val b = repository.upsertTemplate(
            MemoryTableTemplate(name = "Shared"),
            actorAssistantId = "assistant-b",
        )

        assertEquals(MemoryTableScopeType.ASSISTANT, a.scopeType)
        assertEquals("assistant-a", a.scopeId)
        assertEquals(MemoryTableScopeType.ASSISTANT, b.scopeType)
        assertEquals("assistant-b", b.scopeId)
        assertEquals(
            listOf(a.id),
            repository.getEffectiveTemplates("assistant-a").filter { it.name == "Shared" }.map { it.id },
        )
        assertEquals(
            listOf(b.id),
            repository.getEffectiveTemplates("assistant-b").filter { it.name == "Shared" }.map { it.id },
        )
    }

    @Test
    fun actorlessTemplateUpsertKeepsLegacyDuplicateImportCompatibility() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        repository.upsertTemplate(
            MemoryTableTemplate(
                id = "legacy-global",
                name = "Shared",
                scopeType = MemoryTableScopeType.GLOBAL,
                scopeId = MemoryRepository.GLOBAL_MEMORY_ID,
            )
        )
        repository.upsertTemplate(
            MemoryTableTemplate(
                id = "legacy-assistant",
                name = " shared ",
                scopeType = MemoryTableScopeType.ASSISTANT,
                scopeId = "assistant-a",
            )
        )

        assertEquals(listOf("legacy-global", "legacy-assistant"), dao.templates.map { it.id })
        assertEquals(listOf("GLOBAL", "ASSISTANT"), dao.templates.map { it.scopeType })
        assertEquals(
            listOf(MemoryRepository.GLOBAL_MEMORY_ID, "assistant-a"),
            dao.templates.map { it.scopeId },
        )
    }

    @Test
    fun assistantTemplateNameConflictsWithGlobalAndSameAssistantTemplates() {
        val dao = FakeMemoryTableDAO(
            templates = listOf(
                templateEntity(
                    id = "global",
                    scopeType = "GLOBAL",
                    scopeId = MemoryRepository.GLOBAL_MEMORY_ID,
                    name = " Caf\u00e9   Table ",
                ),
                templateEntity(
                    id = "private",
                    scopeType = "ASSISTANT",
                    scopeId = "assistant-a",
                    name = "Private Notes",
                ),
            ),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        assertThrows(MemoryTableTemplateNameConflictException::class.java) {
            runBlocking {
                repository.upsertTemplate(
                    MemoryTableTemplate(name = "cafe\u0301\tTABLE"),
                    actorAssistantId = "assistant-a",
                )
            }
        }
        assertThrows(MemoryTableTemplateNameConflictException::class.java) {
            runBlocking {
                repository.upsertTemplate(
                    MemoryTableTemplate(name = " private\nnotes "),
                    actorAssistantId = "assistant-a",
                )
            }
        }
        assertEquals(2, dao.templates.size)
    }

    @Test
    fun globalTemplateNameConflictsWithAnyAssistantTemplate() {
        val dao = FakeMemoryTableDAO(
            templates = listOf(
                templateEntity(
                    id = "private-b",
                    scopeType = "ASSISTANT",
                    scopeId = "assistant-b",
                    name = "Shared",
                ),
            ),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        assertThrows(MemoryTableTemplateNameConflictException::class.java) {
            runBlocking {
                repository.upsertTemplate(
                    template = MemoryTableTemplate(name = " shared "),
                    actorAssistantId = "assistant-a",
                    requestedScopeType = MemoryTableScopeType.GLOBAL,
                )
            }
        }
        assertEquals(listOf("private-b"), dao.templates.map { it.id })
    }

    @Test
    fun scopedTemplateCreateSupportsRequestedGlobalScope() = runBlocking {
        val dao = FakeMemoryTableDAO()
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val template = repository.upsertTemplate(
            template = MemoryTableTemplate(name = "Global Table"),
            actorAssistantId = "assistant-a",
            requestedScopeType = MemoryTableScopeType.GLOBAL,
        )

        assertEquals(MemoryTableScopeType.GLOBAL, template.scopeType)
        assertEquals(MemoryRepository.GLOBAL_MEMORY_ID, template.scopeId)
        assertEquals("GLOBAL", dao.templates.single().scopeType)
        assertEquals(MemoryRepository.GLOBAL_MEMORY_ID, dao.templates.single().scopeId)
    }

    @Test
    fun scopedTemplateUpdateExcludesItsOwnIdFromNameConflictCheck() = runBlocking {
        val dao = FakeMemoryTableDAO(
            templates = listOf(
                templateEntity(
                    id = "template-a",
                    scopeType = "ASSISTANT",
                    scopeId = "assistant-a",
                    name = "Shared Name",
                ),
            ),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val updated = repository.upsertTemplate(
            MemoryTableTemplate(id = "template-a", name = " shared   NAME "),
            actorAssistantId = "assistant-a",
            requestedScopeType = MemoryTableScopeType.GLOBAL,
        )

        assertEquals(" shared   NAME ", updated.name)
        assertEquals(MemoryTableScopeType.ASSISTANT, updated.scopeType)
        assertEquals("assistant-a", updated.scopeId)
        assertEquals(" shared   NAME ", dao.templates.single().name)
    }

    @Test
    fun scopedTemplateUpdateAllowsUnchangedLegacyDuplicateName() = runBlocking {
        val dao = FakeMemoryTableDAO(
            templates = listOf(
                templateEntity("template-a", "ASSISTANT", "assistant-a", name = "Shared Name"),
                templateEntity("template-b", "ASSISTANT", "assistant-a", name = " shared   NAME "),
            ),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val updated = repository.upsertTemplate(
            MemoryTableTemplate(
                id = "template-a",
                name = " shared\tname ",
                description = "Updated description",
            ),
            actorAssistantId = "assistant-a",
        )

        assertEquals("Updated description", updated.description)
        assertEquals("Updated description", dao.templates.first { it.id == "template-a" }.description)
        assertEquals(" shared   NAME ", dao.templates.first { it.id == "template-b" }.name)
    }

    @Test
    fun deletingDuplicateNameTemplateLeavesOtherIdAndDocumentsUntouched() = runBlocking {
        val dao = FakeMemoryTableDAO(
            templates = listOf(
                templateEntity("template-a", "ASSISTANT", "assistant-a", name = "Duplicate"),
                templateEntity("template-b", "ASSISTANT", "assistant-a", name = " duplicate "),
            ),
            documents = listOf(
                doc("doc-a", "ASSISTANT", "assistant-a", templateId = "template-a"),
                doc("doc-b", "ASSISTANT", "assistant-a", templateId = "template-b"),
            ),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val deleted = repository.deleteTemplate("template-a", actorAssistantId = "assistant-a")

        assertTrue(deleted)
        assertEquals(listOf("template-b"), dao.templates.map { it.id })
        assertEquals(listOf("doc-b"), dao.documents.map { it.id })
    }

    @Test
    fun scopedTemplateUpdateAndDeleteRejectKnownForeignId() = runBlocking {
        val dao = FakeMemoryTableDAO(
            templates = listOf(templateEntity("template-a", "ASSISTANT", "assistant-a")),
            documents = listOf(doc("doc-a", "ASSISTANT", "assistant-a")),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.upsertTemplate(
                    MemoryTableTemplate(id = "template-a", name = "stolen"),
                    actorAssistantId = "assistant-b",
                )
            }
        }
        val deleted = repository.deleteTemplate("template-a", actorAssistantId = "assistant-b")

        assertFalse(deleted)
        assertEquals("Template template-a", dao.templates.single().name)
        assertEquals(listOf("doc-a"), dao.documents.map { it.id })
    }

    @Test
    fun scopedTemplateReadAndDeleteRejectMalformedGlobalOwner() = runBlocking {
        val dao = FakeMemoryTableDAO(
            templates = listOf(templateEntity("malformed-global", "GLOBAL", "other")),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        assertEquals(null, repository.getEffectiveTemplate("malformed-global", "assistant-a"))
        assertFalse(repository.deleteTemplate("malformed-global", actorAssistantId = "assistant-a"))
        assertEquals(listOf("malformed-global"), dao.templates.map { it.id })
    }

    @Test
    fun scopedTemplateUpdateCannotChangeExistingOwnership() = runBlocking {
        val dao = FakeMemoryTableDAO(
            templates = listOf(templateEntity("template-a", "ASSISTANT", "assistant-a")),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        val updated = repository.upsertTemplate(
            MemoryTableTemplate(
                id = "template-a",
                name = "Updated",
                scopeType = MemoryTableScopeType.GLOBAL,
                scopeId = MemoryRepository.GLOBAL_MEMORY_ID,
            ),
            actorAssistantId = "assistant-a",
        )

        assertEquals(MemoryTableScopeType.ASSISTANT, updated.scopeType)
        assertEquals("assistant-a", updated.scopeId)
        assertEquals("ASSISTANT", dao.templates.single().scopeType)
        assertEquals("assistant-a", dao.templates.single().scopeId)
    }

    @Test
    fun scopedDocumentMutationRejectsForeignKnownIdAndPrivateTemplateGlobalDocument() = runBlocking {
        val dao = FakeMemoryTableDAO(
            templates = listOf(templateEntity("template-a", "ASSISTANT", "assistant-a")),
            documents = listOf(doc("doc-a", "ASSISTANT", "assistant-a")),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        assertEquals(
            null,
            repository.getEffectiveDocument("doc-a", assistantId = "assistant-b"),
        )
        assertFalse(repository.deleteDocument("doc-a", assistantId = "assistant-b"))
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.upsertDocument(
                    MemoryTableDocument(
                        id = "doc-a",
                        templateId = "template-a",
                        scopeType = MemoryTableScopeType.ASSISTANT,
                        scopeId = "assistant-b",
                    ),
                    actorAssistantId = "assistant-b",
                )
            }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.upsertDocument(
                    MemoryTableDocument(
                        templateId = "template-a",
                        scopeType = MemoryTableScopeType.GLOBAL,
                        scopeId = MemoryRepository.GLOBAL_MEMORY_ID,
                    ),
                    actorAssistantId = "assistant-a",
                )
            }
        }

        assertEquals(listOf("doc-a"), dao.documents.map { it.id })
    }

    @Test
    fun copyGlobalTemplateRejectsDuplicateNameUnderAssistantScope() {
        val dao = FakeMemoryTableDAO(
            templates = listOf(templateEntity("global", "GLOBAL", MemoryRepository.GLOBAL_MEMORY_ID)),
            documents = listOf(
                doc(
                    id = "global-doc",
                    scopeType = "GLOBAL",
                    scopeId = MemoryRepository.GLOBAL_MEMORY_ID,
                    templateId = "global",
                )
            ),
        )
        val repository = MemoryTableRepository(dao, FakeMemoryTableSnapshotDAO())

        assertThrows(MemoryTableTemplateNameConflictException::class.java) {
            runBlocking {
                repository.copyGlobalTemplateToAssistant("global", actorAssistantId = "assistant-a")
            }
        }

        assertEquals(listOf("global"), dao.templates.map { it.id })
        assertEquals(listOf("global-doc"), dao.documents.map { it.id })
        assertEquals("global", dao.documents.single().templateId)
    }

    private fun doc(
        id: String,
        scopeType: String,
        scopeId: String,
        payloadJson: String = "{}",
        revision: Int = 0,
        templateId: String = "template",
    ) = MemoryTableDocumentEntity(
        id = id,
        templateId = templateId,
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = payloadJson,
        revision = revision,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun templateEntity(
        id: String,
        scopeType: String,
        scopeId: String,
        name: String = "Template $id",
    ) = MemoryTableTemplateEntity(
        id = id,
        name = name,
        description = "",
        schemaJson = """{"tables":[{"name":"facts","columns":[{"name":"key"}]}]}""",
        scopeType = scopeType,
        scopeId = scopeId,
        createdAt = 1,
        updatedAt = 1,
    )

    private class FakeMemoryTableDAO(
        templates: List<MemoryTableTemplateEntity> = emptyList(),
        documents: List<MemoryTableDocumentEntity> = emptyList(),
    ) : MemoryTableDAO {
        val templates = templates.toMutableList()
        val documents = documents.toMutableList()
        var effectiveDocumentReads = 0
        var templateUpserts = 0
        var documentUpserts = 0

        override fun getTemplatesFlow(): Flow<List<MemoryTableTemplateEntity>> = flowOf(templates)

        override suspend fun getTemplates(): List<MemoryTableTemplateEntity> = templates

        override suspend fun getTemplate(id: String): MemoryTableTemplateEntity? =
            templates.firstOrNull { it.id == id }

        override fun getEffectiveTemplatesFlow(assistantId: String): Flow<List<MemoryTableTemplateEntity>> =
            flowOf(templates.toList())

        override suspend fun getEffectiveTemplates(assistantId: String): List<MemoryTableTemplateEntity> =
            templates.toList()

        override suspend fun getEffectiveTemplate(id: String, assistantId: String): MemoryTableTemplateEntity? =
            templates.firstOrNull {
                it.id == id && (
                    (it.scopeType == "GLOBAL" && it.scopeId == MemoryRepository.GLOBAL_MEMORY_ID) ||
                        (it.scopeType == "ASSISTANT" && it.scopeId == assistantId)
                    )
            }

        override suspend fun upsertTemplate(template: MemoryTableTemplateEntity) {
            templateUpserts++
            templates.removeAll { it.id == template.id }
            templates += template
        }

        override suspend fun insertTemplateIgnore(template: MemoryTableTemplateEntity): Long {
            if (templates.any { it.id == template.id }) return -1
            templates += template
            return 1
        }

        override suspend fun updateEffectiveTemplateFields(
            id: String,
            assistantId: String,
            name: String,
            description: String,
            schemaJson: String,
            updatedAt: Long,
        ): Int {
            val index = templates.indexOfFirst {
                it.id == id && (
                    (it.scopeType == "GLOBAL" && it.scopeId == MemoryRepository.GLOBAL_MEMORY_ID) ||
                        (it.scopeType == "ASSISTANT" && it.scopeId == assistantId)
                    )
            }
            if (index < 0) return 0
            templates[index] = templates[index].copy(
                name = name,
                description = description,
                schemaJson = schemaJson,
                updatedAt = updatedAt,
            )
            return 1
        }

        override suspend fun deleteTemplate(id: String): Int {
            val before = templates.size
            templates.removeAll { it.id == id }
            return before - templates.size
        }

        override suspend fun deleteSnapshotsByTemplate(templateId: String): Int = 0

        override fun getDocumentsFlow(): Flow<List<MemoryTableDocumentEntity>> = flowOf(documents)

        override suspend fun getDocuments(): List<MemoryTableDocumentEntity> = documents

        override suspend fun getEffectiveDocuments(
            assistantId: String,
            conversationId: String?,
        ): List<MemoryTableDocumentEntity> {
            effectiveDocumentReads++
            return documents.toList()
        }

        override fun getEffectiveDocumentsFlow(
            assistantId: String,
            conversationId: String?,
        ): Flow<List<MemoryTableDocumentEntity>> = flowOf(documents.toList())

        override fun getDocumentsForScopeFlow(
            scopeType: String,
            scopeId: String,
        ): Flow<List<MemoryTableDocumentEntity>> =
            flowOf(documents.filter { it.scopeType == scopeType && it.scopeId == scopeId })

        override suspend fun getDocumentsForScope(
            scopeType: String,
            scopeId: String,
        ): List<MemoryTableDocumentEntity> =
            documents.filter { it.scopeType == scopeType && it.scopeId == scopeId }

        override suspend fun getDocument(id: String): MemoryTableDocumentEntity? =
            documents.firstOrNull { it.id == id }

        override suspend fun getEffectiveDocument(
            id: String,
            assistantId: String,
            conversationId: String?,
        ): MemoryTableDocumentEntity? =
            documents.firstOrNull {
                it.id == id && (
                    it.scopeType == "GLOBAL" ||
                        (it.scopeType == "ASSISTANT" && it.scopeId == assistantId) ||
                        (conversationId != null && it.scopeType == "CONVERSATION" && it.scopeId == conversationId)
                    )
            }

        override suspend fun upsertDocument(document: MemoryTableDocumentEntity) {
            documentUpserts++
            documents.removeAll { it.id == document.id }
            documents += document
        }

        override suspend fun deleteDocument(id: String): Int {
            val before = documents.size
            documents.removeAll { it.id == id }
            return before - documents.size
        }

        override suspend fun deleteSnapshotsForDocument(documentId: String): Int = 0

        override suspend fun deleteDocumentsByTemplate(templateId: String): Int {
            val before = documents.size
            documents.removeAll { it.templateId == templateId }
            return before - documents.size
        }

        override suspend fun getDocumentIdsOwnedByAssistant(assistantId: String): List<String> {
            val ownedTemplateIds = templates
                .filter { it.scopeType == "ASSISTANT" && it.scopeId == assistantId }
                .mapTo(mutableSetOf()) { it.id }
            return documents.filter {
                (it.scopeType == "ASSISTANT" && it.scopeId == assistantId) ||
                    it.templateId in ownedTemplateIds
            }.map { it.id }
        }

        override suspend fun deleteDocumentsOwnedByAssistant(assistantId: String): Int {
            val ownedTemplateIds = templates
                .filter { it.scopeType == "ASSISTANT" && it.scopeId == assistantId }
                .mapTo(mutableSetOf()) { it.id }
            val before = documents.size
            documents.removeAll {
                (it.scopeType == "ASSISTANT" && it.scopeId == assistantId) ||
                    it.templateId in ownedTemplateIds
            }
            return before - documents.size
        }

        override suspend fun deleteTemplatesOwnedByAssistant(assistantId: String): Int {
            val before = templates.size
            templates.removeAll { it.scopeType == "ASSISTANT" && it.scopeId == assistantId }
            return before - templates.size
        }

        override suspend fun deleteSnapshotsOwnedByAssistant(assistantId: String): Int = 0
    }

    private class FakeMemoryTableSnapshotDAO : MemoryTableSnapshotDAO {
        val snapshots = mutableListOf<MemoryTableSnapshotEntity>()

        override fun getSnapshotsForDocumentFlow(documentId: String) =
            flowOf(snapshots.filter { it.documentId == documentId }.sortedByDescending { it.revision })

        override suspend fun getSnapshotsForDocument(documentId: String): List<MemoryTableSnapshotEntity> =
            snapshots.filter { it.documentId == documentId }.sortedByDescending { it.revision }

        override suspend fun getSnapshot(documentId: String, revision: Int): MemoryTableSnapshotEntity? =
            snapshots.firstOrNull { it.documentId == documentId && it.revision == revision }

        override suspend fun upsertSnapshot(snapshot: MemoryTableSnapshotEntity) {
            snapshots.removeAll { it.documentId == snapshot.documentId && it.revision == snapshot.revision }
            snapshots += snapshot
        }

        override suspend fun countSnapshots(documentId: String): Int =
            snapshots.count { it.documentId == documentId }

        override suspend fun pruneSnapshots(documentId: String, keep: Int): Int {
            val forDoc = snapshots.filter { it.documentId == documentId }.sortedByDescending { it.revision }
            val toRemove = forDoc.drop(keep)
            snapshots.removeAll(toRemove)
            return toRemove.size
        }

        override suspend fun deleteSnapshotsForDocument(documentId: String): Int {
            val before = snapshots.size
            snapshots.removeAll { it.documentId == documentId }
            return before - snapshots.size
        }
    }
}

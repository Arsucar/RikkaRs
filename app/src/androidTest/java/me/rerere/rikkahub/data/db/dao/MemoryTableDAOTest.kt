package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableSnapshotEntity
import me.rerere.rikkahub.data.db.entity.MemoryTableTemplateEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryTableDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MemoryTableDAO
    private lateinit var snapshotDao: MemoryTableSnapshotDAO
    private lateinit var conversationDao: ConversationDAO

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.memoryTableDao()
        snapshotDao = database.memoryTableSnapshotDao()
        conversationDao = database.conversationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun effectiveQueriesIsolateAssistantAndOptionalConversationScopes() = runBlocking {
        listOf(
            document("global", "GLOBAL", "global", updatedAt = 60),
            document("assistant-a", "ASSISTANT", "assistant-a", updatedAt = 50),
            document("assistant-b", "ASSISTANT", "assistant-b", updatedAt = 40),
            document("conversation-a", "CONVERSATION", "conversation-a", updatedAt = 30),
            document("conversation-b", "CONVERSATION", "conversation-b", updatedAt = 20),
            document("unknown", "UNKNOWN", "assistant-a", updatedAt = 10),
        ).forEach { dao.upsertDocument(it) }

        assertEquals(
            listOf("global", "assistant-a"),
            dao.getEffectiveDocuments("assistant-a", conversationId = null).map { it.id },
        )
        assertEquals(
            listOf("global", "assistant-a"),
            dao.getEffectiveDocumentsFlow("assistant-a", conversationId = null).first().map { it.id },
        )
        assertEquals(
            listOf("global", "assistant-a", "conversation-a"),
            dao.getEffectiveDocuments("assistant-a", conversationId = "conversation-a").map { it.id },
        )
        assertEquals(
            listOf("global", "assistant-a", "conversation-a"),
            dao.getEffectiveDocumentsFlow("assistant-a", conversationId = "conversation-a").first().map { it.id },
        )
        assertEquals(
            listOf("global", "assistant-b", "conversation-b"),
            dao.getEffectiveDocuments("assistant-b", conversationId = "conversation-b").map { it.id },
        )
    }

    @Test
    fun activeAndTrashQueriesAreMutuallyExclusive() = runBlocking {
        conversationDao.insert(conversation("conversation-a", "assistant-a"))
        conversationDao.insert(conversation("conversation-b", "assistant-b"))
        listOf(
            document("active-global", "GLOBAL", "__global__", updatedAt = 60),
            document(
                "deleted-global",
                "GLOBAL",
                "__global__",
                updatedAt = 50,
                deletedAt = 500,
                deletedBy = "user_ui",
            ),
            document(
                "deleted-assistant-a",
                "ASSISTANT",
                "assistant-a",
                updatedAt = 40,
                deletedAt = 400,
                deletedBy = "memory_table_tool",
            ),
            document(
                "deleted-conversation-a",
                "CONVERSATION",
                "conversation-a",
                updatedAt = 30,
                deletedAt = 300,
                deletedBy = "user_ui",
            ),
            document(
                "deleted-conversation-b",
                "CONVERSATION",
                "conversation-b",
                updatedAt = 20,
                deletedAt = 200,
                deletedBy = "user_ui",
            ),
        ).forEach { dao.upsertDocument(it) }

        assertEquals(listOf("active-global"), dao.getDocuments().map { it.id })
        assertEquals(listOf("active-global"), dao.getDocumentsFlow().first().map { it.id })
        assertEquals(
            listOf("active-global"),
            dao.getEffectiveDocuments("assistant-a", "conversation-a").map { it.id },
        )
        assertEquals(null, dao.getDocument("deleted-global"))
        assertEquals("deleted-global", dao.getDocumentIncludingDeleted("deleted-global")?.id)
        assertEquals(
            listOf("deleted-global", "deleted-assistant-a", "deleted-conversation-a"),
            dao.getDeletedDocumentsForAssistantFlow("assistant-a").first().map { it.id },
        )
        assertEquals(
            setOf(
                "active-global",
                "deleted-global",
                "deleted-assistant-a",
                "deleted-conversation-a",
                "deleted-conversation-b",
            ),
            dao.getDocumentsIncludingDeleted().mapTo(mutableSetOf()) { it.id },
        )
    }

    @Test
    fun effectiveTemplateQueriesIsolateAssistantOwnersAndGlobal() = runBlocking {
        listOf(
            template("global", "GLOBAL", "__global__", updatedAt = 60),
            template("malformed-global", "GLOBAL", "other", updatedAt = 55),
            template("assistant-a", "ASSISTANT", "assistant-a", updatedAt = 50),
            template("assistant-b", "ASSISTANT", "assistant-b", updatedAt = 40),
            template("conversation", "CONVERSATION", "conversation-a", updatedAt = 30),
        ).forEach { dao.upsertTemplate(it) }

        assertEquals(
            listOf("global", "assistant-a"),
            dao.getEffectiveTemplates("assistant-a").map { it.id },
        )
        assertEquals(
            listOf("global", "assistant-a"),
            dao.getEffectiveTemplatesFlow("assistant-a").first().map { it.id },
        )
        assertEquals("assistant-a", dao.getEffectiveTemplate("assistant-a", "assistant-a")?.id)
        assertEquals(null, dao.getEffectiveTemplate("assistant-a", "assistant-b"))
        assertEquals(null, dao.getEffectiveTemplate("malformed-global", "assistant-a"))
    }

    @Test
    fun effectiveTemplateDeleteIsAtomicAndRejectsForeignOrMalformedOwners() = runBlocking {
        dao.upsertTemplate(template("assistant-a", "ASSISTANT", "assistant-a", updatedAt = 30))
        dao.upsertTemplate(template("malformed-global", "GLOBAL", "other", updatedAt = 20))
        dao.upsertDocument(
            document(
                id = "assistant-a-doc",
                scopeType = "ASSISTANT",
                scopeId = "assistant-a",
                updatedAt = 30,
                templateId = "assistant-a",
            )
        )
        snapshotDao.upsertSnapshot(snapshot("assistant-a-snapshot", "assistant-a-doc", revision = 0))
        dao.upsertDocument(
            document(
                id = "assistant-a-deleted-doc",
                scopeType = "ASSISTANT",
                scopeId = "assistant-a",
                updatedAt = 25,
                templateId = "assistant-a",
                deletedAt = 25,
                deletedBy = "user_ui",
            )
        )
        snapshotDao.upsertSnapshot(snapshot("assistant-a-deleted-snapshot", "assistant-a-deleted-doc", revision = 0))
        snapshotDao.upsertSnapshot(snapshot("unrelated-snapshot", "unrelated-doc", revision = 0))
        dao.upsertDocument(
            document(
                id = "malformed-global-doc",
                scopeType = "GLOBAL",
                scopeId = "global",
                updatedAt = 20,
                templateId = "malformed-global",
            )
        )

        assertEquals(0, dao.deleteEffectiveTemplateAndDocuments("assistant-a", "assistant-b"))
        assertEquals(0, dao.deleteEffectiveTemplateAndDocuments("malformed-global", "assistant-a"))
        assertEquals("assistant-a", dao.getTemplate("assistant-a")?.id)
        assertEquals("assistant-a-doc", dao.getDocument("assistant-a-doc")?.id)
        assertEquals("malformed-global", dao.getTemplate("malformed-global")?.id)
        assertEquals("malformed-global-doc", dao.getDocument("malformed-global-doc")?.id)

        assertEquals(1, dao.deleteEffectiveTemplateAndDocuments("assistant-a", "assistant-a"))
        assertEquals(null, dao.getTemplate("assistant-a"))
        assertEquals(null, dao.getDocument("assistant-a-doc"))
        assertEquals(null, dao.getDocumentIncludingDeleted("assistant-a-deleted-doc"))
        assertEquals(0, snapshotDao.countSnapshots("assistant-a-doc"))
        assertEquals(0, snapshotDao.countSnapshots("assistant-a-deleted-doc"))
        assertEquals(1, snapshotDao.countSnapshots("unrelated-doc"))
    }

    @Test
    fun softDeleteRestoreAndPurgePreserveRequiredState() = runBlocking {
        val original = document(
            id = "doc",
            scopeType = "ASSISTANT",
            scopeId = "assistant-a",
            updatedAt = 200,
            payloadJson = """{"facts":[{"key":"name"}]}""",
            revision = 7,
            createdAt = 100,
            sourceDocumentId = "source-doc",
            followSource = true,
        )
        dao.upsertDocument(original)
        snapshotDao.upsertSnapshot(snapshot("snapshot-1", "doc", 5, payloadJson = "old-1"))
        snapshotDao.upsertSnapshot(snapshot("snapshot-2", "doc", 6, payloadJson = "old-2"))
        snapshotDao.upsertSnapshot(snapshot("unrelated", "other", 0))

        assertEquals(1, dao.softDeleteDocument("doc", 300, "user_ui"))
        assertEquals(0, dao.softDeleteDocument("doc", 400, "memory_table_tool"))
        val deleted = dao.getDocumentIncludingDeleted("doc")
        assertEquals(300L, deleted?.deletedAt)
        assertEquals("user_ui", deleted?.deletedBy)
        assertEquals(2, snapshotDao.countSnapshots("doc"))

        assertEquals(1, dao.restoreDocument("doc"))
        assertEquals(0, dao.restoreDocument("doc"))
        assertEquals(original, dao.getDocument("doc"))
        assertEquals(2, snapshotDao.countSnapshots("doc"))

        assertEquals(1, dao.softDeleteDocument("doc", 500, "user_ui"))
        assertEquals(1, dao.deleteDocumentAndSnapshots("doc"))
        assertEquals(null, dao.getDocumentIncludingDeleted("doc"))
        assertEquals(0, snapshotDao.countSnapshots("doc"))
        assertEquals(1, snapshotDao.countSnapshots("other"))
    }

    @Test
    fun assistantAndConversationCleanupDeleteActiveTrashAndSnapshots() = runBlocking {
        conversationDao.insert(conversation("conversation-a", "assistant-a"))
        conversationDao.insert(conversation("conversation-b", "assistant-b"))
        dao.upsertTemplate(template("global-template", "GLOBAL", "__global__", updatedAt = 10))
        listOf(
            document(
                "conversation-a-active",
                "CONVERSATION",
                "conversation-a",
                updatedAt = 40,
                templateId = "global-template",
            ),
            document(
                "conversation-a-deleted",
                "CONVERSATION",
                "conversation-a",
                updatedAt = 30,
                templateId = "global-template",
                deletedAt = 30,
                deletedBy = "user_ui",
            ),
            document(
                "conversation-b-deleted",
                "CONVERSATION",
                "conversation-b",
                updatedAt = 20,
                templateId = "global-template",
                deletedAt = 20,
                deletedBy = "user_ui",
            ),
            document(
                "global-active",
                "GLOBAL",
                "__global__",
                updatedAt = 10,
                templateId = "global-template",
            ),
        ).forEach { document ->
            dao.upsertDocument(document)
            snapshotDao.upsertSnapshot(snapshot("snapshot-${document.id}", document.id, 0))
        }

        dao.deleteMemoryTableDataOwnedByAssistant("assistant-a")
        assertEquals(null, dao.getDocumentIncludingDeleted("conversation-a-active"))
        assertEquals(null, dao.getDocumentIncludingDeleted("conversation-a-deleted"))
        assertEquals(0, snapshotDao.countSnapshots("conversation-a-active"))
        assertEquals(0, snapshotDao.countSnapshots("conversation-a-deleted"))
        assertEquals("conversation-b-deleted", dao.getDocumentIncludingDeleted("conversation-b-deleted")?.id)
        assertEquals("global-active", dao.getDocument("global-active")?.id)
        assertEquals("global-template", dao.getTemplate("global-template")?.id)

        dao.deleteMemoryTableDataForConversation("conversation-b")
        assertEquals(null, dao.getDocumentIncludingDeleted("conversation-b-deleted"))
        assertEquals(0, snapshotDao.countSnapshots("conversation-b-deleted"))
        assertEquals("global-active", dao.getDocument("global-active")?.id)
    }

    @Test
    fun effectiveTemplateUpdateMovesScopeAndPreservesDocuments() = runBlocking {
        dao.upsertTemplate(template("global", "GLOBAL", "__global__", updatedAt = 30))
        dao.upsertDocument(
            document(
                id = "global-doc",
                scopeType = "GLOBAL",
                scopeId = "global",
                updatedAt = 30,
                templateId = "global",
            )
        )

        assertEquals(
            1,
            dao.updateEffectiveTemplateFields(
                id = "global",
                assistantId = "assistant-a",
                name = "Moved",
                description = "private",
                schemaJson = """{"tables":[{"name":"facts","columns":[{"name":"key"}]}]}""",
                scopeType = "ASSISTANT",
                scopeId = "assistant-a",
                updatedAt = 40,
            ),
        )
        assertEquals(0, dao.updateEffectiveTemplateFields(
            id = "global",
            assistantId = "assistant-b",
            name = "Stolen",
            description = "",
            schemaJson = "{}",
            scopeType = "GLOBAL",
            scopeId = "__global__",
            updatedAt = 50,
        ))

        val moved = dao.getTemplate("global")
        assertEquals("Moved", moved?.name)
        assertEquals("ASSISTANT", moved?.scopeType)
        assertEquals("assistant-a", moved?.scopeId)
        assertEquals("GLOBAL", dao.getDocument("global-doc")?.scopeType)
        assertEquals("global", dao.getDocument("global-doc")?.scopeId)
        assertEquals("global", dao.getDocument("global-doc")?.templateId)
    }

    @Test
    fun effectiveDocumentByIdRejectsKnownForeignId() = runBlocking {
        dao.upsertDocument(document("doc-a", "ASSISTANT", "assistant-a", updatedAt = 10))

        assertEquals("doc-a", dao.getEffectiveDocument("doc-a", "assistant-a", null)?.id)
        assertEquals(null, dao.getEffectiveDocument("doc-a", "assistant-b", null))
    }

    @Test
    fun payloadCasAllowsOnlyOneWriterForExpectedActiveRevision() = runBlocking {
        dao.upsertDocument(
            document(
                id = "cas-doc",
                scopeType = "ASSISTANT",
                scopeId = "assistant-a",
                updatedAt = 10,
                revision = 5,
                payloadJson = """{"value":"old"}""",
            )
        )

        assertEquals(1, updatePayloadCas("cas-doc", 5, "assistant-a", null, "first", 20))
        assertEquals(0, updatePayloadCas("cas-doc", 5, "assistant-a", null, "second", 30))
        val stored = dao.getDocument("cas-doc")
        assertEquals(6, stored?.revision)
        assertEquals("first", stored?.payloadJson)
        assertEquals(20L, stored?.updatedAt)
    }

    @Test
    fun payloadCasRejectsDeletedGlobalForeignAndWrongConversationTargets() = runBlocking {
        dao.upsertDocument(
            document(
                id = "deleted",
                scopeType = "ASSISTANT",
                scopeId = "assistant-a",
                updatedAt = 10,
                deletedAt = 11,
                deletedBy = "user_ui",
            )
        )
        dao.upsertDocument(document("global", "GLOBAL", "__global__", updatedAt = 10))
        dao.upsertDocument(document("foreign", "ASSISTANT", "assistant-b", updatedAt = 10))
        dao.upsertDocument(document("conversation", "CONVERSATION", "conversation-a", updatedAt = 10))

        assertEquals(0, updatePayloadCas("deleted", 0, "assistant-a", null, "changed", 20))
        assertEquals(0, updatePayloadCas("global", 0, "assistant-a", null, "changed", 20))
        assertEquals(0, updatePayloadCas("foreign", 0, "assistant-a", null, "changed", 20))
        assertEquals(0, updatePayloadCas("conversation", 0, "assistant-a", "conversation-b", "changed", 20))
        assertEquals(1, updatePayloadCas("conversation", 0, "assistant-a", "conversation-a", "changed", 20))
    }

    private suspend fun updatePayloadCas(
        id: String,
        expectedRevision: Int,
        assistantId: String,
        conversationId: String?,
        payloadJson: String,
        updatedAt: Long,
    ): Int {
        val document = dao.getDocumentIncludingDeleted(id) ?: error("missing test document $id")
        return dao.updateDocumentPayloadCas(
            id = id,
            expectedRevision = expectedRevision,
            expectedTemplateId = document.templateId,
            expectedScopeType = document.scopeType,
            expectedScopeId = document.scopeId,
            assistantId = assistantId,
            conversationId = conversationId,
            payloadJson = payloadJson,
            updatedAt = updatedAt,
        )
    }

    private fun document(
        id: String,
        scopeType: String,
        scopeId: String,
        updatedAt: Long,
        templateId: String = "template",
        payloadJson: String = "{}",
        revision: Int = 0,
        createdAt: Long = 1,
        sourceDocumentId: String? = null,
        followSource: Boolean = false,
        deletedAt: Long? = null,
        deletedBy: String? = null,
    ) = MemoryTableDocumentEntity(
        id = id,
        templateId = templateId,
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = payloadJson,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sourceDocumentId = sourceDocumentId,
        followSource = followSource,
        deletedAt = deletedAt,
        deletedBy = deletedBy,
    )

    private fun template(
        id: String,
        scopeType: String,
        scopeId: String,
        updatedAt: Long,
    ) = MemoryTableTemplateEntity(
        id = id,
        name = id,
        description = "",
        schemaJson = """{"tables":[{"name":"facts","columns":[{"name":"key"}]}]}""",
        scopeType = scopeType,
        scopeId = scopeId,
        createdAt = 1,
        updatedAt = updatedAt,
    )

    private fun snapshot(
        id: String,
        documentId: String,
        revision: Int,
        payloadJson: String = "{}",
        createdAt: Long = revision.toLong(),
    ) = MemoryTableSnapshotEntity(
        id = id,
        documentId = documentId,
        revision = revision,
        payloadJson = payloadJson,
        createdAt = createdAt,
    )

    private fun conversation(id: String, assistantId: String) = ConversationEntity(
        id = id,
        assistantId = assistantId,
        title = id,
        nodes = "[]",
        createAt = 1,
        updateAt = 1,
        chatSuggestions = "[]",
        isPinned = false,
    )
}

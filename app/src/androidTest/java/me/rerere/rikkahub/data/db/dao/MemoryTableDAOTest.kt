package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
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

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.memoryTableDao()
        snapshotDao = database.memoryTableSnapshotDao()
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
        assertEquals(0, snapshotDao.countSnapshots("assistant-a-doc"))
        assertEquals(1, snapshotDao.countSnapshots("unrelated-doc"))
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

    private fun document(
        id: String,
        scopeType: String,
        scopeId: String,
        updatedAt: Long,
        templateId: String = "template",
    ) = MemoryTableDocumentEntity(
        id = id,
        templateId = templateId,
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = "{}",
        revision = 0,
        createdAt = 1,
        updatedAt = updatedAt,
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
    ) = MemoryTableSnapshotEntity(
        id = id,
        documentId = documentId,
        revision = revision,
        payloadJson = "{}",
        createdAt = revision.toLong(),
    )
}

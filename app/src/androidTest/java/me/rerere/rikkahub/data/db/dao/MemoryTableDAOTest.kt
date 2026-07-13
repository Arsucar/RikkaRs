package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.MemoryTableDocumentEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryTableDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MemoryTableDAO

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.memoryTableDao()
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

    private fun document(
        id: String,
        scopeType: String,
        scopeId: String,
        updatedAt: Long,
    ) = MemoryTableDocumentEntity(
        id = id,
        templateId = "template",
        scopeType = scopeType,
        scopeId = scopeId,
        payloadJson = "{}",
        revision = 0,
        createdAt = 1,
        updatedAt = updatedAt,
    )
}

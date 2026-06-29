package me.rerere.rikkahub.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Repository archive flip methods do not touch FTS; seeding uses DAO only so tests avoid
 * [ConversationRepository.insertConversation] / [ConversationRepository.updateConversation] (FTS).
 *
 * Auto-unarchive on content change is covered by JVM [ConversationRepositoryAutoUnarchiveTest].
 */
@RunWith(AndroidJUnit4::class)
class ConversationRepositoryArchiveInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ConversationRepository

    private val assistantId = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ConversationRepository(
            conversationDAO = database.conversationDao(),
            messageNodeDAO = database.messageNodeDao(),
            favoriteDAO = database.favoriteDao(),
            database = database,
            filesManager = FilesManager(
                context = context,
                repository = FilesRepository(database.managedFileDao()),
                appScope = AppScope(),
            ),
            messageFtsManager = MessageFtsManager(database),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun archiveConversation_setsArchivedFlags() = runBlocking {
        val id = Uuid.random()
        seedConversationEntity(id, updateAt = Instant.parse("2023-05-10T09:00:00Z"))

        repository.archiveConversation(id)

        val entity = database.conversationDao().getConversationById(id.toString())!!
        assertTrue(entity.isArchived)
        assertTrue(entity.archivedAt > 0L)

        val loaded = repository.getConversationById(id)
        assertNotNull(loaded)
        assertTrue(loaded!!.isArchived)
        assertNotNull(loaded.archivedAt)
    }

    @Test
    fun unarchiveConversation_doesNotChangeUpdateAt() = runBlocking {
        val id = Uuid.random()
        val fixedUpdateAt = Instant.parse("2023-05-10T09:00:00Z")
        seedConversationEntity(id, updateAt = fixedUpdateAt)
        repository.archiveConversation(id)

        repository.unarchiveConversation(id)

        val entity = database.conversationDao().getConversationById(id.toString())!!
        assertFalse(entity.isArchived)
        assertEquals(0L, entity.archivedAt)
        assertEquals(fixedUpdateAt.toEpochMilli(), entity.updateAt)

        val loaded = repository.getConversationById(id)!!
        assertFalse(loaded.isArchived)
        assertNull(loaded.archivedAt)
        assertEquals(fixedUpdateAt, loaded.updateAt)
    }

    private suspend fun seedConversationEntity(id: Uuid, updateAt: Instant) {
        val idStr = id.toString()
        val createAt = Instant.parse("2023-05-09T09:00:00Z")
        val message = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("seed")),
        )
        database.conversationDao().insert(
            ConversationEntity(
                id = idStr,
                assistantId = assistantId,
                title = "Repo archive test",
                nodes = "[]",
                createAt = createAt.toEpochMilli(),
                updateAt = updateAt.toEpochMilli(),
                chatSuggestions = "[]",
                isPinned = false,
            ),
        )
        database.messageNodeDao().insert(
            MessageNodeEntity(
                id = Uuid.random().toString(),
                conversationId = idStr,
                nodeIndex = 0,
                messages = JsonInstant.encodeToString(listOf(message)),
                selectIndex = 0,
            ),
        )
    }
}
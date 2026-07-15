package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.room.withTransaction
import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.ConversationTagCrossRef
import me.rerere.rikkahub.data.db.entity.ConversationTagEntity
import me.rerere.rikkahub.data.repository.ConversationFilter
import me.rerere.rikkahub.data.repository.LightConversationEntity
import me.rerere.rikkahub.data.repository.buildConversationFilterQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ConversationTagForkTransactionTest {
    private lateinit var database: AppDatabase
    private lateinit var conversationDao: ConversationDAO
    private lateinit var tagDao: ConversationTagDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        conversationDao = database.conversationDao()
        tagDao = database.conversationTagDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun forkCopiesEveryRelationAndSubsequentChangesAreIndependent() = runBlocking {
        val sourceId = Uuid.random().toString()
        val forkId = Uuid.random().toString()
        val tags = listOf(tag("review"), tag("done"))
        conversationDao.insert(conversation(sourceId))
        tags.forEach { tagDao.insertTagIgnore(it) }
        tags.forEach { tagDao.insertRelationIgnore(ConversationTagCrossRef(sourceId, it.id)) }

        database.withTransaction {
            conversationDao.insert(conversation(forkId))
            tagDao.copyRelationsForFork(sourceId, forkId)
        }

        assertEquals(tags.map { it.id }.toSet(), tagDao.getTagsForConversation(forkId).map { it.id }.toSet())
        tagDao.deleteRelation(forkId, tags.first().id)
        assertEquals(2, tagDao.countTagsForConversation(sourceId))
        assertEquals(1, tagDao.countTagsForConversation(forkId))
    }

    @Test
    fun failureAfterRelationCopyRollsBackForkAndRelations() = runBlocking {
        val sourceId = Uuid.random().toString()
        val forkId = Uuid.random().toString()
        val tag = tag("rollback")
        conversationDao.insert(conversation(sourceId))
        tagDao.insertTagIgnore(tag)
        tagDao.insertRelationIgnore(ConversationTagCrossRef(sourceId, tag.id))

        runCatching {
            database.withTransaction {
                conversationDao.insert(conversation(forkId))
                tagDao.copyRelationsForFork(sourceId, forkId)
                error("injected fork failure")
            }
        }

        assertFalse(conversationDao.existsById(forkId))
        assertEquals(0, tagDao.countTagsForConversation(forkId))
        assertTrue(tagDao.relationExists(sourceId, tag.id))
    }

    @Test
    fun pagingUsesTagOrAndAssistantAndWithoutDuplicates() = runBlocking {
        val assistantId = Uuid.random()
        val otherAssistantId = Uuid.random()
        val first = conversation(Uuid.random().toString(), assistantId.toString(), "alpha")
        val second = conversation(Uuid.random().toString(), assistantId.toString(), "beta")
        val other = conversation(Uuid.random().toString(), otherAssistantId.toString(), "other")
        listOf(first, second, other).forEach { conversationDao.insert(it) }
        val tagA = tag("a")
        val tagB = tag("b")
        listOf(tagA, tagB).forEach { tagDao.insertTagIgnore(it) }
        tagDao.insertRelationIgnore(ConversationTagCrossRef(first.id, tagA.id))
        tagDao.insertRelationIgnore(ConversationTagCrossRef(first.id, tagB.id))
        tagDao.insertRelationIgnore(ConversationTagCrossRef(second.id, tagB.id))
        tagDao.insertRelationIgnore(ConversationTagCrossRef(other.id, tagA.id))

        val source = conversationDao.getConversationsPaging(
            buildConversationFilterQuery(
                ConversationFilter(
                    assistantId = assistantId,
                    tagIds = setOf(Uuid.parse(tagA.id), Uuid.parse(tagB.id)),
                )
            )
        )
        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))
        val page = result as PagingSource.LoadResult.Page<Int, LightConversationEntity>

        assertEquals(setOf(first.id, second.id), page.data.map { it.id }.toSet())
        assertEquals(2, page.data.size)
    }

    private fun tag(name: String) = ConversationTagEntity(
        id = Uuid.random().toString(),
        normalizedName = name,
        displayName = name,
        colorKey = "blue",
        createdAt = 1,
        updatedAt = 1,
    )

    private fun conversation(
        id: String,
        assistantId: String = Uuid.random().toString(),
        title: String = id,
    ) = ConversationEntity(
        id = id,
        assistantId = assistantId,
        title = title,
        nodes = "[]",
        createAt = 1,
        updateAt = 1,
        chatSuggestions = "[]",
        isPinned = false,
    )
}

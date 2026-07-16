package me.rerere.rikkahub.data.db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class Migration_39_40_Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate39To40_keepsAllConversationsAndRelationsWhileRemovingArchiveColumns() {
        val name = "migration-test-39-40"
        val ordinaryConversationId = Uuid.random().toString()
        val conversationId = Uuid.random().toString()
        val assistantId = Uuid.random().toString()
        val chatModelId = Uuid.random().toString()
        val folderId = Uuid.random().toString()
        val modeInjectionIds = "[\"${Uuid.random()}\"]"
        val lorebookIds = "[\"${Uuid.random()}\"]"
        val tagId = Uuid.random().toString()
        val nodeId = Uuid.random().toString()
        helper.createDatabase(name, 39).apply {
            insert(
                "conversationentity",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", ordinaryConversationId)
                    put("assistant_id", Uuid.random().toString())
                    put("title", "Ordinary")
                    put("nodes", "[]")
                    put("create_at", 50L)
                    put("update_at", 60L)
                    put("is_archived", 0)
                    put("archived_at", 0L)
                },
            )
            insert(
                "conversationentity",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", conversationId)
                    put("assistant_id", assistantId)
                    put("chat_model_id", chatModelId)
                    put("title", "Previously archived")
                    put("nodes", "[]")
                    put("create_at", 100L)
                    put("update_at", 200L)
                    put("suggestions", "[\"keep\"]")
                    put("is_pinned", 1)
                    put("custom_system_prompt", "keep")
                    put("mode_injection_ids", modeInjectionIds)
                    put("lorebook_ids", lorebookIds)
                    put("workspace_cwd", "/workspace")
                    put("is_archived", 1)
                    put("archived_at", 300L)
                    put("folder_id", folderId)
                    put("memory_table_isolation", 1)
                },
            )
            insert(
                "conversation_tags",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", tagId)
                    put("normalized_name", "keep")
                    put("display_name", "Keep")
                    put("color_key", "blue")
                    put("created_at", 100L)
                    put("updated_at", 100L)
                },
            )
            insert(
                "conversation_tag_cross_ref",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("conversation_id", conversationId)
                    put("tag_id", tagId)
                },
            )
            insert(
                "message_node",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", nodeId)
                    put("conversation_id", conversationId)
                    put("node_index", 0)
                    put("messages", "[]")
                    put("select_index", 0)
                    put("hidden", 0)
                },
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 40, true)
        db.query("SELECT * FROM conversationentity LIMIT 0").use { cursor ->
            val columns = cursor.columnNames.toList()
            assertFalse(columns.contains("is_archived"))
            assertFalse(columns.contains("archived_at"))
        }
        db.query("SELECT id FROM conversationentity ORDER BY id").use { cursor ->
            val ids = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertEquals(setOf(ordinaryConversationId, conversationId), ids.toSet())
        }
        db.query(
            """
            SELECT assistant_id, chat_model_id, title, create_at, update_at, suggestions, is_pinned,
                   custom_system_prompt, mode_injection_ids, lorebook_ids, workspace_cwd, folder_id,
                   memory_table_isolation
            FROM conversationentity WHERE id = ?
            """.trimIndent(),
            arrayOf(conversationId),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(assistantId, cursor.getString(0))
            assertEquals(chatModelId, cursor.getString(1))
            assertEquals("Previously archived", cursor.getString(2))
            assertEquals(100L, cursor.getLong(3))
            assertEquals(200L, cursor.getLong(4))
            assertEquals("[\"keep\"]", cursor.getString(5))
            assertEquals(1, cursor.getInt(6))
            assertEquals("keep", cursor.getString(7))
            assertEquals(modeInjectionIds, cursor.getString(8))
            assertEquals(lorebookIds, cursor.getString(9))
            assertEquals("/workspace", cursor.getString(10))
            assertEquals(folderId, cursor.getString(11))
            assertEquals(1, cursor.getInt(12))
        }
        db.query(
            "SELECT COUNT(*) FROM conversation_tag_cross_ref WHERE conversation_id = ?",
            arrayOf(conversationId),
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query(
            "SELECT id, messages FROM message_node WHERE conversation_id = ?",
            arrayOf(conversationId),
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(nodeId, it.getString(0))
            assertEquals("[]", it.getString(1))
        }
        db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        db.close()
    }
}

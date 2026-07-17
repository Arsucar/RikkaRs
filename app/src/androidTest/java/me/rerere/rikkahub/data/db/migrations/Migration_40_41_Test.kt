package me.rerere.rikkahub.data.db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_40_41_Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate40To41_preservesDocumentAndSnapshotAndAddsTrashColumns() {
        val name = "migration-test-40-41"
        val documentId = "document-40"
        val payload = """{"rows":[{"key":"current"}]}"""
        val snapshotPayload = """{"rows":[{"key":"previous"}]}"""
        helper.createDatabase(name, 40).apply {
            insert(
                "memory_table_documents",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", documentId)
                    put("template_id", "template-40")
                    put("scope_type", "ASSISTANT")
                    put("scope_id", "assistant-40")
                    put("payload_json", payload)
                    put("revision", 7)
                    put("created_at", 100L)
                    put("updated_at", 200L)
                    put("source_document_id", "source-document-40")
                    put("follow_source", 1)
                },
            )
            insert(
                "memory_table_snapshots",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "snapshot-40")
                    put("document_id", documentId)
                    put("revision", 6)
                    put("payload_json", snapshotPayload)
                    put("created_at", 150L)
                },
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 41, true)
        db.query(
            """
            SELECT template_id, scope_type, scope_id, payload_json, revision, created_at, updated_at,
                   source_document_id, follow_source, deleted_at, deleted_by
            FROM memory_table_documents WHERE id = ?
            """.trimIndent(),
            arrayOf(documentId),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("template-40", cursor.getString(0))
            assertEquals("ASSISTANT", cursor.getString(1))
            assertEquals("assistant-40", cursor.getString(2))
            assertEquals(payload, cursor.getString(3))
            assertEquals(7, cursor.getInt(4))
            assertEquals(100L, cursor.getLong(5))
            assertEquals(200L, cursor.getLong(6))
            assertEquals("source-document-40", cursor.getString(7))
            assertEquals(1, cursor.getInt(8))
            assertTrue(cursor.isNull(9))
            assertTrue(cursor.isNull(10))
        }
        db.query(
            "SELECT document_id, revision, payload_json, created_at FROM memory_table_snapshots WHERE id = ?",
            arrayOf("snapshot-40"),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(documentId, cursor.getString(0))
            assertEquals(6, cursor.getInt(1))
            assertEquals(snapshotPayload, cursor.getString(2))
            assertEquals(150L, cursor.getLong(3))
        }
        db.query("PRAGMA table_info(memory_table_documents)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val nullableColumns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                if (cursor.getInt(notNullIndex) == 0) nullableColumns += cursor.getString(nameIndex)
            }
            assertTrue("deleted_at" in nullableColumns)
            assertTrue("deleted_by" in nullableColumns)
        }
        db.query("PRAGMA index_list(memory_table_documents)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val indexes = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
            assertTrue("index_memory_table_documents_deleted_at" in indexes)
        }
        db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        db.close()
    }
}

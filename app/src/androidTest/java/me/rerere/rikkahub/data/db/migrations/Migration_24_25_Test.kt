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
import java.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class Migration_24_25_Test {
    private val testDb = "migration-test-24-25"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate24To25_addsArchivedAtColumnWithDefault() {
        val conversationId = Uuid.random().toString()
        val now = Instant.now().toEpochMilli()

        helper.createDatabase(testDb, 24).apply {
            val values = ContentValues().apply {
                put("id", conversationId)
                put("assistant_id", Uuid.random().toString())
                put("title", "Archived on v24")
                put("nodes", "[]")
                put("create_at", now)
                put("update_at", now)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("custom_system_prompt", "")
                put("mode_injection_ids", "[]")
                put("lorebook_ids", "[]")
                put("workspace_cwd", "")
                put("is_archived", 1)
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, values)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 25, true)

        val cursor = db.query("SELECT * FROM conversationentity LIMIT 0")
        val columnNames = cursor.columnNames.toList()
        cursor.close()

        assertTrue(columnNames.contains("archived_at"))

        val rowCursor = db.query(
            "SELECT is_archived, archived_at FROM conversationentity WHERE id = ?",
            arrayOf(conversationId),
        )
        assertTrue(rowCursor.moveToFirst())
        assertEquals(1, rowCursor.getInt(0))
        assertEquals(0L, rowCursor.getLong(1))
        rowCursor.close()

        db.close()
    }
}
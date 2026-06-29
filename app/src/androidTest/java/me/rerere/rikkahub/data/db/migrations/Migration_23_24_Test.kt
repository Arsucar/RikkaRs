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
import java.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class Migration_23_24_Test {
    private val testDb = "migration-test-23-24"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate23To24_addsIsArchivedColumnWithDefault() {
        val conversationId = Uuid.random().toString()
        val now = Instant.now().toEpochMilli()

        helper.createDatabase(testDb, 23).apply {
            val values = ContentValues().apply {
                put("id", conversationId)
                put("assistant_id", Uuid.random().toString())
                put("title", "Pre-migration conversation")
                put("nodes", "[]")
                put("create_at", now)
                put("update_at", now)
                put("suggestions", "[]")
                put("is_pinned", 0)
                put("custom_system_prompt", "")
                put("mode_injection_ids", "[]")
                put("lorebook_ids", "[]")
                put("workspace_cwd", "")
            }
            insert("conversationentity", SQLiteDatabase.CONFLICT_NONE, values)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 24, true, Migration_23_24)

        val cursor = db.query("SELECT * FROM conversationentity LIMIT 0")
        val columnNames = cursor.columnNames.toList()
        cursor.close()

        assertTrue(columnNames.contains("is_archived"))
        assertFalse("v24 schema should not have archived_at yet", columnNames.contains("archived_at"))

        val rowCursor = db.query(
            "SELECT is_archived FROM conversationentity WHERE id = ?",
            arrayOf(conversationId),
        )
        assertTrue(rowCursor.moveToFirst())
        assertEquals(0, rowCursor.getInt(0))
        rowCursor.close()

        db.close()
    }
}
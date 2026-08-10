package me.rerere.rikkahub.data.db.migrations

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
class Migration_50_51_Test {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationAddsTrustedWriteRootsColumn() {
        val databaseName = "migration-test-50-51"
        helper.createDatabase(databaseName, 50).use { db ->
            // Ensure workspaces table exists at v50 (created by prior migrations / schema).
            // If empty schema helpers only create tables for entities at that version via Room.
        }

        val db = helper.runMigrationsAndValidate(databaseName, 51, true, Migration_50_51)
        db.query("PRAGMA table_info(workspaces)").use { cursor ->
            val names = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                names.add(cursor.getString(nameIndex))
            }
            assertTrue(names.contains("trusted_write_roots"))
        }
        // Default for new column on existing rows
        db.query("SELECT trusted_write_roots FROM workspaces LIMIT 1").use { cursor ->
            if (cursor.moveToFirst()) {
                assertEquals("[]", cursor.getString(0))
            }
        }
        db.close()
    }
}

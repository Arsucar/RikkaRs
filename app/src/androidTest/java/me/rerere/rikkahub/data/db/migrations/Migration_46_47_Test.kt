package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_46_47_Test {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationCreatesApiCallRecordsTable() {
        val databaseName = "migration-test-46-47"
        helper.createDatabase(databaseName, 46).close()

        val db = helper.runMigrationsAndValidate(databaseName, 47, true, Migration_46_47)
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='api_call_records'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.query("PRAGMA table_info(api_call_records)").use { cursor ->
            val names = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                names.add(cursor.getString(nameIndex))
            }
            assertTrue(names.containsAll(
                listOf(
                    "id",
                    "provider_id",
                    "model_id",
                    "request_at",
                    "status",
                    "error_type",
                    "latency_ms",
                ),
            ))
        }
        db.close()
    }
}

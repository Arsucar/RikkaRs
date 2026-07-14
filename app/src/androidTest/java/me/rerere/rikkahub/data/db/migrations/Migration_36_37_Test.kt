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
class Migration_36_37_Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate36To37AddsSubagentContextStore() {
        val name = "migration-test-36-37"
        helper.createDatabase(name, 36).close()

        val db = helper.runMigrationsAndValidate(name, 37, true, Migration_36_37)
        db.query("PRAGMA table_info(subagent_contexts)").use { cursor ->
            val columns = buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertTrue("context_id" in columns)
            assertTrue("messages_json" in columns)
            assertTrue("revision" in columns)
            assertTrue("expires_at" in columns)
        }
        db.close()
    }
}

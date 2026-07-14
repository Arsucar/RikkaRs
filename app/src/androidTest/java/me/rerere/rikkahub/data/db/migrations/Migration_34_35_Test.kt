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
class Migration_34_35_Test {
    private val testDb = "migration-test-34-35"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate34To35AddsNullableCompressHiddenCount() {
        helper.createDatabase(testDb, 34).close()

        val db = helper.runMigrationsAndValidate(testDb, 35, true, Migration_34_35)
        val cursor = db.query("PRAGMA table_info(message_node)")
        var found = false
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (name == "compress_hidden_count") {
                found = true
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
            }
        }
        cursor.close()
        db.close()

        assertTrue(found)
    }
}

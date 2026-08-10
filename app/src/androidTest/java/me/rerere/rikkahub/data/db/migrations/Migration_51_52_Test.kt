package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_51_52_Test {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationDropsModeInjectionIdsColumn() {
        val databaseName = "migration-test-51-52"
        helper.createDatabase(databaseName, 51).use { db ->
            // v51 schema includes mode_injection_ids; seed a row if table exists.
            runCatching {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO ConversationEntity (
                        id, assistant_id, title, nodes, create_at, update_at
                    ) VALUES ('c1', 'a1', 't', '[]', 1, 1)
                    """.trimIndent(),
                )
            }
        }

        val db = helper.runMigrationsAndValidate(databaseName, 52, true, Migration_51_52)
        db.query("PRAGMA table_info(ConversationEntity)").use { cursor ->
            val names = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                names.add(cursor.getString(nameIndex))
            }
            assertFalse(names.contains("mode_injection_ids"))
            assertTrue(names.contains("lorebook_ids"))
            assertTrue(names.contains("variables"))
        }
        db.close()
    }
}

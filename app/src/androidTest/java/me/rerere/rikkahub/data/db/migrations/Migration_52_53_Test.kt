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
class Migration_52_53_Test {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration52To53_addsConversationEntityCompositeIndexes() {
        val databaseName = "migration-test-52-53"
        helper.createDatabase(databaseName, 52).use { db ->
            // Seed a row so the index is created over real data; index presence is
            // verified separately via sqlite_master below.
            db.execSQL(
                """
                INSERT OR IGNORE INTO ConversationEntity (
                    id, assistant_id, title, nodes, create_at, update_at
                ) VALUES ('c1', 'a1', 't', '[]', 1, 1)
                """.trimIndent(),
            )
        }

        // runMigrationsAndValidate re-runs the migration and validates that the
        // resulting schema (including index names/DDL) exactly matches Room's
        // expected v53 schema derived from the @Database/@Entity annotations.
        val db = helper.runMigrationsAndValidate(databaseName, 53, true, Migration_52_53)

        val indexNames = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'ConversationEntity'").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(nameIndex))
            }
        }

        assertTrue(
            "expected index_ConversationEntity_assistant_id_is_pinned_update_at, got $indexNames",
            indexNames.contains("index_ConversationEntity_assistant_id_is_pinned_update_at"),
        )
        assertTrue(
            "expected index_ConversationEntity_folder_id_is_pinned_update_at, got $indexNames",
            indexNames.contains("index_ConversationEntity_folder_id_is_pinned_update_at"),
        )
        assertTrue(
            "expected index_ConversationEntity_is_pinned_update_at, got $indexNames",
            indexNames.contains("index_ConversationEntity_is_pinned_update_at"),
        )
        assertTrue(
            "expected index_ConversationEntity_create_at, got $indexNames",
            indexNames.contains("index_ConversationEntity_create_at"),
        )

        db.close()
    }
}

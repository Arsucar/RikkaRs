package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
class Migration_44_45_Test {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationKeepsExistingNullableColumn() {
        val databaseName = "migration-test-44-45-existing"
        helper.createDatabase(databaseName, 44).close()

        val db = helper.runMigrationsAndValidate(databaseName, 45, true, Migration_44_45)
        assertNullableCompressHiddenCount(db)
        db.close()
    }

    @Test
    fun migrationRepairsVersion44DatabaseWithMissingColumn() {
        val databaseName = "migration-test-44-45-missing"
        helper.createDatabase(databaseName, 44).use { db ->
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL("ALTER TABLE message_node RENAME TO message_node_with_compress_count")
            db.execSQL(
                """
                CREATE TABLE message_node (
                    id TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    node_index INTEGER NOT NULL,
                    messages TEXT NOT NULL,
                    select_index INTEGER NOT NULL,
                    hidden INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(id),
                    FOREIGN KEY(conversation_id) REFERENCES ConversationEntity(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO message_node(id, conversation_id, node_index, messages, select_index, hidden)
                SELECT id, conversation_id, node_index, messages, select_index, hidden
                FROM message_node_with_compress_count
                """.trimIndent()
            )
            db.execSQL("DROP TABLE message_node_with_compress_count")
            db.execSQL(
                "CREATE INDEX index_message_node_conversation_id ON message_node(conversation_id)"
            )
            db.execSQL("PRAGMA foreign_keys=ON")
        }

        val db = helper.runMigrationsAndValidate(databaseName, 45, true, Migration_44_45)
        assertNullableCompressHiddenCount(db)
        db.close()
    }

    private fun assertNullableCompressHiddenCount(db: SupportSQLiteDatabase) {
        db.query("PRAGMA table_info(message_node)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "compress_hidden_count") {
                    found = true
                    assertEquals(0, cursor.getInt(notNullIndex))
                }
            }
            assertTrue(found)
        }
    }
}

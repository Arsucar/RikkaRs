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
class Migration_35_36_Test {
    private val testDb = "migration-test-35-36"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate35To36MakesLegacyTemplatesGlobalAndAddsOwnerIndex() {
        helper.createDatabase(testDb, 35).use { db ->
            db.execSQL(
                """
                INSERT INTO memory_table_templates(
                    id, name, description, schema_json, created_at, updated_at
                ) VALUES(
                    'legacy-template',
                    'Legacy',
                    '',
                    '{"tables":[{"name":"facts","columns":[{"name":"key"}]}]}',
                    1,
                    2
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 36, true, Migration_35_36)
        db.query(
            """
            SELECT scope_type, scope_id
            FROM memory_table_templates
            WHERE id = 'legacy-template'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("GLOBAL", cursor.getString(0))
            assertEquals("__global__", cursor.getString(1))
        }
        db.query("PRAGMA index_list(memory_table_templates)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) ==
                    "index_memory_table_templates_scope_type_scope_id"
                ) {
                    found = true
                }
            }
            assertTrue(found)
        }
        db.close()
    }
}

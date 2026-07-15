package me.rerere.rikkahub.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseIntegrityTest {
    @Test
    fun validDatabasePassesForeignKeyCheck() {
        val file = createDatabase("valid") { database ->
            database.execSQL("INSERT INTO parent(id) VALUES (1)")
            database.execSQL("INSERT INTO child(id, parent_id) VALUES (1, 1)")
        }

        validateRestoredDatabaseForeignKeys(file)
    }

    @Test
    fun orphanedRowIsReportedWithDatabaseLocation() {
        val file = createDatabase("invalid") { database ->
            database.execSQL("INSERT INTO child(id, parent_id) VALUES (7, 99)")
        }

        val error = try {
            validateRestoredDatabaseForeignKeys(file)
            throw AssertionError("Expected restored database integrity validation to fail")
        } catch (error: DatabaseForeignKeyIntegrityException) {
            error
        }

        assertEquals(
            ForeignKeyViolation(
                table = "child",
                rowId = 7,
                parentTable = "parent",
                foreignKeyIndex = 0,
            ),
            error.violations.single(),
        )
    }

    private fun createDatabase(
        suffix: String,
        populate: (SQLiteDatabase) -> Unit,
    ) = InstrumentationRegistry.getInstrumentation().targetContext
        .getDatabasePath("foreign-key-integrity-$suffix-${System.nanoTime()}.db")
        .also { file ->
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
                database.execSQL("PRAGMA foreign_keys = OFF")
                database.execSQL("CREATE TABLE parent(id INTEGER NOT NULL PRIMARY KEY)")
                database.execSQL(
                    """
                    CREATE TABLE child(
                        id INTEGER NOT NULL PRIMARY KEY,
                        parent_id INTEGER NOT NULL,
                        FOREIGN KEY(parent_id) REFERENCES parent(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                populate(database)
            }
        }
}

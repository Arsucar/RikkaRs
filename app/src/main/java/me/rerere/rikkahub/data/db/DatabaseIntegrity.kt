package me.rerere.rikkahub.data.db

import android.database.sqlite.SQLiteDatabase
import java.io.File

data class ForeignKeyViolation(
    val table: String,
    val rowId: Long,
    val parentTable: String,
    val foreignKeyIndex: Int,
)

class DatabaseForeignKeyIntegrityException(
    val violations: List<ForeignKeyViolation>,
) : IllegalStateException(
    buildString {
        append("Restored database failed foreign-key integrity check")
        violations.firstOrNull()?.let { violation ->
            append(": table=${violation.table}, rowId=${violation.rowId}, parent=${violation.parentTable}")
        }
        if (violations.size > 1) append(" (+${violations.size - 1} more)")
    },
)

/**
 * Opens a restored database through an independent connection so WAL contents are visible,
 * then reports every foreign-key violation before the app is restarted onto that database.
 */
fun validateRestoredDatabaseForeignKeys(databaseFile: File) {
    require(databaseFile.isFile) { "Restored database file does not exist: ${databaseFile.absolutePath}" }

    val database = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE,
    )
    val violations = try {
        database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ForeignKeyViolation(
                            table = cursor.getString(0),
                            rowId = cursor.getLong(1),
                            parentTable = cursor.getString(2),
                            foreignKeyIndex = cursor.getInt(3),
                        )
                    )
                }
            }
        }
    } finally {
        database.close()
    }

    if (violations.isNotEmpty()) {
        throw DatabaseForeignKeyIntegrityException(violations)
    }
}

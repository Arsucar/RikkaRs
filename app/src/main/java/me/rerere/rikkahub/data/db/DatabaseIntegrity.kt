package me.rerere.rikkahub.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
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

class DatabaseIntegrityException(
    val errors: List<String>,
) : IllegalStateException(
    "Database failed integrity check: ${errors.firstOrNull() ?: "unknown error"}"
)

/**
 * Opens a database with the same Requery + libsimple stack as production Room.
 * Framework SQLite cannot load FTS5 `tokenize='simple'` tables used by message_fts.
 */
fun openRequeryDatabase(
    context: Context,
    databaseFile: File,
    flags: Int = SQLiteDatabase.OPEN_READONLY,
): SQLiteDatabase {
    require(databaseFile.isFile) { "Database file does not exist: ${databaseFile.absolutePath}" }
    val configuration = SQLiteDatabaseConfiguration(
        databaseFile.absolutePath,
        flags,
    ).apply {
        customExtensions.add(
            SQLiteCustomExtension(
                context.applicationInfo.nativeLibraryDir + "/libsimple",
                null,
            )
        )
    }
    return SQLiteDatabase.openDatabase(configuration, null, null)
}

fun validateDatabaseIntegrity(databaseFile: File, context: Context) {
    val database = openRequeryDatabase(context, databaseFile, SQLiteDatabase.OPEN_READONLY)
    try {
        validateDatabaseIntegrity(database)
    } finally {
        database.close()
    }
}

fun validateDatabaseIntegrity(database: SupportSQLiteDatabase) {
    val errors = database.query("PRAGMA integrity_check").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val result = cursor.getString(0)
                if (result != "ok") add(result)
            }
        }
    }
    if (errors.isNotEmpty()) throw DatabaseIntegrityException(errors)
}

/**
 * Opens a restored database through an independent Requery connection so WAL contents are
 * visible and FTS simple tokenizer is available, then reports every foreign-key violation
 * before the app is restarted onto that database.
 */
fun validateRestoredDatabaseForeignKeys(databaseFile: File, context: Context) {
    val database = openRequeryDatabase(context, databaseFile, SQLiteDatabase.OPEN_READWRITE)
    try {
        val violations = validateDatabaseForeignKeys(database)
        if (violations.isNotEmpty()) throw DatabaseForeignKeyIntegrityException(violations)
    } finally {
        database.close()
    }
}

fun validateDatabaseForeignKeys(database: SupportSQLiteDatabase): List<ForeignKeyViolation> =
    database.query("PRAGMA foreign_key_check").use { cursor ->
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

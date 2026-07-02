package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * Fork v26（无文件夹）与上游 v26（含文件夹）identity hash 不同但版本号同为 26。
 * 升到 27 并为旧库补全 [conversation_folder] 与 [folder_id]。
 */
val Migration_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(26, 27)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversation_folder (
                    id TEXT NOT NULL PRIMARY KEY,
                    assistant_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    sort_index INTEGER NOT NULL DEFAULT 0,
                    create_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_conversation_folder_assistant_id ON conversation_folder (assistant_id)",
            )
            if (!db.hasTableColumn("conversationentity", "folder_id")) {
                db.execSQL(
                    "ALTER TABLE conversationentity ADD COLUMN folder_id TEXT NOT NULL DEFAULT ''",
                )
            }
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

internal fun SupportSQLiteDatabase.hasTableColumn(table: String, column: String): Boolean {
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return false
        while (cursor.moveToNext()) {
            if (column.equals(cursor.getString(nameIndex), ignoreCase = true)) {
                return true
            }
        }
    }
    return false
}
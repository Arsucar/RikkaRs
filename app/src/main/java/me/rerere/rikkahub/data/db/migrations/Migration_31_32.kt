package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

// #96: additive migration that introduces the memory table revision snapshot
// table. It only creates a new table + index and never touches existing data,
// so it is safe for existing users and fully reversible by dropping the table.
val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(31, 32)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memory_table_snapshots (
                    id TEXT NOT NULL PRIMARY KEY,
                    document_id TEXT NOT NULL,
                    revision INTEGER NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_memory_table_snapshots_document_id " +
                    "ON memory_table_snapshots(document_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_memory_table_snapshots_document_id_revision " +
                    "ON memory_table_snapshots(document_id, revision)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_memory_table_snapshots_created_at " +
                    "ON memory_table_snapshots(created_at)"
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

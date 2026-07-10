package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

// #89: additive migration that adds the conversation-level "follow/detach" columns to
// memory_table_documents. It only appends two nullable/defaulted columns and never
// touches existing data, so it is safe for existing users.
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(32, 33)
        try {
            db.execSQL(
                "ALTER TABLE memory_table_documents ADD COLUMN source_document_id TEXT"
            )
            db.execSQL(
                "ALTER TABLE memory_table_documents ADD COLUMN follow_source INTEGER NOT NULL DEFAULT 0"
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(23, 24)
        try {
            db.execSQL(
                "ALTER TABLE conversationentity ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0",
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
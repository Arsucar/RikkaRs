package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(24, 25)
        try {
            db.execSQL(
                "ALTER TABLE conversationentity ADD COLUMN archived_at INTEGER NOT NULL DEFAULT 0",
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
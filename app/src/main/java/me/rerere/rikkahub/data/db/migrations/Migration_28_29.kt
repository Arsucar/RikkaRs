package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.repository.MemoryRepository

val Migration_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(28, 29)
        try {
            if (!db.hasTableColumn("memoryentity", "scope")) {
                db.execSQL(
                    "ALTER TABLE memoryentity ADD COLUMN scope TEXT NOT NULL DEFAULT 'ASSISTANT'",
                )
            }
            db.execSQL(
                "UPDATE memoryentity SET scope = 'GLOBAL' WHERE assistant_id = ?",
                arrayOf(MemoryRepository.GLOBAL_MEMORY_ID),
            )
            db.execSQL(
                "UPDATE memoryentity SET scope = 'ASSISTANT' WHERE scope NOT IN ('ASSISTANT', 'GLOBAL')",
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

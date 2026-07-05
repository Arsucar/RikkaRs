package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(27, 28)
        try {
            if (!db.hasTableColumn("conversationentity", "chat_model_id")) {
                db.execSQL(
                    "ALTER TABLE conversationentity ADD COLUMN chat_model_id TEXT NOT NULL DEFAULT ''",
                )
            }
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

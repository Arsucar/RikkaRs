package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(34, 35)
        try {
            db.execSQL("ALTER TABLE message_node ADD COLUMN compress_hidden_count INTEGER")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

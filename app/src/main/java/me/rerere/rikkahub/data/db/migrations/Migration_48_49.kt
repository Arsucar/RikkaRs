package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** #220: mid-generation checkpoint metadata on ConversationEntity. */
object Migration_48_49 : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE ConversationEntity ADD COLUMN checkpoint_step TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL(
            "ALTER TABLE ConversationEntity ADD COLUMN is_checkpoint_snapshot INTEGER NOT NULL DEFAULT 0",
        )
    }
}

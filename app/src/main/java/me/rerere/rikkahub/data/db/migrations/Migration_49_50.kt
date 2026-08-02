package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** #217/#216: conversation variables + per-node branch variable snapshots. */
object Migration_49_50 : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE ConversationEntity ADD COLUMN variables TEXT NOT NULL DEFAULT '{}'",
        )
        db.execSQL(
            "ALTER TABLE message_node ADD COLUMN variable_snapshots TEXT NOT NULL DEFAULT '{}'",
        )
    }
}

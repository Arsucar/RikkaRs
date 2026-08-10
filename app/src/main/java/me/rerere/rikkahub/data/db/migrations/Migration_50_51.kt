package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** #258: per-workspace trusted write directory roots for write/edit hard approval bypass. */
object Migration_50_51 : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE workspaces ADD COLUMN trusted_write_roots TEXT NOT NULL DEFAULT '[]'",
        )
    }
}

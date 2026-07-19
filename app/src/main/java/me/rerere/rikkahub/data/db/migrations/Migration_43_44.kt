package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_43_44 : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE hook_runs ADD COLUMN event_payload_json TEXT NOT NULL DEFAULT '{}'")
    }
}

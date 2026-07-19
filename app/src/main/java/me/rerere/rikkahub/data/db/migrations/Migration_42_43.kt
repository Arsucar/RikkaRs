package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_42_43 : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE hook_runs ADD COLUMN event_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE hook_runs ADD COLUMN event_type TEXT NOT NULL DEFAULT 'FINAL_ASSISTANT_RESPONSE_SUCCESS'")
        db.execSQL("ALTER TABLE hook_runs ADD COLUMN event_schema_version INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE hook_runs ADD COLUMN event_context_id TEXT")
        db.execSQL("ALTER TABLE hook_runs ADD COLUMN event_occurred_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE hook_runs SET event_id = 'legacy:' || logical_turn_id || ':' || trigger, " +
                "event_occurred_at = started_at WHERE event_id = ''"
        )
        db.execSQL("DROP INDEX IF EXISTS index_hook_runs_logical_turn_id_trigger")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_hook_runs_event_id ON hook_runs(event_id)")
    }
}

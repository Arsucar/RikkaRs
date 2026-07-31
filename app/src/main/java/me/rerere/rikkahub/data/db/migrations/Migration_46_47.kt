package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_46_47 : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS api_call_records (
                id TEXT NOT NULL PRIMARY KEY,
                provider_id TEXT NOT NULL,
                provider_name TEXT NOT NULL,
                model_id TEXT NOT NULL,
                model_display_name TEXT NOT NULL,
                request_at INTEGER NOT NULL,
                response_at INTEGER,
                status TEXT NOT NULL,
                latency_ms INTEGER,
                token_input INTEGER,
                token_output INTEGER,
                error_type TEXT,
                error_code TEXT,
                error_message TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_api_call_records_request_at " +
                "ON api_call_records(request_at)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_api_call_records_provider_id_model_id_request_at " +
                "ON api_call_records(provider_id, model_id, request_at)",
        )
    }
}

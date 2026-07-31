package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_45_46 : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_stats (
                conversation_id TEXT NOT NULL PRIMARY KEY,
                message_count INTEGER NOT NULL,
                user_message_count INTEGER NOT NULL,
                token_input INTEGER NOT NULL,
                token_output INTEGER NOT NULL,
                token_cached INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (conversation_id) REFERENCES ConversationEntity(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_stats_conversation_id " +
                "ON message_stats(conversation_id)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_stats_daily (
                conversation_id TEXT NOT NULL,
                day TEXT NOT NULL,
                user_message_count INTEGER NOT NULL,
                PRIMARY KEY (conversation_id, day),
                FOREIGN KEY (conversation_id) REFERENCES ConversationEntity(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_stats_daily_conversation_id " +
                "ON message_stats_daily(conversation_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_message_stats_daily_day " +
                "ON message_stats_daily(day)"
        )
    }
}

// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_47_48 : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS episodic_memory (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                assistant_id TEXT NOT NULL,
                content TEXT NOT NULL,
                summary TEXT NOT NULL,
                importance INTEGER NOT NULL,
                is_core INTEGER NOT NULL,
                embedding TEXT,
                embedding_model TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_recalled_at INTEGER,
                recall_count INTEGER NOT NULL,
                source_conversation_id TEXT,
                source_message_index INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_episodic_memory_assistant_id " +
                "ON episodic_memory(assistant_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_episodic_memory_assistant_id_is_core " +
                "ON episodic_memory(assistant_id, is_core)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS semantic_memory_state (
                assistant_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                last_summarized_message_count INTEGER NOT NULL,
                last_summarized_at INTEGER NOT NULL,
                PRIMARY KEY(assistant_id, conversation_id)
            )
            """.trimIndent(),
        )
    }
}

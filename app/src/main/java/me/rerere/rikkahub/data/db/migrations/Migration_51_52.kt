package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * #259: drop conversation-level mode_injection_ids (independent injection path removed).
 *
 * SQLite before 3.35 lacks DROP COLUMN; recreate ConversationEntity without the column.
 */
object Migration_51_52 : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ConversationEntity_new (
                id TEXT NOT NULL PRIMARY KEY,
                assistant_id TEXT NOT NULL DEFAULT '0950e2dc-9bd5-4801-afa3-aa887aa36b4e',
                chat_model_id TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                nodes TEXT NOT NULL,
                create_at INTEGER NOT NULL,
                update_at INTEGER NOT NULL,
                suggestions TEXT NOT NULL DEFAULT '[]',
                is_pinned INTEGER NOT NULL DEFAULT 0,
                custom_system_prompt TEXT NOT NULL DEFAULT '',
                lorebook_ids TEXT NOT NULL DEFAULT '[]',
                workspace_cwd TEXT NOT NULL DEFAULT '',
                folder_id TEXT NOT NULL DEFAULT '',
                memory_table_isolation INTEGER NOT NULL DEFAULT 0,
                checkpoint_step TEXT NOT NULL DEFAULT '',
                is_checkpoint_snapshot INTEGER NOT NULL DEFAULT 0,
                variables TEXT NOT NULL DEFAULT '{}'
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO ConversationEntity_new (
                id, assistant_id, chat_model_id, title, nodes, create_at, update_at,
                suggestions, is_pinned, custom_system_prompt, lorebook_ids, workspace_cwd,
                folder_id, memory_table_isolation, checkpoint_step, is_checkpoint_snapshot, variables
            )
            SELECT
                id, assistant_id, chat_model_id, title, nodes, create_at, update_at,
                suggestions, is_pinned, custom_system_prompt, lorebook_ids, workspace_cwd,
                folder_id, memory_table_isolation, checkpoint_step, is_checkpoint_snapshot, variables
            FROM ConversationEntity
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE ConversationEntity")
        db.execSQL("ALTER TABLE ConversationEntity_new RENAME TO ConversationEntity")
    }
}

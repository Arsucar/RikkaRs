package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_36_37 : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `subagent_contexts` (
                `context_id` TEXT NOT NULL,
                `conversation_id` TEXT,
                `parent_assistant_id` TEXT NOT NULL,
                `scope_json` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `messages_json` TEXT NOT NULL,
                `usage_json` TEXT,
                `last_error` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `expires_at` INTEGER NOT NULL,
                `revision` INTEGER NOT NULL,
                PRIMARY KEY(`context_id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subagent_contexts_status` ON `subagent_contexts` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subagent_contexts_updated_at` ON `subagent_contexts` (`updated_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subagent_contexts_parent_assistant_id_conversation_id` ON `subagent_contexts` (`parent_assistant_id`, `conversation_id`)")
    }
}

package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * #288: add composite indexes to ConversationEntity.
 *
 * Covers the 13 ConversationDAO queries that ORDER BY is_pinned DESC,
 * update_at DESC with various WHERE clauses, plus create_at range scans.
 * Eliminates full table scans as conversation count grows.
 *
 * Index names MUST match what Room auto-generates from the entity so that
 * schema validation passes. Because ConversationEntity declares no tableName,
 * Room uses the mixed-case class name "ConversationEntity" as the table name,
 * so the generated index name prefix is "index_ConversationEntity_...".
 */
object Migration_52_53 : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ConversationEntity_assistant_id_is_pinned_update_at` ON `ConversationEntity` (`assistant_id`, `is_pinned`, `update_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ConversationEntity_folder_id_is_pinned_update_at` ON `ConversationEntity` (`folder_id`, `is_pinned`, `update_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ConversationEntity_is_pinned_update_at` ON `ConversationEntity` (`is_pinned`, `update_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ConversationEntity_create_at` ON `ConversationEntity` (`create_at`)",
        )
    }
}

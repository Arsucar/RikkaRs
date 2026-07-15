package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_37_38 : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conversation_tags` (
                `id` TEXT NOT NULL,
                `normalized_name` TEXT NOT NULL,
                `display_name` TEXT NOT NULL,
                `color_key` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_conversation_tags_normalized_name` " +
                "ON `conversation_tags` (`normalized_name`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_conversation_tags_created_at` " +
                "ON `conversation_tags` (`created_at`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conversation_tag_cross_ref` (
                `conversation_id` TEXT NOT NULL,
                `tag_id` TEXT NOT NULL,
                PRIMARY KEY(`conversation_id`, `tag_id`),
                FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tag_id`) REFERENCES `conversation_tags`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_conversation_tag_cross_ref_tag_id_conversation_id` " +
                "ON `conversation_tag_cross_ref` (`tag_id`, `conversation_id`)"
        )
    }
}

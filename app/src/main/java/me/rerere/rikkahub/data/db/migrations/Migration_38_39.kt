package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_38_39 : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `generation_logical_turns` (
                `logical_turn_id` TEXT NOT NULL,
                `conversation_id` TEXT NOT NULL,
                `assistant_id` TEXT NOT NULL,
                `source_node_id` TEXT,
                `source_message_id` TEXT,
                `invocation_kind` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `completed_at` INTEGER,
                PRIMARY KEY(`logical_turn_id`),
                FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_generation_logical_turns_conversation_id_status` " +
                "ON `generation_logical_turns` (`conversation_id`, `status`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_generation_logical_turns_updated_at` " +
                "ON `generation_logical_turns` (`updated_at`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `generation_logical_turn_pending_tools` (
                `logical_turn_id` TEXT NOT NULL,
                `tool_call_id` TEXT NOT NULL,
                PRIMARY KEY(`logical_turn_id`, `tool_call_id`),
                FOREIGN KEY(`logical_turn_id`) REFERENCES `generation_logical_turns`(`logical_turn_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_generation_logical_turn_pending_tools_tool_call_id` " +
                "ON `generation_logical_turn_pending_tools` (`tool_call_id`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `hook_runs` (
                `run_id` TEXT NOT NULL,
                `conversation_id` TEXT NOT NULL,
                `assistant_id` TEXT NOT NULL,
                `logical_turn_id` TEXT NOT NULL,
                `node_id` TEXT,
                `message_id` TEXT,
                `message_model_id` TEXT,
                `invocation_kind` TEXT NOT NULL,
                `trigger` TEXT NOT NULL,
                `config_version` INTEGER NOT NULL,
                `config_hash` TEXT NOT NULL,
                `started_at` INTEGER NOT NULL,
                `ended_at` INTEGER,
                `status` TEXT NOT NULL,
                `failure_count` INTEGER NOT NULL,
                PRIMARY KEY(`run_id`),
                FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_hook_runs_logical_turn_id_trigger` " +
                "ON `hook_runs` (`logical_turn_id`, `trigger`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hook_runs_conversation_id_started_at` " +
                "ON `hook_runs` (`conversation_id`, `started_at`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_hook_runs_status` ON `hook_runs` (`status`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `hook_executions` (
                `execution_id` TEXT NOT NULL,
                `run_id` TEXT NOT NULL,
                `hook_id` TEXT NOT NULL,
                `hook_order` INTEGER NOT NULL,
                `hook_config_version` INTEGER NOT NULL,
                `hook_config_hash` TEXT NOT NULL,
                `model_id` TEXT NOT NULL,
                `action_type` TEXT NOT NULL,
                `started_at` INTEGER,
                `ended_at` INTEGER,
                `status` TEXT NOT NULL,
                `decision` TEXT,
                `tag_id` TEXT,
                `reason` TEXT,
                `reason_truncated` INTEGER NOT NULL DEFAULT 0,
                `error_code` TEXT,
                `sanitized_error` TEXT,
                `duration_ms` INTEGER,
                `lease_token` INTEGER NOT NULL,
                PRIMARY KEY(`execution_id`),
                FOREIGN KEY(`run_id`) REFERENCES `hook_runs`(`run_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_hook_executions_run_id_hook_id_hook_config_version` " +
                "ON `hook_executions` (`run_id`, `hook_id`, `hook_config_version`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hook_executions_run_id_hook_order` " +
                "ON `hook_executions` (`run_id`, `hook_order`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hook_executions_status` ON `hook_executions` (`status`)"
        )
    }
}

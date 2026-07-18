package me.rerere.rikkahub.data.db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_41_42_Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate41To42_preservesAddTagAuditAndAddsSyncCursorSchema() {
        val name = "migration-test-41-42"
        helper.createDatabase(name, 41).apply {
            insert("ConversationEntity", SQLiteDatabase.CONFLICT_NONE, conversationValues())
            insert("generation_logical_turns", SQLiteDatabase.CONFLICT_NONE, logicalTurnValues())
            insert("hook_runs", SQLiteDatabase.CONFLICT_NONE, runValues())
            insert("hook_executions", SQLiteDatabase.CONFLICT_NONE, executionValues())
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 42, true)
        db.query(
            """
            SELECT action_type, tag_id, reason, status, execution_mode,
                   target_document_id, target_template_id, target_scope_type, target_scope_id,
                   base_revision, result_revision, operation_count, operation_summary_json,
                   diff_summary_json, retry_of_execution_id, idempotency_key
            FROM hook_executions WHERE execution_id = 'execution-41'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ADD_CONVERSATION_TAG", cursor.getString(0))
            assertEquals("tag-41", cursor.getString(1))
            assertEquals("legacy reason", cursor.getString(2))
            assertEquals("SUCCESS", cursor.getString(3))
            assertEquals("AUTO", cursor.getString(4))
            for (index in 5..15) assertTrue("column $index should default to null", cursor.isNull(index))
        }
        db.query("SELECT COUNT(*) FROM hook_action_cursors").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("PRAGMA index_list(hook_executions)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val indexes = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
            assertTrue("index_hook_executions_target_document_id_ended_at" in indexes)
            assertTrue("index_hook_executions_retry_of_execution_id" in indexes)
        }
        db.query("PRAGMA index_list(hook_action_cursors)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val indexes = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
            assertTrue(
                "index_hook_action_cursors_hook_id_hook_config_version_target_document_id_source_kind_source_key" in
                    indexes
            )
            assertTrue("index_hook_action_cursors_target_document_id_committed_at" in indexes)
            assertTrue("index_hook_action_cursors_execution_id" in indexes)
        }
        db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        db.close()
    }

    private fun conversationValues() = ContentValues().apply {
        put("id", "conversation-41")
        put("assistant_id", "assistant-41")
        put("title", "legacy")
        put("nodes", "[]")
        put("create_at", 1L)
        put("update_at", 2L)
    }

    private fun logicalTurnValues() = ContentValues().apply {
        put("logical_turn_id", "turn-41")
        put("conversation_id", "conversation-41")
        put("assistant_id", "assistant-41")
        put("invocation_kind", "USER_MESSAGE")
        put("status", "COMPLETED")
        put("created_at", 1L)
        put("updated_at", 2L)
        put("completed_at", 2L)
    }

    private fun runValues() = ContentValues().apply {
        put("run_id", "run-41")
        put("conversation_id", "conversation-41")
        put("assistant_id", "assistant-41")
        put("logical_turn_id", "turn-41")
        put("invocation_kind", "USER_MESSAGE")
        put("trigger", "AFTER_ASSISTANT_RESPONSE_SUCCESS")
        put("config_version", 1L)
        put("config_hash", "legacy-hash")
        put("started_at", 10L)
        put("ended_at", 20L)
        put("status", "SUCCESS")
        put("failure_count", 0)
    }

    private fun executionValues() = ContentValues().apply {
        put("execution_id", "execution-41")
        put("run_id", "run-41")
        put("hook_id", "hook-41")
        put("hook_order", 0)
        put("hook_config_version", 1L)
        put("hook_config_hash", "legacy-hash")
        put("model_id", "model-41")
        put("action_type", "ADD_CONVERSATION_TAG")
        put("started_at", 10L)
        put("ended_at", 20L)
        put("status", "SUCCESS")
        put("decision", "APPLY")
        put("tag_id", "tag-41")
        put("reason", "legacy reason")
        put("reason_truncated", 0)
        put("duration_ms", 10L)
        put("lease_token", 7L)
    }
}

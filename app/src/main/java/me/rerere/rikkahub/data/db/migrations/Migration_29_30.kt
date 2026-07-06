package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(29, 30)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memory_table_templates (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    schema_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_table_templates_updated_at ON memory_table_templates(updated_at)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memory_table_documents (
                    id TEXT NOT NULL PRIMARY KEY,
                    template_id TEXT NOT NULL,
                    scope_type TEXT NOT NULL,
                    scope_id TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    revision INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_table_documents_template_id ON memory_table_documents(template_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_table_documents_scope_type_scope_id ON memory_table_documents(scope_type, scope_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_table_documents_updated_at ON memory_table_documents(updated_at)")
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

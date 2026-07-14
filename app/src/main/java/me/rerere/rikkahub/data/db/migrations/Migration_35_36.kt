package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(35, 36)
        try {
            db.execSQL(
                """
                ALTER TABLE memory_table_templates
                ADD COLUMN scope_type TEXT NOT NULL DEFAULT 'GLOBAL'
                """.trimIndent()
            )
            db.execSQL(
                """
                ALTER TABLE memory_table_templates
                ADD COLUMN scope_id TEXT NOT NULL DEFAULT '__global__'
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_memory_table_templates_scope_type_scope_id
                ON memory_table_templates(scope_type, scope_id)
                """.trimIndent()
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

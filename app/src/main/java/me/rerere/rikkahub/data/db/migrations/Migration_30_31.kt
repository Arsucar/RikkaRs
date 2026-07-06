package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(30, 31)
        try {
            db.execSQL(
                """
                ALTER TABLE memory_table_templates
                ADD COLUMN description TEXT NOT NULL DEFAULT ''
                """.trimIndent()
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_44_45 : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val hasCompressHiddenCount = db.query("PRAGMA table_info(message_node)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == "compress_hidden_count" }
        }

        if (!hasCompressHiddenCount) {
            db.execSQL("ALTER TABLE message_node ADD COLUMN compress_hidden_count INTEGER")
        }
    }
}

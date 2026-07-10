package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

// #89: 为 conversation 增加对话级记忆表隔离开关列。仅追加一个带默认值的列，
// 不触碰既有数据，对老用户安全。
val Migration_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(33, 34)
        try {
            db.execSQL(
                "ALTER TABLE conversationentity ADD COLUMN memory_table_isolation INTEGER NOT NULL DEFAULT 0"
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

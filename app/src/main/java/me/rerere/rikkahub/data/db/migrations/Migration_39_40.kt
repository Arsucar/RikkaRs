package me.rerere.rikkahub.data.db.migrations

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec

@DeleteColumn(tableName = "ConversationEntity", columnName = "is_archived")
@DeleteColumn(tableName = "ConversationEntity", columnName = "archived_at")
class Migration_39_40 : AutoMigrationSpec

package me.rerere.rikkahub.data.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.openRequeryDatabase
import me.rerere.rikkahub.data.db.validateDatabaseForeignKeys
import me.rerere.rikkahub.data.db.validateDatabaseIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupDatabaseSnapshotTest {
    @Test
    fun vacuumIntoCreatesStandaloneConsistentDatabaseWithFtsSimple() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "backup-snapshot-source-${System.nanoTime()}"
        val database = buildRequeryDatabase(context, databaseName)
        var snapshot = File(context.cacheDir, "missing-snapshot")

        try {
            val db = database.openHelper.writableDatabase
            assertTrue(db.version > 0)
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
                    text,
                    node_id UNINDEXED,
                    message_id UNINDEXED,
                    conversation_id UNINDEXED,
                    title UNINDEXED,
                    update_at UNINDEXED,
                    tokenize = 'simple'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO message_fts(text, node_id, message_id, conversation_id, title, update_at)
                VALUES ('hello snapshot fts', 'n1', 'm1', 'c1', 't', 1)
                """.trimIndent()
            )

            snapshot = createDatabaseSnapshot(database, context)

            assertTrue(snapshot.isFile)
            assertTrue(snapshot.length() > 0L)

            openRequeryDatabase(context, snapshot).use { opened ->
                validateDatabaseIntegrity(opened)
                assertEquals(emptyList<Any>(), validateDatabaseForeignKeys(opened))
                opened.query("SELECT count(*) FROM message_fts").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.getLong(0) >= 1L)
                }
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            snapshot.delete()
        }
    }

    @Test
    fun concurrentSnapshotsUseDistinctFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "backup-snapshot-concurrent-${System.nanoTime()}"
        val database = buildRequeryDatabase(context, databaseName)
        var first = File(context.cacheDir, "missing-a")
        var second = File(context.cacheDir, "missing-b")

        try {
            assertTrue(database.openHelper.writableDatabase.version > 0)
            first = createDatabaseSnapshot(database, context)
            second = createDatabaseSnapshot(database, context)
            assertNotEquals(first.absolutePath, second.absolutePath)
            assertTrue(first.isFile)
            assertTrue(second.isFile)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            first.delete()
            second.delete()
        }
    }

    private fun buildRequeryDatabase(context: android.content.Context, name: String): AppDatabase {
        val simplePath = context.applicationInfo.nativeLibraryDir + "/libsimple"
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(
                RequerySQLiteOpenHelperFactory(
                    listOf(
                        RequerySQLiteOpenHelperFactory.ConfigurationOptions { options ->
                            options.customExtensions.add(
                                SQLiteCustomExtension(simplePath, null)
                            )
                            options
                        }
                    )
                )
            )
            .build()
    }
}

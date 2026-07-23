package me.rerere.rikkahub.data.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class BackupArchiveZipTest {
    @Test
    fun archiveContainsSettingsAndDatabaseWithoutWalSidecars() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "backup-archive-zip-${System.nanoTime()}"
        val simplePath = context.applicationInfo.nativeLibraryDir + "/libsimple"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
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
        val settingsStore = SettingsStore(context, AppScope())
        val archive = BackupArchive(
            settingsStore = settingsStore,
            json = JsonInstant,
            context = context,
            database = database,
        )
        var zipFile = context.cacheDir.resolve("missing-zip")

        try {
            assertTrue(database.openHelper.writableDatabase.version > 0)
            zipFile = archive.create(
                BackupArchiveOptions(
                    includeDatabase = true,
                    includeFiles = false,
                )
            )

            assertTrue(zipFile.isFile)
            ZipFile(zipFile).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue(names.contains("settings.json"))
                assertTrue(names.contains("rikka_hub.db"))
                assertFalse(names.any { it.endsWith("-wal") || it.endsWith("-shm") })
                assertFalse(names.contains("rikka_hub.db-wal"))
                assertFalse(names.contains("rikka_hub.db-shm"))
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            zipFile.delete()
        }
    }
}

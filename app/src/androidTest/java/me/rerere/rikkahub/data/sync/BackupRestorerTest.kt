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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * #184: verifies the shared atomic restore. A backup archive is produced from an isolated Room
 * database (never the live `rikka_hub`), then restored into a second isolated database so the test
 * can assert the on-disk swap, the failure rollback, and the config.items gating without touching
 * production data.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestorerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val simplePath = context.applicationInfo.nativeLibraryDir + "/libsimple"

    private fun buildDatabase(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(
                RequerySQLiteOpenHelperFactory(
                    listOf(
                        RequerySQLiteOpenHelperFactory.ConfigurationOptions { options ->
                            options.customExtensions.add(SQLiteCustomExtension(simplePath, null))
                            options
                        }
                    )
                )
            )
            .build()

    /** Creates a valid backup archive containing settings.json + rikka_hub.db from [sourceName]. */
    private fun createArchive(sourceName: String, includeDatabase: Boolean, includeFiles: Boolean): File {
        val database = buildDatabase(sourceName)
        val settingsStore = SettingsStore(context, AppScope())
        return try {
            // Force the database file onto disk before snapshotting.
            assertTrue(database.openHelper.writableDatabase.version > 0)
            val archive = BackupArchive(
                settingsStore = settingsStore,
                json = JsonInstant,
                context = context,
                database = database,
            )
            runBlocking {
                archive.create(BackupArchiveOptions(includeDatabase = includeDatabase, includeFiles = includeFiles))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun restoreSwapsDatabaseIntoTheFileTheAppReopens() = runBlocking {
        val sourceName = "restorer-src-${System.nanoTime()}"
        val targetName = "restorer-dst-${System.nanoTime()}"
        val archive = createArchive(sourceName, includeDatabase = true, includeFiles = false)

        try {
            val restorer = BackupRestorer(
                context = context,
                json = JsonInstant,
                settingsStore = SettingsStore(context, AppScope()),
                databaseName = targetName,
            )
            restorer.restore(archive, includeDatabase = true, includeFiles = false)

            // The restored database must land on getDatabasePath(targetName) — the exact file Room
            // reopens — and NOT on "<targetName>.db", which the app would never read.
            val liveDb = context.getDatabasePath(targetName)
            assertTrue("restored db must exist at the Room path", liveDb.isFile)
            assertTrue("restored db must be non-empty", liveDb.length() > 0)
            assertFalse(
                "restore must not write to <name>.db",
                File(liveDb.parentFile, "$targetName.db").exists(),
            )
        } finally {
            archive.delete()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
        }
    }

    @Test
    fun corruptDatabaseArchiveLeavesLiveDatabaseUntouched() = runBlocking {
        val targetName = "restorer-corrupt-${System.nanoTime()}"

        // Seed an existing live database file with recognisable bytes.
        val liveDb = context.getDatabasePath(targetName)
        liveDb.parentFile?.mkdirs()
        val originalBytes = "original-live-database".toByteArray()
        FileOutputStream(liveDb).use { it.write(originalBytes) }

        // Build an archive whose rikka_hub.db entry is not a valid SQLite database.
        val badArchive = File.createTempFile("bad_backup_", ".zip", context.cacheDir)
        ZipOutputStream(FileOutputStream(badArchive)).use { zip ->
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("rikka_hub.db"))
            zip.write("not a real sqlite file".toByteArray())
            zip.closeEntry()
        }

        try {
            val restorer = BackupRestorer(
                context = context,
                json = JsonInstant,
                settingsStore = SettingsStore(context, AppScope()),
                databaseName = targetName,
            )
            // Validation on the staged copy must fail and abort before touching the live database.
            assertThrows(Throwable::class.java) {
                runBlocking { restorer.restore(badArchive, includeDatabase = true, includeFiles = false) }
            }

            assertTrue("live database must survive a failed restore", liveDb.isFile)
            assertArrayEquals(
                "live database bytes must be unchanged after a failed restore",
                originalBytes,
                liveDb.readBytes(),
            )
            // No leftover backup sidecar files.
            assertFalse(File(liveDb.parentFile, "$targetName.restore-bak").exists())
        } finally {
            badArchive.delete()
            context.deleteDatabase(targetName)
            liveDb.delete()
        }
    }

    @Test
    fun includeDatabaseFalseSkipsDatabaseSwap() = runBlocking {
        val sourceName = "restorer-skip-src-${System.nanoTime()}"
        val targetName = "restorer-skip-dst-${System.nanoTime()}"
        val archive = createArchive(sourceName, includeDatabase = true, includeFiles = false)

        // Seed a live database that must remain untouched when includeDatabase = false.
        val liveDb = context.getDatabasePath(targetName)
        liveDb.parentFile?.mkdirs()
        val originalBytes = "keep-me".toByteArray()
        FileOutputStream(liveDb).use { it.write(originalBytes) }

        try {
            val restorer = BackupRestorer(
                context = context,
                json = JsonInstant,
                settingsStore = SettingsStore(context, AppScope()),
                databaseName = targetName,
            )
            restorer.restore(archive, includeDatabase = false, includeFiles = false)

            assertArrayEquals(
                "database must be untouched when includeDatabase = false",
                originalBytes,
                liveDb.readBytes(),
            )
        } finally {
            archive.delete()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
            liveDb.delete()
        }
    }
}

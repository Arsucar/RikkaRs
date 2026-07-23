package me.rerere.rikkahub.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class BackupArchiveIntegrationTest : KoinComponent {
    @Test
    fun freshInstallArchiveCompletesAndContainsConsistentDatabase() = runBlocking {
        val archive: BackupArchive = get()
        val backupFile = withTimeout(30_000) {
            archive.create(
                BackupArchiveOptions(
                    includeDatabase = true,
                    includeFiles = true,
                )
            )
        }

        try {
            assertTrue(backupFile.isFile)
            ZipFile(backupFile).use { zip ->
                assertTrue(zip.getEntry("settings.json") != null)
                assertTrue(zip.getEntry("rikka_hub.db") != null)
                assertFalse(zip.getEntry("rikka_hub-wal") != null)
                assertFalse(zip.getEntry("rikka_hub-shm") != null)
            }
        } finally {
            backupFile.delete()
        }
    }
}

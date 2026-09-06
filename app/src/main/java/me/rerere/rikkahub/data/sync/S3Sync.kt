package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.sync.s3.S3Client
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.File
import java.time.Instant

private const val TAG = "S3Sync"

class S3Sync(
    private val backupManager: BackupManager,
    private val context: Context,
    private val httpClient: HttpClient,
    private val backupArchive: BackupArchive,
    private val backupRestorer: BackupRestorer,
) {
    private fun getS3Client(config: S3Config): S3Client {
        return S3Client(config, httpClient)
    }

    suspend fun testS3(config: S3Config) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        // Test by listing objects with max 1 result
        client.listObjects(maxKeys = 1).getOrThrow()
        Log.i(TAG, "testS3: Connection successful")
    }

    suspend fun backupToS3(
        config: S3Config,
        onStage: (BackupTaskStage) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        onStage(BackupTaskStage.PREPARING)
        val file = prepareBackupFile(config)
        try {
            onStage(BackupTaskStage.TRANSFERRING)
            val client = getS3Client(config)
            val key = "rikkahub_backups/${file.name}"
            client.putObject(key = key, file = file, contentType = "application/zip").getOrThrow()
            Log.i(TAG, "backupToS3: Uploaded ${file.name} (${file.length().fileSizeToString()})")
        } finally {
            file.delete()
        }
    }

    suspend fun listBackupFiles(config: S3Config): List<S3BackupItem> = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val result = client.listObjects(
            prefix = "rikkahub_backups/",
            maxKeys = 1000
        ).getOrThrow()

        result.objects
            .filter { it.key.startsWith("rikkahub_backups/backup_") && it.key.endsWith(".zip") }
            .map { obj ->
                S3BackupItem(
                    key = obj.key,
                    displayName = obj.key.substringAfterLast("/"),
                    size = obj.size,
                    lastModified = obj.lastModified ?: Instant.EPOCH
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun restoreFromS3(
        config: S3Config,
        item: S3BackupItem,
        onStage: (BackupTaskStage) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val backupFile = File.createTempFile("restore-", ".zip", context.cacheDir)

        try {
            // Download backup file directly to file to avoid OOM
            onStage(BackupTaskStage.TRANSFERRING)
            Log.i(TAG, "restoreFromS3: Downloading ${item.displayName}")
            client.downloadObjectToFile(item.key, backupFile).getOrThrow()

            Log.i(TAG, "restoreFromS3: Downloaded ${backupFile.length().fileSizeToString()}")

            // Restore from backup file
            onStage(BackupTaskStage.RESTORING)
            restoreFromBackupFile(backupFile, config)
        } finally {
            // Clean up temp file
            if (backupFile.exists()) {
                backupFile.delete()
                Log.i(TAG, "restoreFromS3: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        client.deleteObject(item.key).getOrThrow()
        Log.i(TAG, "deleteS3BackupFile: Deleted ${item.key}")
    }

    /** Consistent snapshot via [BackupArchive]: validated DB snapshot + files + settings. */
    suspend fun prepareBackupFile(config: S3Config): File = backupArchive.create(
        BackupArchiveOptions(
            includeDatabase = config.items.contains(S3Config.BackupItem.DATABASE),
            includeFiles = config.items.contains(S3Config.BackupItem.FILES),
        )
    )

    // #184: delegate to the shared atomic restorer. The database is unpacked + validated in a temp
    // dir, swapped in atomically with a rollback fallback, and settings are applied only after the
    // swap succeeds, honouring the config.items switches.
    private suspend fun restoreFromBackupFile(backupFile: File, config: S3Config) {
        backupRestorer.restore(
            backupFile = backupFile,
            includeDatabase = config.items.contains(S3Config.BackupItem.DATABASE),
            includeFiles = config.items.contains(S3Config.BackupItem.FILES),
        )
    }
}

data class S3BackupItem(
    val key: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)

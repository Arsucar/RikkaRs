package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryTableSnapshotEntity

@Dao
interface MemoryTableSnapshotDAO {
    @Query(
        """
        SELECT * FROM memory_table_snapshots
        WHERE document_id = :documentId
        ORDER BY revision DESC
        """
    )
    fun getSnapshotsForDocumentFlow(documentId: String): Flow<List<MemoryTableSnapshotEntity>>

    @Query(
        """
        SELECT * FROM memory_table_snapshots
        WHERE document_id = :documentId
        ORDER BY revision DESC
        """
    )
    suspend fun getSnapshotsForDocument(documentId: String): List<MemoryTableSnapshotEntity>

    @Query(
        """
        SELECT * FROM memory_table_snapshots
        WHERE document_id = :documentId AND revision = :revision
        LIMIT 1
        """
    )
    suspend fun getSnapshot(documentId: String, revision: Int): MemoryTableSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: MemoryTableSnapshotEntity)

    @Query("SELECT COUNT(*) FROM memory_table_snapshots WHERE document_id = :documentId")
    suspend fun countSnapshots(documentId: String): Int

    // Deletes all but the most recent [keep] snapshots for a document (by revision),
    // enforcing the retention policy so snapshot history does not grow unbounded.
    @Query(
        """
        DELETE FROM memory_table_snapshots
        WHERE document_id = :documentId
          AND revision NOT IN (
            SELECT revision FROM memory_table_snapshots
            WHERE document_id = :documentId
            ORDER BY revision DESC
            LIMIT :keep
          )
        """
    )
    suspend fun pruneSnapshots(documentId: String, keep: Int): Int

    @Query("DELETE FROM memory_table_snapshots WHERE document_id = :documentId")
    suspend fun deleteSnapshotsForDocument(documentId: String): Int
}

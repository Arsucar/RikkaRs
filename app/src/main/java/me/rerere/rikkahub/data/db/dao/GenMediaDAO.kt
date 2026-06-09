package me.rerere.rikkahub.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.GenMediaEntity

@Dao
interface GenMediaDAO {
    @Query("SELECT * FROM genmediaentity WHERE type != :trashType ORDER BY create_at DESC")
    fun getAll(trashType: String): PagingSource<Int, GenMediaEntity>

    @Query(
        """
        SELECT * FROM genmediaentity
        WHERE type != :trashType
        AND (
            prompt LIKE '%' || :keyword || '%'
            OR model_id LIKE '%' || :keyword || '%'
            OR type LIKE '%' || :keyword || '%'
        )
        ORDER BY create_at DESC
        """
    )
    fun searchAll(trashType: String, keyword: String): PagingSource<Int, GenMediaEntity>

    @Query("SELECT * FROM genmediaentity WHERE type != :trashType ORDER BY create_at DESC")
    suspend fun getAllMedia(trashType: String): List<GenMediaEntity>

    @Query("SELECT * FROM genmediaentity WHERE type != :trashType ORDER BY create_at DESC")
    fun observeAllMedia(trashType: String): Flow<List<GenMediaEntity>>

    @Query("SELECT * FROM genmediaentity WHERE type = :type ORDER BY create_at DESC")
    fun observeByType(type: String): Flow<List<GenMediaEntity>>

    @Insert
    suspend fun insert(media: GenMediaEntity): Long

    @Query("UPDATE genmediaentity SET type = :type WHERE id = :id")
    suspend fun updateType(id: Int, type: String)

    @Query("DELETE FROM genmediaentity WHERE id = :id")
    suspend fun delete(id: Int)
}

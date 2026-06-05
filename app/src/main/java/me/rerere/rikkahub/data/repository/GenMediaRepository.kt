package me.rerere.rikkahub.data.repository

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.db.entity.GenMediaEntity

class GenMediaRepository(private val dao: GenMediaDAO) {
    fun getAllMedia(): PagingSource<Int, GenMediaEntity> = dao.getAll(GenMediaEntity.TYPE_IMAGE_TRASH)

    fun observeAllMedia(): Flow<List<GenMediaEntity>> = dao.observeAllMedia(GenMediaEntity.TYPE_IMAGE_TRASH)

    fun observeTrashMedia(): Flow<List<GenMediaEntity>> = dao.observeByType(GenMediaEntity.TYPE_IMAGE_TRASH)

    suspend fun insertMedia(media: GenMediaEntity) = dao.insert(media)

    suspend fun moveToTrash(id: Int) = dao.updateType(id, GenMediaEntity.TYPE_IMAGE_TRASH)

    suspend fun restoreMedia(id: Int, type: String) = dao.updateType(id, type)

    suspend fun deleteMedia(id: Int) = dao.delete(id)
}

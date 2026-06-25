package me.rerere.rikkahub.data.favorite

import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.model.FavoriteType
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFavoriteAdapterTest {
    @Test
    fun updateCollectionId_withoutMetaJson_writesCollectionId() {
        val entity = FavoriteEntity(
            id = "image:42",
            type = FavoriteType.IMAGE.value,
            refKey = "image:42",
            refJson = """{"imageId":42}""",
            snapshotJson = """{"imageId":42,"prompt":"p","filePath":"/f","timestamp":1,"model":"m","type":"gen","sourcePaths":null}""",
            metaJson = null,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val updated = ImageFavoriteAdapter.updateCollectionId(entity, "col-1")
        assertTrue(updated.metaJson?.contains("col-1") == true)
    }
}
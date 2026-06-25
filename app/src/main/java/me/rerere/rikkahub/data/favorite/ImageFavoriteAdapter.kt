package me.rerere.rikkahub.data.favorite

import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.model.FavoriteMeta
import me.rerere.rikkahub.data.model.FavoriteType
import me.rerere.rikkahub.data.model.ImageFavoriteRef
import me.rerere.rikkahub.data.model.ImageFavoriteSnapshot
import me.rerere.rikkahub.data.model.ImageFavoriteTarget
import me.rerere.rikkahub.utils.JsonInstant

object ImageFavoriteAdapter : FavoriteAdapter<ImageFavoriteTarget> {
    override val type: FavoriteType = FavoriteType.IMAGE

    override fun buildRefKey(target: ImageFavoriteTarget): String {
        return buildRefKey(target.imageId)
    }

    fun buildRefKey(imageId: Int): String = "image:$imageId"

    override fun buildFavoriteEntity(
        target: ImageFavoriteTarget,
        existing: FavoriteEntity?,
        now: Long
    ): FavoriteEntity {
        val ref = ImageFavoriteRef(imageId = target.imageId)
        val snapshot = ImageFavoriteSnapshot(
            imageId = target.imageId,
            prompt = target.prompt,
            filePath = target.filePath,
            timestamp = target.timestamp,
            model = target.model,
            type = target.type,
            sourcePaths = target.sourcePaths,
        )
        val meta = FavoriteMeta(
            title = target.prompt.take(80).ifBlank { "图片收藏" },
            subtitle = target.model,
            previewText = target.prompt.take(160),
            collectionId = target.collectionId ?: existing?.let { decodeMeta(it)?.collectionId },
        )

        return FavoriteEntity(
            id = existing?.id ?: buildRefKey(target),
            type = type.value,
            refKey = buildRefKey(target),
            refJson = JsonInstant.encodeToString(ref),
            snapshotJson = JsonInstant.encodeToString(snapshot),
            metaJson = JsonInstant.encodeToString(meta),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    fun decodeRef(entity: FavoriteEntity): ImageFavoriteRef? {
        if (entity.type != type.value) return null
        return runCatching {
            JsonInstant.decodeFromString<ImageFavoriteRef>(entity.refJson)
        }.getOrNull()
    }

    fun decodeSnapshot(entity: FavoriteEntity): ImageFavoriteSnapshot? {
        if (entity.type != type.value) return null
        return runCatching {
            JsonInstant.decodeFromString<ImageFavoriteSnapshot>(entity.snapshotJson)
        }.getOrNull()
    }

    fun decodeMeta(entity: FavoriteEntity): FavoriteMeta? {
        if (entity.type != type.value) return null
        val raw = entity.metaJson ?: return null
        return runCatching {
            JsonInstant.decodeFromString<FavoriteMeta>(raw)
        }.getOrNull()
    }

    fun updateCollectionId(entity: FavoriteEntity, collectionId: String?): FavoriteEntity {
        val meta = decodeMeta(entity) ?: FavoriteMeta()
        return entity.copy(
            metaJson = JsonInstant.encodeToString(meta.copy(collectionId = collectionId)),
            updatedAt = System.currentTimeMillis(),
        )
    }
}

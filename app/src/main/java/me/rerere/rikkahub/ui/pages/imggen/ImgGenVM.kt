package me.rerere.rikkahub.ui.pages.imggen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.rikkahub.data.datastore.ImageFavoriteCollection
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.favorite.ImageFavoriteAdapter
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.FavoriteType
import me.rerere.rikkahub.data.model.ImageFavoriteTarget
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import java.io.File
import kotlin.uuid.Uuid

@Serializable
data class GeneratedImage(
    val id: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String,
    val type: String = GenMediaEntity.TYPE_IMAGE_GENERATION,
    val sourcePaths: String? = null,
)

data class GeneratedImageGroup(
    val prompt: String,
    val timestamp: Long,
    val model: String,
    val images: List<GeneratedImage>,
    val variants: List<GeneratedImageVariantGroup> = emptyList(),
)

data class GeneratedImageVariantGroup(
    val label: String,
    val prompt: String,
    val timestamp: Long,
    val model: String,
    val images: List<GeneratedImage>,
)

data class ImageFavoriteListItem(
    val favoriteId: String,
    val refKey: String,
    val image: GeneratedImage,
    val collectionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

private fun GenMediaEntity.toGeneratedImage(filesManager: FilesManager): GeneratedImage {
    val imagesDir = filesManager.getImagesDir()
    val fullPath = File(imagesDir, this.path.removePrefix("images/")).absolutePath

    return GeneratedImage(
        id = this.id,
        prompt = this.prompt,
        filePath = fullPath,
        timestamp = this.createAt,
        model = this.modelId,
        type = this.type,
        sourcePaths = this.sourcePaths,
    )
}

private data class ExactPromptGroup(
    val prompt: String,
    val normalizedPrompt: String,
    val timestamp: Long,
    val model: String,
    val images: List<GeneratedImage>,
)

private data class PromptTemplate(
    val prefix: String,
    val suffix: String,
    val score: Int,
)

private data class PromptCluster(
    val groups: List<ExactPromptGroup>,
    val template: PromptTemplate?,
)

private const val MAX_VARIANT_GROUPING_LENGTH = 360
private const val MAX_VARIANT_LABEL_LENGTH = 80
private const val MAX_VARIANT_SEARCH_WINDOW = 30

internal fun buildGeneratedImageGroups(images: List<GeneratedImage>): List<GeneratedImageGroup> {
    val exactGroups = images
        .groupBy { normalizePromptForGrouping(it.prompt) }
        .values
        .map { groupImages ->
            val sortedImages = groupImages.sortedByDescending { it.timestamp }
            ExactPromptGroup(
                prompt = sortedImages.firstOrNull()?.prompt.orEmpty(),
                normalizedPrompt = normalizePromptForGrouping(sortedImages.firstOrNull()?.prompt.orEmpty()),
                timestamp = sortedImages.maxOfOrNull { it.timestamp } ?: 0L,
                model = sortedImages.firstOrNull()?.model.orEmpty(),
                images = sortedImages,
            )
        }
        .sortedByDescending { it.timestamp }

    return buildPromptClusters(exactGroups)
        .flatMap { cluster ->
            val template = cluster.template
            if (template == null) {
                cluster.groups.map { it.toGeneratedImageGroup() }
            } else {
                listOf(cluster.groups.toGeneratedImageGroup(template))
            }
        }
        .sortedByDescending { it.timestamp }
}

private fun ExactPromptGroup.toGeneratedImageGroup(): GeneratedImageGroup {
    return GeneratedImageGroup(
        prompt = prompt,
        timestamp = timestamp,
        model = model,
        images = images,
    )
}

private fun List<ExactPromptGroup>.toGeneratedImageGroup(template: PromptTemplate): GeneratedImageGroup {
    val variants = map { exactGroup ->
        val variant = exactGroup.normalizedPrompt.extractVariant(template)?.toVariantLabel() ?: exactGroup.prompt
        GeneratedImageVariantGroup(
            label = variant,
            prompt = exactGroup.prompt,
            timestamp = exactGroup.timestamp,
            model = exactGroup.model,
            images = exactGroup.images,
        )
    }
    return GeneratedImageGroup(
        prompt = template.toDisplayPrompt(),
        timestamp = maxOfOrNull { it.timestamp } ?: 0L,
        model = firstOrNull()?.model.orEmpty(),
        images = flatMap { it.images }.sortedByDescending { it.timestamp },
        variants = variants,
    )
}

private fun List<GeneratedImageGroup>.filterByImageSearchKeyword(keyword: String): List<GeneratedImageGroup> {
    return mapNotNull { group ->
        group.filterByImageSearchKeyword(keyword)
    }
}

private fun GeneratedImageGroup.filterByImageSearchKeyword(keyword: String): GeneratedImageGroup? {
    val matchingImages = images.filter { it.matchesImageSearchKeyword(keyword) }
    if (matchingImages.isEmpty() && !matchesImageSearchKeyword(keyword)) {
        return null
    }
    if (variants.isEmpty()) {
        return copy(images = matchingImages.ifEmpty { images })
    }
    val matchingVariants = variants.mapNotNull { variant ->
        val variantImages = variant.images.filter { it.matchesImageSearchKeyword(keyword) }
        when {
            variantImages.isNotEmpty() -> variant.copy(images = variantImages)
            variant.matchesImageSearchKeyword(keyword) -> variant
            else -> null
        }
    }
    if (matchingVariants.isEmpty()) {
        return null
    }
    return copy(
        images = matchingVariants.flatMap { it.images },
        variants = matchingVariants,
    )
}

private fun GeneratedImageGroup.matchesImageSearchKeyword(keyword: String): Boolean =
    prompt.contains(keyword, ignoreCase = true) ||
        model.contains(keyword, ignoreCase = true)

private fun GeneratedImageVariantGroup.matchesImageSearchKeyword(keyword: String): Boolean =
    label.contains(keyword, ignoreCase = true) ||
        prompt.contains(keyword, ignoreCase = true) ||
        model.contains(keyword, ignoreCase = true)

private fun GeneratedImage.matchesImageSearchKeyword(keyword: String): Boolean =
    prompt.contains(keyword, ignoreCase = true) ||
        model.contains(keyword, ignoreCase = true) ||
        type.contains(keyword, ignoreCase = true)

private fun normalizePromptForGrouping(prompt: String): String {
    return prompt
        .trim()
        .replace(Regex("\\s+"), " ")
        .replace('，', ',')
        .replace('。', '.')
        .replace('；', ';')
        .replace('：', ':')
        .replace('、', ',')
}

private fun findPromptTemplate(prompts: List<String>): PromptTemplate? {
    if (prompts.size < 2 || prompts.any { it.isBlank() } || prompts.distinct().size < 2) return null

    val minLength = prompts.minOf { it.length }
    val commonPrefix = findCommonPrefix(prompts)
    val commonSuffix = findCommonSuffix(prompts, commonPrefix.length)
    val commonLength = commonPrefix.length + commonSuffix.length
    val minCommonLength = maxOf(12, (minLength * 0.30f).toInt())

    if (commonLength < minCommonLength) return null

    val variants = prompts.map { it.extractVariant(commonPrefix, commonSuffix) ?: return null }
    if (!areUsefulVariants(variants)) return null

    return PromptTemplate(
        prefix = commonPrefix,
        suffix = commonSuffix,
        score = commonLength,
    )
}

private fun buildPromptClusters(groups: List<ExactPromptGroup>): List<PromptCluster> {
    val assigned = BooleanArray(groups.size)
    val clusters = mutableListOf<PromptCluster>()

    groups.indices.forEach { headIndex ->
        if (assigned[headIndex]) return@forEach

        assigned[headIndex] = true
        val clusterIndices = mutableListOf(headIndex)
        var clusterTemplate: PromptTemplate? = null
        val searchEnd = minOf(groups.size, headIndex + 1 + MAX_VARIANT_SEARCH_WINDOW)

        for (candidateIndex in headIndex + 1 until searchEnd) {
            if (assigned[candidateIndex]) continue

            val proposedIndices = clusterIndices + candidateIndex
            val template = findPromptTemplate(proposedIndices.map { groups[it].normalizedPrompt })
            if (template != null) {
                assigned[candidateIndex] = true
                clusterIndices.add(candidateIndex)
                clusterTemplate = template
            }
        }

        clusters += PromptCluster(
            groups = clusterIndices.map { groups[it] }.sortedByDescending { it.timestamp },
            template = clusterTemplate,
        )
    }

    return clusters.sortedByDescending { cluster ->
        cluster.groups.maxOfOrNull { it.timestamp } ?: 0L
    }
}

private fun findCommonPrefix(values: List<String>): String {
    return values.reduceOrNull { prefix, value -> prefix.commonPrefixWith(value) }.orEmpty()
}

private fun findCommonSuffix(values: List<String>, prefixLength: Int): String {
    return values
        .map { it.drop(prefixLength) }
        .reduceOrNull { suffix, value -> suffix.commonSuffixWith(value) }
        .orEmpty()
}

private fun String.extractVariant(template: PromptTemplate): String? {
    return extractVariant(template.prefix, template.suffix)
}

private fun String.extractVariant(prefix: String, suffix: String): String? {
    if (!startsWith(prefix) || !endsWith(suffix)) return null
    val endIndex = length - suffix.length
    if (endIndex < prefix.length) return null
    val variant = substring(prefix.length, endIndex).cleanVariantLabel()
    return variant
}

private fun String.cleanVariantLabel(): String {
    return trim()
        .trim(',', '.', ';', ':', '，', '。', '；', '：', '、', ' ', '\n', '\t')
}

private fun String.toVariantLabel(): String {
    val label = ifBlank { "原始版本" }
    return if (label.length <= MAX_VARIANT_LABEL_LENGTH) {
        label
    } else {
        val headLength = (MAX_VARIANT_LABEL_LENGTH * 0.65f).toInt()
        val tailLength = MAX_VARIANT_LABEL_LENGTH - headLength - 5
        label.take(headLength).trimEnd() + " ... " + label.takeLast(tailLength).trimStart()
    }
}

private fun areUsefulVariants(variants: List<String>): Boolean {
    return variants.any { it.isNotBlank() } &&
        variants.all { it.isBlank() || it.length <= MAX_VARIANT_GROUPING_LENGTH }
}

private fun PromptTemplate.toDisplayPrompt(): String {
    val cleanPrefix = prefix.cleanVariantLabel()
    val cleanSuffix = suffix.cleanVariantLabel()
    return when {
        cleanPrefix.isNotBlank() && cleanSuffix.isNotBlank() -> "$cleanPrefix ... $cleanSuffix"
        cleanPrefix.isNotBlank() -> cleanPrefix
        cleanSuffix.isNotBlank() -> cleanSuffix
        else -> "相似提示词"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ImgGenVM(
    val settingsStore: SettingsStore,
    private val session: ImgGenSession,
    private val genMediaRepository: GenMediaRepository,
    private val favoriteRepository: FavoriteRepository,
    private val filesManager: FilesManager,
) : ViewModel() {
    val prompt: StateFlow<String> = session.prompt
    val numberOfImages: StateFlow<Int> = session.numberOfImages
    val aspectRatio: StateFlow<ImageAspectRatio> = session.aspectRatio
    val isGenerating: StateFlow<Boolean> = session.isGenerating
    val activeJobs: StateFlow<Map<Long, ImgGenActiveJob>> = session.activeJobs
    val error: StateFlow<String?> = session.error
    val referenceImages: StateFlow<List<String>> = session.referenceImages
    private val _imageSearchQuery = MutableStateFlow("")
    val imageSearchQuery: StateFlow<String> = _imageSearchQuery

    val generatedImages: Flow<PagingData<GeneratedImage>> = imageSearchQuery
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { keyword ->
            Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                pagingSourceFactory = {
                    if (keyword.isBlank()) {
                        genMediaRepository.getAllMedia()
                    } else {
                        genMediaRepository.searchAllMedia(keyword)
                    }
                }
            ).flow
        }
        .map { pagingData ->
            pagingData.map { entity -> entity.toGeneratedImage(filesManager) }
        }
        .cachedIn(viewModelScope)

    val groupedImages: StateFlow<List<GeneratedImageGroup>> = combine(
        genMediaRepository.observeAllMedia(),
        imageSearchQuery.map { it.trim() }.distinctUntilChanged(),
    ) { entities, keyword ->
        val images = entities.map { entity -> entity.toGeneratedImage(filesManager) }
        if (keyword.isBlank()) {
            buildGeneratedImageGroups(images)
        } else {
            buildGeneratedImageGroups(images).filterByImageSearchKeyword(keyword)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val trashImages: StateFlow<List<GeneratedImage>> = genMediaRepository.observeTrashMedia()
        .map { entities ->
            entities.map { entity -> entity.toGeneratedImage(filesManager) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val imageFavorites: StateFlow<List<ImageFavoriteListItem>> = favoriteRepository
        .listByType(FavoriteType.IMAGE)
        .map { entities ->
            entities.mapNotNull { entity ->
                val snapshot = ImageFavoriteAdapter.decodeSnapshot(entity) ?: return@mapNotNull null
                val meta = ImageFavoriteAdapter.decodeMeta(entity)
                ImageFavoriteListItem(
                    favoriteId = entity.id,
                    refKey = entity.refKey,
                    image = GeneratedImage(
                        id = snapshot.imageId,
                        prompt = snapshot.prompt,
                        filePath = snapshot.filePath,
                        timestamp = snapshot.timestamp,
                        model = snapshot.model,
                        type = snapshot.type,
                        sourcePaths = snapshot.sourcePaths,
                    ),
                    collectionId = meta?.collectionId,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val imageFavoriteIds: StateFlow<Set<Int>> = favoriteRepository
        .listByType(FavoriteType.IMAGE)
        .map { entities ->
            entities.mapNotNull { entity -> ImageFavoriteAdapter.decodeRef(entity)?.imageId }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun updatePrompt(prompt: String) = session.updatePrompt(prompt)

    fun updateImageSearchQuery(query: String) {
        _imageSearchQuery.value = query
    }

    fun updateNumberOfImages(count: Int) = session.updateNumberOfImages(count)

    fun updateAspectRatio(aspectRatio: ImageAspectRatio) = session.updateAspectRatio(aspectRatio)

    fun selectImageGenerationModel(modelId: Uuid) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(imageGenerationModelId = modelId)
            }
            session.updateNumberOfImages(session.numberOfImages.value)
        }
    }

    fun addReferenceImages(paths: List<String>) = session.addReferenceImages(paths)

    fun removeReferenceImage(path: String) = session.removeReferenceImage(path)

    fun clearError() = session.clearError()

    fun startNewSession() = session.startNewSession()

    fun generateImage() = session.generateImage()

    fun editImage() = session.editImage()

    fun cancelGeneration() = session.cancelGeneration()

    fun cancelJob(jobId: Long) = session.cancelJob(jobId)

    fun dismissJob(jobId: Long) = session.dismissJob(jobId)

    fun regenerateJob(jobId: Long) = session.regenerateFromJob(jobId)

    fun regenerateFromImage(image: GeneratedImage) = session.regenerateFromImage(image)

    fun deleteImage(image: GeneratedImage) = session.deleteImage(image)

    fun permanentlyDeleteImage(image: GeneratedImage) = session.permanentlyDeleteImage(image)

    fun permanentlyDeleteImages(images: List<GeneratedImage>) = session.permanentlyDeleteImages(images)

    fun restoreImage(image: GeneratedImage) = session.restoreImage(image)

    fun toggleImageFavorite(
        image: GeneratedImage,
        onResult: (Boolean) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                val existing = favoriteRepository.getImageFavorite(image.id)
                if (existing != null) {
                    deleteFavoriteSnapshotFile(existing)
                    favoriteRepository.removeImageFavorite(image.id)
                    false
                } else {
                    val favoriteImagePath = copyImageToFavoriteStorage(image.filePath)
                    favoriteRepository.addImageFavorite(
                        ImageFavoriteTarget(
                            imageId = image.id,
                            prompt = image.prompt,
                            filePath = favoriteImagePath,
                            timestamp = image.timestamp,
                            model = image.model,
                            type = image.type,
                            sourcePaths = image.sourcePaths,
                        )
                    )
                    true
                }
            }.onSuccess(onResult).onFailure(onError)
        }
    }

    fun appendQuickMessage(content: String) {
        val separator = if (prompt.value.isBlank() || prompt.value.endsWith("\n")) "" else "\n"
        updatePrompt(prompt.value + separator + content)
    }

    fun addImageQuickMessage(title: String, content: String) {
        updateImageQuickMessages { messages ->
            messages + QuickMessage(title = title, content = content)
        }
    }

    fun updateImageQuickMessage(updated: QuickMessage) {
        updateImageQuickMessages { messages ->
            messages.map { message -> if (message.id == updated.id) updated else message }
        }
    }

    fun deleteImageQuickMessage(id: Uuid) {
        updateImageQuickMessages { messages ->
            messages.filterNot { message -> message.id == id }
        }
    }

    fun addImageFavoriteCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            settingsStore.update { settings ->
                val id = Uuid.random().toString()
                settings.copy(
                    imageFavoriteCollections = settings.imageFavoriteCollections + ImageFavoriteCollection(
                        id = id,
                        name = trimmed,
                    ),
                )
            }
        }
    }

    fun renameImageFavoriteCollection(collectionId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    imageFavoriteCollections = settings.imageFavoriteCollections.map { collection ->
                        if (collection.id == collectionId) collection.copy(name = trimmed) else collection
                    },
                )
            }
        }
    }

    fun deleteImageFavoriteCollection(collectionId: String) {
        viewModelScope.launch {
            favoriteRepository.listByType(FavoriteType.IMAGE).first().forEach { entity ->
                val meta = ImageFavoriteAdapter.decodeMeta(entity) ?: return@forEach
                if (meta.collectionId != collectionId) return@forEach
                val imageId = ImageFavoriteAdapter.decodeRef(entity)?.imageId ?: return@forEach
                favoriteRepository.setImageFavoriteCollection(imageId, null)
            }
            settingsStore.update { settings ->
                settings.copy(
                    imageFavoriteCollections = settings.imageFavoriteCollections.filterNot { it.id == collectionId },
                )
            }
        }
    }

    fun setImageFavoriteCollection(imageId: Int, collectionId: String?) {
        viewModelScope.launch {
            favoriteRepository.setImageFavoriteCollection(imageId, collectionId)
        }
    }

    private fun updateImageQuickMessages(update: (List<QuickMessage>) -> List<QuickMessage>) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(imageQuickMessages = update(settings.imageQuickMessages).distinctBy { it.id })
            }
        }
    }

    private suspend fun copyImageToFavoriteStorage(imagePath: String): String = withContext(Dispatchers.IO) {
        val source = File(imagePath.removePrefix("file://"))
        require(source.exists()) {
            "Image file does not exist"
        }

        val favoritesDir = File(filesManager.getImagesDir(), FAVORITES_DIR).apply {
            mkdirs()
        }
        val extension = source.extension.ifBlank { "png" }
        val target = File(favoritesDir, "favorite_${System.currentTimeMillis()}_${Uuid.random()}.$extension")
        source.copyTo(target, overwrite = false)
        target.absolutePath
    }

    private suspend fun deleteFavoriteSnapshotFile(entity: FavoriteEntity) = withContext(Dispatchers.IO) {
        val snapshot = ImageFavoriteAdapter.decodeSnapshot(entity) ?: return@withContext
        val favoritesDir = File(filesManager.getImagesDir(), FAVORITES_DIR)
        val file = File(snapshot.filePath)
        val favoritesRoot = favoritesDir.canonicalPath + File.separator
        val filePath = runCatching { file.canonicalPath }.getOrNull() ?: return@withContext
        if (filePath.startsWith(favoritesRoot) && file.exists()) {
            file.delete()
        }
    }

    companion object {
        private const val FAVORITES_DIR = "favorites"
    }
}

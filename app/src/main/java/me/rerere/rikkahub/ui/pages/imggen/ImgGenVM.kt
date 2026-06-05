package me.rerere.rikkahub.ui.pages.imggen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.ImageAspectRatio
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

private const val MAX_VARIANT_GROUPING_LENGTH = 360
private const val MAX_VARIANT_LABEL_LENGTH = 80

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

    return buildPromptComponents(exactGroups)
        .flatMap { componentIndices ->
            val componentGroups = componentIndices.map { exactGroups[it] }.sortedByDescending { it.timestamp }
            val template = if (componentGroups.size >= 2) {
                findPromptTemplate(componentGroups.map { it.normalizedPrompt })
            } else {
                null
            }

            if (template == null) {
                componentGroups.map { it.toGeneratedImageGroup() }
            } else {
                listOf(componentGroups.toGeneratedImageGroup(template))
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

private fun findPromptTemplate(left: String, right: String): PromptTemplate? {
    return findPromptTemplate(listOf(left, right))
}

private fun findPromptTemplate(prompts: List<String>): PromptTemplate? {
    if (prompts.size < 2 || prompts.any { it.isBlank() } || prompts.distinct().size < 2) return null

    val minLength = prompts.minOf { it.length }
    val commonPrefixLength = commonPrefixLength(prompts)
    val commonSuffixLength = commonSuffixLength(prompts, commonPrefixLength)
    val commonLength = commonPrefixLength + commonSuffixLength
    val minCommonLength = maxOf(12, (minLength * 0.30f).toInt())

    if (commonLength < minCommonLength) return null

    val prefix = prompts.first().take(commonPrefixLength)
    val suffix = prompts.first().takeLast(commonSuffixLength)
    val variants = prompts.map { it.extractVariant(prefix, suffix) ?: return null }
    if (!areUsefulVariants(variants)) return null

    return PromptTemplate(
        prefix = prefix,
        suffix = suffix,
        score = commonLength,
    )
}

private fun buildPromptComponents(groups: List<ExactPromptGroup>): List<List<Int>> {
    val parent = IntArray(groups.size) { it }

    fun find(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }

    fun union(left: Int, right: Int) {
        val leftRoot = find(left)
        val rightRoot = find(right)
        if (leftRoot != rightRoot) {
            parent[rightRoot] = leftRoot
        }
    }

    groups.indices.forEach { left ->
        for (right in left + 1 until groups.size) {
            if (findPromptTemplate(groups[left].normalizedPrompt, groups[right].normalizedPrompt) != null) {
                union(left, right)
            }
        }
    }

    return groups.indices
        .groupBy { find(it) }
        .values
        .map { it.sortedByDescending { index -> groups[index].timestamp } }
        .sortedByDescending { indices -> indices.maxOfOrNull { groups[it].timestamp } ?: 0L }
}

private fun commonPrefixLength(values: List<String>): Int {
    val first = values.firstOrNull() ?: return 0
    val max = values.minOfOrNull { it.length } ?: 0
    var index = 0
    while (index < max && values.all { it[index] == first[index] }) {
        index++
    }
    return index
}

private fun commonSuffixLength(values: List<String>, prefixLength: Int): Int {
    val first = values.firstOrNull() ?: return 0
    val max = (values.minOfOrNull { it.length } ?: 0) - prefixLength
    var index = 0
    while (
        index < max &&
        values.all { it[it.lastIndex - index] == first[first.lastIndex - index] }
    ) {
        index++
    }
    return index
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
    val error: StateFlow<String?> = session.error
    val currentGeneratedImages: StateFlow<List<GeneratedImage>> = session.currentGeneratedImages
    val referenceImages: StateFlow<List<String>> = session.referenceImages

    val pager = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { genMediaRepository.getAllMedia() }
    )
    val generatedImages: Flow<PagingData<GeneratedImage>> = pager.flow
        .map { pagingData ->
            pagingData.map { entity -> entity.toGeneratedImage(filesManager) }
        }
        .cachedIn(viewModelScope)

    val groupedImages: StateFlow<List<GeneratedImageGroup>> = genMediaRepository.observeAllMedia()
        .map { entities ->
            buildGeneratedImageGroups(
                entities.map { entity -> entity.toGeneratedImage(filesManager) }
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

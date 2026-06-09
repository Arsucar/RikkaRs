package me.rerere.rikkahub.ui.pages.imggen

import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.ImageSizeValidationError
import me.rerere.ai.ui.resolveGptImage2Size
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.ImageGenerationSettings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** 单次生成/编辑的最大出图数量 */
const val MAX_GENERATION_IMAGES = 4

class ImgGenSession(
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
) {
    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _numberOfImages = MutableStateFlow(1)
    val numberOfImages: StateFlow<Int> = _numberOfImages

    private val _aspectRatio = MutableStateFlow(ImageAspectRatio.SQUARE)
    val aspectRatio: StateFlow<ImageAspectRatio> = _aspectRatio

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentGeneratedImages = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val currentGeneratedImages: StateFlow<List<GeneratedImage>> = _currentGeneratedImages

    private val _referenceImages = MutableStateFlow<List<String>>(emptyList())
    val referenceImages: StateFlow<List<String>> = _referenceImages

    private var cancelJob: Job? = null

    fun updatePrompt(prompt: String) {
        _prompt.value = prompt
    }

    fun updateNumberOfImages(count: Int) {
        _numberOfImages.value = count.coerceIn(1, maxImagesForCurrentModel())
    }

    fun updateAspectRatio(aspectRatio: ImageAspectRatio) {
        _aspectRatio.value = aspectRatio
    }

    fun addReferenceImages(paths: List<String>) {
        _referenceImages.value = (_referenceImages.value + paths).distinct().take(MAX_REFERENCE_IMAGES)
    }

    fun removeReferenceImage(path: String) {
        _referenceImages.value = _referenceImages.value.filterNot { it == path }
        deleteReferenceFiles(listOf(path))
    }

    fun clearReferenceImages() {
        deleteReferenceFiles(_referenceImages.value)
        _referenceImages.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }

    fun startNewSession() {
        cancelJob?.cancel()
        clearReferenceImages()
        _prompt.value = ""
        _currentGeneratedImages.value = emptyList()
        _error.value = null
        _isGenerating.value = false
        _numberOfImages.value = 1
        _aspectRatio.value = ImageAspectRatio.SQUARE
    }

    fun generateImage() {
        if (prompt.value.isBlank()) return
        cancelJob?.cancel()
        cancelJob = appScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()

                val settings = settingsStore.settingsFlow.first()
                val model = settings.findModelById(settings.imageGenerationModelId)
                    ?: throw IllegalStateException("No model selected")
                val provider = model.findProvider(settings.providers)
                    ?: throw IllegalStateException("Provider not found")
                val providerSetting = settings.providers.find { it.id == provider.id }
                    ?: throw IllegalStateException("Provider setting not found")
                val imageSettings = settings.imageGenerationSettings
                val isGptImage2 = model.modelId.equals(GPT_IMAGE_2, ignoreCase = true)
                val gptImage2Size = if (isGptImage2) imageSettings.resolveGptImage2SizeOrThrow() else null

                val params = ImageGenerationParams(
                    model = model,
                    prompt = _prompt.value,
                    numOfImages = _numberOfImages.value.coerceIn(1, MAX_GENERATION_IMAGES),
                    aspectRatio = _aspectRatio.value,
                    size = gptImage2Size,
                    quality = imageSettings.quality.takeIf { isGptImage2 },
                    outputFormat = imageSettings.outputFormat.takeIf { isGptImage2 },
                    outputCompression = imageSettings.outputCompression.takeIf {
                        isGptImage2 && imageSettings.outputFormat.supportsCompression
                    },
                    background = imageSettings.background.takeIf { isGptImage2 },
                    moderation = imageSettings.moderation.takeIf { isGptImage2 },
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies
                )

                val result = providerManager.getProviderByType(provider)
                    .generateImage(providerSetting, params)

                _currentGeneratedImages.value = result.items.mapIndexed { index, item ->
                    saveImageToStorage(
                        item = item,
                        prompt = _prompt.value,
                        modelName = model.displayName,
                        index = index
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e(TAG, "Failed to generate image", e)
                _error.value = e.message ?: "Unknown error occurred"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun editImage() {
        if (prompt.value.isBlank() || referenceImages.value.isEmpty()) return
        cancelJob?.cancel()
        cancelJob = appScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()

                val settings = settingsStore.settingsFlow.first()
                val model = settings.findModelById(settings.imageGenerationModelId)
                    ?: throw IllegalStateException("No model selected")
                val provider = model.findProvider(settings.providers)
                    ?: throw IllegalStateException("Provider not found")
                val providerSetting = settings.providers.find { it.id == provider.id }
                    ?: throw IllegalStateException("Provider setting not found")
                val imageSettings = settings.imageGenerationSettings
                val isGptImage2 = model.modelId.equals(GPT_IMAGE_2, ignoreCase = true)
                val gptImage2Size = if (isGptImage2) imageSettings.resolveGptImage2SizeOrThrow() else null
                val sourceImages = _referenceImages.value

                val params = ImageEditParams(
                    model = model,
                    prompt = _prompt.value,
                    images = sourceImages,
                    numOfImages = _numberOfImages.value.coerceIn(1, MAX_GENERATION_IMAGES),
                    aspectRatio = _aspectRatio.value,
                    size = gptImage2Size,
                    quality = imageSettings.quality.takeIf { isGptImage2 },
                    outputFormat = imageSettings.outputFormat.takeIf { isGptImage2 },
                    outputCompression = imageSettings.outputCompression.takeIf {
                        isGptImage2 && imageSettings.outputFormat.supportsCompression
                    },
                    background = imageSettings.background.takeIf { isGptImage2 },
                    moderation = imageSettings.moderation.takeIf { isGptImage2 },
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies
                )

                val result = providerManager.getProviderByType(provider)
                    .editImage(providerSetting, params)

                val sourcePaths = sourceImages.joinToString(separator = "\n")
                _currentGeneratedImages.value = result.items.mapIndexed { index, item ->
                    saveImageToStorage(
                        item = item,
                        prompt = _prompt.value,
                        modelName = model.displayName,
                        index = index,
                        type = GenMediaEntity.TYPE_IMAGE_EDIT,
                        sourcePaths = sourcePaths,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e(TAG, "Failed to edit image", e)
                _error.value = e.message ?: "Unknown error occurred"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun cancelGeneration() {
        cancelJob?.cancel()
    }

    fun deleteImage(image: GeneratedImage) {
        appScope.launch {
            try {
                genMediaRepository.moveToTrash(image.id)
                _currentGeneratedImages.value = _currentGeneratedImages.value.filterNot {
                    it.id == image.id || it.filePath == image.filePath
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image", e)
                _error.value = "Failed to delete image"
            }
        }
    }

    fun permanentlyDeleteImage(image: GeneratedImage) {
        appScope.launch {
            try {
                permanentlyDeleteImageFileAndRecord(image)
                _currentGeneratedImages.value = _currentGeneratedImages.value.filterNot {
                    it.id == image.id || it.filePath == image.filePath
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to permanently delete image", e)
                _error.value = "Failed to permanently delete image"
            }
        }
    }

    fun permanentlyDeleteImages(images: List<GeneratedImage>) {
        appScope.launch {
            try {
                images.forEach { image ->
                    permanentlyDeleteImageFileAndRecord(image)
                }
                val ids = images.map { it.id }.toSet()
                val paths = images.map { it.filePath }.toSet()
                _currentGeneratedImages.value = _currentGeneratedImages.value.filterNot {
                    it.id in ids || it.filePath in paths
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear trash images", e)
                _error.value = "Failed to clear trash images"
            }
        }
    }

    fun restoreImage(image: GeneratedImage) {
        appScope.launch {
            try {
                val restoreType = if (image.sourcePaths.isNullOrBlank()) {
                    GenMediaEntity.TYPE_IMAGE_GENERATION
                } else {
                    GenMediaEntity.TYPE_IMAGE_EDIT
                }
                genMediaRepository.restoreMedia(image.id, restoreType)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore image", e)
                _error.value = "Failed to restore image"
            }
        }
    }

    private suspend fun permanentlyDeleteImageFileAndRecord(image: GeneratedImage) {
        genMediaRepository.deleteMedia(image.id)
        val file = File(image.filePath)
        if (file.exists()) {
            file.delete()
        }
    }

    private suspend fun saveImageToStorage(
        item: ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
        type: String = GenMediaEntity.TYPE_IMAGE_GENERATION,
        sourcePaths: String? = null,
    ): GeneratedImage {
        val imagesDir = filesManager.getImagesDir()
        val timestamp = System.currentTimeMillis()
        val extension = item.mimeType.substringAfterLast("/", "png").substringBefore(";").ifBlank { "png" }
        val filename = "${timestamp}_${modelName}_$index.$extension"
        val imageFile = File(imagesDir, filename)

        val createdFile = filesManager.createImageFileFromBase64(item.data, imageFile.absolutePath)
        val relativePath = "images/${createdFile.name}"
        val entity = GenMediaEntity(
            path = relativePath,
            modelId = modelName,
            prompt = prompt,
            createAt = timestamp,
            type = type,
            sourcePaths = sourcePaths,
        )
        val id = genMediaRepository.insertMedia(entity).toInt()

        return GeneratedImage(
            id = id,
            prompt = prompt,
            filePath = createdFile.absolutePath,
            timestamp = timestamp,
            model = modelName,
            type = type,
            sourcePaths = sourcePaths,
        )
    }

    private fun deleteReferenceFiles(paths: List<String>) {
        appScope.launch {
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    private fun maxImagesForCurrentModel(): Int = MAX_GENERATION_IMAGES

    private fun ImageGenerationSettings.resolveGptImage2SizeOrThrow(): String {
        val result = size.resolveGptImage2Size(customSize)
        result.error?.let { error ->
            throw IllegalArgumentException(error.toUserMessage())
        }
        return result.normalizedSize ?: "auto"
    }

    private fun ImageSizeValidationError.toUserMessage(): String {
        return when (this) {
            ImageSizeValidationError.EMPTY -> "请输入自定义尺寸，例如 2048x1152"
            ImageSizeValidationError.FORMAT -> "尺寸格式应为 宽x高，例如 2048x1152"
            ImageSizeValidationError.MAX_EDGE -> "自定义尺寸的最长边不能超过 3840px"
            ImageSizeValidationError.MULTIPLE_OF_16 -> "自定义尺寸的宽和高都必须是 16 的倍数"
            ImageSizeValidationError.ASPECT_RATIO -> "自定义尺寸的长短边比例不能超过 3:1"
            ImageSizeValidationError.TOTAL_PIXELS -> "自定义尺寸总像素需在 655360 到 8294400 之间"
        }
    }

    companion object {
        private const val TAG = "ImgGenSession"
        private const val GPT_IMAGE_2 = "gpt-image-2"
        private const val MAX_REFERENCE_IMAGES = 16
    }
}

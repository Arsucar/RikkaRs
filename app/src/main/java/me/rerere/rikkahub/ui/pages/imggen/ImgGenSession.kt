package me.rerere.rikkahub.ui.pages.imggen

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.util.concurrent.atomic.AtomicLong

/** 单次生成/编辑的最大出图数量 */
const val MAX_GENERATION_IMAGES = 4

/** 用户可配置并发上限的最大值（Semaphore 容量） */
const val MAX_CONCURRENT_IMAGE_GENERATION_JOBS_CAP = 8

/** @deprecated 使用设置中的 maxConcurrentJobs；仅作 UI/默认值回退 */
const val MAX_CONCURRENT_IMAGE_GENERATION_JOBS = 4

data class ImgGenActiveJob(
    val id: Long,
    val prompt: String,
    val images: List<GeneratedImage>,
    val isEdit: Boolean,
    val isRunning: Boolean = true,
    val isAwaitingPermit: Boolean = true,
    val errorMessage: String? = null,
    val referenceSourcePaths: List<String> = emptyList(),
    val numberOfImages: Int = 1,
    val aspectRatio: ImageAspectRatio = ImageAspectRatio.SQUARE,
)

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

    private val _activeJobs = MutableStateFlow<Map<Long, ImgGenActiveJob>>(emptyMap())
    val activeJobs: StateFlow<Map<Long, ImgGenActiveJob>> = _activeJobs.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        appScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                maxConcurrentJobsLimit = settings.imageGenerationSettings.maxConcurrentJobs.coerceIn(
                    1,
                    MAX_CONCURRENT_IMAGE_GENERATION_JOBS_CAP,
                )
            }
        }
        appScope.launch {
            _activeJobs.collect { jobs ->
                _isGenerating.value = jobs.values.any { it.isRunning }
            }
        }
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _referenceImages = MutableStateFlow<List<String>>(emptyList())
    val referenceImages: StateFlow<List<String>> = _referenceImages

    private val jobIdSeq = AtomicLong(0)
    private val jobSlotMutex = Mutex()
    private var reservedJobSlots = 0
    private val apiConcurrencyMutex = Mutex()
    private var apiInFlight = 0
    private val runningJobs = mutableMapOf<Long, Job>()
    private var maxConcurrentJobsLimit = MAX_CONCURRENT_IMAGE_GENERATION_JOBS

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

    fun reportUserError(message: String) {
        _error.value = message
    }

    fun startNewSession() {
        cancelGeneration()
        clearReferenceImages()
        _prompt.value = ""
        _error.value = null
        _numberOfImages.value = 1
        _aspectRatio.value = ImageAspectRatio.SQUARE
    }

    fun generateImage() {
        if (prompt.value.isBlank()) return
        appScope.launch {
            if (!tryReserveJobSlot()) return@launch
            val snapshot = captureGenerationSnapshot(isEdit = false)
            enqueueGenerationJob(snapshot)
        }
    }

    fun editImage() {
        if (prompt.value.isBlank() || referenceImages.value.isEmpty()) return
        appScope.launch {
            if (!tryReserveJobSlot()) return@launch
            val snapshot = captureGenerationSnapshot(isEdit = true)
            enqueueGenerationJob(snapshot)
        }
    }

    fun cancelGeneration() {
        val runningIds = runningJobs.keys.toList()
        runningIds.forEach { cancelJob(it) }
    }

    fun cancelJob(jobId: Long) {
        runningJobs.remove(jobId)?.cancel()
        _activeJobs.update { it - jobId }
    }

    fun dismissJob(jobId: Long) {
        runningJobs.remove(jobId)?.cancel()
        _activeJobs.update { it - jobId }
    }

    fun regenerateFromImage(image: GeneratedImage) {
        appScope.launch {
            if (!tryReserveJobSlot()) return@launch
            val parentJob = _activeJobs.value.values.firstOrNull { job ->
                job.images.any { it.id == image.id || it.filePath == image.filePath }
            }
            val refs = image.sourcePaths
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toList()
                .orEmpty()
            val isEdit = refs.isNotEmpty()
            enqueueSnapshotJob(
                prompt = image.prompt,
                numberOfImages = parentJob?.numberOfImages ?: 1,
                aspectRatio = parentJob?.aspectRatio ?: _aspectRatio.value,
                referenceImages = refs,
                isEdit = isEdit,
            )
        }
    }

    fun regenerateFromJob(jobId: Long) {
        val job = _activeJobs.value[jobId] ?: return
        appScope.launch {
            if (!tryReserveJobSlot()) return@launch
            dismissJob(jobId)
            val newJobId = jobIdSeq.incrementAndGet()
            _activeJobs.update {
                it + (
                    newJobId to ImgGenActiveJob(
                        id = newJobId,
                        prompt = job.prompt,
                        images = emptyList(),
                        isEdit = job.isEdit,
                        referenceSourcePaths = job.referenceSourcePaths,
                        numberOfImages = job.numberOfImages,
                        aspectRatio = job.aspectRatio,
                    )
                    )
            }
            val snapshot = GenerationSnapshot(
                jobId = newJobId,
                prompt = job.prompt,
                numberOfImages = job.numberOfImages,
                aspectRatio = job.aspectRatio,
                referenceImages = job.referenceSourcePaths,
                isEdit = job.isEdit,
            )
            enqueueGenerationJob(snapshot)
        }
    }

    fun deleteImage(image: GeneratedImage) {
        appScope.launch {
            try {
                genMediaRepository.moveToTrash(image.id)
                _activeJobs.update { jobs ->
                    jobs.mapValues { (_, job) ->
                        job.copy(images = job.images.filterNot {
                            it.id == image.id || it.filePath == image.filePath
                        })
                    }
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
                _activeJobs.update { jobs ->
                    jobs.mapValues { (_, job) ->
                        job.copy(images = job.images.filterNot {
                            it.id == image.id || it.filePath == image.filePath
                        })
                    }
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
                _activeJobs.update { jobs ->
                    jobs.mapValues { (_, job) ->
                        job.copy(images = job.images.filterNot {
                            it.id in ids || it.filePath in paths
                        })
                    }
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

    private data class GenerationSnapshot(
        val jobId: Long,
        val prompt: String,
        val numberOfImages: Int,
        val aspectRatio: ImageAspectRatio,
        val referenceImages: List<String>,
        val isEdit: Boolean,
    )

    private fun enqueueSnapshotJob(
        prompt: String,
        numberOfImages: Int,
        aspectRatio: ImageAspectRatio,
        referenceImages: List<String>,
        isEdit: Boolean,
    ) {
        val jobId = jobIdSeq.incrementAndGet()
        _activeJobs.update {
            it + (
                jobId to ImgGenActiveJob(
                    id = jobId,
                    prompt = prompt,
                    images = emptyList(),
                    isEdit = isEdit,
                    referenceSourcePaths = if (isEdit) referenceImages else emptyList(),
                    numberOfImages = numberOfImages,
                    aspectRatio = aspectRatio,
                )
            )
        }
        enqueueGenerationJob(
            GenerationSnapshot(
                jobId = jobId,
                prompt = prompt,
                numberOfImages = numberOfImages,
                aspectRatio = aspectRatio,
                referenceImages = referenceImages,
                isEdit = isEdit,
            ),
        )
    }

    private fun captureGenerationSnapshot(isEdit: Boolean): GenerationSnapshot {
        val jobId = jobIdSeq.incrementAndGet()
        _activeJobs.update {
            it + (
                jobId to ImgGenActiveJob(
                    id = jobId,
                    prompt = _prompt.value,
                    images = emptyList(),
                    isEdit = isEdit,
                    referenceSourcePaths = if (isEdit) _referenceImages.value.toList() else emptyList(),
                    numberOfImages = _numberOfImages.value,
                    aspectRatio = _aspectRatio.value,
                )
                )
        }
        return GenerationSnapshot(
            jobId = jobId,
            prompt = _prompt.value,
            numberOfImages = _numberOfImages.value,
            aspectRatio = _aspectRatio.value,
            referenceImages = _referenceImages.value.toList(),
            isEdit = isEdit,
        )
    }

    private fun enqueueGenerationJob(snapshot: GenerationSnapshot) {
        val job = appScope.launch {
            withApiConcurrencyLimit {
                markJobStarted(snapshot.jobId)
                runGeneration(snapshot)
            }
        }
        runningJobs[snapshot.jobId] = job
        job.invokeOnCompletion { cause ->
            runningJobs.remove(snapshot.jobId)
            appScope.launch { releaseJobSlot() }
            when {
                cause is CancellationException -> {
                    _activeJobs.update { it - snapshot.jobId }
                }

                cause == null -> {
                    _activeJobs.update { jobs ->
                        val existing = jobs[snapshot.jobId] ?: return@update jobs
                        jobs + (
                            snapshot.jobId to existing.copy(
                                isRunning = false,
                                isAwaitingPermit = false,
                                errorMessage = null,
                            )
                            )
                    }
                }

                else -> {
                    _activeJobs.update { jobs ->
                        val existing = jobs[snapshot.jobId] ?: return@update jobs
                        if (existing.errorMessage != null) {
                            jobs
                        } else {
                            jobs + (
                                snapshot.jobId to existing.copy(
                                    isRunning = false,
                                    isAwaitingPermit = false,
                                    errorMessage = cause.message ?: "Unknown error occurred",
                                )
                                )
                        }
                    }
                }
            }
        }
    }

    private suspend fun runGeneration(snapshot: GenerationSnapshot) {
        try {
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

            val imagesFlow = if (snapshot.isEdit) {
                val params = ImageEditParams(
                    model = model,
                    prompt = snapshot.prompt,
                    images = snapshot.referenceImages,
                    numOfImages = snapshot.numberOfImages.coerceIn(1, MAX_GENERATION_IMAGES),
                    aspectRatio = snapshot.aspectRatio,
                    size = gptImage2Size,
                    quality = imageSettings.quality.takeIf { isGptImage2 },
                    outputFormat = imageSettings.outputFormat.takeIf { isGptImage2 },
                    outputCompression = imageSettings.outputCompression.takeIf {
                        isGptImage2 && imageSettings.outputFormat.supportsCompression
                    },
                    background = imageSettings.background.takeIf { isGptImage2 },
                    moderation = imageSettings.moderation.takeIf { isGptImage2 },
                    stream = imageSettings.imageStreaming,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                )
                providerManager.getProviderByType(provider).editImage(providerSetting, params)
            } else {
                val params = ImageGenerationParams(
                    model = model,
                    prompt = snapshot.prompt,
                    numOfImages = snapshot.numberOfImages.coerceIn(1, MAX_GENERATION_IMAGES),
                    aspectRatio = snapshot.aspectRatio,
                    size = gptImage2Size,
                    quality = imageSettings.quality.takeIf { isGptImage2 },
                    outputFormat = imageSettings.outputFormat.takeIf { isGptImage2 },
                    outputCompression = imageSettings.outputCompression.takeIf {
                        isGptImage2 && imageSettings.outputFormat.supportsCompression
                    },
                    background = imageSettings.background.takeIf { isGptImage2 },
                    moderation = imageSettings.moderation.takeIf { isGptImage2 },
                    stream = imageSettings.imageStreaming,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                )
                providerManager.getProviderByType(provider).generateImage(providerSetting, params)
            }

            collectImageGeneration(
                jobId = snapshot.jobId,
                images = imagesFlow,
                prompt = snapshot.prompt,
                modelName = model.displayName,
                type = if (snapshot.isEdit) GenMediaEntity.TYPE_IMAGE_EDIT else GenMediaEntity.TYPE_IMAGE_GENERATION,
                sourcePaths = snapshot.referenceImages.takeIf { snapshot.isEdit }?.joinToString(separator = "\n"),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to generate image", e)
            val message = e.message ?: "Unknown error occurred"
            _error.value = message
            _activeJobs.update { jobs ->
                val existing = jobs[snapshot.jobId] ?: return@update jobs
                jobs + (
                    snapshot.jobId to existing.copy(
                        isRunning = false,
                        isAwaitingPermit = false,
                        errorMessage = message,
                    )
                    )
            }
            throw e
        }
    }

    private suspend fun permanentlyDeleteImageFileAndRecord(image: GeneratedImage) {
        genMediaRepository.deleteMedia(image.id)
        val file = File(image.filePath)
        if (file.exists()) {
            file.delete()
        }
    }

    private suspend fun collectImageGeneration(
        jobId: Long,
        images: Flow<ImageGenerationItem>,
        prompt: String,
        modelName: String,
        type: String = GenMediaEntity.TYPE_IMAGE_GENERATION,
        sourcePaths: String? = null,
    ) {
        val finalImages = mutableListOf<GeneratedImage>()
        var previewFile: File? = null
        var finalIndex = 0

        images.collect { item ->
            if (item.partial) {
                previewFile?.delete()
                val imageFile = saveImagePreview(
                    item = item,
                    modelName = modelName,
                    index = item.partialImageIndex ?: finalIndex,
                )
                previewFile = imageFile
                updateJobImages(
                    jobId,
                    finalImages + GeneratedImage(
                        id = 0,
                        prompt = prompt,
                        filePath = imageFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        model = modelName,
                        type = type,
                        sourcePaths = sourcePaths,
                    ),
                )
            } else {
                previewFile?.delete()
                previewFile = null
                val saved = saveImageToStorage(
                    item = item,
                    prompt = prompt,
                    modelName = modelName,
                    index = finalIndex,
                    type = type,
                    sourcePaths = sourcePaths,
                )
                finalImages.add(saved)
                finalIndex++
                updateJobImages(jobId, finalImages.toList())
            }
        }
    }

    private fun updateJobImages(jobId: Long, images: List<GeneratedImage>) {
        _activeJobs.update { jobs ->
            val existing = jobs[jobId] ?: return@update jobs
            jobs + (jobId to existing.copy(images = images))
        }
    }

    private fun saveImagePreview(
        item: ImageGenerationItem,
        modelName: String,
        index: Int,
    ): File {
        val imagesDir = filesManager.getImagesDir()
        val timestamp = System.currentTimeMillis()
        val previewFile = File(imagesDir, "preview_${timestamp}_${modelName}_$index.png")
        return filesManager.createImageFileFromBase64(item.data, previewFile.absolutePath)
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

    private suspend fun tryReserveJobSlot(): Boolean {
        val limit = maxConcurrentJobsLimit
        val reserved = jobSlotMutex.withLock {
            if (reservedJobSlots >= limit) {
                false
            } else {
                reservedJobSlots++
                true
            }
        }
        if (!reserved) {
            _error.value = "最多同时进行 $limit 个生图请求（已完成任务不占名额）"
        }
        return reserved
    }

    private suspend fun releaseJobSlot() {
        jobSlotMutex.withLock {
            if (reservedJobSlots > 0) {
                reservedJobSlots--
            }
        }
    }

    private suspend fun <T> withApiConcurrencyLimit(block: suspend () -> T): T {
        while (true) {
            val acquired = apiConcurrencyMutex.withLock {
                if (apiInFlight < maxConcurrentJobsLimit) {
                    apiInFlight++
                    true
                } else {
                    false
                }
            }
            if (acquired) break
            delay(IMGGEN_POLL_INTERVAL_MS)
        }
        return try {
            block()
        } finally {
            apiConcurrencyMutex.withLock {
                if (apiInFlight > 0) {
                    apiInFlight--
                }
            }
        }
    }

    private fun markJobStarted(jobId: Long) {
        _activeJobs.update { jobs ->
            val existing = jobs[jobId] ?: return@update jobs
            jobs + (jobId to existing.copy(isAwaitingPermit = false))
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
        private const val IMGGEN_POLL_INTERVAL_MS = 50L
    }
}

package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ImageSizeOption(val apiValue: String?) {
    @SerialName("auto")
    AUTO("auto"),

    @SerialName("1024x1024")
    SIZE_1024_1024("1024x1024"),

    @SerialName("1536x1024")
    SIZE_1536_1024("1536x1024"),

    @Deprecated("Kept for decoding previously saved settings")
    @SerialName("1792x1024")
    SIZE_1792_1024("1792x1024"),

    @SerialName("1024x1536")
    SIZE_1024_1536("1024x1536"),

    @Deprecated("Kept for decoding previously saved settings")
    @SerialName("1024x1792")
    SIZE_1024_1792("1024x1792"),

    @Deprecated("Kept for decoding previously saved settings")
    @SerialName("2048x1024")
    SIZE_2048_1024("2048x1024"),

    @SerialName("2048x2048")
    SIZE_2048_2048("2048x2048"),

    @SerialName("2048x1152")
    SIZE_2048_1152("2048x1152"),

    @SerialName("3840x2160")
    SIZE_3840_2160("3840x2160"),

    @SerialName("2160x3840")
    SIZE_2160_3840("2160x3840"),

    @SerialName("custom")
    CUSTOM(null);

    companion object {
        val selectableEntries = listOf(
            AUTO,
            SIZE_1024_1024,
            SIZE_1536_1024,
            SIZE_1024_1536,
            SIZE_2048_2048,
            SIZE_2048_1152,
            SIZE_3840_2160,
            SIZE_2160_3840,
            CUSTOM,
        )
    }
}

@Serializable
enum class ImageQualityOption(val apiValue: String) {
    @SerialName("auto")
    AUTO("auto"),

    @SerialName("low")
    LOW("low"),

    @SerialName("medium")
    MEDIUM("medium"),

    @SerialName("high")
    HIGH("high"),
}

@Serializable
enum class ImageOutputFormatOption(val apiValue: String, val mimeType: String, val supportsCompression: Boolean) {
    @SerialName("png")
    PNG("png", "image/png", false),

    @SerialName("jpeg")
    JPEG("jpeg", "image/jpeg", true),

    @SerialName("webp")
    WEBP("webp", "image/webp", true),

    @Deprecated("Kept for decoding previously saved settings")
    @SerialName("url")
    URL("png", "image/png", false),

    @Deprecated("Kept for decoding previously saved settings")
    @SerialName("b64_json")
    B64_JSON("png", "image/png", false);

    companion object {
        val selectableEntries = listOf(PNG, JPEG, WEBP)
    }
}

@Serializable
enum class ImageBackgroundOption(val apiValue: String) {
    @SerialName("auto")
    AUTO("auto"),

    @SerialName("opaque")
    OPAQUE("opaque"),
}

@Serializable
enum class ImageModerationOption(val apiValue: String) {
    @SerialName("auto")
    AUTO("auto"),

    @SerialName("low")
    LOW("low"),
}

enum class ImageSizeValidationError {
    EMPTY,
    FORMAT,
    MAX_EDGE,
    MULTIPLE_OF_16,
    ASPECT_RATIO,
    TOTAL_PIXELS,
}

data class ImageSizeValidationResult(
    val normalizedSize: String?,
    val error: ImageSizeValidationError?,
)

fun ImageSizeOption.resolveGptImage2Size(customSize: String): ImageSizeValidationResult {
    if (this != ImageSizeOption.CUSTOM) {
        return ImageSizeValidationResult(
            normalizedSize = apiValue ?: "auto",
            error = null,
        )
    }
    return validateGptImage2Size(customSize)
}

fun validateGptImage2Size(input: String): ImageSizeValidationResult {
    val normalized = input.normalizeImageSizeInput()
    if (normalized.isBlank()) {
        return ImageSizeValidationResult(normalizedSize = null, error = ImageSizeValidationError.EMPTY)
    }

    val match = IMAGE_SIZE_PATTERN.matchEntire(normalized)
        ?: return ImageSizeValidationResult(normalizedSize = normalized, error = ImageSizeValidationError.FORMAT)
    val width = match.groupValues[1].toInt()
    val height = match.groupValues[2].toInt()
    val longEdge = maxOf(width, height)
    val shortEdge = minOf(width, height)
    val totalPixels = width.toLong() * height.toLong()

    return when {
        longEdge > 3840 -> ImageSizeValidationResult(normalized, ImageSizeValidationError.MAX_EDGE)
        width % 16 != 0 || height % 16 != 0 -> {
            ImageSizeValidationResult(normalized, ImageSizeValidationError.MULTIPLE_OF_16)
        }
        longEdge > shortEdge * 3 -> ImageSizeValidationResult(normalized, ImageSizeValidationError.ASPECT_RATIO)
        totalPixels !in 655_360L..8_294_400L -> {
            ImageSizeValidationResult(normalized, ImageSizeValidationError.TOTAL_PIXELS)
        }
        else -> ImageSizeValidationResult(normalized, error = null)
    }
}

private fun String.normalizeImageSizeInput(): String {
    return trim()
        .lowercase()
        .replace("×", "x")
        .replace("\\s+".toRegex(), "")
}

private val IMAGE_SIZE_PATTERN = Regex("""^(\d{2,5})x(\d{2,5})$""")

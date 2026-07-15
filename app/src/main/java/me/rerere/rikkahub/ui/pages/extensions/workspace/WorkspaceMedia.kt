package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.Intent
import android.net.Uri
import java.net.URLConnection
import me.rerere.rikkahub.utils.isMarkdownFileName
import me.rerere.rikkahub.utils.isTextLikeFileName

internal enum class WorkspaceFileKind {
    TEXT,
    IMAGE,
    OTHER,
}

internal fun classifyWorkspaceFile(fileName: String): WorkspaceFileKind = when {
    isTextLikeFileName(fileName) || isMarkdownFileName(fileName) -> WorkspaceFileKind.TEXT
    workspaceFileExtension(fileName) in IMAGE_EXTENSIONS -> WorkspaceFileKind.IMAGE
    else -> WorkspaceFileKind.OTHER
}

internal fun workspaceMimeType(fileName: String): String {
    val extension = workspaceFileExtension(fileName)
    return MIME_TYPES[extension]
        ?: URLConnection.guessContentTypeFromName("file.$extension")
        ?: FALLBACK_MIME_TYPE
}

internal fun buildWorkspaceViewIntent(uri: Uri, mimeType: String): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

private fun workspaceFileExtension(fileName: String): String = fileName
    .substringBefore('?')
    .substringBefore('#')
    .substringAfterLast('.', missingDelimiterValue = "")
    .lowercase()

private val IMAGE_EXTENSIONS = setOf("avif", "bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "webp")
private val MIME_TYPES = mapOf(
    "avi" to "video/x-msvideo",
    "avif" to "image/avif",
    "bmp" to "image/bmp",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "gif" to "image/gif",
    "heic" to "image/heic",
    "heif" to "image/heif",
    "jpeg" to "image/jpeg",
    "jpg" to "image/jpeg",
    "m4v" to "video/x-m4v",
    "mkv" to "video/x-matroska",
    "mov" to "video/quicktime",
    "mp3" to "audio/mpeg",
    "mp4" to "video/mp4",
    "ogg" to "audio/ogg",
    "pdf" to "application/pdf",
    "png" to "image/png",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "wav" to "audio/wav",
    "webm" to "video/webm",
    "webp" to "image/webp",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "zip" to "application/zip",
)
private const val FALLBACK_MIME_TYPE = "application/octet-stream"

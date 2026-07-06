package me.rerere.rikkahub.utils

import java.util.Locale

const val MAX_TEXT_FILE_VIEW_BYTES: Long = 5L * 1024L * 1024L

private val TEXT_FILE_EXTENSIONS = setOf(
    "bat",
    "c",
    "cc",
    "cfg",
    "conf",
    "cpp",
    "cs",
    "css",
    "csv",
    "dart",
    "diff",
    "env",
    "go",
    "gradle",
    "graphql",
    "h",
    "hpp",
    "html",
    "ini",
    "java",
    "js",
    "json",
    "jsx",
    "kt",
    "kts",
    "log",
    "lua",
    "markdown",
    "md",
    "mdown",
    "mjs",
    "mkd",
    "mkdn",
    "patch",
    "php",
    "properties",
    "py",
    "rb",
    "rs",
    "scss",
    "sh",
    "sql",
    "swift",
    "toml",
    "ts",
    "tsx",
    "txt",
    "xml",
    "yaml",
    "yml",
)

private val TEXT_FILE_NAMES = setOf(
    ".bash_profile",
    ".bashrc",
    ".dockerignore",
    ".editorconfig",
    ".env",
    ".gitattributes",
    ".gitignore",
    ".gitkeep",
    ".gitmodules",
    ".npmrc",
    ".profile",
    ".prettierrc",
    ".prettierignore",
    ".yarnrc",
    ".zprofile",
    ".zshrc",
    "authors",
    "changelog",
    "codeowners",
    "contributors",
    "copying",
    "dockerfile",
    "gemfile",
    "license",
    "makefile",
    "notice",
    "procfile",
    "rakefile",
    "readme",
    "vagrantfile",
)

private val TEXT_FILE_NAME_PREFIXES = setOf(
    ".env.",
    ".eslintrc",
    ".prettierrc",
    ".stylelintrc",
)

private val TEXT_MIME_TYPES = setOf(
    "application/graphql",
    "application/javascript",
    "application/json",
    "application/ld+json",
    "application/markdown",
    "application/toml",
    "application/x-httpd-php",
    "application/x-javascript",
    "application/x-sh",
    "application/xhtml+xml",
    "application/xml",
)

fun isTextLikeFileName(fileName: String): Boolean {
    val normalized = fileName.substringBefore('?').substringBefore('#')
    val lowerName = normalized.lowercase(Locale.ROOT)
    val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    return extension in TEXT_FILE_EXTENSIONS ||
        lowerName in TEXT_FILE_NAMES ||
        TEXT_FILE_NAME_PREFIXES.any { lowerName.startsWith(it) }
}

fun isMarkdownFileName(fileName: String): Boolean {
    val normalized = fileName.substringBefore('?').substringBefore('#')
    val lowerName = normalized.lowercase(Locale.ROOT)
    val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    return extension in setOf("md", "markdown", "mdown", "mkd", "mkdn") || lowerName == "readme"
}

fun isTextLikeMime(mime: String?): Boolean {
    if (mime.isNullOrBlank()) return false
    val normalized = mime.substringBefore(';').trim().lowercase(Locale.ROOT)
    return normalized.startsWith("text/") || normalized in TEXT_MIME_TYPES || normalized.endsWith("+json") ||
        normalized.endsWith("+xml")
}

fun isTextLikeFile(fileName: String, mime: String? = null): Boolean {
    return isTextLikeMime(mime) || isTextLikeFileName(fileName)
}

fun isTextFileSizeAllowed(sizeBytes: Long): Boolean {
    return sizeBytes in 0..MAX_TEXT_FILE_VIEW_BYTES
}

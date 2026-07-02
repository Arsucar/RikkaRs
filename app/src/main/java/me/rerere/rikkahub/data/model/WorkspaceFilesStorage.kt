package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorkspaceFilesStorage {
    PRIVATE,
    EXTERNAL,
}
package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorkspaceFilesStorage {
    PRIVATE,
    EXTERNAL,
}

fun WorkspaceFilesStorage.supportsWorkspaceGitPackWrites(): Boolean =
    this == WorkspaceFilesStorage.PRIVATE

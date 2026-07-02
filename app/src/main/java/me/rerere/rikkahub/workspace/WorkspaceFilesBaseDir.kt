package me.rerere.rikkahub.workspace

import android.content.Context
import me.rerere.rikkahub.data.model.WorkspaceFilesStorage
import java.io.File

fun resolveWorkspaceFilesBaseDir(
    context: Context,
    storage: WorkspaceFilesStorage,
): File {
    return when (storage) {
        WorkspaceFilesStorage.PRIVATE -> File(context.filesDir, "workspaces")
        WorkspaceFilesStorage.EXTERNAL -> {
            context.getExternalFilesDir("workspaces")
                ?: error("External storage unavailable")
        }
    }
}

fun peekWorkspaceFilesBaseDir(
    context: Context,
    storage: WorkspaceFilesStorage,
): File? {
    return when (storage) {
        WorkspaceFilesStorage.PRIVATE -> File(context.filesDir, "workspaces")
        WorkspaceFilesStorage.EXTERNAL -> context.getExternalFilesDir("workspaces")
    }
}
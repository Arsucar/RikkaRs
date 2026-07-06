package me.rerere.workspace

import java.io.File

class WorkspaceProotCommandBuilder(
    // Keep disabled for normal workspace shells: Git finalizes pack files with
    // hard-link/rename flows that --link2symlink corrupts on bind-mounted /workspace.
    private val emulateHardLinksWithSymlinks: Boolean = false,
) {
    fun buildArgs(
        rootfsDir: File,
        workingDirectory: String,
        workspaceFilesDir: File,
        bindMounts: List<WorkspaceBindMount> = emptyList(),
    ): List<String> = buildList {
        add("--root-id")
        if (emulateHardLinksWithSymlinks) {
            add("--link2symlink")
        }
        add("--kill-on-exit")
        add("-r")
        add(rootfsDir.absolutePath)
        add("-w")
        add(workingDirectory)
        add("-b")
        add("${workspaceFilesDir.absolutePath}:$WORKSPACE_DIR")

        bindMounts.forEach { mount ->
            if (mount.source.exists()) {
                add("-b")
                add("${mount.source.absolutePath}:${mount.target.trimEnd('/')}")
            }
        }

        listOf("/dev", "/proc", "/sys").forEach { path ->
            if (File(path).exists()) {
                add("-b")
                add(path)
            }
        }
    }

    companion object {
        const val WORKSPACE_DIR = "/workspace"
    }
}

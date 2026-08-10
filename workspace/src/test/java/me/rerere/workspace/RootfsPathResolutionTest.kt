package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class RootfsPathResolutionTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var skillsDir: File
    private lateinit var manager: WorkspaceManager

    private val root = "test-workspace"

    private fun createManager(): WorkspaceManager {
        skillsDir = tempFolder.newFolder("skills")
        val uploadDir = tempFolder.newFolder("upload")
        return WorkspaceManager(
            baseDir = tempFolder.newFolder("workspaces"),
            bindMounts = listOf(
                WorkspaceBindMount(source = skillsDir, target = "/skills"),
                WorkspaceBindMount(source = uploadDir, target = "/upload"),
            ),
        ).also { it.ensureWorkspace(root) }
    }

    @Test
    fun readsFileWrittenThroughBindMountPath() {
        manager = createManager()
        File(skillsDir, "issue-1561").mkdirs()
        File(skillsDir, "issue-1561/SKILL.md").writeText("---\nversion: before\n---\n")

        val size = manager.rootfsFileSize(root, "/skills/issue-1561/SKILL.md")
        val buffer = ByteArrayOutputStream(size.toInt())
        manager.exportRootfsFile(root, "/skills/issue-1561/SKILL.md", buffer)

        assertEquals("---\nversion: before\n---\n", buffer.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun bindMountTargetDoesNotMatchLongerSiblingPrefix() {
        val skills = tempFolder.newFolder("skills-src")
        val skillsets = tempFolder.newFolder("skillsets-src")
        val manager = WorkspaceManager(
            baseDir = tempFolder.newFolder("workspaces"),
            bindMounts = listOf(
                WorkspaceBindMount(source = skills, target = "/skills"),
                WorkspaceBindMount(source = skillsets, target = "/skillsets"),
            ),
        ).also { it.ensureWorkspace(root) }

        assertEquals(skills, manager.resolveRootfsPath(root, "/skills/a.md").rootDir)
        assertEquals(skillsets, manager.resolveRootfsPath(root, "/skillsets/a.md").rootDir)
    }

    @Test
    fun workspacePathStillResolvesToFilesArea() {
        manager = createManager()
        File(manager.filesDir(root), "notes.txt").writeText("hello")

        val location = manager.resolveRootfsPath(root, "/workspace/notes.txt")
        assertEquals(manager.filesDir(root), location.rootDir)
        assertEquals("notes.txt", location.relativePath)

        val buffer = ByteArrayOutputStream()
        manager.exportRootfsFile(root, "/workspace/notes.txt", buffer)
        assertEquals("hello", buffer.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun unknownAbsolutePathFallsBackToRootfsInterior() {
        manager = createManager()
        File(manager.linuxDir(root), "etc").mkdirs()
        File(manager.linuxDir(root), "etc/hostname").writeText("rikkahub\n")

        val buffer = ByteArrayOutputStream()
        manager.exportRootfsFile(root, "/etc/hostname", buffer)
        assertEquals("rikkahub\n", buffer.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun traversalOutOfBindMountIsRejected() {
        manager = createManager()
        tempFolder.newFile("secret.txt").writeText("secret")

        val error = assertThrows(IllegalArgumentException::class.java) {
            manager.rootfsFileSize(root, "/skills/../secret.txt")
        }
        assertTrue(error.message!!.contains("escapes workspace root"))
    }

    @Test
    fun kernelFilesystemPathIsRejectedWithHint() {
        manager = createManager()

        val error = assertThrows(IllegalStateException::class.java) {
            manager.rootfsFileSize(root, "/proc/version")
        }
        assertTrue(error.message!!.contains("workspace_shell"))
    }

    @Test
    fun missingFileReportsOriginalAbsolutePath() {
        manager = createManager()

        val error = assertThrows(IllegalArgumentException::class.java) {
            manager.rootfsFileSize(root, "/skills/missing/SKILL.md")
        }
        assertEquals("File does not exist: /skills/missing/SKILL.md", error.message)
    }

    @Test
    fun directoryPathIsNotReadableAsFile() {
        manager = createManager()
        File(skillsDir, "issue-1561").mkdirs()

        val error = assertThrows(IllegalArgumentException::class.java) {
            manager.rootfsFileSize(root, "/skills/issue-1561")
        }
        assertEquals("Path is not a file: /skills/issue-1561", error.message)
    }

    @Test
    fun listFilesLinuxRedirectsToBindMountSource() {
        manager = createManager()
        File(skillsDir, "issue-1561").mkdirs()
        File(skillsDir, "issue-1561/SKILL.md").writeText("skill-body\n")

        val entries = manager.listFiles(root, "skills", WorkspaceStorageArea.LINUX)
        assertTrue(entries.any { it.name == "issue-1561" && it.isDirectory })

        val nested = manager.listFiles(root, "skills/issue-1561", WorkspaceStorageArea.LINUX)
        assertTrue(nested.any { it.name == "SKILL.md" && !it.isDirectory })
        assertEquals("skills/issue-1561/SKILL.md", nested.first { it.name == "SKILL.md" }.path)
    }

    @Test
    fun listFilesLinuxRootSynthesizesMountPlaceholders() {
        manager = createManager()
        // 不创建 linux 占位目录, 仍应能在根列表看到挂载名
        val names = manager.listFiles(root, "", WorkspaceStorageArea.LINUX).map { it.name }.toSet()
        assertTrue(names.contains("skills"))
        assertTrue(names.contains("upload"))
        assertTrue(names.contains("workspace"))
    }

    @Test
    fun listFilesLinuxWorkspaceMapsToFilesArea() {
        manager = createManager()
        File(manager.filesDir(root), "notes.txt").writeText("hello")
        File(manager.filesDir(root), "docs").mkdirs()

        val entries = manager.listFiles(root, "workspace", WorkspaceStorageArea.LINUX)
        assertTrue(entries.any { it.name == "notes.txt" && !it.isDirectory })
        assertTrue(entries.any { it.name == "docs" && it.isDirectory })
        assertEquals("workspace/notes.txt", entries.first { it.name == "notes.txt" }.path)
    }

    @Test
    fun listFilesLinuxEmptyOrMissingMountSourceReturnsEmpty() {
        manager = createManager()
        // skillsDir 存在但空
        assertTrue(manager.listFiles(root, "skills", WorkspaceStorageArea.LINUX).isEmpty())

        val missingSource = tempFolder.newFolder("missing-skills-parent")
        val gone = File(missingSource, "gone")
        val managerMissing = WorkspaceManager(
            baseDir = tempFolder.newFolder("workspaces-missing"),
            bindMounts = listOf(
                WorkspaceBindMount(source = gone, target = "/skills"),
            ),
        ).also { it.ensureWorkspace(root) }
        assertTrue(managerMissing.listFiles(root, "skills", WorkspaceStorageArea.LINUX).isEmpty())
    }

    @Test
    fun listFilesLinuxKernelPathDoesNotRedirectAsMount() {
        manager = createManager()
        File(manager.linuxDir(root), "proc").mkdirs()
        File(manager.linuxDir(root), "proc/placeholder").writeText("x")

        val entries = manager.listFiles(root, "proc", WorkspaceStorageArea.LINUX)
        assertTrue(entries.any { it.name == "placeholder" })
        // 不得映射到 bind mount
        assertEquals(
            manager.linuxDir(root),
            manager.resolveRootfsPath(root, "/etc").rootDir,
        )
    }

    @Test
    fun readTextAndExportFileLinuxUseBindMountSource() {
        manager = createManager()
        File(skillsDir, "issue-1561").mkdirs()
        File(skillsDir, "issue-1561/SKILL.md").writeText("from-host\n")

        val text = manager.readText(
            root = root,
            path = "skills/issue-1561/SKILL.md",
            area = WorkspaceStorageArea.LINUX,
        )
        assertEquals("from-host\n", text)
        assertEquals(
            "from-host\n".toByteArray().size.toLong(),
            manager.fileSize(root, "skills/issue-1561/SKILL.md", WorkspaceStorageArea.LINUX),
        )

        val buffer = ByteArrayOutputStream()
        manager.exportFile(
            root = root,
            path = "skills/issue-1561/SKILL.md",
            area = WorkspaceStorageArea.LINUX,
            outputStream = buffer,
        )
        assertEquals("from-host\n", buffer.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun listFilesLinuxExtraBindMountSkillsPrivate() {
        manager = createManager()
        val privateDir = tempFolder.newFolder("assistant-skills")
        File(privateDir, "private-skill").mkdirs()
        File(privateDir, "private-skill/SKILL.md").writeText("private\n")

        val extra = listOf(
            WorkspaceBindMount(source = privateDir, target = "/skills_private"),
        )
        val rootEntries = manager.listFiles(
            root = root,
            path = "",
            area = WorkspaceStorageArea.LINUX,
            extraBindMounts = extra,
        )
        assertTrue(rootEntries.any { it.name == "skills_private" })

        val entries = manager.listFiles(
            root = root,
            path = "skills_private",
            area = WorkspaceStorageArea.LINUX,
            extraBindMounts = extra,
        )
        assertTrue(entries.any { it.name == "private-skill" })

        val text = manager.readText(
            root = root,
            path = "skills_private/private-skill/SKILL.md",
            area = WorkspaceStorageArea.LINUX,
            extraBindMounts = extra,
        )
        assertEquals("private\n", text)
    }

    @Test
    fun bindMountsExposesConstructorTable() {
        manager = createManager()
        val targets = manager.bindMounts().map { it.target }.toSet()
        assertTrue(targets.contains("/skills"))
        assertTrue(targets.contains("/upload"))
    }
}

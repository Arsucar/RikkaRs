package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.model.GitChangeKind
import me.rerere.rikkahub.data.model.GitChangeSection
import me.rerere.rikkahub.data.model.GitRepositoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import me.rerere.workspace.WorkspaceCommandResult
import java.nio.charset.StandardCharsets

class WorkspaceGitRepositoryTest {
    @Test
    fun parsesBranchAndAllChangeGroupsWithNulPaths() {
        val output = listOf(
            "# branch.oid 0123456789abcdef",
            "# branch.head feature/git-drawer",
            ordinary("M.", "staged file.txt"),
            ordinary(".M", "unstaged.txt"),
            ordinary("MM", "both.txt"),
            renamed("R.", "new name.txt"),
            "old name.txt",
            unmerged("AU", "conflict.txt"),
            "? untracked file.txt",
            "",
        ).joinToString("\u0000")

        val parsed = parseGitStatusPorcelain(output, maxFiles = 300)
        val status = GitRepositoryStatus(
            workspaceId = "workspace",
            projectName = "Project",
            branch = parsed.branch,
            detachedHead = parsed.detachedHead,
            changes = parsed.changes,
            truncated = parsed.truncated,
        )

        assertEquals("feature/git-drawer", parsed.branch)
        assertNull(parsed.detachedHead)
        assertFalse(parsed.truncated)
        assertEquals(6, status.changeCount)
        assertEquals(
            listOf("staged file.txt", "both.txt", "new name.txt", "conflict.txt"),
            status.changesIn(GitChangeSection.STAGED).map { it.path },
        )
        assertEquals(
            listOf("unstaged.txt", "both.txt", "conflict.txt"),
            status.changesIn(GitChangeSection.UNSTAGED).map { it.path },
        )
        assertEquals(
            listOf("untracked file.txt"),
            status.changesIn(GitChangeSection.UNTRACKED).map { it.path },
        )
        val rename = parsed.changes.first { it.path == "new name.txt" }
        assertEquals("old name.txt", rename.originalPath)
        assertEquals(GitChangeKind.RENAMED, rename.kindFor(GitChangeSection.STAGED))
        assertEquals(GitChangeKind.CONFLICTED, parsed.changes.first { it.path == "conflict.txt" }
            .kindFor(GitChangeSection.STAGED))
        assertEquals(GitChangeKind.CONFLICTED, parsed.changes.first { it.path == "conflict.txt" }
            .kindFor(GitChangeSection.UNSTAGED))
    }

    @Test
    fun parsesDetachedHeadAndTruncatesByFileCount() {
        val records = buildList {
            add("# branch.oid abcdef1234567890")
            add("# branch.head (detached)")
            repeat(4) { index -> add(ordinary(".M", "file-$index.txt")) }
            add("")
        }

        val parsed = parseGitStatusPorcelain(records.joinToString("\u0000"), maxFiles = 3)

        assertNull(parsed.branch)
        assertEquals("abcdef12", parsed.detachedHead)
        assertEquals(3, parsed.changes.size)
        assertTrue(parsed.truncated)
    }

    @Test
    fun everyUnmergedStatusIsDisplayedAsConflictInBothGroups() {
        listOf("DD", "AU", "UD", "UA", "DU", "AA", "UU").forEach { status ->
            val parsed = parseGitStatusPorcelain(
                listOf(unmerged(status, "$status.txt"), "").joinToString("\u0000"),
                maxFiles = 10,
            )
            val change = parsed.changes.single()

            assertEquals(GitChangeKind.CONFLICTED, change.kindFor(GitChangeSection.STAGED))
            assertEquals(GitChangeKind.CONFLICTED, change.kindFor(GitChangeSection.UNSTAGED))
        }
    }

    @Test
    fun malformedRecordsAreIgnoredWithoutLosingValidEntries() {
        val output = listOf(
            "1 broken",
            "2 R. broken",
            "missing original",
            "? valid.txt",
            "! ignored.txt",
            "",
        ).joinToString("\u0000")

        val parsed = parseGitStatusPorcelain(output, maxFiles = 10)

        assertEquals(listOf("valid.txt"), parsed.changes.map { it.path })
    }

    @Test
    fun repositoryRootCwdIsDerivedFromGitShowPrefix() {
        assertEquals("", resolveGitRepositoryRootCwd("app/src", "app/src/\n"))
        assertEquals("nested/repo", resolveGitRepositoryRootCwd("nested/repo", "\n"))
        assertEquals("nested/repo", resolveGitRepositoryRootCwd("nested/repo/src/main", "src/main/\n"))
        assertEquals("nested", resolveGitRepositoryRootCwd("nested/ repo/src", " repo/src/\n"))
        assertThrows(IllegalArgumentException::class.java) {
            resolveGitRepositoryRootCwd("nested/repo", "other/\n")
        }
    }

    @Test
    fun diffPathWithShellMetacharactersRemainsOneArgument() {
        val path = "folder/name; echo leaked.txt"

        val arguments = buildGitDiffArguments(GitChangeSection.UNTRACKED, path)

        assertEquals(path, arguments.last())
        assertEquals(1, arguments.count { it == path })
        assertTrue(arguments.contains("--no-index"))
        assertTrue(arguments.contains("--numstat"))
        assertTrue(arguments.contains("--patch"))
        assertTrue(arguments.contains("/dev/null"))
        assertEquals("--", arguments[arguments.lastIndex - 1])
    }

    @Test
    fun gitExitOneIsAcceptedOnlyForNoIndexDiff() {
        val difference = WorkspaceCommandResult(exitCode = 1, stdout = "diff", stderr = "")

        difference.requireGitSuccess(allowNoIndexDifference = true)
        assertThrows(GitReadException.Failed::class.java) {
            difference.requireGitSuccess(allowNoIndexDifference = false)
        }
        assertThrows(GitReadException.TimedOut::class.java) {
            WorkspaceCommandResult(exitCode = -1, stdout = "", stderr = "", timedOut = true)
                .requireGitSuccess()
        }
        assertThrows(GitReadException.GitUnavailable::class.java) {
            WorkspaceCommandResult(exitCode = 127, stdout = "", stderr = "not found").requireGitSuccess()
        }
        assertThrows(GitReadException.NotRepository::class.java) {
            WorkspaceCommandResult(exitCode = 128, stdout = "", stderr = "fatal: not a git repository")
                .requireGitSuccess()
        }
    }

    @Test
    fun utf8TruncationKeepsCompleteCodePointsAndBinaryNumStatIsDetected() {
        val text = "a".repeat(65_534) + "中" + "tail"

        val truncated = truncateUtf8(text, 65_536)

        assertEquals("a".repeat(65_534), truncated)
        assertTrue(truncated.toByteArray(StandardCharsets.UTF_8).size <= 65_536)
        assertTrue(isBinaryNumStat("-\t-\timage.png"))
        assertFalse(isBinaryNumStat("12\t3\ttext.txt"))
    }

    @Test
    fun combinedDiffSeparatesNumStatFromPatchAndDetectsBinary() {
        val textOutput = "1\t2\tname diff --git marker.txt\n\n" +
            "diff --git a/file.txt b/file.txt\n--- a/file.txt\n+++ b/file.txt\n"
        val binaryOutput = "-\t-\timage.png\n\n" +
            "diff --git a/image.png b/image.png\nBinary files differ\n"

        val text = parseCombinedGitDiff(textOutput)
        val binary = parseCombinedGitDiff(binaryOutput)

        assertFalse(text.binary)
        assertTrue(text.patch.startsWith("diff --git a/file.txt"))
        assertTrue(binary.binary)
    }

    private fun ordinary(xy: String, path: String): String =
        "1 $xy N... 100644 100644 100644 aaaaaaa bbbbbbb $path"

    private fun renamed(xy: String, path: String): String =
        "2 $xy N... 100644 100644 100644 aaaaaaa bbbbbbb R100 $path"

    private fun unmerged(xy: String, path: String): String =
        "u $xy N... 100644 100644 100644 100644 aaaaaaa bbbbbbb ccccccc $path"
}

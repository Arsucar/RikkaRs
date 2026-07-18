package me.rerere.rikkahub.service.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIssueEvidenceTest {
    @Test
    fun acceptsIssueNumberAndStrictIssueUrlWithSuccessLanguage() {
        val number = detectGitHubIssueCompletionEvidence("Issue #148 was created successfully")
        val url = detectGitHubIssueCompletionEvidence(
            "已创建 HTTPS://GITHUB.COM/owner/repo/issues/149/?source=hook#done。"
        )

        assertEquals(GitHubIssueEvidenceType.ISSUE_NUMBER, number?.type)
        assertEquals(148L, number?.issueNumber)
        assertEquals(GitHubIssueEvidenceType.ISSUE_URL, url?.type)
        assertEquals(149L, url?.issueNumber)
        assertTrue(url?.normalizedUrl.orEmpty().contains("/issues/149"))
    }

    @Test
    fun acceptsSupportedEnglishAndChineseSuccessPhrases() {
        listOf(
            "Filed #1",
            "Issue #2 submitted",
            "#3 published successfully",
            "#4 已创建",
            "#5 创建成功",
            "#6 已提交",
            "#7 提交成功",
            "#8 已登记",
        ).forEach { text ->
            assertTrue(text, detectGitHubIssueCompletionEvidence(text) != null)
        }
    }

    @Test
    fun rejectsNegativeDraftPlanFailureAndWaitingLanguage() {
        listOf(
            "Issue #148 was not created",
            "Couldn't create #148",
            "Create failed for #148",
            "Draft for #148 was submitted",
            "Plan to create #148",
            "Suggest #148 is created",
            "Waiting to confirm #148 was created",
            "#148 未创建",
            "#148 创建失败",
            "#148 报错",
            "#148 草稿已提交",
            "计划创建 #148",
            "建议已创建 #148",
            "等待确认 #148 已创建",
            "#148 尚未执行",
        ).forEach { text ->
            assertNull(text, detectGitHubIssueCompletionEvidence(text))
        }
    }

    @Test
    fun rejectsNonIssueRefsUnsafeNumbersAndConflictingMarkers() {
        listOf(
            "Created 148",
            "Created #0",
            "Created #01",
            "Created C#123",
            "Created /#123",
            "Created #999999999999999999999999999999999",
            "PR #148 created",
            "Build #148 submitted",
            "Order #148 filed",
            "Commit #148 created",
            "Release #148 created",
            "Created https://github.com/owner/repo",
            "Created https://github.com/owner/repo/issues",
            "Created https://github.com/owner/repo/issues/new",
            "Created https://github.com/owner/repo/pull/148",
            "Created https://github.example.com/owner/repo/issues/148",
            "Created https://user@github.com/owner/repo/issues/148",
            "Created https://github.com:443/owner/repo/issues/148",
            "Created https://github.com/owner/repo/issues/%31%34%38",
            "Created https://github.com/owner/repo/issues/0",
            "Created https://github.com/owner/repo/issues/0148",
            "Created https://evil.test/?issue=#148",
            "Created https://github.com/owner/repo/issues/new#148",
        ).forEach { text ->
            assertNull(text, detectGitHubIssueCompletionEvidence(text))
        }
    }

    @Test
    fun rejectsRefsInCodeQuotesDifferentClausesAndBeyondWindow() {
        val farApart = "Created " + "😀".repeat(81) + " #148"
        listOf(
            "`gh issue create #148` created",
            "Result: ``#148 created``",
            "Result: ```#148 created```",
            "```sh\ngh issue create #148\n```\ncreated",
            "> Issue #148 was created",
            "Issue #148 discussed. Branch created successfully.",
            farApart,
        ).forEach { text ->
            assertNull(text, detectGitHubIssueCompletionEvidence(text))
        }
    }

    @Test
    fun evidenceDistanceUsesExactUnicodeCodePointBoundary() {
        val accepted = "Created" + "😀".repeat(80) + "#148"
        val rejected = "Created" + "😀".repeat(81) + "#148"

        assertEquals(148L, detectGitHubIssueCompletionEvidence(accepted)?.issueNumber)
        assertNull(detectGitHubIssueCompletionEvidence(rejected))
    }
}

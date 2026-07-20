package me.rerere.rikkahub.service.hooks

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class HookProviderExecutorTest {
    @Test
    fun promptAlwaysContainsFrozenResponseAllowedTagsAndStrictSchema() {
        val tagId = Uuid.random()
        val prompt = buildHookEvaluationPrompt(
            FrozenHookModelRequest.AddConversationTag(
                modelId = Uuid.random(),
                prompt = "Decide whether the task is complete.",
                messageTextSnapshot = "The issue was submitted successfully.",
                allowedTags = mapOf(tagId to "Completed"),
            )
        )

        assertTrue(prompt.contains("The issue was submitted successfully."))
        assertTrue(prompt.contains(tagId.toString()))
        assertTrue(prompt.contains("Completed"))
        assertTrue(prompt.contains("exactly the keys decision, tagId, and reason"))
    }

    @Test
    fun manageTagsPromptUsesMultiOpSchemaAndNoEvidenceGate() {
        val tagId = Uuid.random()
        val prompt = buildHookEvaluationPrompt(
            FrozenHookModelRequest.ManageConversationTags(
                modelId = Uuid.random(),
                prompt = "Apply tag strategy from the response.",
                messageTextSnapshot = "Work finished without issue URLs.",
                allowedTags = mapOf(tagId to "Completed"),
            )
        )

        assertTrue(prompt.contains("Work finished without issue URLs."))
        assertTrue(prompt.contains(tagId.toString()))
        assertTrue(prompt.contains("Completed"))
        assertTrue(prompt.contains("exactly the keys decision, operations, and reason"))
        assertTrue(prompt.contains("Prefer remove before add"))
        assertTrue(!prompt.contains("github_issue_evidence"))
        assertTrue(!prompt.contains("exactly the keys decision and reason"))
    }

    @Test
    fun memoryTablePromptContainsOnlyFrozenBoundedInputsAndStrictOutputContract() {
        val userMessageId = Uuid.random()
        val assistantMessageId = Uuid.random()
        val prompt = buildHookEvaluationPrompt(
            FrozenHookModelRequest.SyncMemoryTable(
                modelId = Uuid.random(),
                prompt = "Update facts from {{ content }}.",
                messages = listOf(
                    FrozenHookMessage(userMessageId, MessageRole.USER, "user fact"),
                    FrozenHookMessage(assistantMessageId, MessageRole.ASSISTANT, "assistant answer"),
                ),
                targetDocumentId = "document-1",
                baseRevision = 7,
                schemaJson = """{"tables":[{"name":"facts"}]}""",
                payloadJson = """{"facts":[]}""",
                maxOperations = 4,
            )
        )

        assertTrue(prompt.contains(userMessageId.toString()))
        assertTrue(prompt.contains("role=\"user\""))
        assertTrue(prompt.contains("assistant answer"))
        assertTrue(prompt.contains("document-1"))
        assertTrue(prompt.contains("Frozen base revision: 7"))
        assertTrue(prompt.contains("array of at most 4"))
        assertTrue(prompt.contains("exactly the keys decision, baseRevision, operations, and reason"))
        assertTrue(prompt.contains("Do not output target ids, scopes, tools"))
    }

    @Test
    fun tagTransitionPromptKeepsTagAuthorityLocalAndUsesStrictSchema() {
        val addTagId = Uuid.random()
        val removeTagId = Uuid.random()
        val prompt = buildHookEvaluationPrompt(
            FrozenHookModelRequest.TransitionConversationTags(
                modelId = Uuid.random(),
                prompt = "Confirm {{ github_issue_evidence }} from {{ content }}.",
                messageTextSnapshot = "Created https://github.com/owner/repo/issues/148",
                evidence = GitHubIssueEvidence(
                    type = GitHubIssueEvidenceType.ISSUE_URL,
                    issueNumber = 148,
                    normalizedUrl = "https://github.com/owner/repo/issues/148",
                ),
                addTagId = addTagId,
                addTagName = "Completed",
                removeTagId = removeTagId,
                removeTagName = "In progress",
            )
        )

        assertTrue(prompt.contains("https://github.com/owner/repo/issues/148"))
        assertTrue(prompt.contains("remove 'In progress', then add 'Completed'"))
        assertTrue(prompt.contains("exactly the keys decision and reason"))
        assertTrue(prompt.contains("tag IDs and transition scope are fixed locally"))
        assertTrue(!prompt.contains(addTagId.toString()))
        assertTrue(!prompt.contains(removeTagId.toString()))
    }
}

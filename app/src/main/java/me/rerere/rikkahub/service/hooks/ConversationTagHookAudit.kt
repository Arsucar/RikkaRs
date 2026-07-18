package me.rerere.rikkahub.service.hooks

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookRuntimeRules
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

@Serializable
data class ConversationTagTransitionAuditSummary(
    val action: String,
    val filter: String,
    val evidence: String,
    val addTagId: String,
    val removeTagId: String,
    val added: Boolean?,
    val removed: Boolean?,
)

@Serializable
data class ConversationTagTransitionAuditOperation(
    val type: String,
    val tagId: String,
    val changed: Boolean,
)

internal fun transitionAuditSummaryJson(
    config: HookActionConfig.TransitionConversationTags,
    evidence: GitHubIssueEvidence?,
    added: Boolean?,
    removed: Boolean?,
): String = JsonInstant.encodeToString(
    ConversationTagTransitionAuditSummary(
        action = "transition_conversation_tags",
        filter = "github_issue_completion",
        evidence = when (evidence?.type) {
            GitHubIssueEvidenceType.ISSUE_URL -> "issue_url"
            GitHubIssueEvidenceType.ISSUE_NUMBER -> "issue_number"
            null -> "none"
        },
        addTagId = config.addTagId.toString(),
        removeTagId = config.removeTagId.toString(),
        added = added,
        removed = removed,
    )
).take(HookRuntimeRules.MAX_SYNC_AUDIT_JSON_CHARS)

internal fun transitionAuditDiffJson(
    addTagId: Uuid,
    removeTagId: Uuid,
    added: Boolean?,
    removed: Boolean?,
): String {
    val operations = if (added == null || removed == null) {
        emptyList()
    } else {
        listOf(
            ConversationTagTransitionAuditOperation("remove", removeTagId.toString(), removed),
            ConversationTagTransitionAuditOperation("add", addTagId.toString(), added),
        )
    }
    return JsonInstant.encodeToString(operations).take(HookRuntimeRules.MAX_SYNC_AUDIT_JSON_CHARS)
}

internal fun transitionPreparedAudit(
    config: HookActionConfig.TransitionConversationTags,
    evidence: GitHubIssueEvidence?,
): HookPreparedAudit.ConversationTagTransition = HookPreparedAudit.ConversationTagTransition(
    tagId = config.addTagId,
    operationCount = 0,
    operationSummaryJson = transitionAuditSummaryJson(config, evidence, added = null, removed = null),
    diffSummaryJson = transitionAuditDiffJson(
        addTagId = config.addTagId,
        removeTagId = config.removeTagId,
        added = null,
        removed = null,
    ),
)

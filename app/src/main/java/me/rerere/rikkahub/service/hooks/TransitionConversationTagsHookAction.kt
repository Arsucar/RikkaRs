package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.ConversationHook
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.configurationHash
import me.rerere.rikkahub.data.repository.ConversationTagRepository
import kotlin.uuid.Uuid

class TransitionConversationTagsHookAction(
    private val settingsStore: SettingsStore,
    private val tagRepository: ConversationTagRepository,
    private val committer: ConversationTagHookCommitter,
) : HookActionHandler {
    override val actionType: HookActionType = HookActionType.TRANSITION_CONVERSATION_TAGS

    override suspend fun prepare(
        hook: ConversationHook,
        context: HookFreezeContext,
    ): HookActionPreparation {
        val config = hook.actionConfig as? HookActionConfig.TransitionConversationTags
            ?: return HookActionPreparation.Cancelled(HookErrorCode.ACTION_FAILED)
        if (!isCurrentHookEnabled(hook, context.assistantId)) {
            return HookActionPreparation.Skipped(HookErrorCode.HOOK_DISABLED)
        }
        val emptyAudit = transitionPreparedAudit(config, evidence = null)
        if (config.addTagId == config.removeTagId) {
            return HookActionPreparation.Skipped(
                errorCode = HookErrorCode.TAG_TRANSITION_CONFLICT,
                audit = emptyAudit,
                decision = null,
            )
        }
        val addTag = tagRepository.getTag(config.addTagId)
        val removeTag = tagRepository.getTag(config.removeTagId)
        if (addTag == null || removeTag == null) {
            return HookActionPreparation.Skipped(
                errorCode = HookErrorCode.TAG_NOT_FOUND,
                audit = emptyAudit,
                decision = null,
            )
        }
        val evidence = detectGitHubIssueCompletionEvidence(context.sourceMessageTextSnapshot)
            ?: return HookActionPreparation.Skipped(
                errorCode = HookErrorCode.GITHUB_ISSUE_EVIDENCE_NOT_FOUND,
                audit = emptyAudit,
                decision = null,
            )
        val audit = transitionPreparedAudit(config, evidence)
        return HookActionPreparation.Ready(
            PreparedHookAction.TransitionConversationTags(
                request = FrozenHookModelRequest.TransitionConversationTags(
                    modelId = hook.modelId,
                    prompt = hook.prompt,
                    messageTextSnapshot = context.sourceMessageTextSnapshot,
                    evidence = evidence,
                    addTagId = config.addTagId,
                    addTagName = addTag.displayName,
                    removeTagId = config.removeTagId,
                    removeTagName = removeTag.displayName,
                ),
                audit = audit,
                hookId = hook.id,
                hookConfigVersion = hook.configVersion,
                hookConfigHash = hook.configurationHash(),
                assistantId = context.assistantId,
                conversationId = context.conversation.id,
                sourceNodeId = context.sourceNodeId,
                sourceMessageId = context.sourceMessageId,
                addTagId = config.addTagId,
                removeTagId = config.removeTagId,
                evidence = evidence,
            )
        )
    }

    override fun parse(raw: String, prepared: PreparedHookAction): ParsedHookOutput {
        if (prepared !is PreparedHookAction.TransitionConversationTags) {
            throw HookOutputException(HookErrorCode.ACTION_FAILED)
        }
        return TransitionConversationTagsHookOutputParser.parse(raw)
    }

    override suspend fun execute(
        executionId: Uuid,
        leaseToken: Long,
        prepared: PreparedHookAction,
        output: ParsedHookOutput,
    ): HookActionResult {
        val transition = prepared as? PreparedHookAction.TransitionConversationTags
            ?: return HookActionResult.Cancelled(HookErrorCode.ACTION_FAILED)
        val parsed = output as? ParsedTransitionConversationTagsHookOutput
            ?: return HookActionResult.Cancelled(HookErrorCode.SCHEMA_MISMATCH)
        if (parsed.decision == HookDecision.SKIP) return HookActionResult.Skipped(null)
        if (!isCurrentHookEnabled(transition)) {
            return HookActionResult.Skipped(null, HookErrorCode.HOOK_DISABLED)
        }
        committer.commitTransition(executionId, leaseToken, transition, parsed)
        return HookActionResult.Terminalized
    }

    private fun isCurrentHookEnabled(hook: ConversationHook, assistantId: Uuid): Boolean {
        val currentAssistant = settingsStore.settingsFlow.value.assistants.firstOrNull { it.id == assistantId }
            ?: return false
        val currentHook = currentAssistant.hooks.firstOrNull { it.id == hook.id } ?: return false
        return currentHook.enabled && currentHook.configVersion == hook.configVersion &&
            currentHook.configurationHash() == hook.configurationHash()
    }

    private fun isCurrentHookEnabled(prepared: PreparedHookAction.TransitionConversationTags): Boolean {
        val currentAssistant = settingsStore.settingsFlow.value.assistants
            .firstOrNull { it.id == prepared.assistantId }
            ?: return false
        val currentHook = currentAssistant.hooks.firstOrNull { it.id == prepared.hookId } ?: return false
        return currentHook.enabled && currentHook.configVersion == prepared.hookConfigVersion &&
            currentHook.configurationHash() == prepared.hookConfigHash
    }
}

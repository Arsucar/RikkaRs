package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookTrigger
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantHooksPageTest {
    private val tagId = Uuid.random()

    @Test
    fun validDraftCanBeSaved() {
        val validation = validateHookEditor(
            name = "Review completed work",
            modelIsValid = true,
            trigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
            prompt = "Apply the tag when the work is complete.",
            actionConfig = HookActionConfig.ManageConversationTags(setOf(tagId)),
            availableTagIds = setOf(tagId),
        )

        assertTrue(validation.canSave)
        assertNull(validation.nameError)
        assertNull(validation.modelError)
        assertNull(validation.triggerError)
        assertNull(validation.promptError)
        assertNull(validation.actionError)
    }

    @Test
    fun invalidFieldsExposeSpecificErrors() {
        val validation = validateHookEditor(
            name = " ",
            modelIsValid = false,
            trigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
            prompt = "\n",
            actionConfig = HookActionConfig.ManageConversationTags(),
            availableTagIds = emptySet(),
        )

        assertFalse(validation.canSave)
        assertEquals(HookEditorFieldError.NAME_REQUIRED, validation.nameError)
        assertEquals(HookEditorFieldError.MODEL_REQUIRED, validation.modelError)
        assertEquals(HookEditorFieldError.PROMPT_REQUIRED, validation.promptError)
        assertEquals(HookEditorFieldError.TAG_REQUIRED, validation.actionError)
    }

    @Test
    fun deletedAllowedTagMustBeRemovedBeforeSaving() {
        val validation = validateHookEditor(
            name = "Review completed work",
            modelIsValid = true,
            trigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
            prompt = "Apply the tag when the work is complete.",
            actionConfig = HookActionConfig.ManageConversationTags(setOf(tagId)),
            availableTagIds = emptySet(),
        )

        assertFalse(validation.canSave)
        assertEquals(HookEditorFieldError.TAG_UNAVAILABLE, validation.actionError)
    }

    @Test
    fun actionTypeUsesLocalizedPresentationBoundary() {
        assertEquals(
            R.string.assistant_hook_action_manage_tags,
            hookActionLabelRes(HookActionType.MANAGE_CONVERSATION_TAGS),
        )
        assertEquals(
            R.string.assistant_hook_action_sync_memory_table,
            hookActionLabelRes(HookActionType.SYNC_MEMORY_TABLE),
        )
        assertEquals(
            R.string.assistant_hook_action_manage_tags,
            hookActionLabelRes(HookActionType.ADD_CONVERSATION_TAG),
        )
        assertEquals(
            R.string.assistant_hook_action_manage_tags,
            hookActionLabelRes(HookActionType.TRANSITION_CONVERSATION_TAGS),
        )
    }

    @Test
    fun legacyTransitionConfigNormalizesToManageAllowlistValidation() {
        val addTagId = Uuid.random()
        val removeTagId = Uuid.random()
        val valid = transitionValidation(addTagId, removeTagId, setOf(addTagId, removeTagId))
        val unavailable = transitionValidation(addTagId, removeTagId, setOf(addTagId))

        assertTrue(valid.canSave)
        assertEquals(HookEditorFieldError.TAG_UNAVAILABLE, unavailable.actionError)
    }

    @Test
    fun tagCatalogLoadingBlocksTagActionsButNotMemorySync() {
        val addTagId = Uuid.random()
        val removeTagId = Uuid.random()
        val transition = validateHookEditor(
            name = "Transition",
            modelIsValid = true,
            trigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
            prompt = "Decide.",
            actionConfig = HookActionConfig.ManageConversationTags(setOf(addTagId, removeTagId)),
            availableTagIds = setOf(addTagId, removeTagId),
            tagCatalogReady = false,
        )
        val target = document("assistant", MemoryTableScopeType.ASSISTANT, "assistant-id")
        val sync = validateHookEditor(
            name = "Sync",
            modelIsValid = true,
            trigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
            prompt = "Sync.",
            actionConfig = HookActionConfig.SyncMemoryTable(target.id, target.scopeType),
            availableTagIds = emptySet(),
            tagCatalogReady = false,
            availableDocuments = listOf(target),
        )

        assertEquals(HookEditorFieldError.TAG_CATALOG_UNAVAILABLE, transition.actionError)
        assertTrue(sync.canSave)
    }

    @Test
    fun validAssistantAndConversationSyncTargetsCanBeSaved() {
        val assistantDocument = document("assistant", MemoryTableScopeType.ASSISTANT, "assistant-id")
        val conversationDocument = document("conversation", MemoryTableScopeType.CONVERSATION, "conversation-id")

        assertTrue(syncValidation(assistantDocument, listOf(assistantDocument)).canSave)
        assertTrue(
            syncValidation(
                conversationDocument,
                listOf(conversationDocument),
                conversationId = "conversation-id",
            ).canSave
        )
    }

    @Test
    fun syncValidationRejectsMissingUnavailableGlobalAndMismatchedTargets() {
        val assistantDocument = document("assistant", MemoryTableScopeType.ASSISTANT, "assistant-id")
        val conversationDocument = document("conversation", MemoryTableScopeType.CONVERSATION, "other")
        val globalDocument = document("global", MemoryTableScopeType.GLOBAL, "__global__")

        assertEquals(
            HookEditorFieldError.TARGET_REQUIRED,
            syncValidation(assistantDocument.copy(id = ""), listOf(assistantDocument)).actionError,
        )
        assertEquals(
            HookEditorFieldError.TARGET_UNAVAILABLE,
            syncValidation(assistantDocument, emptyList()).actionError,
        )
        assertEquals(
            HookEditorFieldError.TARGET_GLOBAL_FORBIDDEN,
            syncValidation(globalDocument, listOf(globalDocument)).actionError,
        )
        assertEquals(
            HookEditorFieldError.TARGET_SCOPE_MISMATCH,
            syncValidation(
                conversationDocument,
                listOf(conversationDocument),
                conversationId = "current",
            ).actionError,
        )
    }

    @Test
    fun syncValidationRejectsMissingRolesAndInvalidLimits() {
        val target = document("assistant", MemoryTableScopeType.ASSISTANT, "assistant-id")
        val base = HookActionConfig.SyncMemoryTable(
            targetDocumentId = target.id,
            targetScopeType = target.scopeType,
        )
        val invalidActions = listOf(
            base.copy(includeUserMessages = false, includeAssistantMessages = false) to
                HookEditorFieldError.ROLE_REQUIRED,
            base.copy(recentMessageCount = 0) to HookEditorFieldError.LIMIT_INVALID,
            base.copy(maxContextChars = 0) to HookEditorFieldError.LIMIT_INVALID,
            base.copy(maxOperations = 0) to HookEditorFieldError.LIMIT_INVALID,
            base.copy(minimumIntervalSeconds = -1) to HookEditorFieldError.LIMIT_INVALID,
        )

        invalidActions.forEach { (action, expected) ->
            assertEquals(expected, validation(action, listOf(target)).actionError)
        }
    }

    private fun syncValidation(
        target: MemoryTableDocument,
        availableDocuments: List<MemoryTableDocument>,
        conversationId: String? = null,
    ): HookEditorValidation = validation(
        HookActionConfig.SyncMemoryTable(
            targetDocumentId = target.id,
            targetScopeType = target.scopeType,
        ),
        availableDocuments,
        conversationId,
    )

    private fun transitionValidation(
        addTagId: Uuid,
        removeTagId: Uuid,
        availableTagIds: Set<Uuid>,
    ) = validateHookEditor(
        name = "Transition tags",
        modelIsValid = true,
        trigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
        prompt = "Apply the configured transition.",
        actionConfig = HookActionConfig.TransitionConversationTags(addTagId, removeTagId),
        availableTagIds = availableTagIds,
    )

    private fun validation(
        action: HookActionConfig.SyncMemoryTable,
        availableDocuments: List<MemoryTableDocument>,
        conversationId: String? = null,
    ) = validateHookEditor(
        name = "Sync memory",
        modelIsValid = true,
        trigger = HookTrigger.AFTER_ASSISTANT_RESPONSE_SUCCESS,
        prompt = "Return operations.",
        actionConfig = action,
        availableTagIds = emptySet(),
        availableDocuments = availableDocuments,
        conversationId = conversationId,
    )

    private fun document(id: String, scope: MemoryTableScopeType, scopeId: String) = MemoryTableDocument(
        id = id,
        templateId = "template",
        scopeType = scope,
        scopeId = scopeId,
    )
}

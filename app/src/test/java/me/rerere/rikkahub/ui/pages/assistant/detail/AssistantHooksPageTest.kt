package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.HookActionConfig
import me.rerere.rikkahub.data.model.HookActionType
import me.rerere.rikkahub.data.model.HookTrigger
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
            actionConfig = HookActionConfig.AddConversationTag(setOf(tagId)),
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
            actionConfig = HookActionConfig.AddConversationTag(),
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
            actionConfig = HookActionConfig.AddConversationTag(setOf(tagId)),
            availableTagIds = emptySet(),
        )

        assertFalse(validation.canSave)
        assertEquals(HookEditorFieldError.TAG_UNAVAILABLE, validation.actionError)
    }

    @Test
    fun actionTypeUsesLocalizedPresentationBoundary() {
        assertEquals(
            R.string.assistant_hook_action_add_tag,
            hookActionLabelRes(HookActionType.ADD_CONVERSATION_TAG),
        )
    }
}

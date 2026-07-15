package me.rerere.rikkahub.service.hooks

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class HookProviderExecutorTest {
    @Test
    fun promptAlwaysContainsFrozenResponseAllowedTagsAndStrictSchema() {
        val tagId = Uuid.random()
        val prompt = buildHookEvaluationPrompt(
            FrozenHookModelRequest(
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
}

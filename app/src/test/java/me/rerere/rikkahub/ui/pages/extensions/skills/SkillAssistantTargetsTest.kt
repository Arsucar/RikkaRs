package me.rerere.rikkahub.ui.pages.extensions.skills

import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillAssistantTargetsTest {
    @Test
    fun `build marks assistants that already have a private copy`() {
        val firstAssistant = Assistant(id = Uuid.random(), name = "First")
        val secondAssistant = Assistant(id = Uuid.random(), name = "Second")

        val targets = SkillAssistantTargets.build(
            assistants = listOf(firstAssistant, secondAssistant),
            privateSkillNamesByAssistant = mapOf(
                secondAssistant.id to setOf("demo"),
            ),
            skillName = "demo",
        )

        assertFalse(targets.first { it.assistant.id == firstAssistant.id }.hasPrivateSkill)
        assertTrue(targets.first { it.assistant.id == secondAssistant.id }.hasPrivateSkill)
    }
}

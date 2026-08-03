package me.rerere.rikkahub.data.experimental

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.isVariableSystemEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalFeatureResolveTest {
    @Test
    fun `defaults are all off`() {
        val settings = Settings(init = true)
        val assistant = Assistant()
        assertFalse(resolveExperimentalFeature(FEATURE_CHAT_KEEPALIVE, settings))
        assertFalse(resolveExperimentalFeature(FEATURE_CHECKPOINT_CACHE, settings))
        assertFalse(resolveExperimentalFeature(FEATURE_VARIABLE_SYSTEM, settings, assistant))
        assertFalse(assistant.isVariableSystemEnabled())
    }

    @Test
    fun `unknown id resolves false`() {
        val settings = Settings(init = true)
        assertFalse(resolveExperimentalFeature("not_a_real_feature", settings))
        assertFalse(
            resolveExperimentalFeature(
                "not_a_real_feature",
                settings.copy(experimentalFeatures = mapOf("not_a_real_feature" to false)),
            ),
        )
        assertTrue(
            resolveExperimentalFeature(
                "not_a_real_feature",
                settings.copy(experimentalFeatures = mapOf("not_a_real_feature" to true)),
            ),
        )
    }

    @Test
    fun `map wins over legacy booleans for global features`() {
        val settings = Settings(
            init = true,
            enableKeepAliveNotification = true,
            enableCheckpointCache = true,
            experimentalFeatures = mapOf(
                FEATURE_CHAT_KEEPALIVE to false,
                FEATURE_CHECKPOINT_CACHE to false,
            ),
        )
        assertFalse(resolveExperimentalFeature(FEATURE_CHAT_KEEPALIVE, settings))
        assertFalse(resolveExperimentalFeature(FEATURE_CHECKPOINT_CACHE, settings))
    }

    @Test
    fun `legacy bridge used when map missing key`() {
        val settings = Settings(
            init = true,
            enableKeepAliveNotification = true,
            enableCheckpointCache = true,
            experimentalFeatures = emptyMap(),
        )
        assertTrue(resolveExperimentalFeature(FEATURE_CHAT_KEEPALIVE, settings))
        assertTrue(resolveExperimentalFeature(FEATURE_CHECKPOINT_CACHE, settings))
    }

    @Test
    fun `global and assistant scopes are isolated`() {
        val settings = Settings(
            init = true,
            experimentalFeatures = mapOf(FEATURE_CHAT_KEEPALIVE to true),
        )
        val assistantOn = Assistant(
            experimentalFeatureOverrides = mapOf(FEATURE_VARIABLE_SYSTEM to true),
        )
        val assistantOff = Assistant(
            experimentalFeatureOverrides = mapOf(FEATURE_VARIABLE_SYSTEM to false),
            enableVariableSystem = true,
        )
        assertTrue(resolveExperimentalFeature(FEATURE_CHAT_KEEPALIVE, settings, assistantOff))
        assertFalse(resolveExperimentalFeature(FEATURE_VARIABLE_SYSTEM, settings, Assistant()))
        assertTrue(resolveExperimentalFeature(FEATURE_VARIABLE_SYSTEM, settings, assistantOn))
        assertFalse(resolveExperimentalFeature(FEATURE_VARIABLE_SYSTEM, settings, assistantOff))
        assertTrue(assistantOn.isVariableSystemEnabled())
        assertFalse(assistantOff.isVariableSystemEnabled())
    }

    @Test
    fun `assistant map wins over legacy enableVariableSystem`() {
        val assistant = Assistant(
            enableVariableSystem = true,
            experimentalFeatureOverrides = mapOf(FEATURE_VARIABLE_SYSTEM to false),
        )
        assertFalse(assistant.isVariableSystemEnabled())
        assertFalse(
            resolveExperimentalFeature(
                FEATURE_VARIABLE_SYSTEM,
                Settings(init = true),
                assistant,
            ),
        )
    }

    @Test
    fun `assistant legacy bridge when overrides missing`() {
        val assistant = Assistant(enableVariableSystem = true)
        assertTrue(assistant.isVariableSystemEnabled())
    }

    @Test
    fun `registry lists hot-pluggable specs by scope`() {
        val global = ExperimentalFeatureRegistry.byScope(ExperimentalFeatureScope.Global)
        val assistant = ExperimentalFeatureRegistry.byScope(ExperimentalFeatureScope.Assistant)
        assertEquals(
            setOf(FEATURE_CHAT_KEEPALIVE, FEATURE_CHECKPOINT_CACHE),
            global.map { it.id }.toSet(),
        )
        assertEquals(setOf(FEATURE_VARIABLE_SYSTEM), assistant.map { it.id }.toSet())
        assertEquals(
            ExperimentalFeatureRegistry.all.size,
            global.size + assistant.size,
        )
        assertTrue(ExperimentalFeatureRegistry.get(FEATURE_CHAT_KEEPALIVE) != null)
        assertTrue(ExperimentalFeatureRegistry.all.all { !it.defaultEnabled })
    }

    @Test
    fun `decodeExperimentalFeatures recovers from corrupt json`() {
        assertEquals(emptyMap<String, Boolean>(), me.rerere.rikkahub.data.datastore.decodeExperimentalFeatures(null))
        assertEquals(emptyMap<String, Boolean>(), me.rerere.rikkahub.data.datastore.decodeExperimentalFeatures(""))
        assertEquals(emptyMap<String, Boolean>(), me.rerere.rikkahub.data.datastore.decodeExperimentalFeatures("{not json"))
        assertEquals(
            mapOf(FEATURE_CHAT_KEEPALIVE to true),
            me.rerere.rikkahub.data.datastore.decodeExperimentalFeatures(
                """{"chat_keepalive":true}""",
            ),
        )
    }
}

package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTreeStateTest {
    private val saverScope = SaverScope { true }

    @Test
    fun saverRoundTripPreservesEmptyStateWithBundleSafeValue() {
        val restored = roundTrip(JsonTreeState())

        assertEquals(emptyMap<String, Boolean>(), restored.snapshot())
    }

    @Test
    fun saverRoundTripPreservesExpandedAndCollapsedPaths() {
        val state = JsonTreeState().apply {
            setExpanded("", true)
            setExpanded("/request", false)
            setExpanded("/request/items/0", true)
        }

        val restored = roundTrip(state)

        assertEquals(state.snapshot(), restored.snapshot())
    }

    private fun roundTrip(state: JsonTreeState): JsonTreeState {
        val saved = with(JsonTreeState.Saver) { saverScope.save(state) }

        assertNotNull(saved)
        assertTrue(saved is ArrayList<*>)
        assertTrue(saved!!.all { it is String })
        return requireNotNull(JsonTreeState.Saver.restore(saved))
    }
}

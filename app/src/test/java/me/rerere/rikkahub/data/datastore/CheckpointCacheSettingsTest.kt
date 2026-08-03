package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointCacheSettingsTest {
    @Test
    fun `coerceCheckpointStepInterval snaps to allowed set`() {
        assertEquals(4, coerceCheckpointStepInterval(4))
        assertEquals(8, coerceCheckpointStepInterval(8))
        assertEquals(16, coerceCheckpointStepInterval(16))
        assertEquals(32, coerceCheckpointStepInterval(32))
        assertEquals(4, coerceCheckpointStepInterval(1))
        assertEquals(8, coerceCheckpointStepInterval(9))
        assertEquals(16, coerceCheckpointStepInterval(20))
        assertEquals(32, coerceCheckpointStepInterval(100))
        assertEquals(8, coerceCheckpointStepInterval(7))
        assertEquals(DEFAULT_CHECKPOINT_STEP_INTERVAL, coerceCheckpointStepInterval(7))
    }

    @Test
    fun `shouldWriteCheckpoint respects enable flag and interval`() {
        assertFalse(
            shouldWriteCheckpoint(
                enableCheckpointCache = false,
                stepIndex = 8,
                lastCheckpointStep = -1,
                interval = 8,
            ),
        )
        // last=-1, stepIndex=6 => delta 7 < 8
        assertFalse(
            shouldWriteCheckpoint(
                enableCheckpointCache = true,
                stepIndex = 6,
                lastCheckpointStep = -1,
                interval = 8,
            ),
        )
        // last=-1, stepIndex=7 => delta 8 >= 8
        assertTrue(
            shouldWriteCheckpoint(
                enableCheckpointCache = true,
                stepIndex = 7,
                lastCheckpointStep = -1,
                interval = 8,
            ),
        )
        // after checkpoint at 7, next needs +8
        assertFalse(
            shouldWriteCheckpoint(
                enableCheckpointCache = true,
                stepIndex = 14,
                lastCheckpointStep = 7,
                interval = 8,
            ),
        )
        assertTrue(
            shouldWriteCheckpoint(
                enableCheckpointCache = true,
                stepIndex = 15,
                lastCheckpointStep = 7,
                interval = 8,
            ),
        )
        assertTrue(
            shouldWriteCheckpoint(
                enableCheckpointCache = true,
                stepIndex = 3,
                lastCheckpointStep = -1,
                interval = 4,
            ),
        )
    }

    @Test
    fun `Settings defaults keep checkpoint cache off`() {
        val settings = Settings(init = true)
        assertFalse(settings.enableCheckpointCache)
        assertEquals(DEFAULT_CHECKPOINT_STEP_INTERVAL, settings.checkpointStepInterval)
        assertTrue(settings.experimentalFeatures.isEmpty())
    }
}

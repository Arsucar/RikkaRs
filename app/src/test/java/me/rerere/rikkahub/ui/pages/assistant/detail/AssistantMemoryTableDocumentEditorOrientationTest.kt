package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantMemoryTableDocumentEditorOrientationTest {
    @Test
    fun portraitTargetsFixedLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            memoryTableEditorTargetOrientation(Configuration.ORIENTATION_PORTRAIT),
        )
    }

    @Test
    fun landscapeTargetsFixedPortrait() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            memoryTableEditorTargetOrientation(Configuration.ORIENTATION_LANDSCAPE),
        )
    }

    @Test
    fun undefinedOrientationTargetsFixedLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            memoryTableEditorTargetOrientation(Configuration.ORIENTATION_UNDEFINED),
        )
    }

    @Test
    fun unknownConfigurationTargetsFixedLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            memoryTableEditorTargetOrientation(99),
        )
    }
}

package me.rerere.rikkahub.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringUtilsAndroidTest {

    @Test
    fun titlePromptPlaceholdersCompileOnAndroid() {
        assertEquals(
            "Summarize using zh-CN language\ncontent=hello",
            "Summarize using {locale} language\ncontent={content}".applyPlaceholders(
                "locale" to "zh-CN",
                "content" to "hello",
            ),
        )
    }

    @Test
    fun hookPromptPlaceholdersCompileOnAndroid() {
        assertEquals(
            "content=hello\nallowed=work, personal",
            "content={content}\nallowed={allowed_tags}".applyPlaceholders(
                "content" to "hello",
                "allowed_tags" to "work, personal",
            ),
        )
    }
}

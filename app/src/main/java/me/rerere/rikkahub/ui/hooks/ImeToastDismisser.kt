package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import com.dokar.sonner.ToasterState

/**
 * Automatically dismisses all toasts when the IME (keyboard) is opened.
 * This prevents toasts from capturing focus and blocking text input.
 */
@Composable
fun ImeToastDismisser(toasterState: ToasterState) {
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
    var previousImeHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        snapshotFlow {
            ime.getBottom(localDensity)
        }.collect { keyboardHeight ->
            // Keyboard is opening (height increased from 0 or very small value)
            if (keyboardHeight > 100 && previousImeHeight <= 100) {
                toasterState.dismissAll()
            }
            previousImeHeight = keyboardHeight
        }
    }
}

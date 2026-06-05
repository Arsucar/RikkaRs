package me.rerere.rikkahub.ui.context

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

val LocalToaster = staticCompositionLocalOf<AppToasterState> { error("Not provided") }

data class AppToast(
    val id: Long,
    val message: String,
    val type: ToastType,
    val durationMillis: Long,
)

@Stable
class AppToasterState {
    private var nextId by mutableLongStateOf(0L)
    internal val toasts = mutableStateListOf<AppToast>()

    fun show(
        message: Any?,
        type: ToastType = ToastType.Normal,
        duration: Duration = durationFor(type),
    ): AppToast {
        val toast = AppToast(
            id = nextId++,
            message = message?.toString().orEmpty(),
            type = type,
            durationMillis = duration.inWholeMilliseconds.coerceAtLeast(0L),
        )
        toasts += toast
        while (toasts.size > MAX_VISIBLE_TOASTS) {
            toasts.removeAt(0)
        }
        return toast
    }

    fun dismiss(toast: AppToast) {
        dismiss(toast.id)
    }

    fun dismiss(id: Long) {
        toasts.removeAll { it.id == id }
    }

    fun dismissAll() {
        toasts.clear()
    }

    companion object {
        private const val MAX_VISIBLE_TOASTS = 3

        private fun durationFor(type: ToastType): Duration {
            return when (type) {
                ToastType.Error,
                ToastType.Warning -> 3200.milliseconds
                else -> 1400.milliseconds
            }
        }
    }
}

@Composable
fun rememberAppToasterState(): AppToasterState = remember { AppToasterState() }

@Composable
fun AppToaster(
    state: AppToasterState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.toasts.forEach { toast ->
                key(toast.id) {
                    LaunchedEffect(toast.id, toast.durationMillis) {
                        delay(toast.durationMillis)
                        state.dismiss(toast.id)
                    }
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { -it / 2 },
                        exit = fadeOut() + slideOutVertically { -it / 2 },
                    ) {
                        AppToastItem(toast)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppToastItem(toast: AppToast) {
    val (containerColor, contentColor) = appToastColors(toast.type)
    Surface(
        modifier = Modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = toast.message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun appToastColors(type: ToastType): Pair<Color, Color> {
    return when (type) {
        ToastType.Success -> Color(0xFF2E7D32) to Color.White
        ToastType.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ToastType.Warning -> Color(0xFFFFF3CD) to Color(0xFF3D2C00)
        ToastType.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
}

package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.dokar.sonner.ToastType
import com.jvziyaoyao.scale.image.pager.ImagePager
import com.jvziyaoyao.scale.zoomable.pager.rememberZoomablePagerState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.compose.koinInject
import java.io.File

@Composable
fun ImagePreviewDialog(
    images: List<String>,
    initialPage: Int = 0,
    labels: List<String> = emptyList(),
    models: List<String> = emptyList(),
    prompts: List<String> = emptyList(),
    onUseAsReference: ((String) -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val safeInitialPage = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val state = rememberZoomablePagerState(initialPage = safeInitialPage) { images.size }
    val toaster = LocalToaster.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hazeState = rememberHazeState()
    val hazeStyle = HazeMaterials.thin(containerColor = Color.Black.copy(alpha = 0.45f))
    val currentImage = images.getOrNull(state.currentPage)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentImage != null) {
                AsyncImage(
                    model = File(currentImage.removePrefix("file://")),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState),
                    contentScale = ContentScale.Crop,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeEffect(state = hazeState) {
                        blurEffect { style = hazeStyle }
                    },
            )

            ImagePager(
                modifier = Modifier.fillMaxSize(),
                pagerState = state,
                imageLoader = { index ->
                    val painter = rememberAsyncImagePainter(images[index])
                    return@ImagePager Pair(painter, painter.intrinsicSize)
                },
            )

            val label = labels.getOrNull(state.currentPage)?.takeIf { it.isNotBlank() }
            val model = models.getOrNull(state.currentPage)?.takeIf { it.isNotBlank() }
            val labelText = buildString {
                label?.let { append(it) }
                if (label != null && model != null) append(" · ")
                model?.let { append(it) }
            }.takeIf { it.isNotBlank() }
            if (labelText != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.56f),
                ) {
                    Text(
                        text = labelText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onUseAsReference?.let { useAsReference ->
                    IconButton(
                        onClick = {
                            images.getOrNull(state.currentPage)?.let { useAsReference(it) }
                        },
                    ) {
                        Icon(HugeIcons.Add01, null, tint = Color.White)
                    }
                }

                prompts.getOrNull(state.currentPage)?.takeIf { it.isNotBlank() }?.let { prompt ->
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(prompt))
                            toaster.show(message = "已复制提示词", type = ToastType.Success)
                        },
                    ) {
                        Icon(HugeIcons.Copy01, null, tint = Color.White)
                    }
                }

                IconButton(
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            runCatching {
                                toaster.show("正在保存")
                                val imgUrl = images[state.currentPage]
                                filesManager.saveMessageImage(context, imgUrl)
                                toaster.show(message = "已保存图片", type = ToastType.Success)
                            }.onFailure {
                                it.printStackTrace()
                                toaster.show(
                                    message = it.toString(),
                                    type = ToastType.Error,
                                )
                            }
                        }
                    },
                ) {
                    Icon(HugeIcons.Download01, null, tint = Color.White)
                }
            }
        }
    }
}

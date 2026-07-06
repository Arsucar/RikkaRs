package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FullScreenMarkdownViewer(
    title: String,
    markdown: String,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val htmlContent = remember(context, markdown, colorScheme) {
        buildMarkdownPreviewHtml(
            context = context,
            markdown = markdown,
            colorScheme = colorScheme,
        )
    }
    val webViewState = rememberWebViewState(
        data = htmlContent,
        baseUrl = "https://rikkahub.local",
        mimeType = "text/html",
        settings = {
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        },
    )

    BasicAlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = onDismiss, enabled = !isLoading) {
                            Text(stringResource(R.string.common_confirm))
                        }
                    }
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (!isLoading && errorMessage == null) {
                        WebView(
                            state = webViewState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

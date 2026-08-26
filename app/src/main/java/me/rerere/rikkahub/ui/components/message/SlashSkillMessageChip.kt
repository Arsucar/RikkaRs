package me.rerere.rikkahub.ui.components.message

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.readSkillBodyByIdentifier
import org.koin.compose.koinInject

@Composable
fun SlashSkillSentChip(
    skillName: String,
    modifier: Modifier = Modifier,
) {
    val skillManager: SkillManager = koinInject()
    val clipboard = LocalClipboard.current
    var showDetail by remember { mutableStateOf(false) }
    val body by produceState<String?>(initialValue = null, key1 = skillName, key2 = showDetail) {
        if (!showDetail) return@produceState
        value = withContext(Dispatchers.IO) {
            skillManager.readSkillBodyByIdentifier(skillName)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        onClick = { showDetail = true },
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Book02,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = skillName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(min = 48.dp, max = 220.dp),
            )
            IconButton(
                onClick = { showDetail = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showDetail) {
        BasicAlertDialog(
            onDismissRequest = { showDetail = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = skillName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val text = body?.takeIf { it.isNotBlank() } ?: skillName
                                clipboard.nativeClipboard.setPrimaryClip(
                                    ClipData.newPlainText("skill", text),
                                )
                            },
                        ) {
                            Icon(HugeIcons.Copy01, contentDescription = stringResource(R.string.copy))
                        }
                        IconButton(onClick = { showDetail = false }) {
                            Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                    val scroll = rememberScrollState()
                    SelectionContainer {
                        Text(
                            text = body?.takeIf { it.isNotBlank() } ?: "...",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 400.dp)
                                .verticalScroll(scroll),
                        )
                    }
                }
            }
        }
    }
}

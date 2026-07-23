package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toFixed
import java.time.Duration
import java.time.DateTimeException

internal data class TokenStatsDisplay(
    val promptLabel: String,
    val cachedLabel: String?,
    val completionLabel: String,
    val tpsLabel: String?,
    val durationLabel: String?,
)

/**
 * Build token-stats labels for a message. Returns null if usage is absent or formatting fails.
 * Isolated from Compose so ExceptionInInitializerError / bad timestamps cannot crash the list.
 */
internal fun buildTokenStatsDisplay(message: UIMessage): TokenStatsDisplay? {
    return try {
        val usage = message.usage ?: return null
        val promptLabel = "${usage.promptTokens.formatNumber()} tokens"
        val cachedLabel = if (usage.cachedTokens > 0) {
            "(${usage.cachedTokens.formatNumber()} cached)"
        } else {
            null
        }
        val completionLabel = "${usage.completionTokens.formatNumber()} tokens"

        var tpsLabel: String? = null
        var durationLabel: String? = null
        val finishedAt = message.finishedAt
        if (finishedAt != null) {
            val duration = Duration.between(
                message.createdAt.toJavaLocalDateTime(),
                finishedAt.toJavaLocalDateTime()
            )
            val durationMs = duration.toMillis()
            if (durationMs > 0) {
                val tps = usage.completionTokens.toFloat() / durationMs * 1000
                tpsLabel = "${tps.toFixed(1)} tok/s"
                durationLabel = "${(durationMs / 1000f).toFixed(1)}s"
            }
        }

        TokenStatsDisplay(
            promptLabel = promptLabel,
            cachedLabel = cachedLabel,
            completionLabel = completionLabel,
            tpsLabel = tpsLabel,
            durationLabel = durationLabel,
        )
    } catch (_: DateTimeException) {
        null
    } catch (_: ArithmeticException) {
        null
    } catch (_: ExceptionInInitializerError) {
        null
    } catch (_: NoClassDefFoundError) {
        null
    }
}

/**
 * 显示消息的技术统计信息（如 token 使用量）
 */
@Composable
fun ChatMessageNerdLine(
    message: UIMessage,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val settings = LocalSettings.current.displaySetting
    if (!settings.showTokenUsage) return

    val stats = buildTokenStatsDisplay(message) ?: return

    ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
        CompositionLocalProvider(LocalContentColor provides color) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = modifier.padding(horizontal = 4.dp),
            ) {
                // Input tokens
                StatsItem(
                    icon = {
                        Icon(
                            imageVector = HugeIcons.Upload02,
                            contentDescription = "Input",
                            tint = color,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    content = {
                        Text(text = stats.promptLabel)
                        stats.cachedLabel?.let { cached ->
                            Text(text = cached)
                        }
                    }
                )
                // Output tokens
                StatsItem(
                    icon = {
                        Icon(
                            imageVector = HugeIcons.Download04,
                            contentDescription = "Output",
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    content = {
                        Text(text = stats.completionLabel)
                    }
                )
                // TPS
                stats.tpsLabel?.let { tps ->
                    StatsItem(
                        icon = {
                            Icon(
                                imageVector = HugeIcons.Zap,
                                contentDescription = "Speed",
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        content = {
                            Text(text = tps)
                        }
                    )
                }
                // Duration
                stats.durationLabel?.let { duration ->
                    StatsItem(
                        icon = {
                            Icon(
                                imageVector = HugeIcons.Clock02,
                                contentDescription = "Duration",
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        content = {
                            Text(text = duration)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatsItem(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        icon()
        content()
    }
}

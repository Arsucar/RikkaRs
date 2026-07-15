package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.ConversationTagErrorCode
import me.rerere.rikkahub.ui.theme.extendColors

@Composable
fun conversationTagColor(colorKey: String): Color = when (colorKey) {
    "red" -> MaterialTheme.extendColors.red6
    "orange", "amber", "yellow" -> MaterialTheme.extendColors.orange6
    "green", "lime" -> MaterialTheme.extendColors.green6
    "blue", "cyan", "teal", "indigo" -> MaterialTheme.extendColors.blue6
    "gray", "grey" -> MaterialTheme.extendColors.gray6
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun ConversationTagLabel(
    name: String,
    colorKey: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(10.dp)
                .background(conversationTagColor(colorKey), CircleShape),
        )
        Text(
            text = name,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun conversationTagErrorMessage(code: ConversationTagErrorCode): String = stringResource(
    when (code) {
        ConversationTagErrorCode.EMPTY_NAME -> R.string.conversation_tag_error_empty_name
        ConversationTagErrorCode.NAME_TOO_LONG -> R.string.conversation_tag_error_name_too_long
        ConversationTagErrorCode.INVALID_COLOR -> R.string.conversation_tag_error_invalid_color
        ConversationTagErrorCode.DUPLICATE_NAME -> R.string.conversation_tag_error_duplicate_name
        ConversationTagErrorCode.TAG_LIMIT_REACHED -> R.string.conversation_tag_error_tag_limit
        ConversationTagErrorCode.CONVERSATION_TAG_LIMIT_REACHED -> R.string.conversation_tag_error_conversation_limit
        ConversationTagErrorCode.TAG_NOT_FOUND -> R.string.conversation_tag_error_tag_not_found
        ConversationTagErrorCode.CONVERSATION_NOT_FOUND -> R.string.conversation_tag_error_conversation_not_found
        ConversationTagErrorCode.SAME_TAG -> R.string.conversation_tag_error_same_tag
    }
)

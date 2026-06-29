package me.rerere.rikkahub.ui.pages.archive

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Archive
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PinOff
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.uuid.Uuid
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@Composable
fun ArchivePage(vm: ArchiveVM = koinViewModel()) {
    val navController = LocalNavController.current
    val searchMode by vm.archiveSearchMode.collectAsStateWithLifecycle()
    val conversations by vm.archivedConversations.collectAsStateWithLifecycle()
    val messageResults by vm.messageSearchResults.collectAsStateWithLifecycle()
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val showTitleList =
        searchMode == ArchiveSearchMode.TITLE ||
            (searchMode == ArchiveSearchMode.MESSAGE && searchText.isBlank())
    val listEmpty =
        if (showTitleList) {
            conversations.isEmpty()
        } else {
            messageResults.isEmpty()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showTopMenu = true }) {
                        Icon(
                            HugeIcons.MoreVertical,
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.archive_unarchive_all)) },
                            onClick = {
                                vm.unarchiveAll()
                                showTopMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.archive_clear_all)) },
                            onClick = {
                                showClearAllDialog = true
                                showTopMenu = false
                            },
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val modes = ArchiveSearchMode.entries
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = searchMode == mode,
                        onClick = { vm.setSearchMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) {
                        Text(
                            when (mode) {
                                ArchiveSearchMode.TITLE ->
                                    stringResource(R.string.archive_search_mode_title)
                                ArchiveSearchMode.MESSAGE ->
                                    stringResource(R.string.archive_search_mode_message)
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    vm.setSearchQuery(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.archive_search_placeholder)) },
                singleLine = true,
            )

            if (listEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.archive_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (showTitleList) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        ArchivedConversationItem(
                            conversation = conversation,
                            onClick = {
                                navController.navigate(
                                    Screen.Chat(id = conversation.id.toString()),
                                )
                            },
                            onUnarchive = { vm.unarchive(conversation) },
                            onDeletePermanently = { vm.deletePermanently(conversation) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        messageResults,
                        key = { "${it.conversationId}:${it.messageId}" },
                    ) { result ->
                        ArchivedMessageResultItem(
                            result = result,
                            onClick = {
                                navigateToChatPage(
                                    navController,
                                    chatId = Uuid.parse(result.conversationId),
                                    nodeId = Uuid.parse(result.nodeId),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.archive_clear_all)) },
            text = { Text(stringResource(R.string.archive_clear_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAll()
                        showClearAllDialog = false
                    },
                ) {
                    Text(stringResource(R.string.history_page_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.history_page_cancel))
                }
            },
        )
    }
}

@Composable
private fun ArchivedConversationItem(
    conversation: Conversation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onUnarchive: () -> Unit = {},
    onDeletePermanently: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(25),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = conversation.title.ifBlank {
                            stringResource(R.string.history_page_new_conversation)
                        }.trim(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                supportingContent = {
                    val archivedLabel = conversation.archivedAt?.toLocalDateTime()
                    if (archivedLabel != null) {
                        Text(archivedLabel)
                    } else {
                        Text(conversation.updateAt.toLocalDateTime())
                    }
                },
                trailingContent = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            HugeIcons.MoreVertical,
                            contentDescription = null,
                        )
                    }
                },
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archive_unarchive)) },
                leadingIcon = { Icon(HugeIcons.PinOff, contentDescription = null) },
                onClick = {
                    onUnarchive()
                    showMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archive_delete_permanently)) },
                leadingIcon = { Icon(HugeIcons.Delete01, contentDescription = null) },
                onClick = {
                    onDeletePermanently()
                    showMenu = false
                },
            )
        }
    }
}

@Composable
private fun ArchivedMessageResultItem(
    result: MessageSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val untitled = stringResource(R.string.search_page_untitled)
    val snippetText = buildAnnotatedString {
        val snippet = result.snippet
        var index = 0
        while (index < snippet.length) {
            val start = snippet.indexOf('[', index)
            if (start == -1) {
                append(snippet.substring(index))
                break
            }
            if (start > index) {
                append(snippet.substring(index, start))
            }
            val end = snippet.indexOf(']', start + 1)
            if (end == -1) {
                append(snippet.substring(start))
                break
            }
            val matched = snippet.substring(start + 1, end)
            withStyle(SpanStyle(background = highlightColor)) {
                append(matched)
            }
            index = end + 1
        }
    }
    val formattedTime = remember(result.updateAt) {
        result.updateAt.toLocalDateTime()
    }

    Surface(
        onClick = onClick,
        color = CustomColors.listItemColors.containerColor,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = result.title.ifBlank { untitled },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = snippetText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
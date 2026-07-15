package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ColorPicker
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.GitMerge
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.ConversationTag
import me.rerere.rikkahub.data.model.ConversationTagErrorCode
import me.rerere.rikkahub.data.model.ConversationTagRules
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ConversationTagLabel
import me.rerere.rikkahub.ui.components.ui.conversationTagColor
import me.rerere.rikkahub.ui.components.ui.conversationTagErrorMessage
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingConversationTagsPage(vm: SettingConversationTagsVM = koinViewModel()) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    val referenceCounts by vm.referenceCounts.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val countsById = remember(referenceCounts) { referenceCounts.associate { it.tagId to it.count } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editingTag by remember { mutableStateOf<ConversationTag?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var recoloringTag by remember { mutableStateOf<ConversationTag?>(null) }
    var deletingTag by remember { mutableStateOf<ConversationTag?>(null) }
    var pendingMerge by remember { mutableStateOf<Pair<ConversationTag, ConversationTag>?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.conversation_tag_settings_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        enabled = tags.size < ConversationTagRules.MAX_TAGS,
                    ) {
                        Icon(HugeIcons.Add01, stringResource(R.string.conversation_tag_create))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        when {
            !loaded -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            tags.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.conversation_tag_empty_title))
                        Text(
                            stringResource(R.string.conversation_tag_empty_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { showCreateDialog = true }) {
                            Text(stringResource(R.string.conversation_tag_create))
                        }
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tags, key = { it.id }) { tag ->
                    ConversationTagCard(
                        tag = tag,
                        referenceCount = countsById[tag.id] ?: 0,
                        onRename = { editingTag = tag },
                        onRecolor = { recoloringTag = tag },
                        onDelete = { deletingTag = tag },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ConversationTagNameDialog(
            title = stringResource(R.string.conversation_tag_create),
            initialName = "",
            initialColor = ConversationTagRules.defaultColor(tags.size),
            showColor = true,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color, complete ->
                vm.createTag(name, color) { error ->
                    complete(error)
                    if (error == null) showCreateDialog = false
                }
            },
        )
    }

    editingTag?.let { source ->
        ConversationTagNameDialog(
            title = stringResource(R.string.conversation_tag_rename),
            initialName = source.displayName,
            initialColor = source.colorKey,
            showColor = false,
            onDismiss = { editingTag = null },
            onConfirm = { name, _, complete ->
                vm.renameTag(source.id, name) { error ->
                    if (error is ConversationTagUiError.Domain &&
                        error.code == ConversationTagErrorCode.DUPLICATE_NAME
                    ) {
                        val targetName = runCatching {
                            ConversationTagRules.normalizeName(name).normalizedName
                        }.getOrNull()
                        val target = tags.firstOrNull {
                            it.id != source.id && runCatching {
                                ConversationTagRules.normalizeName(it.displayName).normalizedName
                            }.getOrNull() == targetName
                        }
                        if (target != null) {
                            editingTag = null
                            pendingMerge = source to target
                            return@renameTag
                        }
                    }
                    complete(error)
                    if (error == null) editingTag = null
                }
            },
        )
    }

    recoloringTag?.let { tag ->
        ConversationTagColorDialog(
            tag = tag,
            onDismiss = { recoloringTag = null },
            onConfirm = { color, complete ->
                vm.recolorTag(tag.id, color) { error ->
                    complete(error)
                    if (error == null) recoloringTag = null
                }
            },
        )
    }

    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text(stringResource(R.string.conversation_tag_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.conversation_tag_delete_confirm,
                        tag.displayName,
                        countsById[tag.id] ?: 0,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTag(tag.id) { if (it == null) deletingTag = null }
                }) { Text(stringResource(R.string.conversation_tag_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingMerge?.let { (source, target) ->
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            icon = { Icon(HugeIcons.GitMerge, null) },
            title = { Text(stringResource(R.string.conversation_tag_merge_title)) },
            text = {
                Text(stringResource(R.string.conversation_tag_merge_confirm, source.displayName, target.displayName))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.mergeTag(source.id, target.id) { if (it == null) pendingMerge = null }
                }) { Text(stringResource(R.string.conversation_tag_merge)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMerge = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ConversationTagCard(
    tag: ConversationTag,
    referenceCount: Int,
    onRename: () -> Unit,
    onRecolor: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        ListItem(
            headlineContent = { ConversationTagLabel(tag.displayName, tag.colorKey) },
            supportingContent = {
                Text(stringResource(R.string.conversation_tag_reference_count, referenceCount))
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(HugeIcons.MoreVertical, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.conversation_tag_rename)) },
                            leadingIcon = { Icon(HugeIcons.PencilEdit01, null) },
                            onClick = { menuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.conversation_tag_change_color)) },
                            leadingIcon = { Icon(HugeIcons.ColorPicker, null) },
                            onClick = { menuExpanded = false; onRecolor() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.conversation_tag_delete)) },
                            leadingIcon = { Icon(HugeIcons.Delete01, null) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ConversationTagNameDialog(
    title: String,
    initialName: String,
    initialColor: String,
    showColor: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, (ConversationTagUiError?) -> Unit) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var color by remember(initialColor) { mutableStateOf(initialColor) }
    var error by remember { mutableStateOf<ConversationTagUiError?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.conversation_tag_name)) },
                    supportingText = {
                        Text(stringResource(R.string.conversation_tag_name_limit, ConversationTagRules.MAX_NAME_CODE_POINTS))
                    },
                    isError = error != null,
                    singleLine = true,
                )
                if (showColor) ConversationTagColorOptions(color) { color = it; error = null }
                error?.let { Text(conversationTagUiErrorMessage(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) { error = it } }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ConversationTagColorDialog(
    tag: ConversationTag,
    onDismiss: () -> Unit,
    onConfirm: (String, (ConversationTagUiError?) -> Unit) -> Unit,
) {
    var color by remember(tag.id) { mutableStateOf(tag.colorKey) }
    var error by remember { mutableStateOf<ConversationTagUiError?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversation_tag_change_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConversationTagColorOptions(color) { color = it; error = null }
                error?.let { Text(conversationTagUiErrorMessage(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) { error = it } }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ConversationTagColorOptions(selectedColor: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConversationTagRules.COLOR_PALETTE.forEach { colorKey ->
            FilterChip(
                selected = colorKey == selectedColor,
                onClick = { onSelect(colorKey) },
                label = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(conversationTagColor(colorKey), CircleShape)
                        )
                        Text(colorKey)
                    }
                },
            )
        }
    }
}

@Composable
private fun conversationTagUiErrorMessage(error: ConversationTagUiError): String = when (error) {
    is ConversationTagUiError.Domain -> conversationTagErrorMessage(error.code)
    ConversationTagUiError.Unexpected -> stringResource(R.string.conversation_tag_error_unknown)
}

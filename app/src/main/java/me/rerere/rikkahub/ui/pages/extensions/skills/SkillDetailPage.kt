package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.rerere.rikkahub.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.FilePen
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SkillDetailPage(skillName: String, assistantId: String? = null) {
    val vm = koinViewModel<SkillDetailVM>()
    LaunchedEffect(skillName, assistantId) {
        vm.init(
            name = skillName,
            assistantId = assistantId?.let { Uuid.parse(it) },
        )
    }

    val tree by vm.tree.collectAsStateWithLifecycle()
    val assistantTargets by vm.assistantTargets.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val resources = LocalResources.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toaster = LocalToaster.current

    var editingFile by remember { mutableStateOf<SkillFile?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SkillFile?>(null) }
    var selectedAssistantId by rememberSaveable(skillName) { mutableStateOf<String?>(null) }
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
    val deleteFailedMsg = stringResource(R.string.skill_detail_page_delete_failed)
    val copyFailedMsg = stringResource(R.string.skill_detail_page_private_copy_failed)
    val selectedTarget = remember(assistantTargets, selectedAssistantId) {
        assistantTargets.firstOrNull { it.assistant.id.toString() == selectedAssistantId }
            ?: assistantTargets.firstOrNull()
    }
    LaunchedEffect(assistantTargets) {
        if (selectedTarget != null) {
            selectedAssistantId = selectedTarget.assistant.id.toString()
        }
    }

    val scrollState = rememberScrollState()
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    val fabVisible by remember {
        derivedStateOf {
            val delta = scrollState.value - previousScrollOffset
            previousScrollOffset = scrollState.value
            delta <= 0
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(skillName) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Lucide.Plus, contentDescription = null)
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding + PaddingValues(8.dp)),
        ) {
            if (assistantId == null) {
                GlobalSkillAssistantPanel(
                    assistantTargets = assistantTargets,
                    selectedTarget = selectedTarget,
                    defaultAssistantName = defaultAssistantName,
                    onSelect = { selectedAssistantId = it.assistant.id.toString() },
                    onCopy = { target ->
                        val assistantName = target.assistant.displayName(defaultAssistantName)
                        vm.copyGlobalSkillToAssistant(target.assistant.id) { success ->
                            if (success) {
                                toaster.show(
                                    resources.getString(
                                        R.string.skill_detail_page_private_copy_success,
                                        assistantName,
                                    )
                                )
                            } else {
                                toaster.show(copyFailedMsg)
                            }
                        }
                    },
                    onManage = { target ->
                        navController.navigate(Screen.AssistantInjections(target.assistant.id.toString(), initialPage = 3))
                    },
                )
            }
            FileTree(
                nodes = tree,
                depth = 0,
                onEdit = { editingFile = it },
                onDelete = { deleteTarget = it },
            )
        }
    }

    editingFile?.let { skillFile ->
        EditFileDialog(
            skillFile = skillFile,
            initialContent = remember(skillFile.relativePath) { vm.readFile(skillFile) },
            onDismiss = { editingFile = null },
            onConfirm = { content ->
                vm.saveFile(skillFile.relativePath, content) { error ->
                    if (error == null) editingFile = null
                    else toaster.show(error)
                }
            },
        )
    }

    if (showAddDialog) {
        AddFileDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { fileName, content ->
                vm.saveFile(fileName, content) { error ->
                    if (error == null) showAddDialog = false
                    else toaster.show(error)
                }
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.skill_detail_page_delete_file),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { skillFile ->
                vm.deleteFile(skillFile) { success ->
                    if (!success) toaster.show(deleteFailedMsg)
                }
            }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.skill_detail_page_delete_confirm, deleteTarget?.relativePath ?: ""))
    }
}

@Composable
private fun GlobalSkillAssistantPanel(
    assistantTargets: List<SkillAssistantTarget>,
    selectedTarget: SkillAssistantTarget?,
    defaultAssistantName: String,
    onSelect: (SkillAssistantTarget) -> Unit,
    onCopy: (SkillAssistantTarget) -> Unit,
    onManage: (SkillAssistantTarget) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Lucide.FileText,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.skill_detail_page_global_skill_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.skill_detail_page_global_skill_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.skill_detail_page_private_copy_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (assistantTargets.isEmpty()) {
                Text(
                    text = stringResource(R.string.skill_detail_page_no_assistants),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (selectedTarget != null) {
                Select(
                    options = assistantTargets,
                    selectedOption = selectedTarget,
                    onOptionSelected = onSelect,
                    optionToString = { it.assistant.displayName(defaultAssistantName) },
                    optionDescription = {
                        if (it.hasPrivateSkill) {
                            stringResource(R.string.skill_detail_page_private_copy_exists)
                        } else {
                            stringResource(R.string.skill_detail_page_private_copy_not_created)
                        }
                    },
                    leading = {
                        Icon(
                            imageVector = Lucide.FileText,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (selectedTarget.hasPrivateSkill) {
                    Text(
                        text = stringResource(R.string.skill_detail_page_private_copy_exists),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                OutlinedButton(
                    onClick = { onManage(selectedTarget) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Lucide.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.skill_detail_page_manage_assistant_skills),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Button(
                    onClick = { onCopy(selectedTarget) },
                    enabled = !selectedTarget.hasPrivateSkill,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.skill_detail_page_copy_to_assistant),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTree(
    nodes: List<SkillFileNode>,
    depth: Int,
    onEdit: (SkillFile) -> Unit,
    onDelete: (SkillFile) -> Unit,
) {
    nodes.fastForEach { node ->
        when (node) {
            is SkillFileNode.FileNode -> FileItem(
                skillFile = node.skillFile,
                depth = depth,
                onEdit = { onEdit(node.skillFile) },
                onDelete = { onDelete(node.skillFile) },
            )

            is SkillFileNode.DirNode -> DirItem(
                node = node,
                depth = depth,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

private fun Assistant.displayName(defaultAssistantName: String): String {
    return name.ifBlank { defaultAssistantName }
}

@Composable
private fun FileItem(
    skillFile: SkillFile,
    depth: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (16 + depth * 20).dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.FileText,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = skillFile.file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Text(
                text = "${skillFile.file.length()} B",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Lucide.FilePen,
                    contentDescription = stringResource(R.string.edit),
                    modifier = Modifier.size(16.dp),
                )
            }
            if (skillFile.relativePath != "SKILL.md") {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Lucide.Trash2,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DirItem(
    node: SkillFileNode.DirNode,
    depth: Int,
    onEdit: (SkillFile) -> Unit,
    onDelete: (SkillFile) -> Unit,
) {
    var expanded by rememberSaveable(node.relativePath) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = (16 + depth * 20).dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (expanded) Lucide.FolderOpen else Lucide.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    FileTree(
                        nodes = node.children,
                        depth = depth + 1,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditFileDialog(
    skillFile: SkillFile,
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (content: String) -> Unit,
) {
    var content by rememberSaveable(skillFile.relativePath) { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(skillFile.relativePath, fontFamily = FontFamily.Monospace) },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.skill_detail_page_content)) },
                minLines = 10,
                maxLines = 20,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(content) }) { Text(stringResource(R.string.skill_detail_page_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AddFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, content: String) -> Unit,
) {
    var fileName by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    val fileNameError = fileName.isNotBlank() && (fileName.contains('\\'))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skill_detail_page_new_file)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(R.string.skill_detail_page_file_name)) },
                    placeholder = { Text("examples/basic.md", fontFamily = FontFamily.Monospace) },
                    supportingText = {
                        if (fileNameError) Text(
                            stringResource(R.string.skill_detail_page_file_name_invalid),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    isError = fileNameError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.skill_detail_page_content)) },
                    minLines = 6,
                    maxLines = 14,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(fileName.trim(), content) },
                enabled = fileName.isNotBlank() && !fileNameError,
            ) {
                Text(stringResource(R.string.skill_detail_page_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

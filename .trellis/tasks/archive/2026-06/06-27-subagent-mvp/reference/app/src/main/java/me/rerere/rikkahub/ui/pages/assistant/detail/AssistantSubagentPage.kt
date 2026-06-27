package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.mergeSubagentProfiles
import me.rerere.rikkahub.data.ai.subagent.removeSubagentProfile
import me.rerere.rikkahub.data.ai.subagent.upsertSubagentProfile
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantSubagentPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = { parametersOf(id) }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_page_tab_subagent)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantSubagentContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            onUpdate = { vm.update(it) },
            onOpenProfile = { profileName ->
                navController.navigate(Screen.AssistantSubagentProfile(id, profileName))
            },
        )
    }
}

@Composable
private fun AssistantSubagentContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val profiles = mergeSubagentProfiles(assistant.subagentProfiles, assistant.disabledBuiltinSubagents)
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- 总开关 + 最大深度 ----
        item {
            Card(
                colors = CustomColors.cardColorsOnSurfaceContainer
            ) {
                val levels = (assistant.subagentMaxDepth - 1).coerceAtLeast(0)
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text(stringResource(R.string.subagent_enable_title)) },
                    description = { Text(stringResource(R.string.subagent_enable_desc)) },
                    tail = {
                        Switch(
                            checked = assistant.enableSubagents,
                            onCheckedChange = {
                                onUpdate(assistant.copy(enableSubagents = it))
                            },
                        )
                    },
                )

                HorizontalDivider()

                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text(stringResource(R.string.subagent_max_depth_title)) },
                    description = {
                        if (levels == 0) {
                            Text(stringResource(R.string.subagent_max_depth_disabled))
                        } else {
                            Text(
                                stringResource(
                                    R.string.subagent_max_depth_desc,
                                    assistant.subagentMaxDepth,
                                    levels,
                                )
                            )
                        }
                    },
                ) {
                    Slider(
                        value = assistant.subagentMaxDepth.toFloat(),
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    subagentMaxDepth = it.toInt().coerceIn(1, 5)
                                )
                            )
                        },
                        valueRange = 1f..5f,
                        steps = 3,
                    )
                }

                HorizontalDivider()

                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text(stringResource(R.string.subagent_delegate_only_title)) },
                    description = { Text(stringResource(R.string.subagent_delegate_only_desc)) },
                    tail = {
                        Switch(
                            checked = assistant.subagentDelegateOnly,
                            enabled = assistant.enableSubagents,
                            onCheckedChange = {
                                onUpdate(assistant.copy(subagentDelegateOnly = it))
                            },
                        )
                    },
                )

                HorizontalDivider()

                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text(stringResource(R.string.subagent_parallel_execution_title)) },
                    description = { Text(stringResource(R.string.subagent_parallel_execution_desc)) },
                    tail = {
                        Switch(
                            checked = assistant.parallelToolExecution,
                            enabled = assistant.enableSubagents,
                            onCheckedChange = {
                                onUpdate(assistant.copy(parallelToolExecution = it))
                            },
                        )
                    },
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.subagent_profiles_section),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.subagent_profiles_section_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (assistant.disabledBuiltinSubagents.isNotEmpty()) {
                        IconButton(onClick = {
                            onUpdate(assistant.copy(disabledBuiltinSubagents = emptySet()))
                        }) {
                            Icon(HugeIcons.Refresh03, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(HugeIcons.Add01, contentDescription = null)
                    }
                }
            }
        }

        // ---- profile 卡片 ----
        items(profiles.size) { index ->
            val profile = profiles[index]
            CardGroup(modifier = Modifier.fillMaxWidth()) {
                item(
                    onClick = { onOpenProfile(profile.name) },
                    leadingContent = { Icon(HugeIcons.Connect, null) },
                    overlineContent = { Text(profile.name) },
                    headlineContent = {
                        Text(profile.displayName.ifBlank { profile.name })
                    },
                    supportingContent = {
                        Text(
                            text = profile.description.ifBlank { profile.name },
                            maxLines = 2,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { pendingDelete = profile.name }) {
                                Icon(HugeIcons.Delete01, contentDescription = null)
                            }
                            Icon(HugeIcons.ArrowRight01, null)
                        }
                    },
                )
            }
        }
    }

    // ---- 新建子代理对话框 ----
    if (showCreateDialog) {
        val isValidName = newProfileName.matches(SubagentProfile.IdentifierRegex) &&
            profiles.none { it.name == newProfileName }
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newProfileName = ""
            },
            title = { Text("新建子代理") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("名称") },
                        supportingText = { Text("小写字母/数字/下划线，字母开头") },
                        singleLine = true,
                        isError = newProfileName.isNotEmpty() && !isValidName,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isValidName,
                    onClick = {
                        val profile = SubagentProfile(name = newProfileName)
                        onUpdate(
                            assistant.copy(
                                subagentProfiles = upsertSubagentProfile(
                                    assistant.subagentProfiles,
                                    profile,
                                )
                            )
                        )
                        onOpenProfile(newProfileName)
                        newProfileName = ""
                        showCreateDialog = false
                    },
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newProfileName = ""
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    // ---- 删除确认对话框 ----
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除子代理") },
            text = { Text("确定删除该子代理配置吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = pendingDelete!!
                        val isBuiltin = SubagentProfile.BUILTIN.any { it.name == name }
                        onUpdate(
                            assistant.copy(
                                subagentProfiles = removeSubagentProfile(assistant.subagentProfiles, name),
                                // 内置 profile 删除后加入禁用集合，使其不再出现在合并结果中（完全删除）；
                                // 自定义 profile 仅从 custom 列表移除即可。
                                disabledBuiltinSubagents = if (isBuiltin) {
                                    assistant.disabledBuiltinSubagents + name
                                } else {
                                    assistant.disabledBuiltinSubagents
                                },
                            )
                        )
                        pendingDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

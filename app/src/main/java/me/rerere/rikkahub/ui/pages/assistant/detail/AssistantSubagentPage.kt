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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
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
    val settings by vm.settings.collectAsStateWithLifecycle()
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
            globalProfiles = settings.globalSubagentProfiles,
            onUpdate = { vm.update(it) },
            onOpenProfile = { profileName, createMode ->
                navController.navigate(Screen.AssistantSubagentProfile(id, profileName, createMode))
            },
            onOpenGlobalProfile = { profileName ->
                navController.navigate(Screen.ExtensionSubagentProfile(profileName, false))
            },
        )
    }
}

@Composable
private fun AssistantSubagentContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    globalProfiles: List<SubagentProfile>,
    onUpdate: (Assistant) -> Unit,
    onOpenProfile: (String, Boolean) -> Unit,
    onOpenGlobalProfile: (String) -> Unit,
) {
    val entries = subagentListEntries(assistant, globalProfiles)
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SubagentListEntry?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AssistantSubagentHubCard(
                assistant = assistant,
                onUpdate = onUpdate,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    if (assistant.disabledGlobalSubagents.isNotEmpty()) {
                        IconButton(onClick = {
                            onUpdate(
                                assistant.copy(
                                    disabledGlobalSubagents = emptySet(),
                                )
                            )
                        }) {
                            Icon(
                                HugeIcons.Refresh03,
                                contentDescription = stringResource(R.string.common_refresh),
                            )
                        }
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            HugeIcons.Add01,
                            contentDescription = stringResource(R.string.extensions_page_add_subagent),
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.subagent_profiles_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        items(entries.size) { index ->
            val entry = entries[index]
            val profile = entry.profile
            val cardAlpha = if (entry.isDisabledGlobal) 0.45f else 1f
            CardGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(cardAlpha),
            ) {
                item(
                    onClick = {
                        when {
                            entry.isDisabledGlobal -> Unit
                            // Always open via assistant page so parent-assistant memory
                            // table documents are available for injection selection.
                            // Global-only field editing remains redirected in the form.
                            else -> onOpenProfile(profile.name, false)
                        }
                    },
                    leadingContent = { Icon(HugeIcons.Connect, null) },
                    overlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(profile.name)
                            if (entry.isGlobal) {
                                Text(
                                    text = stringResource(R.string.subagent_global_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    },
                    headlineContent = {
                        Text(profile.name)
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = profile.description.ifBlank { profile.name },
                                maxLines = 2,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            val spawnLabel = if (profile.canSpawn) {
                                stringResource(R.string.subagent_can_spawn_yes)
                            } else {
                                stringResource(R.string.subagent_can_spawn_no)
                            }
                            Text(
                                text = stringResource(
                                    R.string.subagent_profile_card_meta,
                                    workspaceAccessLabel(profile.workspaceAccess),
                                    workspaceApprovalLabel(profile.workspaceApproval),
                                    spawnLabel,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (entry.isDisabledGlobal) {
                                IconButton(onClick = {
                                    onUpdate(
                                        assistant.copy(
                                            disabledGlobalSubagents = assistant.disabledGlobalSubagents - profile.name,
                                        )
                                    )
                                }) {
                                    Icon(
                                        HugeIcons.Refresh03,
                                        contentDescription = stringResource(R.string.common_refresh),
                                    )
                                }
                            } else {
                                if (entry.isGlobal) {
                                    IconButton(onClick = {
                                        onUpdate(
                                            assistant.copy(
                                                disabledGlobalSubagents = assistant.disabledGlobalSubagents + profile.name,
                                            )
                                        )
                                    }) {
                                        Icon(
                                            HugeIcons.Delete01,
                                            contentDescription = stringResource(R.string.common_delete),
                                        )
                                    }
                                } else {
                                    IconButton(onClick = {
                                        val cloneName = generateCloneName(
                                            profile.name,
                                            assistant,
                                            globalProfiles,
                                        )
                                        val clone = profile.copy(
                                            name = cloneName,
                                        )
                                        onUpdate(
                                            assistant.copy(
                                                subagentProfiles = upsertSubagentProfile(
                                                    assistant.subagentProfiles,
                                                    clone,
                                                )
                                            )
                                        )
                                        onOpenProfile(cloneName, false)
                                    }) {
                                        Icon(
                                            HugeIcons.Copy01,
                                            contentDescription = stringResource(R.string.common_copy),
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = entry }) {
                                        Icon(
                                            HugeIcons.Delete01,
                                            contentDescription = stringResource(R.string.common_delete),
                                        )
                                    }
                                }
                            }
                            if (!entry.isDisabledGlobal) {
                                Icon(HugeIcons.ArrowRight01, null)
                            }
                        }
                    },
                )
            }
        }
    }

    if (showCreateDialog) {
        val takenNames = entries.map { it.profile.name }.toSet()
        val isValidName = newProfileName.matches(SubagentProfile.IdentifierRegex) &&
            newProfileName !in takenNames &&
            !isGlobalSubagentName(newProfileName, globalProfiles)
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newProfileName = ""
            },
            title = { Text(stringResource(R.string.subagent_create_profile_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text(stringResource(R.string.subagent_profile_name)) },
                        supportingText = { Text(stringResource(R.string.subagent_profile_name_desc)) },
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
                        onOpenProfile(newProfileName, true)
                        newProfileName = ""
                        showCreateDialog = false
                    },
                ) {
                    Text(stringResource(R.string.skill_detail_page_create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newProfileName = ""
                    },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (pendingDelete != null) {
        val entry = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.subagent_delete_profile_title)) },
            text = { Text(stringResource(R.string.subagent_delete_profile_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(
                            assistant.copy(
                                subagentProfiles = removeSubagentProfile(
                                    assistant.subagentProfiles,
                                    entry.profile.name,
                                )
                            )
                        )
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private fun generateCloneName(
    base: String,
    assistant: Assistant,
    globalProfiles: List<SubagentProfile>,
): String {
    val taken = subagentListEntries(assistant, globalProfiles).map { it.profile.name }.toSet()
    var i = 1
    while (true) {
        val candidate = "${base}_copy$i"
        if (candidate.matches(SubagentProfile.IdentifierRegex) && candidate !in taken) {
            return candidate
        }
        i++
    }
}

@Composable
private fun AssistantSubagentHubCard(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CustomColors.cardColorsOnSurfaceContainer,
        modifier = modifier,
    ) {
        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.subagent_enable_title)) },
            description = { Text(stringResource(R.string.subagent_enable_desc)) },
            tail = {
                Switch(
                    checked = assistant.enableSubagents,
                    onCheckedChange = { enabled ->
                        onUpdate(assistant.copy(enableSubagents = enabled))
                    },
                )
            },
        )

        HorizontalDivider()

        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.subagent_max_depth_title)) },
            description = {
                if (assistant.subagentMaxDepth <= 1) {
                    Text(stringResource(R.string.subagent_max_depth_disabled))
                } else {
                    Text(
                        stringResource(
                            R.string.subagent_max_depth_desc,
                            assistant.subagentMaxDepth,
                            assistant.subagentMaxDepth - 1,
                        )
                    )
                }
            },
        ) {
            var localMaxDepth by remember(assistant.id, assistant.subagentMaxDepth) {
                mutableStateOf(assistant.subagentMaxDepth.toFloat())
            }
            Slider(
                value = localMaxDepth,
                onValueChange = { localMaxDepth = it },
                onValueChangeFinished = {
                    onUpdate(
                        assistant.copy(
                            subagentMaxDepth = localMaxDepth.toInt().coerceIn(1, 5)
                        )
                    )
                },
                valueRange = 1f..5f,
                steps = 3,
                enabled = assistant.enableSubagents,
            )
        }

        HorizontalDivider()

        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.subagent_max_concurrent_title)) },
            description = {
                Text(
                    stringResource(
                        R.string.subagent_max_concurrent_desc,
                        assistant.subagentMaxConcurrent,
                    )
                )
            },
        ) {
            var localMaxConcurrent by remember(assistant.id, assistant.subagentMaxConcurrent) {
                mutableStateOf(assistant.subagentMaxConcurrent.toFloat())
            }
            Slider(
                value = localMaxConcurrent,
                onValueChange = { localMaxConcurrent = it },
                onValueChangeFinished = {
                    onUpdate(
                        assistant.copy(
                            subagentMaxConcurrent = localMaxConcurrent.toInt().coerceIn(1, 5)
                        )
                    )
                },
                valueRange = 1f..5f,
                steps = 3,
                enabled = assistant.enableSubagents,
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
                    onCheckedChange = { v ->
                        onUpdate(assistant.copy(subagentDelegateOnly = v))
                    },
                    enabled = assistant.enableSubagents,
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
                    onCheckedChange = { v ->
                        onUpdate(assistant.copy(parallelToolExecution = v))
                    },
                    enabled = assistant.enableSubagents,
                )
            },
        )

        HorizontalDivider()

        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.subagent_profile_steps_countdown_threshold)) },
            description = { Text(stringResource(R.string.subagent_profile_steps_countdown_off)) },
        ) {
            var localThreshold by remember(assistant.id, assistant.stepsCountdownThreshold) {
                mutableStateOf(assistant.stepsCountdownThreshold?.toFloat() ?: -1f)
            }
            Slider(
                value = localThreshold,
                onValueChange = { localThreshold = it },
                onValueChangeFinished = {
                    val persistedValue = when {
                        localThreshold < 0f -> null
                        localThreshold.roundToInt() == 0 -> 0
                        else -> localThreshold.roundToInt().coerceIn(1, 16)
                    }
                    onUpdate(assistant.copy(stepsCountdownThreshold = persistedValue))
                },
                valueRange = -1f..16f,
                modifier = Modifier.fillMaxWidth(),
                enabled = assistant.enableSubagents,
            )
            Text(
                text = when {
                    localThreshold < 0f -> stringResource(R.string.subagent_profile_steps_countdown_auto)
                    localThreshold.roundToInt() == 0 -> stringResource(R.string.subagent_profile_steps_countdown_off)
                    else -> localThreshold.roundToInt().toString()
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

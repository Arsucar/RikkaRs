package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExtensionSubagentsPage() {
    val vm: SettingVM = koinViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SubagentProfile?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val profiles = SubagentRegistry.effectiveGlobalProfiles(settings.globalSubagentProfiles)

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.extensions_subagents_page_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            HugeIcons.Add01,
                            contentDescription = stringResource(R.string.extensions_page_add_subagent),
                        )
                    }
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            HugeIcons.MoreVertical,
                            contentDescription = stringResource(R.string.skills_page_more_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.extensions_subagents_page_restore_defaults)) },
                            onClick = {
                                showOverflowMenu = false
                                vm.restoreDefaultSubagents()
                                toaster.show(context.getString(R.string.extensions_subagents_page_restore_defaults_done))
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (profiles.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.extensions_subagents_page_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(profiles, key = { it.name }) { profile ->
                CardGroup(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item(
                        onClick = {
                            navController.navigate(Screen.ExtensionSubagentProfile(profile.name, false))
                        },
                        leadingContent = { Icon(HugeIcons.Connect, null) },
                        headlineContent = { Text(profile.displayName.ifBlank { profile.name }) },
                        supportingContent = {
                            Text(profile.description.take(80))
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        navController.navigate(
                                            Screen.ExtensionSubagentProfile(profile.name, false),
                                        )
                                    },
                                ) {
                                    Icon(
                                        HugeIcons.ArrowRight01,
                                        contentDescription = stringResource(R.string.extensions_subagents_open),
                                    )
                                }
                                IconButton(onClick = { pendingDelete = profile }) {
                                    Icon(
                                        HugeIcons.Delete01,
                                        contentDescription = stringResource(R.string.common_delete),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        val takenNames = profiles.map { it.name }.toSet()
        val isValidName = newProfileName.matches(SubagentProfile.IdentifierRegex) &&
            newProfileName !in takenNames
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
                        navController.navigate(Screen.ExtensionSubagentProfile(newProfileName, true))
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
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.subagent_delete_profile_title)) },
            text = { Text(stringResource(R.string.subagent_delete_profile_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteGlobalSubagent(pendingDelete!!.name)
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
package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.data.ai.subagent.SUBAGENT_TOOL_NAMES
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.ai.subagent.WorkspaceAccess
import me.rerere.rikkahub.data.ai.subagent.WorkspaceApproval
import me.rerere.rikkahub.data.ai.subagent.toggleSkill
import me.rerere.rikkahub.data.ai.subagent.withLocalToolOptions
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.WorkspaceToolDefaultApprovals
import me.rerere.rikkahub.data.ai.tools.resolveWorkspaceToolApproval
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.TextArea
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantSubagentProfilePage(id: String, profileName: String, createMode: Boolean = false) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = { parametersOf(id) }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val mcpServerConfigs by vm.mcpServerConfigs.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        subagentListEntries(assistant, settings.globalSubagentProfiles)
                            .firstOrNull { it.profile.name == profileName }
                            ?.profile
                            ?.name
                            ?: profileName
                    )
                },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantSubagentProfileContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            globalProfiles = settings.globalSubagentProfiles,
            providers = providers,
            mcpServers = mcpServerConfigs,
            skills = skills,
            presets = settings.presets,
            profileName = profileName,
            createMode = createMode,
            onUpdate = { vm.update(it) },
        )
    }
}

@Composable
internal fun AssistantSubagentProfileContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    globalProfiles: List<SubagentProfile>,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    mcpServers: List<me.rerere.rikkahub.data.ai.mcp.McpServerConfig>,
    skills: List<me.rerere.rikkahub.data.files.SkillMetadata>,
    presets: List<Preset>,
    profileName: String,
    createMode: Boolean,
    onUpdate: (Assistant) -> Unit,
    readOnly: Boolean = false,
) {
    var currentProfileName by remember(profileName) { mutableStateOf(profileName) }
    val effectiveGlobals = SubagentRegistry.effectiveGlobalProfiles(globalProfiles)
    val resolved = SubagentRegistry.resolveProfile(currentProfileName, assistant, globalProfiles)
        ?: effectiveGlobals.firstOrNull { it.name == currentProfileName }
        ?: SubagentProfile(name = currentProfileName)

    val isGlobalOnly = currentProfileName in effectiveGlobals.map { it.name } &&
        currentProfileName !in assistant.subagentProfiles.map { it.name }

    val latestAssistant = rememberUpdatedState(assistant)
    var pathDraft by remember(currentProfileName) { mutableStateOf("") }

    fun persist(transform: (SubagentProfile) -> SubagentProfile) {
        if (readOnly || isGlobalOnly) return
        val base = latestAssistant.value.subagentProfiles.firstOrNull { it.name == currentProfileName }
            ?: SubagentRegistry.resolveProfile(currentProfileName, latestAssistant.value, globalProfiles)
            ?: SubagentRegistry.effectiveGlobalProfiles(globalProfiles).firstOrNull { it.name == currentProfileName }
            ?: SubagentProfile(name = currentProfileName)
        val updated = transform(base)
        val oldName = currentProfileName
        val nextProfiles = latestAssistant.value.subagentProfiles
            .filterNot { it.name == oldName || it.name == updated.name } + updated
        onUpdate(
            latestAssistant.value.copy(
                subagentProfiles = nextProfiles,
            )
        )
        currentProfileName = updated.name
    }

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 4 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(),
    ) {
        if (isGlobalOnly) {
            Text(
                text = stringResource(R.string.subagent_global_edit_in_extensions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(stringResource(R.string.subagent_profile_tab_basic)) },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(stringResource(R.string.subagent_profile_tab_model)) },
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                text = { Text(stringResource(R.string.subagent_profile_tab_tools)) },
            )
            Tab(
                selected = pagerState.currentPage == 3,
                onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                text = { Text(stringResource(R.string.subagent_profile_tab_output)) },
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            SubagentProfileForm(
                resolved = resolved,
                profileName = currentProfileName,
                createMode = createMode,
                maxToolCallsShowsInherit = !isGlobalOnly &&
                    assistant.subagentProfiles.firstOrNull { it.name == currentProfileName }?.maxToolCalls == null,
                takenProfileNames = (subagentListEntries(assistant, globalProfiles).map { it.profile.name } - currentProfileName).toSet(),
                canEditName = !isGlobalOnly &&
                    currentProfileName !in SubagentRegistry.BUILTIN_PROFILES.map { it.name },
                globalProfiles = globalProfiles,
                providers = providers,
                mcpServers = mcpServers,
                skills = skills,
                presets = presets,
                readOnly = readOnly || isGlobalOnly,
                pathDraft = pathDraft,
                onPathDraftChange = { pathDraft = it },
                onPersist = ::persist,
                tabPage = page,
            )
        }
    }
}

@Composable
internal fun SubagentProfileForm(
    resolved: SubagentProfile,
    profileName: String,
    createMode: Boolean,
    maxToolCallsShowsInherit: Boolean = false,
    takenProfileNames: Set<String> = emptySet(),
    canEditName: Boolean = true,
    globalProfiles: List<SubagentProfile> = emptyList(),
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    mcpServers: List<me.rerere.rikkahub.data.ai.mcp.McpServerConfig>,
    skills: List<me.rerere.rikkahub.data.files.SkillMetadata>,
    presets: List<Preset>,
    readOnly: Boolean,
    pathDraft: String,
    onPathDraftChange: (String) -> Unit,
    onPersist: (transform: (SubagentProfile) -> SubagentProfile) -> Unit,
    tabPage: Int,
) {
    fun persist(transform: (SubagentProfile) -> SubagentProfile) {
        if (!readOnly) onPersist(transform)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (tabPage) {
            0 -> {
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = { Text(stringResource(R.string.subagent_profile_name)) },
                        description = { Text(stringResource(R.string.subagent_profile_name_desc)) },
                    ) {
                        var nameDraft by remember(profileName, resolved.name) {
                            mutableStateOf(resolved.name)
                        }
                        val isValidName = nameDraft.matches(SubagentProfile.IdentifierRegex) &&
                            (nameDraft == resolved.name || nameDraft !in takenProfileNames)
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { v ->
                                nameDraft = v
                                if (v != resolved.name && v.matches(SubagentProfile.IdentifierRegex) && v !in takenProfileNames) {
                                    persist { it.copy(name = v) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !readOnly && canEditName,
                            isError = nameDraft.isNotEmpty() && !isValidName,
                        )
                    }

                    HorizontalDivider()

                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = { Text(stringResource(R.string.subagent_profile_description)) },
                        description = { Text(stringResource(R.string.subagent_profile_description_desc)) },
                    ) {
                        OutlinedTextField(
                            value = resolved.description,
                            onValueChange = { v -> persist { it.copy(description = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }

                    HorizontalDivider()

                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = { Text(stringResource(R.string.subagent_profile_system_prompt)) },
                        description = { Text(stringResource(R.string.subagent_profile_system_prompt_desc)) },
                    ) {
                        val promptState = rememberTextFieldState(initialText = resolved.systemPrompt)
                        // Sync when profile changes (user navigates to another subagent)
                        LaunchedEffect(profileName) {
                            promptState.edit { replace(0, length, resolved.systemPrompt) }
                        }
                        // Sync when async-loaded data arrives after composition
                        if (promptState.text.isEmpty() && resolved.systemPrompt.isNotEmpty()) {
                            LaunchedEffect(Unit) {
                                promptState.edit { replace(0, length, resolved.systemPrompt) }
                            }
                        }
                        LaunchedEffect(promptState) {
                            snapshotFlow { promptState.text.toString() }.collect { text ->
                                persist { it.copy(systemPrompt = text) }
                            }
                        }
                        TextArea(
                            state = promptState,
                            label = stringResource(R.string.subagent_profile_system_prompt),
                            minLines = 6,
                            maxLines = 15,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (presets.isNotEmpty()) {
                        HorizontalDivider()

                        FormItem(
                            modifier = Modifier.padding(8.dp),
                            label = { Text(stringResource(R.string.subagent_profile_presets)) },
                            description = { Text(stringResource(R.string.subagent_profile_presets_desc)) },
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                presets.forEach { preset ->
                                    val selected = preset.id in resolved.presetIds
                                    FilterChip(
                                        selected = selected,
                                        enabled = !readOnly,
                                        onClick = {
                                            persist {
                                                it.copy(
                                                    presetIds = if (selected) {
                                                        it.presetIds - preset.id
                                                    } else {
                                                        setOf(preset.id)
                                                    }
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                preset.name.ifBlank {
                                                    stringResource(R.string.extension_content_unnamed_preset)
                                                }
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = { Text(stringResource(R.string.subagent_profile_can_spawn)) },
                        description = { Text(stringResource(R.string.subagent_profile_can_spawn_desc)) },
                        tail = {
                            Switch(
                                checked = resolved.canSpawn,
                                onCheckedChange = { v -> persist { it.copy(canSpawn = v) } },
                            )
                        },
                    )
                }
            }

            1 -> {
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_model)) },
                description = { Text(stringResource(R.string.subagent_profile_model_desc)) },
            ) {
                ModelSelector(
                    modelId = resolved.chatModelId,
                    providers = providers,
                    type = ModelType.CHAT,
                    allowClear = true,
                    onSelect = { model -> persist { it.copy(chatModelId = model.id) } },
                )
            }

            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_temperature)) },
                description = { Text(stringResource(R.string.subagent_profile_temperature_desc)) },
                tail = {
                    Switch(
                        checked = resolved.temperature != null,
                        onCheckedChange = { enabled ->
                            persist { it.copy(temperature = if (enabled) 1.0f else null) }
                        },
                    )
                },
            ) {
                if (resolved.temperature != null) {
                    var temperatureInput by remember(profileName) {
                        mutableStateOf(resolved.temperature.toString())
                    }
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { value ->
                            temperatureInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { t ->
                                persist { it.copy(temperature = t) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_top_p)) },
                tail = {
                    Switch(
                        checked = resolved.topP != null,
                        onCheckedChange = { enabled ->
                            persist { it.copy(topP = if (enabled) 1.0f else null) }
                        },
                    )
                },
            ) {
                resolved.topP?.let { topP ->
                    var topPInput by remember(profileName) { mutableStateOf(topP.toString()) }
                    OutlinedTextField(
                        value = topPInput,
                        onValueChange = { value ->
                            topPInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { p ->
                                persist { it.copy(topP = p) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_max_tokens)) },
            ) {
                OutlinedTextField(
                    value = resolved.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = if (text.isBlank()) null else text.toIntOrNull()?.takeIf { it > 0 }
                        persist { it.copy(maxTokens = tokens) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.subagent_profile_max_tokens_inherit)) },
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_reasoning)) },
            ) {
                ReasoningButton(
                    reasoningLevel = resolved.reasoningLevel,
                    onUpdateReasoningLevel = { level -> persist { it.copy(reasoningLevel = level) } },
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_max_tool_calls)) },
                description = { Text(stringResource(R.string.subagent_profile_max_tool_calls_desc)) },
            ) {
                val resolvedMaxToolCalls = resolved.maxToolCalls ?: 32
                var localMaxToolCalls by remember(profileName, resolvedMaxToolCalls) {
                    mutableStateOf(resolvedMaxToolCalls.toFloat())
                }
                Slider(
                    value = localMaxToolCalls,
                    onValueChange = { localMaxToolCalls = it },
                    onValueChangeFinished = {
                        persist { it.copy(maxToolCalls = localMaxToolCalls.roundToInt().coerceIn(1, 256)) }
                    },
                    valueRange = 1f..256f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = buildString {
                        append(localMaxToolCalls.roundToInt())
                        if (maxToolCallsShowsInherit) {
                            append(" · ")
                            append(stringResource(R.string.subagent_profile_inherit))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_disable_tool_budget_stop)) },
                description = { Text(stringResource(R.string.subagent_profile_disable_tool_budget_stop_desc)) },
                tail = {
                    Switch(
                        checked = resolved.disableToolBudgetStop,
                        onCheckedChange = { v -> persist { it.copy(disableToolBudgetStop = v) } },
                    )
                },
            )

                }
            }

            2 -> {
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_workspace_access)) },
            ) {
                Select(
                    options = WorkspaceAccess.entries,
                    selectedOption = resolved.workspaceAccess,
                    onOptionSelected = { access -> persist { it.copy(workspaceAccess = access) } },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { workspaceAccessLabel(it) },
                    optionDescription = { workspaceAccessDescription(it) },
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_workspace_approval)) },
            ) {
                Select(
                    options = WorkspaceApproval.entries,
                    selectedOption = resolved.workspaceApproval,
                    onOptionSelected = { approval -> persist { it.copy(workspaceApproval = approval) } },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { workspaceApprovalLabel(it) },
                    optionDescription = { workspaceApprovalDescription(it) },
                )
            }

            if (resolved.workspaceApproval == WorkspaceApproval.OVERRIDE) {
                HorizontalDivider()

                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text(stringResource(R.string.subagent_profile_tool_approval_overrides)) },
                    description = { Text(stringResource(R.string.subagent_profile_tool_approval_overrides_desc)) },
                ) {
                    SubagentToolApprovalOverridesEditor(
                        overrides = resolved.toolApprovalOverrides,
                        onChange = { map -> persist { it.copy(toolApprovalOverrides = map) } },
                    )
                }
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_allowed_paths)) },
            ) {
                PathChipEditor(
                    paths = resolved.allowedPathPrefixes,
                    draft = pathDraft,
                    onDraftChange = onPathDraftChange,
                    onAdd = { path ->
                        if (path.isNotBlank() && path !in resolved.allowedPathPrefixes) {
                            persist { it.copy(allowedPathPrefixes = it.allowedPathPrefixes + path) }
                            onPathDraftChange("")
                        }
                    },
                    onRemove = { path -> persist { it.copy(allowedPathPrefixes = it.allowedPathPrefixes - path) } },
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_inherit_tools)) },
                description = { Text(stringResource(R.string.subagent_profile_inherit_tools_desc)) },
                tail = {
                    Switch(
                        checked = resolved.inheritTools,
                        onCheckedChange = { v -> persist { it.copy(inheritTools = v) } },
                    )
                },
            )

            if (resolved.inheritTools) {
                HorizontalDivider()
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text(stringResource(R.string.subagent_profile_excluded_tools)) },
                ) {
                    SubagentExcludedToolChips(
                        selected = resolved.excludedTools,
                        onSelectionChange = { next ->
                            persist { it.copy(excludedTools = next) }
                        },
                    )
                }
                HorizontalDivider()
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text(stringResource(R.string.subagent_profile_extra_local_tools_title)) },
                    description = { Text(stringResource(R.string.subagent_profile_extra_local_tools_desc)) },
                ) {
                    SubagentLocalToolOptionChips(
                        selected = resolved.extraLocalTools,
                        onSelectionChange = { next ->
                            persist { it.withLocalToolOptions(next, extra = true) }
                        },
                    )
                }
            }
                }

                if (!resolved.inheritTools) {
                    LocalToolsSkillMcpSection(
                        resolved = resolved,
                        skills = skills,
                        mcpServers = mcpServers,
                        onPersist = ::persist,
                    )
                }
            }

            3 -> {
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_stream)) },
                tail = {
                    Switch(
                        checked = resolved.streamOutput,
                        onCheckedChange = { v -> persist { it.copy(streamOutput = v) } },
                    )
                },
            )

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_memory)) },
                tail = {
                    Switch(
                        checked = resolved.enableMemory,
                        onCheckedChange = { v -> persist { it.copy(enableMemory = v) } },
                    )
                },
            )
            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_summary_min_length)) },
                description = { Text(stringResource(R.string.subagent_profile_summary_min_length_desc)) },
            ) {
                var localSummaryMinLength by remember(profileName, resolved.summaryMinLength) {
                    mutableStateOf(resolved.summaryMinLength.toFloat())
                }
                Slider(
                    value = localSummaryMinLength,
                    onValueChange = { localSummaryMinLength = it },
                    onValueChangeFinished = {
                        persist { it.copy(summaryMinLength = localSummaryMinLength.roundToInt().coerceIn(0, 1000)) }
                    },
                    valueRange = 0f..1000f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (localSummaryMinLength.roundToInt() > 0) {
                        stringResource(R.string.subagent_profile_summary_min_length_value, localSummaryMinLength.roundToInt())
                    } else {
                        stringResource(R.string.subagent_profile_summary_min_length_disabled)
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_summary_continuation_attempts)) },
                description = { Text(stringResource(R.string.subagent_profile_summary_continuation_attempts_desc)) },
            ) {
                var localSummaryContAttempts by remember(profileName, resolved.summaryContinuationAttempts) {
                    mutableStateOf(resolved.summaryContinuationAttempts.toFloat())
                }
                Slider(
                    value = localSummaryContAttempts,
                    onValueChange = { localSummaryContAttempts = it },
                    onValueChangeFinished = {
                        persist { it.copy(summaryContinuationAttempts = localSummaryContAttempts.roundToInt().coerceIn(0, 5)) }
                    },
                    valueRange = 0f..5f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = localSummaryContAttempts.roundToInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

                }
            }
        }
    }
}


private val SubagentWorkspaceToolNames = listOf(
    "workspace_read_file",
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_shell",
)

private val SubagentExcludedToolSuggestions: List<String> =
    SubagentWorkspaceToolNames + SUBAGENT_TOOL_NAMES.toList()

private val SubagentProfileLocalToolChipOptions = listOf(
    LocalToolOption.JavascriptEngine,
    LocalToolOption.TimeInfo,
    LocalToolOption.Clipboard,
    LocalToolOption.Tts,
    LocalToolOption.ScreenTime,
    LocalToolOption.Logs,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubagentExcludedToolChips(
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    var localSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != localSelected) {
            localSelected = selected
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SubagentExcludedToolSuggestions.forEach { tool ->
            FilterChip(
                selected = tool in localSelected,
                onClick = {
                    val next = if (tool in localSelected) {
                        localSelected - tool
                    } else {
                        localSelected + tool
                    }
                    localSelected = next
                    onSelectionChange(next)
                },
                label = { Text(tool) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubagentLocalToolOptionChips(
    selected: List<LocalToolOption>,
    onSelectionChange: (List<LocalToolOption>) -> Unit,
) {
    var localSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != localSelected) {
            localSelected = selected
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SubagentProfileLocalToolChipOptions.forEach { option ->
            FilterChip(
                selected = option in localSelected,
                onClick = {
                    val next = if (option in localSelected) {
                        localSelected - option
                    } else {
                        localSelected + option
                    }
                    localSelected = next
                    onSelectionChange(next)
                },
                label = { Text(localToolLabel(option)) },
            )
        }
    }
}

@Composable
private fun SubagentToolApprovalOverridesEditor(
    overrides: Map<String, Boolean>,
    onChange: (Map<String, Boolean>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubagentWorkspaceToolNames.forEach { toolName ->
            val autoApprove = !resolveWorkspaceToolApproval(toolName, overrides)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.subagent_profile_tool_auto_approve),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Switch(
                        checked = autoApprove,
                        onCheckedChange = { approved ->
                            val needsApproval = !approved
                            val next = if (needsApproval == (WorkspaceToolDefaultApprovals[toolName] ?: false)) {
                                overrides - toolName
                            } else {
                                overrides + (toolName to needsApproval)
                            }
                            onChange(next)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PathChipEditor(
    paths: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    addPlaceholder: @Composable () -> Unit = {
        Text(stringResource(R.string.subagent_profile_path_add_hint))
    },
    suggestions: List<String> = emptyList(),
    onToggleSuggestion: ((String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            paths.forEach { path ->
                InputChip(
                    selected = true,
                    onClick = {},
                    label = { Text(path) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onRemove(path) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = stringResource(R.string.common_delete),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = addPlaceholder,
            )
            androidx.compose.material3.TextButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        onAdd(draft.trim())
                    }
                },
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        }
        if (suggestions.isNotEmpty() && onToggleSuggestion != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                suggestions.forEach { tool ->
                    FilterChip(
                        selected = tool in paths,
                        onClick = { onToggleSuggestion(tool) },
                        label = { Text(tool) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalToolsSkillMcpSection(
    resolved: SubagentProfile,
    skills: List<me.rerere.rikkahub.data.files.SkillMetadata>,
    mcpServers: List<me.rerere.rikkahub.data.ai.mcp.McpServerConfig>,
    onPersist: ((SubagentProfile) -> SubagentProfile) -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.subagent_profile_local_tools)) },
        ) {
            SubagentLocalToolOptionChips(
                selected = resolved.localTools,
                onSelectionChange = { next ->
                    onPersist { it.withLocalToolOptions(next, extra = false) }
                },
            )
        }

        if (skills.isNotEmpty()) {
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_skills)) },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    skills.forEach { skill ->
                        FilterChip(
                            selected = skill.name in resolved.enabledSkills,
                            onClick = {
                                onPersist {
                                    it.toggleSkill(skill.name, skill.name !in it.enabledSkills)
                                }
                            },
                            label = { Text(skill.name) },
                        )
                    }
                }
            }
        }

        if (mcpServers.isNotEmpty()) {
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_mcp_servers)) },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    mcpServers.forEach { server ->
                        FilterChip(
                            selected = server.id in resolved.mcpServerIds,
                            onClick = {
                                onPersist {
                                    it.copy(
                                        mcpServerIds = if (server.id in it.mcpServerIds) {
                                            it.mcpServerIds - server.id
                                        } else {
                                            it.mcpServerIds + server.id
                                        }
                                    )
                                }
                            },
                            label = { Text(server.commonOptions.name.ifBlank { server.id.toString() }) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun localToolLabel(option: LocalToolOption): String = when (option) {
    LocalToolOption.JavascriptEngine -> stringResource(R.string.assistant_page_local_tools_javascript_engine_title)
    LocalToolOption.TimeInfo -> stringResource(R.string.assistant_page_local_tools_time_info_title)
    LocalToolOption.Clipboard -> stringResource(R.string.assistant_page_local_tools_clipboard_title)
    LocalToolOption.Tts -> stringResource(R.string.assistant_page_local_tools_tts_title)
    LocalToolOption.AskUser -> stringResource(R.string.assistant_page_local_tools_ask_user_title)
    LocalToolOption.ScreenTime -> stringResource(R.string.assistant_page_local_tools_screen_time_title)
    LocalToolOption.Logs -> stringResource(R.string.assistant_page_local_tools_logs_title)
    LocalToolOption.Calendar -> stringResource(R.string.assistant_page_local_tools_calendar_title)
}

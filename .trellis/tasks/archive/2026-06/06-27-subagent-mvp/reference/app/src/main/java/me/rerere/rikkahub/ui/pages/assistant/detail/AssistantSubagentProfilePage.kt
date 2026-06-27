package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.mergeSubagentProfiles
import me.rerere.rikkahub.data.ai.subagent.toggleSkill
import me.rerere.rikkahub.data.ai.subagent.upsertSubagentProfile
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.TextArea
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun AssistantSubagentProfilePage(id: String, profileName: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = { parametersOf(id) }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(profileName) },
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
            providers = providers,
            profileName = profileName,
            onUpdate = { vm.update(it) },
        )
    }
}

@Composable
private fun AssistantSubagentProfileContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    profileName: String,
    onUpdate: (Assistant) -> Unit,
) {
    val merged = mergeSubagentProfiles(assistant.subagentProfiles, assistant.disabledBuiltinSubagents)
    val current = merged.firstOrNull { it.name == profileName } ?: SubagentProfile(name = profileName)

    val latestAssistant = rememberUpdatedState(assistant)

    fun saveProfile(transform: (SubagentProfile) -> SubagentProfile) {
        val updated = transform(current)
        onUpdate(
            assistant.copy(
                subagentProfiles = upsertSubagentProfile(assistant.subagentProfiles, updated)
            )
        )
    }

    fun saveProfileLatest(transform: (SubagentProfile) -> SubagentProfile) {
        val a = latestAssistant.value
        val cur = mergeSubagentProfiles(a.subagentProfiles, a.disabledBuiltinSubagents).firstOrNull { it.name == profileName }
            ?: SubagentProfile(name = profileName)
        onUpdate(a.copy(subagentProfiles = upsertSubagentProfile(a.subagentProfiles, transform(cur))))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- 基本信息 ----
        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_name)) },
                description = { Text(stringResource(R.string.subagent_profile_name_desc)) },
            ) {
                OutlinedTextField(
                    value = current.name,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_display_name)) },
            ) {
                OutlinedTextField(
                    value = current.displayName,
                    onValueChange = { v -> saveProfile { it.copy(displayName = v) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_description)) },
                description = { Text(stringResource(R.string.subagent_profile_description_desc)) },
            ) {
                OutlinedTextField(
                    value = current.description,
                    onValueChange = { v -> saveProfile { it.copy(description = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }

        // ---- 系统提示词 ----
        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_system_prompt)) },
                description = { Text(stringResource(R.string.subagent_profile_system_prompt_desc)) },
            ) {
                val promptState = rememberTextFieldState(initialText = current.systemPrompt)
                LaunchedEffect(promptState) {
                    snapshotFlow { promptState.text }.collect { text ->
                        saveProfileLatest { it.copy(systemPrompt = text.toString()) }
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
        }

        // ---- 模型与参数 ----
        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_model)) },
                description = { Text(stringResource(R.string.subagent_profile_model_desc)) },
            ) {
                ModelSelector(
                    modelId = current.chatModelId,
                    providers = providers,
                    type = ModelType.CHAT,
                    allowClear = true,
                    onSelect = { model ->
                        saveProfile { it.copy(chatModelId = model.id) }
                    },
                )
            }

            HorizontalDivider()

            // temperature —— Switch 开关控制（null = 继承父代理）
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_temperature)) },
                description = { Text(stringResource(R.string.subagent_profile_temperature_desc)) },
                tail = {
                    Switch(
                        checked = current.temperature != null,
                        onCheckedChange = { enabled ->
                            saveProfile { it.copy(temperature = if (enabled) 1.0f else null) }
                        },
                    )
                },
            ) {
                if (current.temperature != null) {
                    var temperatureInput by remember(profileName) {
                        mutableStateOf(current.temperature.toString())
                    }
                    val temperatureValue = temperatureInput.toFloatOrNull()
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { value ->
                            temperatureInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { temperature ->
                                saveProfile { it.copy(temperature = temperature) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = temperatureValue == null || temperatureValue !in 0f..2f,
                        supportingText = { Text("0 - 2") },
                    )
                }
            }

            HorizontalDivider()

            // topP —— Switch 开关控制（null = 继承父代理）
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_top_p)) },
                description = { Text(stringResource(R.string.subagent_profile_top_p_desc)) },
                tail = {
                    Switch(
                        checked = current.topP != null,
                        onCheckedChange = { enabled ->
                            saveProfile { it.copy(topP = if (enabled) 1.0f else null) }
                        },
                    )
                },
            ) {
                current.topP?.let { topP ->
                    var topPInput by remember(profileName) {
                        mutableStateOf(topP.toString())
                    }
                    val topPValue = topPInput.toFloatOrNull()
                    OutlinedTextField(
                        value = topPInput,
                        onValueChange = { value ->
                            topPInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { nextTopP ->
                                saveProfile { it.copy(topP = nextTopP) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = topPValue == null || topPValue !in 0f..1f,
                        supportingText = { Text("0 - 1") },
                    )
                }
            }

            HorizontalDivider()

            // maxTokens —— 直接 TextField（空 = 继承父代理）
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_max_tokens)) },
                description = { Text(stringResource(R.string.subagent_profile_max_tokens_desc)) },
            ) {
                OutlinedTextField(
                    value = current.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = if (text.isBlank()) {
                            null
                        } else {
                            text.toIntOrNull()?.takeIf { it > 0 }
                        }
                        saveProfile { it.copy(maxTokens = tokens) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.subagent_profile_max_tokens_inherit))
                    },
                    supportingText = {
                        if (current.maxTokens != null) {
                            Text(stringResource(R.string.subagent_profile_max_tokens_limit, current.maxTokens))
                        } else {
                            Text(stringResource(R.string.subagent_profile_max_tokens_inherit))
                        }
                    },
                )
            }

            HorizontalDivider()

            // reasoningLevel
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_reasoning)) },
            ) {
                ReasoningButton(
                    reasoningLevel = current.reasoningLevel,
                    onUpdateReasoningLevel = { level ->
                        saveProfile { it.copy(reasoningLevel = level) }
                    },
                )
            }
        }

        // ---- 行为参数 ----
        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            // maxSteps —— Slider
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_max_steps)) },
                description = { Text(stringResource(R.string.subagent_profile_max_steps_desc)) },
            ) {
                Slider(
                    value = current.maxSteps.toFloat(),
                    onValueChange = { v ->
                        saveProfile { it.copy(maxSteps = v.roundToInt().coerceIn(1, 256)) }
                    },
                    valueRange = 1f..256f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.subagent_profile_max_steps_value, current.maxSteps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                )
            }

            HorizontalDivider()

            // inheritTools —— Switch
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_inherit_tools)) },
                description = { Text(stringResource(R.string.subagent_profile_inherit_tools_desc)) },
                tail = {
                    Switch(
                        checked = current.inheritTools,
                        onCheckedChange = { v -> saveProfile { it.copy(inheritTools = v) } },
                    )
                },
            )
        }

        // todo: 仅在 inheritTools=false 时显示工具/skills/MCP 选择
        if (!current.inheritTools) {
            // ---- 本地工具 ----
            val allLocalToolOptions = listOf(
                LocalToolOption.JavascriptEngine,
                LocalToolOption.TimeInfo,
                LocalToolOption.Clipboard,
                LocalToolOption.Tts,
                LocalToolOption.AskBtw,
                LocalToolOption.Logs,
            )
            Card(
                colors = CustomColors.cardColorsOnSurfaceContainer
            ) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text("Local Tools") },
                    // todo: use string resource subagent_profile_local_tools_desc
                    description = { Text("Select local tools available to this subagent") },
                ) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        allLocalToolOptions.forEach { option ->
                            FilterChip(
                                selected = option in current.localTools,
                                onClick = {
                                    saveProfile {
                                        it.copy(
                                            localTools = if (option in it.localTools)
                                                it.localTools - option
                                            else
                                                it.localTools + option
                                        )
                                    }
                                },
                                label = { Text(option::class.simpleName ?: "Unknown") },
                            )
                        }
                    }
                }
            }

            // ---- Skills ----
            val skillManager: SkillManager = koinInject()
            val availableSkills = remember { skillManager.listSkills() }
            if (availableSkills.isNotEmpty()) {
                Card(
                    colors = CustomColors.cardColorsOnSurfaceContainer
                ) {
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = { Text("Skills") },
                        // todo: use string resource subagent_profile_skills_desc
                        description = { Text("Select skills to enable for this subagent") },
                    ) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            availableSkills.forEach { skill ->
                                FilterChip(
                                    selected = skill.name in current.enabledSkills,
                                    onClick = {
                                        saveProfile {
                                            it.toggleSkill(skill.name, skill.name !in it.enabledSkills)
                                        }
                                    },
                                    label = { Text(skill.name) },
                                )
                            }
                        }
                    }
                }
            }

            // ---- MCP 服务器 ----
            val settingsStore: SettingsStore = koinInject()
            val availableMcp by settingsStore.settingsFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val mcpServers = availableMcp?.mcpServers ?: emptyList()
            if (mcpServers.isNotEmpty()) {
                Card(
                    colors = CustomColors.cardColorsOnSurfaceContainer
                ) {
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = { Text("MCP Servers") },
                        // todo: use string resource subagent_profile_mcp_servers_desc
                        description = { Text("Select MCP servers available to this subagent") },
                    ) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            mcpServers.forEach { server ->
                                FilterChip(
                                    selected = server.id in current.mcpServerIds,
                                    onClick = {
                                        saveProfile {
                                            it.copy(
                                                mcpServerIds = if (server.id in it.mcpServerIds)
                                                    it.mcpServerIds - server.id
                                                else
                                                    it.mcpServerIds + server.id
                                            )
                                        }
                                    },
                                    label = { Text(server.commonOptions.name.ifBlank { "Unnamed" }) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            // streamOutput —— Switch
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_stream)) },
                description = { Text(stringResource(R.string.subagent_profile_stream_desc)) },
                tail = {
                    Switch(
                        checked = current.streamOutput,
                        onCheckedChange = { v -> saveProfile { it.copy(streamOutput = v) } },
                    )
                },
            )

            HorizontalDivider()

            // enableMemory —— Switch
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_memory)) },
                description = { Text(stringResource(R.string.subagent_profile_memory_desc)) },
                tail = {
                    Switch(
                        checked = current.enableMemory,
                        onCheckedChange = { v -> saveProfile { it.copy(enableMemory = v) } },
                    )
                },
            )

            HorizontalDivider()

            // summaryMinLength —— Slider
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.subagent_profile_summary_min_length)) },
                description = { Text(stringResource(R.string.subagent_profile_summary_min_length_desc)) },
            ) {
                Slider(
                    value = current.summaryMinLength.toFloat(),
                    onValueChange = { v ->
                        saveProfile { it.copy(summaryMinLength = v.roundToInt().coerceIn(0, 1000)) }
                    },
                    valueRange = 0f..1000f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (current.summaryMinLength > 0) {
                        stringResource(R.string.subagent_profile_summary_min_length_value, current.summaryMinLength)
                    } else {
                        stringResource(R.string.subagent_profile_summary_min_length_disabled)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                )
            }
        }
    }
}

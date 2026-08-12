package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.mcp.McpManager

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    @Deprecated(
        message = "使用 transform 重载避免读快照-全量写竞态 (#267)",
        replaceWith = ReplaceWith("updateSettings { it.copy(...) }"),
    )
    fun updateSettings(settings: Settings) = updateSettings { settings }

    fun updateEnableKeepAliveNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateEnableKeepAliveNotification(enabled)
        }
    }

    fun updateCheckpointCache(enabled: Boolean? = null, stepInterval: Int? = null) {
        viewModelScope.launch {
            settingsStore.updateCheckpointCache(enabled = enabled, stepInterval = stepInterval)
        }
    }

    fun updateExperimentalFeature(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateExperimentalFeature(id, enabled)
        }
    }

    fun deleteGlobalSubagent(name: String) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    globalSubagentProfiles = settings.globalSubagentProfiles.filter { it.name != name },
                )
            }
        }
    }

    fun restoreDefaultSubagents() {
        viewModelScope.launch {
            settingsStore.update { settings ->
                val existingNames = settings.globalSubagentProfiles.map { it.name }.toSet()
                val toAdd = SubagentRegistry.BUILTIN_PROFILES.filter { it.name !in existingNames }
                settings.copy(globalSubagentProfiles = settings.globalSubagentProfiles + toAdd)
            }
        }
    }
}

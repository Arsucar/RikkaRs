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

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
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

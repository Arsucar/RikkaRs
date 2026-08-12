package me.rerere.rikkahub.ui.pages.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.withPrunedAssistantExtensionIds
import me.rerere.rikkahub.data.model.Preset
import kotlin.uuid.Uuid

class PromptVM(
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val presetUpdateMutex = Mutex()

    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update { transform(it).withPrunedAssistantExtensionIds() }
        }
    }

    @Deprecated(
        message = "使用 transform 重载避免读快照-全量写竞态 (#267)",
        replaceWith = ReplaceWith("updateSettings { it.copy(...) }"),
    )
    fun updateSettings(settings: Settings) = updateSettings { settings }

    fun updatePreset(
        presetId: Uuid,
        transform: (Preset) -> Preset,
    ) {
        viewModelScope.launch {
            presetUpdateMutex.withLock {
                settingsStore.updatePreset(presetId, transform)
            }
        }
    }
}

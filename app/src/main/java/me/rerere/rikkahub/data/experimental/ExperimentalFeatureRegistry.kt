package me.rerere.rikkahub.data.experimental

import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant

const val FEATURE_CHAT_KEEPALIVE = "chat_keepalive"
const val FEATURE_CHECKPOINT_CACHE = "checkpoint_cache"
const val FEATURE_VARIABLE_SYSTEM = "variable_system"
const val FEATURE_UI_TYPOGRAPHY = "ui_typography"

enum class ExperimentalFeatureScope {
    Global,
    Assistant,
}

enum class ExperimentalFeatureStatus {
    Experimental,
    Beta,
    Stable,
}

data class FeatureSpec(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val scope: ExperimentalFeatureScope,
    val defaultEnabled: Boolean = false,
    val status: ExperimentalFeatureStatus = ExperimentalFeatureStatus.Experimental,
)

object ExperimentalFeatureRegistry {
    val all: List<FeatureSpec> = listOf(
        FeatureSpec(
            id = FEATURE_CHAT_KEEPALIVE,
            titleRes = R.string.setting_display_page_keep_alive_notification,
            descriptionRes = R.string.setting_display_page_keep_alive_notification_desc,
            scope = ExperimentalFeatureScope.Global,
        ),
        FeatureSpec(
            id = FEATURE_CHECKPOINT_CACHE,
            titleRes = R.string.setting_display_page_checkpoint_cache,
            descriptionRes = R.string.setting_display_page_checkpoint_cache_desc,
            scope = ExperimentalFeatureScope.Global,
        ),
        FeatureSpec(
            id = FEATURE_UI_TYPOGRAPHY,
            titleRes = R.string.experiments_ui_typography_title,
            descriptionRes = R.string.experiments_ui_typography_desc,
            scope = ExperimentalFeatureScope.Global,
            defaultEnabled = false,
        ),
        FeatureSpec(
            id = FEATURE_VARIABLE_SYSTEM,
            titleRes = R.string.assistant_page_variable_system,
            descriptionRes = R.string.assistant_page_variable_system_desc,
            scope = ExperimentalFeatureScope.Assistant,
        ),
    )

    private val byId: Map<String, FeatureSpec> = all.associateBy { it.id }

    fun get(id: String): FeatureSpec? = byId[id]

    fun byScope(scope: ExperimentalFeatureScope): List<FeatureSpec> =
        all.filter { it.scope == scope }
}

/**
 * Resolve whether an experimental feature is enabled.
 *
 * Priority: map key if present → legacy bridge boolean → [FeatureSpec.defaultEnabled].
 * Unknown ids without a map entry resolve to false.
 */
fun resolveExperimentalFeature(
    id: String,
    settings: Settings,
    assistant: Assistant? = null,
): Boolean {
    val spec = ExperimentalFeatureRegistry.get(id)

    when (spec?.scope ?: ExperimentalFeatureScope.Global) {
        ExperimentalFeatureScope.Global -> {
            settings.experimentalFeatures[id]?.let { return it }
            return when (id) {
                FEATURE_CHAT_KEEPALIVE -> settings.enableKeepAliveNotification
                FEATURE_CHECKPOINT_CACHE -> settings.enableCheckpointCache
                else -> spec?.defaultEnabled ?: false
            }
        }
        ExperimentalFeatureScope.Assistant -> {
            assistant?.experimentalFeatureOverrides?.get(id)?.let { return it }
            return when (id) {
                FEATURE_VARIABLE_SYSTEM -> assistant?.enableVariableSystem ?: (spec?.defaultEnabled ?: false)
                else -> spec?.defaultEnabled ?: false
            }
        }
    }
}

package me.rerere.rikkahub.data.datastore.migration

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.datastore.absorbOrphanModeInjections
import me.rerere.rikkahub.data.datastore.migratePresetsJsonWithReferences
import me.rerere.rikkahub.data.model.LegacyModeInjection
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "SettingsJsonMigrator"

/**
 * 对备份文件中的 settings.json 应用与 DataStore migration 相同的迁移逻辑。
 *
 * DataStore migration 作用于分散的 key-value 存储，而备份文件中的 settings.json
 * 是整个 [me.rerere.rikkahub.data.datastore.Settings] 对象的序列化结果。
 * 此工具类负责在反序列化前对旧格式的 JSON 执行等价的迁移操作。
 */
object SettingsJsonMigrator {

    /**
     * 对 settings JSON 字符串依次应用所有版本的迁移。
     * 若发生异常则返回原始 JSON，不中断恢复流程。
     */
    fun migrate(settingsJson: String): String {
        return runCatching {
            val root = JsonInstant.parseToJsonElement(settingsJson).jsonObject.toMutableMap()

            // V1: 修复 mcpServers 中全限定类名的 type 字段
            root["mcpServers"]?.let { element ->
                val migrated = migrateMcpServersJson(JsonInstant.encodeToString(element))
                root["mcpServers"] = JsonInstant.parseToJsonElement(migrated)
            }

            // V2: 修复 assistants 中 UIMessagePart 的 type 字段
            root["assistants"]?.let { element ->
                val migrated = migrateAssistantsJson(JsonInstant.encodeToString(element))
                root["assistants"] = JsonInstant.parseToJsonElement(migrated)
            }

            // V4: legacy root search flag becomes an Assistant-level setting.
            root["assistants"]?.let { element ->
                val legacyEnabled = (root["enableWebSearch"] as? JsonPrimitive)
                    ?.booleanOrNull ?: false
                root["assistants"] = JsonInstant.parseToJsonElement(
                    migrateAssistantWebSearch(JsonInstant.encodeToString(element), legacyEnabled)
                )
            }
            root.remove("enableWebSearch")

            // V3: 将 assistants 中内嵌的 quickMessages 提取为全局 quickMessages
            root["assistants"]?.let { element ->
                val (migratedAssistants, extractedQuickMessages) =
                    migrateAssistantsQuickMessages(JsonInstant.encodeToString(element))
                root["assistants"] = JsonInstant.parseToJsonElement(migratedAssistants)

                if (extractedQuickMessages.isNotEmpty()) {
                    val existing = root["quickMessages"]
                    val existingArray = existing?.let {
                        runCatching { JsonInstant.parseToJsonElement(JsonInstant.encodeToString(it)) as? JsonArray }.getOrNull()
                    } ?: JsonArray(emptyList())
                    val existingIds = existingArray.mapNotNull {
                        (it as? JsonObject)?.get("id")?.toString()?.trim('"')
                    }.toSet()
                    val merged = JsonArray(
                        existingArray + extractedQuickMessages.filter { e ->
                            val id = (e as? JsonObject)?.get("id")?.toString()?.trim('"')
                            id != null && id !in existingIds
                        }
                    )
                    root["quickMessages"] = merged
                }
            }

            // V5 (#259): BEFORE stripping modeInjections — Reference→Custom + orphan absorb.
            // Assistant-only and unreferenced modeInjections are snapshotted into Default Preset
            // so content is not lost when root modeInjections is removed.
            val modeInjections = root["modeInjections"]?.let { element ->
                runCatching {
                    JsonInstant.decodeFromString<List<LegacyModeInjection>>(
                        JsonInstant.encodeToString(element),
                    )
                }.getOrDefault(emptyList())
            }.orEmpty()

            root["presets"]?.let { presetsElement ->
                val presetsJson = JsonInstant.encodeToString(presetsElement)
                val referencedIds = mutableSetOf<kotlin.uuid.Uuid>()
                val migrated = runCatching {
                    absorbOrphanModeInjections(
                        migratePresetsJsonWithReferences(presetsJson, modeInjections, referencedIds),
                        modeInjections,
                        extraCoveredIds = referencedIds,
                    )
                }.getOrNull()
                if (migrated != null) {
                    root["presets"] = JsonInstant.parseToJsonElement(
                        JsonInstant.encodeToString(migrated),
                    )
                }
            } ?: run {
                if (modeInjections.isNotEmpty()) {
                    val migrated = absorbOrphanModeInjections(emptyList(), modeInjections)
                    root["presets"] = JsonInstant.parseToJsonElement(
                        JsonInstant.encodeToString(migrated),
                    )
                }
            }

            // Strip assistant direct modeInjectionIds / allowConversationPromptInjection.
            root["assistants"]?.let { element ->
                val arr = element as? JsonArray ?: return@let
                val cleaned = JsonArray(
                    arr.map { item ->
                        val obj = item as? JsonObject ?: return@map item
                        JsonObject(
                            obj.toMutableMap().apply {
                                remove("modeInjectionIds")
                                remove("allowConversationPromptInjection")
                            },
                        )
                    },
                )
                root["assistants"] = cleaned
            }
            root.remove("modeInjections")

            JsonInstant.encodeToString(JsonObject(root))
        }.onFailure {
            Log.e(TAG, "migrate: Failed to migrate settings JSON, using original", it)
        }.getOrDefault(settingsJson)
    }
}

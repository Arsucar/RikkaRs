package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.export.tryImportCharacterBook
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import org.koin.compose.koinInject

/**
 * 角色卡导入结果：助手 + 检测到的附加世界书
 */
data class TavernCardImportResult(
    val assistant: Assistant,
    val lorebooks: List<Lorebook>,
)

@Composable
fun AssistantImporter(
    modifier: Modifier = Modifier,
    onUpdate: (Assistant, List<Lorebook>) -> Unit,
) {
    SillyTavernImporter(
        modifier = modifier,
        onImport = onUpdate,
    )
}

@Composable
fun SillyTavernImporter(
    modifier: Modifier = Modifier,
    onImport: (Assistant, List<Lorebook>) -> Unit,
) {
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var isLoading by remember { mutableStateOf(false) }

    // 待确认的导入结果（含附加绑定），非 null 时触发弹窗
    var pendingResult by remember { mutableStateOf<TavernCardImportResult?>(null) }

    val launchImport: (Uri) -> Unit = { uri ->
        isLoading = true
        scope.launch {
            try {
                val result = importAssistantFromUri(
                    context = context,
                    uri = uri,
                    toaster = toaster,
                    filesManager = filesManager,
                )
                result?.let { importResult ->
                    if (importResult.lorebooks.isEmpty()) {
                        // 无附加绑定：直接导入
                        onImport(importResult.assistant, emptyList())
                    } else {
                        // 有附加绑定：弹窗让用户确认
                        pendingResult = importResult
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(launchImport) }

    val pngPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(launchImport) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        OutlinedButton(
            onClick = {
                pngPickerLauncher.launch(arrayOf("image/png"))
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            }
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_png))
        }

        OutlinedButton(
            onClick = {
                jsonPickerLauncher.launch(arrayOf("application/json"))
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            }
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_json))
        }
    }

    // 附加绑定确认弹窗
    pendingResult?.let { result ->
        BindingConfirmDialog(
            lorebooks = result.lorebooks,
            onConfirm = {
                onImport(result.assistant, result.lorebooks)
                pendingResult = null
            },
            onSkip = {
                onImport(result.assistant, emptyList())
                pendingResult = null
            },
            onDismiss = {
                pendingResult = null
            },
        )
    }
}

/**
 * 附加绑定确认弹窗：列出检测到的世界书，让用户选择是否导入。
 *
 * 长内容可滚动（遵循 ui-131/ui-204 的 heightIn+verticalScroll 规范）。
 */
@Composable
private fun BindingConfirmDialog(
    lorebooks: List<Lorebook>,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.assistant_importer_binding_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.assistant_importer_binding_dialog_message))
                HorizontalDivider()
                lorebooks.forEach { lorebook ->
                    ListItem(
                        headlineContent = {
                            Text(
                                lorebook.name.ifEmpty { stringResource(R.string.assistant_importer_unnamed_lorebook) },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.assistant_importer_lorebook_entries_count,
                                    lorebook.entries.size
                                )
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.assistant_importer_binding_dialog_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.assistant_importer_binding_dialog_skip))
            }
        },
    )
}

// region Parsing Strategy

private interface TavernCardParser {
    val specName: String
    fun parse(context: Context, json: JsonObject, background: String?): Assistant
}

private class CharaCardV2Parser : TavernCardParser {
    override val specName: String = "chara_card_v2"

    override fun parse(context: Context, json: JsonObject, background: String?): Assistant {
        val data = json["data"]?.jsonObject ?: error(context.getString(R.string.assistant_importer_missing_data_field))
        val name = data["name"]?.jsonPrimitiveOrNull?.contentOrNull
            ?: error(context.getString(R.string.assistant_importer_missing_name_field))
        val firstMessage = data["first_mes"]?.jsonPrimitiveOrNull?.contentOrNull
        val system = data["system_prompt"]?.jsonPrimitiveOrNull?.contentOrNull
        val description = data["description"]?.jsonPrimitiveOrNull?.contentOrNull
        val personality = data["personality"]?.jsonPrimitiveOrNull?.contentOrNull
        val scenario = data["scenario"]?.jsonPrimitiveOrNull?.contentOrNull

        val prompt = buildString {
            appendLine("You are roleplaying as $name.")
            appendLine()
            if (!system.isNullOrBlank()) {
                appendLine(system)
                appendLine()
            }
            appendLine("## Description of the character")
            appendLine(description ?: "Empty")
            appendLine()
            appendLine("## Personality of the character")
            appendLine(personality ?: "Empty")
            appendLine()
            appendLine("## Scenario")
            append(scenario ?: "Empty")
        }

        return Assistant(
            name = name,
            presetMessages = if (firstMessage != null) listOf(UIMessage.assistant(firstMessage)) else emptyList(),
            systemPrompt = prompt,
            background = background
        )
    }
}

private class CharaCardV3Parser : TavernCardParser {
    override val specName: String = "chara_card_v3"

    override fun parse(context: Context, json: JsonObject, background: String?): Assistant {
        val data = json["data"]?.jsonObject ?: error(context.getString(R.string.assistant_importer_missing_data_field))
        val name = data["name"]?.jsonPrimitiveOrNull?.contentOrNull ?: error(context.getString(R.string.assistant_importer_missing_name_field))
        val description = data["description"]?.jsonPrimitiveOrNull?.contentOrNull
        val firstMessage = data["first_mes"]?.jsonPrimitiveOrNull?.contentOrNull
        val system = data["system_prompt"]?.jsonPrimitiveOrNull?.contentOrNull
        val personality = data["personality"]?.jsonPrimitiveOrNull?.contentOrNull
        val scenario = data["scenario"]?.jsonPrimitiveOrNull?.contentOrNull

        val prompt = buildString {
            appendLine("You are roleplaying as $name.")
            appendLine()
            if (!system.isNullOrBlank()) {
                appendLine(system)
                appendLine()
            }
            appendLine("## Description of the character")
            appendLine(description ?: "Empty")
            appendLine()
            appendLine("## Personality of the character")
            appendLine(personality ?: "Empty")
            appendLine()
            appendLine("## Scenario")
            append(scenario ?: "Empty")
        }

        return Assistant(
            name = name,
            presetMessages = if (firstMessage != null) listOf(UIMessage.assistant(firstMessage)) else emptyList(),
            systemPrompt = prompt,
            background = background
        )
    }
}

private val TAVERN_PARSERS: Map<String, TavernCardParser> = listOf(
    CharaCardV2Parser(),
    CharaCardV3Parser()
).associateBy { it.specName }

private fun parseAssistantFromJson(
    context: Context,
    json: JsonObject,
    background: String?,
): Assistant {
    val spec = json["spec"]?.jsonPrimitive?.contentOrNull
        ?: error(context.getString(R.string.assistant_importer_missing_spec_field))
    val parser = TAVERN_PARSERS[spec] ?: error(context.getString(R.string.assistant_importer_unsupported_spec, spec))
    return parser.parse(context = context, json = json, background = background)
}

/**
 * 从角色卡 JSON 中提取内嵌世界书（character_book）。
 *
 * V2/V3 角色卡的 `data.character_book` 是一个完整的世界书对象，遵循
 * malfoyslastname/character-card-spec-v2 的 CharacterBook 类型定义。
 * 注意字段名与独立世界书 JSON 不同（keys vs key、enabled vs disable 等），
 * 故使用 [tryImportCharacterBook] 而非 [LorebookSerializer.tryImportSillyTavern]。
 */
private fun extractLorebooksFromCard(
    json: JsonObject,
    cardName: String,
): List<Lorebook> {
    val data = json["data"]?.jsonObject ?: return emptyList()
    val characterBookJson = data["character_book"] ?: return emptyList()
    val lorebook = tryImportCharacterBook(
        characterBookJson = characterBookJson,
        fallbackName = cardName,
    )
    return if (lorebook != null) listOf(lorebook) else emptyList()
}

// endregion

private suspend fun importAssistantFromUri(
    context: Context,
    uri: Uri,
    toaster: ToasterState,
    filesManager: FilesManager,
): TavernCardImportResult? {
    return try {
        val mime = withContext(Dispatchers.IO) { filesManager.getFileMimeType(uri) }
        val (jsonString, backgroundStr) = withContext(Dispatchers.IO) {
            when (mime) {
                "image/png" -> {
                    val result = ImageUtils.getTavernCharacterMeta(context, uri)
                    result.map { base64Data ->
                        val json = String(Base64.decode(base64Data, Base64.DEFAULT))
                        val bg = filesManager.createChatFilesByContents(listOf(uri)).first().toString()
                        json to bg
                    }.getOrElse { throw it }
                }

                "application/json" -> {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        .use { it?.readText() }
                        ?: error(context.getString(R.string.assistant_importer_read_json_failed))
                    json to null
                }

                else -> error(context.getString(R.string.assistant_importer_unsupported_file_type, mime ?: "unknown"))
            }
        }
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val assistant = parseAssistantFromJson(context = context, json = json, background = backgroundStr)

        // 提取内嵌世界书（character_book）
        val lorebooks = extractLorebooksFromCard(
            json = json,
            cardName = assistant.name,
        )

        TavernCardImportResult(assistant = assistant, lorebooks = lorebooks)
    } catch (exception: Exception) {
        exception.printStackTrace()
        toaster.show(
            message = exception.message ?: context.getString(R.string.assistant_importer_import_failed),
            type = ToastType.Error
        )
        null
    }
}

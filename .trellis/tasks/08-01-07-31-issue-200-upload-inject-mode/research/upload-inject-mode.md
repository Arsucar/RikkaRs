# Research: upload attachment inject mode

- **Query**: 全局偏好「上传附件注入方式」(PATH_ONLY / FULL_BODY) 实现细节调研
- **Scope**: internal
- **Date**: 2026-08-01
- **Task**: `08-01-07-31-issue-200-upload-inject-mode`

## Findings

### 1. DocumentAsPromptTransformer — 全文注入组装逻辑

**文件**: `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/DocumentAsPromptTransformer.kt`

| 符号 | 行号 | 作用 |
|---|---|---|
| `object DocumentAsPromptTransformer` | L15 | `InputMessageTransformer` 单例 |
| `previewPolicy` | L16 | `PreviewTransformPolicy.SideEffectFree`（预览可跑） |
| `transform` | L17–34 | 对每条 message 的 parts 调 `appendDocumentPromptsInOrder` |
| `resolveWorkspacePath` | L54–58 | 仅当父目录名为 `upload` 时返回 `/upload/${file.name}` |
| `readDocumentContent` | L60–77 | 按 mime 解析 PDF/DOCX/PPTX/EPUB，否则 `file.readText()` |
| `appendDocumentPromptsInOrder` | L80–99 | **核心组装**（`internal`，单测直接测它） |

**当前注入格式（始终全文 + 可选 path）** — L85–96:

```kotlin
val prompts = parts.filterIsInstance<UIMessagePart.Document>().map { document ->
    val content = readContent(document)
    val pathAttr = resolvePath(document)?.let { " path=\"$it\"" } ?: ""
    UIMessagePart.Text(
        """
                              <UploadFile name="${document.fileName}"$pathAttr>
                              ```
                              $content
                              ```
                              </UploadFile>
                              """.trimMargin()
    )
}
parts.addAll(prompts)  // 追加到 parts 末尾；原 Document part 保留
```

**要点**:
- 始终调用 `readContent`，无 PATH_ONLY 分支。
- 原 `UIMessagePart.Document` 不删除，只追加 Text prompt。
- `transform` 当前签名有 `ctx: TransformerContext` 但**未使用** `ctx`（L18–19）。

**构造/注入到 ChatService**:
- 文件级 `object`，无需 DI 构造。
- `ChatService.kt` L363–372 静态 lazy 列表直接引用:

```kotlin
private val inputTransformers by lazy {
    listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,  // L368
        OcrTransformer,
        SlashSkillInputTransformer,
    )
}
```

- 实际发送/预览时 `prepareGenerationRequest` L1672–1691 再 `buildList { addAll(inputTransformers); … }`，并追加 MemoryTable / SemanticMemory / Template / WorkspaceReminder。

---

### 2. ChatService.inputTransformers 与 Settings 可达性

**TransformerContext 已含全局 Settings** — `Transformer.kt` L11–21:

```kotlin
class TransformerContext(
    val context: Context,
    val model: Model,
    val assistant: Assistant,
    val settings: Settings,          // ← 全局设置
    val conversationModeInjectionIds: Set<Uuid> = emptySet(),
    val conversationLorebookIds: Set<Uuid> = emptySet(),
    val processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    val workspaceCwd: String? = null,
    val executionMode: TransformerExecutionMode = TransformerExecutionMode.Send,
)
```

**组装路径**:
1. `List<UIMessage>.transforms(...)` L81–116：创建 `TransformerContext(..., settings = settings, ...)`。
2. `GenerationHandler.prepareFirstProviderInput` L107–135：接收 `settings` + `inputTransformers`，传入 `prepareProviderInput`。
3. `ChatService.prepareGenerationRequest` L1620–1737：
   - `settings` 来自调用方（发送: `settingsStore.settingsFlow.first()`；预览同理 L1605）。
   - `transformers = buildList { addAll(inputTransformers); … }`（L1672）。
   - 传给 `generationHandler.prepareFirstProviderInput(..., settings, inputTransformers = transformers, ...)`（L1710–1724）。

**结论**: `DocumentAsPromptTransformer.transform` 内可直接读  
`ctx.settings.displaySetting.<newField>`，**不必**改 ChatService 列表构造或 DI。

---

### 3. 全局偏好存储 — DisplaySetting / pasteLongTextAsFile 模板链路

**类声明** — `PreferencesStore.kt`（实际文件名；类为 `SettingsStore` + `DisplaySetting`）:

| 位置 | 内容 |
|---|---|
| L111 | `class SettingsStore` |
| L125 | `val DISPLAY_SETTING = stringPreferencesKey("display_setting")` |
| L299 | 读：`displaySetting = JsonInstant.decodeFromString(preferences[DISPLAY_SETTING] ?: "{}")` |
| L598 | 写：`preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)` |
| L567–584 | `settingsFlow`（MutableStateFlow） |
| L586–686 | `suspend fun update(settings: Settings)` |
| L688–690 | `suspend fun update(fn: (Settings) -> Settings)` |
| L1042–1052 | `data class Settings` 含 `displaySetting: DisplaySetting = DisplaySetting()` |
| L1268–1306 | `data class DisplaySetting`（`@Serializable`） |

**pasteLongTextAsFile 字段** — L1295–1296:

```kotlin
val pasteLongTextAsFile: Boolean = false,
val pasteLongTextThreshold: Int = 1000,
```

**枚举字段模板（同文件）** — `ChatFontFamily` L1254–1265:

```kotlin
@Serializable
enum class ChatFontFamily {
    @SerialName("default") DEFAULT,
    @SerialName("serif") SERIF,
    @SerialName("monospace") MONOSPACE,
    @SerialName("custom") CUSTOM,
}
```

`DisplaySetting.chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT` (L1301)。

**读入口（运行时）**:
- UI: `vm.settings.collectAsStateWithLifecycle()` → `settings.displaySetting.pasteLongTextAsFile`
- Transformer: `ctx.settings.displaySetting.*`
- ChatInput 消费: `ChatInput.kt` L507–536 `settings.displaySetting.pasteLongTextAsFile`

**写入口**:
- `SettingPreferencesGeneralPage.kt` L41–44:

```kotlin
fun updateDisplaySetting(setting: DisplaySetting) {
    displaySetting = setting
    vm.updateSettings(settings.copy(displaySetting = setting))
}
```

- `SettingVM.kt` L22–25 → `settingsStore.update(settings)`

**序列化 / settings.json**:
- DataStore：整块 `DisplaySetting` JSON 存在 key `display_setting`（L299/L598），新增字段靠 kotlinx.serialization 默认值，**无需**单独 migration（旧 `{}` 会用默认）。
- 备份导出：`BackupArchive.kt` L63–64 `json.encodeToString(settings)` → zip 内 `settings.json`（含嵌套 `displaySetting`）。
- 备份导入：`BackupRestorer.kt` L84–88 `json.decodeFromString<Settings>(SettingsJsonMigrator.migrate(raw))` → `settingsStore.update`。
- 单测模板：`CompressionPreferencesSettingsTest.kt` L49–78（`{}` 默认值 / encode 含字段 / round-trip）。

**Web-UI 镜像**（可选同步）: `web-ui/app/types/settings.ts` L5–22 `DisplaySetting` 接口含 `pasteLongTextAsFile`；消费于 `web-ui/app/components/input/chat-input.tsx` L203–204。

---

### 4. 设置页 UI 模式

**页面**: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesGeneralPage.kt`

- L37–44：`SettingVM` + `settings.displaySetting` + `updateDisplaySetting`
- L38–39：`val settings by vm.settings.collectAsStateWithLifecycle()`

**布尔 Switch 模板（pasteLongTextAsFile）** — L187–198:

```kotlin
item(
    headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_title)) },
    supportingContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_desc)) },
    trailingContent = {
        Switch(
            checked = displaySetting.pasteLongTextAsFile,
            onCheckedChange = {
                updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it))
            }
        )
    },
)
```

**枚举 Select 模板**（若 PATH_ONLY/FULL_BODY 用单选）— `SettingPreferencesUIPage.kt` L259–283:

```kotlin
Select(
    options = ChatFontFamily.entries,
    selectedOption = displaySetting.chatFontFamily,
    onOptionSelected = { family ->
        updateDisplaySetting(displaySetting.copy(chatFontFamily = family))
    },
    optionToString = { it.labelUI() },
)
```

读写链路：`SettingVM.settings` ← `SettingsStore.settingsFlow`；写 → `SettingVM.updateSettings` → `SettingsStore.update`。

---

### 5. 无工作区 / Shell 未 READY 检测

**统一能力 API** — `app/src/main/java/me/rerere/rikkahub/data/model/WorkspaceToolCapability.kt`:

| API | 行号 | 行为 |
|---|---|---|
| `resolveWorkspaceToolCapability(workspaceId, workspaces, readOnly)` | L54–92 | 返回 `WorkspaceToolCapability` |
| `capability.available` | L49, L84 | `true` 仅当 shell 归一化为 READY |
| `unavailableReason` | L20–27, L74–80 | UNCONFIGURED / MISSING / DISABLED / INSTALLING / BROKEN / UNKNOWN |
| `normalizeWorkspaceShellStatus` | L37–44 | 字符串 → enum |

**ChatService 工具挂载门闩** — L1911–1931:

```kotlin
private suspend fun createWorkspaceToolsIfReady(...): List<Tool> {
    val capability = resolveWorkspaceToolCapability(
        workspaceId = workspace?.id,
        workspaces = listOfNotNull(workspace),
        readOnly = readOnly,
    )
    val resolvedWorkspace = capability.workspace ?: return emptyList()
    if (!capability.available) {
        // skip workspace tools
        return emptyList()
    }
    ...
}
```

**WorkspaceReminderTransformer** 同判据 — L26–28:

```kotlin
if (!resolveWorkspaceToolCapability(workspace.id, listOf(workspace)).available) return messages
```

**UI 侧直接比 shellStatus** — `FilesPicker.kt` L248–251:

```kotlin
val boundWorkspace = workspaces.find { it.id == assistant.workspaceId?.toString() }
if (boundWorkspace != null && boundWorkspace.shellStatus == WorkspaceShellStatus.READY.name) { ... }
```

**助手绑定位置**: `assistant.workspaceId`；workspace 实体 `WorkspaceEntity.shellStatus`。

**可复用判断（对话是否可用 workspace 工具）**:
```kotlin
resolveWorkspaceToolCapability(assistant.workspaceId, listOfNotNull(workspace)).available
```
或 `createWorkspaceToolsIfReady` 返回非空。`DocumentAsPromptTransformer` 当前**不**做此判断；`resolveWorkspacePath` 只看本地文件父目录是否叫 `upload`。

---

### 6. 上下文预览 #109 — 存在

| 层 | 文件 | 行号 |
|---|---|---|
| 入口 | `ChatService.buildContextPreview` | L1604–1618 |
| 准备 | `prepareGenerationRequest(..., mode = Preview)` | L1609–1615 |
| 转换 | 同一套 `inputTransformers`（含 `DocumentAsPromptTransformer`） | L1672–1724 |
| DTO | `ContextPreview.kt` `toContextPreview` | L62–86 |
| VM | `ChatVM.loadContextPreview` / `clearContextPreview` | L202–220 |
| UI | `ConversationContextInspector.kt` | 整文件 |
| 抽屉 | `ConversationMemoryTableDrawer.kt` L233+ | 状态绑定 |
| 文案 | `context_inspector_*` strings | values / values-zh |
| 测试 | `ContextPreviewTest.kt`, `PreparedProviderRequestTest.kt` | app/src/test/... |

`DocumentAsPromptTransformer.previewPolicy = SideEffectFree`，预览路径会执行全文注入逻辑，故改注入模式后预览与真实请求一致（同一 transformer 列表 + 同一 `settings`）。

---

### 7. 消息气泡 / 附件 chip 渲染

| 场景 | 文件 | 行号 | 说明 |
|---|---|---|---|
| 输入栏 chip | `ui/components/ai/AttachmentChips.kt` | L128–138 `Document` → `AttachmentChip` | 基于 `UIMessagePart.Document` 的 fileName/url |
| 历史气泡 chip | `ui/components/message/ChatMessage.kt` | L627–676 | Surface + fileName，点击 `openDocument` |
| 输入 state | `ChatInputState.addFiles` | hooks | 存 Document part |

**事实**: chip 渲染读原始 `UIMessagePart.Document`，与 transformer 追加的 Text prompt **解耦**。PATH_ONLY 只改注入 Text 内容，不改 Document part → **不必动 chip UI**。

---

### 8. 本地化

**目录**:
- EN: `app/src/main/res/values/strings.xml`
- ZH: `app/src/main/res/values-zh/strings.xml`
- JA: `app/src/main/res/values-ja/strings.xml`
- 另有: `values-zh-rTW`, `values-ko-rKR`, `values-ru`

**键名约定（同类设置项）**: `setting_display_page_<snake>_title` / `_desc`

**pasteLongTextAsFile 示例**:
- EN L990–992: `setting_display_page_paste_long_text_as_file_title` / `_desc` / `_threshold_title`
- ZH L955–957 同名 key
- JA L662–664 同名 key

**页面标题**: `setting_page_preferences_general`（GeneralPage L52）

---

### 9. 相关单测

**DocumentAsPromptTransformerTest**  
`app/src/test/java/me/rerere/rikkahub/data/ai/transformers/DocumentAsPromptTransformerTest.kt`

- 不测 `transform()`，直接测 `appendDocumentPromptsInOrder`（可注入 fake `readContent`/`resolvePath`）。
- Case1 L11–44：多附件顺序、path 属性、body 内容。
- Case2 L47–60：空列表 / 单文档稳定性。
- Helper L62–68：`document(fileName)` 构造 `UIMessagePart.Document`。

**Preferences 序列化/默认值测试结构**（无 DisplaySetting 专测；可仿）  
`app/src/test/java/me/rerere/rikkahub/data/datastore/CompressionPreferencesSettingsTest.kt`:
- 缺 key → 默认
- `JsonInstant.decodeFromString<Settings>("{}")` → 默认
- `encodeToString(Settings())` 含字段
- round-trip encode/decode

其他: `SettingsModelAndProviderTagsTest.kt`, `MemoryTableBudgetSettingsTest.kt`, `SettingsJsonMigratorWebSearchTest.kt`。

---

## 新增偏好字段的最小改动路径（事实清单 + 模板，非方案）

### pasteLongTextAsFile 完整链路（可复用模板）

1. **字段** `DisplaySetting.pasteLongTextAsFile: Boolean = false` — `PreferencesStore.kt` L1295  
2. **持久化** 随 `DISPLAY_SETTING` JSON encode/decode — L299 / L598（无单独 key）  
3. **备份** 随 `Settings` → `settings.json` — `BackupArchive` / `BackupRestorer`  
4. **UI 写** GeneralPage Switch → `updateDisplaySetting(copy(...))` → `SettingVM.updateSettings` → `SettingsStore.update`  
5. **运行时读** `settings.displaySetting.pasteLongTextAsFile`（ChatInput L532；不经 transformer）  
6. **strings** `setting_display_page_paste_long_text_as_file_{title,desc}` 中英日等  
7. **web-ui** `types/settings.ts` + chat-input 消费（Android 主路径可不阻塞）

### 上传注入模式预计需动的文件（基于上述落点）

| 文件 | 原因 |
|---|---|
| `PreferencesStore.kt` (`DisplaySetting`) | 新增布尔或枚举字段 + 默认值 |
| `DocumentAsPromptTransformer.kt`（及/或 `appendDocumentPromptsInOrder`） | 按 `ctx.settings.displaySetting.*` 分支 PATH_ONLY / FULL_BODY |
| `DocumentAsPromptTransformerTest.kt` | 覆盖仅路径 / 全文两种组装 |
| `SettingPreferencesGeneralPage.kt`（或 UIPage 若用 Select） | 设置项 Switch 或 `Select` |
| `values/strings.xml` + `values-zh` + `values-ja`（及现有其它 locale） | 标题/描述键 |
| （可选）`web-ui/app/types/settings.ts` | DisplaySetting 类型镜像 |
| （可选）`CompressionPreferencesSettingsTest` 风格单测 | 序列化默认值/round-trip |
| **不必动** | `AttachmentChips.kt` / `ChatMessage.kt` Document chip；`ChatService.inputTransformers` 列表（object 已在表内）；`TransformerContext`（已有 `settings`） |

### 消费点关键事实

- Transformer 已能读 `ctx.settings`；改 `appendDocumentPromptsInOrder` 签名或在 `transform` 内分支即可。
- 预览 #109 走同一 transformer → 模式变更自动反映到 context inspector。
- Workspace 可用性：`resolveWorkspaceToolCapability(...).available` 或 `shellStatus == READY`；当前 path 解析**不**依赖 READY。

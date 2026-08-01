# 技术设计：上传附件注入方式偏好

## 现状

- `DocumentAsPromptTransformer`（object，`DocumentAsPromptTransformer.kt`）`transform` 有 `ctx: TransformerContext` 但未用；`appendDocumentPromptsInOrder`（:80-99）总是 `readContent` + 可选 `path`。
- `TransformerContext.settings`（`Transformer.kt:15`）已含全局 `Settings`，`settings.displaySetting` 可读。
- `DisplaySetting`（`PreferencesStore.kt:1268-1306`）：kotlinx `@Serializable`，随 `display_setting` key JSON 整体存读（L299/L598），升级旧 `{}` 用字段默认值。枚举模板 `ChatFontFamily`（L1254-1265）。
- 回退判断现成 API：`resolveWorkspaceToolCapability(workspaceId, workspaces, readOnly).available`（`WorkspaceToolCapability.kt:54-92`）；ChatService 在 `createWorkspaceToolsIfReady` 已用该门闩。

## 方案

1. **字段**：`PreferencesStore.kt` 的 `DisplaySetting` 新增
   ```kotlin
   @SerialName("upload_inject_mode") val documentUploadInjectMode: UploadInjectMode = UploadInjectMode.PATH_ONLY
   ```
   同文件新增
   ```kotlin
   @Serializable enum class UploadInjectMode {
       @SerialName("path_only") PATH_ONLY,
       @SerialName("full_body") FULL_BODY,
   }
   ```
2. **Transformer**：`transform` 读取 `ctx.settings.displaySetting.documentUploadInjectMode`，并新增 workspace 可用性参数。由于 object 单例无 DI，workspace 可用性需经 `TransformerContext` 或 `ChatService` 计算后传入。选最小侵入：
   - `appendDocumentPromptsInOrder` 增加参数 `mode: UploadInjectMode` 与 `workspaceReady: Boolean`；
   - 在 `transform` 中：`workspaceReady` 来源需新增。评估后选：给 `TransformerContext` 加 `workspaceToolAvailable: Boolean = false`（默认 false 保持其它调用点不回归），ChatService `prepareGenerationRequest` 计算 `resolveWorkspaceToolCapability(...).available` 传入 `prepareFirstProviderInput` → `transforms`。
   - PATH_ONLY 分支：`resolvePath(document)?.let { stub } ?: name-only stub`；不调 `readContent`。
   - 回退：PATH_ONLY && !workspaceToolAvailable → 按 FULL_BODY 逻辑执行。
3. **UI**：`SettingPreferencesGeneralPage` 仿 `pasteLongTextAsFile` Switch 行（:187-198）或 `chatFontFamily` Select（`SettingPreferencesUIPage.kt:259-283`）新增条目；读写走 `updateDisplaySetting(displaySetting.copy(documentUploadInjectMode = it))`。
4. **文案**：`setting_display_page_upload_inject_mode_{title,desc}`，EN/ZH/JA（及现有其它 locale）。
5. **测试**：
   - `DocumentAsPromptTransformerTest` 增 PATH_ONLY（含 path、无 path name-stub、workspaceReady=false 回退全文）与 FULL_BODY 回归；
   - 偏好序列化测试仿 `CompressionPreferencesSettingsTest`：`decodeFromString("{}")` → PATH_ONLY；round-trip。

## 边界与取舍

- **不回溯历史消息**：与现网 transformer 对历史 Document 行为一致，仅后续发送生效。
- 上下文预览 #109 走同一 transformer + settings → 自动一致。
- `TransformerContext.workspaceToolAvailable` 默认 false：非 ChatService 调用点（如视觉/完成回调 `visualTransforms`/`onGenerationFinish` 构造 ctx）不传即为 false，但 DocumentAsPromptTransformer 是 input transformer，只在 Send/Preview 走 transforms 链；默认值安全。
- 若给 `TransformerContext` 加字段涉及 `transforms()` 扩展签名，需同步 `GenerationHandler.prepareFirstProviderInput`/`prepareProviderInput`/`generateText` 链；改动面以最小为准，优先在 `prepareGenerationRequest` 后段把结果放进 ctx 而非改所有签名。

# 执行计划：上传附件注入方式偏好

## 步骤

1. **数据**：`PreferencesStore.kt` `DisplaySetting` 增 `documentUploadInjectMode: UploadInjectMode = PATH_ONLY` + 新枚举。
2. **上下文传递**：`Transformer.kt` `TransformerContext` 增 `workspaceToolAvailable: Boolean = false`（含 `transforms()` 扩展参数，默认 false）；`GenerationHandler.prepareFirstProviderInput`/`prepareProviderInput` 增对应参数透传。
3. **ChatService**：`prepareGenerationRequest` 计算 `workspaceToolAvailable = resolveWorkspaceToolCapability(...).available`（复用 createWorkspaceToolsIfReady 门闩逻辑），传入 prepareFirstProviderInput。
4. **Transformer 分支**：`DocumentAsPromptTransformer.transform` 读模式；`appendDocumentPromptsInOrder` 增 mode + workspaceReady 参数，PATH_ONLY 走 stub 不读正文，无 path 保留 name stub，!ready 回退全文。
5. **UI**：`SettingPreferencesGeneralPage` 增设置项。
6. **文案**：values / values-zh / values-ja strings.xml 增键。
7. **测试**：`DocumentAsPromptTransformerTest` 两模式 + 回退 + 无 path stub；偏好序列化测试。
8. 与 #199 冲突协调以主代理为准；实现阶段不编译（最后一个检查子代理允许）。

## 验证

- 最终一次合并 Gradle：编译 + 相关 JVM 单测。
- 安装验收（父任务）。

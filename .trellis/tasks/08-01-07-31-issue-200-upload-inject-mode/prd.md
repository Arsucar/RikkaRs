# feat(#200): 全局偏好控制上传附件注入方式

## Goal

新增全局偏好「上传附件注入方式」（`documentUploadInjectMode`），默认 **PATH_ONLY**（只注入 name + `/upload/...` 路径 stub，不注入正文），可选 FULL_BODY（现网 `DocumentAsPromptTransformer` 全文行为）。入口在「设置 → 偏好/通用」，非工作区基本页、非助手工具页。

## Requirements

- **数据**：`DisplaySetting` 新增枚举字段 `documentUploadInjectMode`，默认 `PATH_ONLY`；随 settings.json 序列化（无独立 migration，kotlinx 默认值处理升级缺省）。
- **Transformer**：`DocumentAsPromptTransformer` 按 `ctx.settings.displaySetting.documentUploadInjectMode` 分支：
  - PATH_ONLY：不调用 `readContent`/解析，仅注入 name + `path`（`resolveWorkspacePath` 结果）；无 path 可解析时保留最小 name stub，禁止静默丢弃。
  - FULL_BODY：现网行为（含多附件顺序回归 #112）。
- **回退**：PATH_ONLY 且当前对话 workspace 工具不可用（`resolveWorkspaceToolCapability(...).available == false` 或等价）时 **回退全文注入**，避免空附件进模型。
- **UI**：`SettingPreferencesGeneralPage` 新增设置项（Switch 两态或 Select），中/英/日文案。
- **不改**：附件 chip（`AttachmentChips`/`ChatMessage` 渲染原始 Document part）、ChatService 静态 transformer 列表。

## Acceptance Criteria

- [ ] 设置→偏好/通用可切换「仅路径 / 全文注入」，默认 PATH_ONLY。
- [ ] PATH_ONLY + 工作区 READY：Provider 上下文不含文件正文，含 name/path stub。
- [ ] PATH_ONLY + 工作区不可用：回退全文注入（不静默空附件）。
- [ ] FULL_BODY：与现网一致（多附件顺序回归 #112）。
- [ ] 上下文预览（#109）与真实请求一致（同一 transformer + settings）。
- [ ] `DocumentAsPromptTransformerTest` 覆盖两模式组装 + 无 path 安全 stub。
- [ ] 偏好序列化单测：升级缺省 = PATH_ONLY、encode/decode round-trip。
- [ ] 中/英/日文案齐全。

## Notes

- 依赖 #199：PATH_ONLY 需 `/upload` knownMount 可读（产品依赖，代码解耦）。
- `ctx.settings` 已可用（`Transformer.kt:11-21`），无需改 DI 或 ChatService transformer 列表。

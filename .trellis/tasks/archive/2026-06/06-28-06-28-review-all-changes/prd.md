# 审查近期所有改动

## 需求
对 release/rikka-arsucar 分支上所有未提交的改动进行代码审查，按模块分组并行执行。

## 分组

| 组 | 文件 |
|---|---|
| A-数据/AI 层 | Model.kt, ProviderSetting.kt, GenerationHandler.kt, Assistant.kt, PreferencesStore.kt, ThinkTagTransformer.kt |
| B-Subagent 核心 | SubagentHost.kt, SubagentProfile.kt, SubagentRegistry.kt, SubagentTools.kt, SubagentModelTest.kt |
| C-Service | ChatService.kt, RouteActivity.kt |
| D-UI 组件 | McpPicker.kt, ModelList.kt, SubagentToolUIs.kt, Markdown.kt, ChainOfThought.kt, ShareSheet.kt |
| E-UI 页面 | AssistantSubagentPage.kt, AssistantSubagentProfilePage.kt, SubagentUiHelpers.kt, ChatDrawer.kt, ChatList.kt, ExtensionsPage.kt, SettingProviderDetailPage.kt, SettingProviderPage.kt, SettingVM.kt, ProviderConfigure.kt, TranslatorPage.kt, WebViewPage.kt |
| F-新文件 | ExtensionSubagentProfilePage.kt, ExtensionSubagentsPage.kt |
| G-i18n | values/strings.xml, values-zh/strings.xml, values-zh-rTW/strings.xml, values-ja/strings.xml, values-ko-rKR/strings.xml, values-ru/strings.xml |

## 审查标准
- 代码风格是否符合项目约定
- 是否存在潜在 bug 或类型安全风险
- i18n 键是否完整覆盖
- 新文件是否与现有架构一致
- 是否有明显的内存/性能问题

## 验收标准
- 每组审查产出审查摘要（发现的 issues + 建议）

# i18n 残留硬编码修复

## Goal

修复工作区内 UI 层英文/中文硬编码，统一走 `stringResource(R.string.*)`。

## Background

review 发现多处用户可见文案未走 i18n：

| 位置 | 内容 | 类型 |
|------|------|------|
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt:217` | `contentDescription = "Clear"` | 英文硬编码 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentRegistry.kt:7-55` | `BUILTIN_PROFILES` 的 `displayName`/`description`/`systemPrompt` | 英文（属数据层，**本任务不处理**，见 Out of Scope） |

证据来源：explore 子代理检索报告 + 主代理 `Read` 确认 L217。

## Requirements

本任务范围 = **仅修高曝光文案**（用户决策，2026-06-27）。全仓扫描发现硬编码远超预期（contentDescription 39 + label 18 + Text 100+，主要在 ImgGenPage/ASR/TTS/Debug），全修会拖慢性能任务，故收窄。

- R-1：`ModelList.kt:217` 的 `contentDescription = "Clear"` → `stringResource`。
- R-2：同源 `Clear` contentDescription 还有：`ChatList.kt:643`、`SettingProviderPage.kt:178`，一并修。
- R-3：聊天/翻译/路由高曝光 Text 硬编码（非数据字段、非日志）：
  - `ChatList.kt:448` "Clear selection"、`:462` "Select all"、`:479` "Confirm"
  - `ChatDrawer.kt:349` "统计数据"
  - `TranslatorPage.kt:155` "粘贴文本"、`:200` "复制翻译结果"
  - `RouteActivity.kt:540` "[开发模式]"（保留中括号格式）
  - `WebViewPage.kt:115` "Open in Browser"、`:127` "Console Logs"
  - `ShareSheet.kt:56` "共享你的LLM模型"
  - `ChainOfThought.kt:434` "Chain of thought"
  - `SettingProviderPage.kt:689` "10% 优惠"
  - `McpPicker.kt:336`（若为模板字符串 `${enabled}/${total} tools`，加 stringResource 带参数）
- R-4：Markdown.kt 的 `contentDescription = "Copy"`/`"Download"`（L929/L944）一并修（代码块高频可见）。

新增字符串键统一加到 `app/src/main/res/values/strings.xml` + `values-zh/strings.xml`（及已存在的其他 values-* 目录）。

## Acceptance Criteria

- AC-1：`ModelList.kt` 内所有用户可见 `contentDescription` / `Text` 文案均走 `stringResource`。
- AC-2：新增的字符串键在 `values/strings.xml` 与 `values-zh/strings.xml`（及其他存在的 locale 目录）中同步存在。
- AC-3：`.\gradlew :app:compileDebugKotlin` 通过。
- AC-4：`.\gradlew lint` 无新增 `HardcodedText` warning。

## Out of Scope

- `SubagentRegistry.BUILTIN_PROFILES` 的数据层默认文案（父任务明确）。
- **ImgGenPage.kt 内部业务文案**（53+ 处，单独任务处理）。
- **ASRProviderConfigure.kt / TTSProviderConfigure.kt**（provider 配置表单 label，18+24 处，单独任务）。
- **DebugPage.kt**（调试页，仅开发可见）。
- **LogPage.kt**（日志页英文文案）。
- **SettingSpeechPage.kt**（provider 名词 "OpenAI Realtime" 等视为专有名词）。
- **WorkspaceTerminalPage.kt** 终端按键（ESC/TAB 等键帽）。
- **@Preview 示例代码**（CardGroup.kt/Switch.kt 等的预览文案）。
- `tag`（provider 自定义标签）、URL、日志、错误堆栈、第三方库。

## Technical Notes

- 新增键命名遵循 `model_list_clear` / `model_list_clear_selection` 风格（页面前缀）。
- `values-zh` 是中文默认；如存在 `values-zh-rCN` / `values-zh-rTW`，需同步。
- 修复时用 `Grep` 全仓搜 `contentDescription = "`、`Text(text = "`、`label = "` 三类模式定位遗漏。

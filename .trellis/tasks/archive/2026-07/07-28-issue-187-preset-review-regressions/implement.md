# Implementation Plan: #187 Preset Review Regressions

## Block A: Runtime Deduplication

- [x] 为 preset entry 解析结果增加内部稳定去重身份。
- [x] Reference 使用目标 `modeInjectionId` 去重，Custom/Builtin 保持 entry id。
- [x] 补直连 + Reference 同目标回归测试和不同 Custom 独立测试。

## Block B: Workspace Default Contract

- [x] 提取完整 Workspace 默认生成函数为注册表与 transformer 的单一来源。
- [x] 注册表默认保留 `{{workspace_name}}` / `{{cwd}}`，transformer 继续在运行时边界解析 override 宏。
- [x] 补默认等价性与关键安全约束测试。
- [x] 将动态宏替换改为单次扫描原始模板，补运行时值不二次解析测试。

## Block C: Accessible Reordering

- [x] 亲自核对 `PresetDetailPage` 当前拖拽与 latest-value mutation，再接入菜单移动命令。
- [x] 用同组索引控制上移/下移 enabled，复用稳定 ID 的 `moveInGroup` mutation。
- [x] 将拖拽手柄改为无空 click 的语义节点，新增英文/简中准确描述。
- [x] 补 helper 测试覆盖中间移动、首尾边界、跨组不变。
- [x] 更新 UI Modification Thinking Guide 的非手势等价操作规则。

## Block D: Quality Gate

- [x] 运行聚焦单测覆盖新回归。
- [x] 运行 `git diff --check`。
- [x] 一次合并运行 `:app:processDebugResources :app:compileDebugKotlin :app:testDebugUnitTest
  :app:compileDebugAndroidTestKotlin`，全部带 `--no-daemon`，跳过 web-ui 构建。
- [x] 生产代码冻结后检查 `adb devices`；有设备执行 `:app:installDebug`，无设备按仓库 APK 交付流程处理。
- [x] 对本轮 diff 做独立静态复核，只检查 #187 三项和新跨越边界。

## Risk Files

- `data/ai/transformers/PromptInjectionTransformer.kt`
- `data/ai/prompts/BuiltinPromptRegistry.kt`
- `data/ai/transformers/WorkspaceReminderTransformer.kt`
- `ui/pages/extensions/PresetDetailPage.kt`
- `ui/pages/extensions/PresetEntryUi.kt`
- `values/strings.xml` / `values-zh/strings.xml`

## Freeze Rule

最终合并 Gradle 通过后不再进行可选生产代码重构；仅 HIGH/CRITICAL 新证据允许重新打开实现循环。

# Issue #137 实施计划

- [x] 加载 `trellis-before-dev`、app 规范、跨层/复用指南、本任务产物及 `locale-tui-localization`。
- [x] 在 model/domain 层集中定义普通记忆与记忆表格 capability，并更新现有 `shouldEnableMemoryTable`/测试。
- [x] 在 `ChatService.prepareGenerationRequest` 中只在普通 capability 开启时读取普通记忆，保持 Preview/实际生成共用路径。
- [x] 核查 `GenerationHandler` 的普通 prompt/tool gate 和通用 tools 合并顺序，必要时仅做最小调整。
- [x] 保持 MemoryTableRepository、Transformer 与表格工具只依赖表格 capability，不引入普通记忆判断。
- [x] 修正 `AssistantMemoryPage` 全局表格 Switch，使其只更新自身字段，并补全局门控原因文案。
- [x] 重构 `AssistantToolsPage` 的工具组 UI 状态，区分偏好、实际启用和控件可用性；应用 LocalSettings 全局表格门控。
- [x] 将本页触及的硬编码用户文案迁移到字符串资源，通过 locale-tui 补齐/核验翻译。
- [x] 新增/更新四组合、工具统计/UI 状态、生成准备和持久化兼容 JVM 测试。
- [x] 搜索所有 `assistant.enableMemory` 外层块，确认没有包裹表格 Repository、Transformer 或 `memory_table_tool`。
- [x] 运行 `git diff --check`。
- [x] 运行聚焦测试：MemoryCapabilities/MemoryTable/AssistantTools/MemoryTableTools/MemoryTableInjectionTransformer/PreferencesStore，全部带 `--no-daemon`。
- [x] 运行 `:app:compileDebugKotlin --no-daemon` 与 `:app:lintDebug --no-daemon`，区分既有基线和新增问题。
- [ ] 按 app 功能改动流程执行 `adb devices` 与 `:app:installDebug --no-daemon`。
- [ ] 真机核对四种组合、两个设置入口、全局门控原因、暗色主题和字体缩放下的状态一致性。

验证记录：`processDebugResources`、`compileDebugKotlin` 和 `testDebugUnitTest` 均通过；91 个测试套件、578 个测试全部通过。`lintDebug` 仍受仓库既有问题阻断（80 errors、34 warnings），交叉核验未命中本次改动行的 error；新增资源仅命中 1 个 `PluralsCandidate` warning。当前未完成 adb 安装和真机四组合验收。

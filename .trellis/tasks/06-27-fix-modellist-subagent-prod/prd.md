# Fix ModelList & Subagent features for production readiness

## Goal

修复审查发现的 12 个问题，使 ModelList 交互、子代理数据层、标签筛选功能达到上线质量。

## Requirements

### R1: Critical 数据层与稳定性

- R1.1: `Settings.globalSubagentProfiles` 必须在 DataStore 中持久化（添加 preferences key + 读写逻辑）
- R1.2: `ChatService.updateConversationState` 的 CAS 循环必须加 retry 上限防活锁
- R1.3: 子代理 `spawn_subagent` 异常/取消时，streaming metadata 中 `subagent_streaming: true` 须被清除，UI 不得永显 spinner

### R2: ModelList 交互修复

- R2.1: 添加「全部展开/全部折叠」按钮（provider group 级别）
- R2.2: 修复 `selectedModelPosition` 计算不尊重折叠状态导致跳转错位
- R2.3: 展开提供商 tab（FlowRow）后，点击 provider 仍能正确跳转，且点击后自动收纳回 LazyRow
- R2.4: 全屏 sheet `fillMaxSize()` 改回合理交互模型（保留全屏内容区但维持 BottomSheet 的拖拽手势暗示）

### R3: 标签增强

- R3.1: ModelList sheet 中添加标签筛选（与 SettingProviderPage 一致的 FilterChip 横行）
- R3.2: `SUGGESTED_PROVIDER_TAGS` 硬编码中文改为 stringResource，且标签文本不持久化中文标记
- R3.3: Provider 模型计数恢复使用 `stringResource(R.string.setting_provider_page_model_count, ...)` 并扩展支持 chat/total 分拆显示

### R4: 子代理运行时补全

- R4.1: `SubagentTranscriptStep.Reasoning.createdAt` 确保与 `UIMessagePart.Reasoning.createdAt` 类型兼容
- R4.2: `ExtensionSubagentProfilePage.persist` 中 profile name 变更时需同步更新路由参数

## Acceptance Criteria

- [ ] AC1: App 重启后 `globalSubagentProfiles` 配置不丢失
- [ ] AC2: `updateConversationState` CAS 循环在 50 次重试后放弃并 log warning
- [ ] AC3: 子代理异常退出后，UI 不再永久展示 spinner（streaming 标记被正确清除）
- [ ] AC4: ModelList 中有「全部展开/全部折叠」按钮，一键操作所有 provider group
- [ ] AC5: 打开 ModelList 时选中模型滚动位置正确（考虑折叠状态）
- [ ] AC6: 展开 provider tab 后点击 chip 能正确跳转并自动收纳回 LazyRow
- [ ] AC7: ModelList sheet 支持标签筛选（按 provider tag 过滤）
- [ ] AC8: 建议标签文案走 stringResource，不再硬编码中文
- [ ] AC9: 模型计数使用 i18n string resource
- [ ] AC10: 编译通过 + `.\gradlew :app:installDebug` 安装成功

## Constraints

- 不改变 `ProviderSetting` 的序列化结构（tags 字段保持现有位置，向后兼容OK）
- 子代理流式进度恢复不依赖 `fix/subagent-streaming-render` 分支合入
- 国际化暂只处理 `values/strings.xml`，其他 locale 文件后续补充

## Notes

- 审查原始报告：本轮审查发现 12 个问题（5 Critical/Major + 7 Minor-Major）
- 上游对比基准：`sub/master` 分支

# 处理所有 open issue 与 PR

## Goal

父任务：系统化处理当前所有 open issue 与 PR。
- 审查 → 修复 → 合并 6 个已有 PR（含对应 issue 关闭）
- 实现 9 个无 PR issue 的修复

## Scope

### A. 已有 PR（审查→修复→合并→关闭对应 issue）

| PR | 对应 Issue | 标题 | 分支 | 初判 |
|----|-----------|------|------|------|
| #300 | #293 | Magic Number 提取常量（13 处 delay/debounce） | fix/magic-number-constants-293 | diff 简洁清晰，需验证常量命名与位置 |
| #299 | #288 | ConversationEntity 联合索引 + Migration 52→53 | fix/conversation-entity-index-288 | 含迁移+测试，需验证索引名与 schema 一致 |
| #298 | #291 | SharingStarted.Eagerly → WhileSubscribed(5000)（22 处） | fix/sharing-started-while-subscribed-291 | 批量替换，需验证 5000ms 是否所有场景合理 |
| #297 | #289 | Modifier 顺序修正（5 处，padding 前置） | fix/modifier-order-289 | 修正点击区域，需验证视觉无回归 |
| #283 | — | 代码鲁棒性（!! / runCatching / InputStream / printStackTrace） | fix/code-robustness-274 | 多文件，需验证每处降级是否合理 |
| #264 | — | README 改进（补充 pnpm 说明） | webbrain/readme-improvement | 文档变更，低风险 |

### B. 无 PR 的 issue（实现修复）

| Issue | 标题 | 类型 | 规模 |
|-------|------|------|------|
| #296 | 拆分 ChatVM 为多个职责专注的 ViewModel（932 行 → 6 VM） | enhancement | 大（重构） |
| #295 | 写操作乐观更新机制（13 个写操作） | enhancement | 大（重构） |
| #294 | 引入 detekt/ktlint 静态分析工具 | bug | 中（工具链） |
| #292 | UI 硬编码字符串 → stringResource（93 处） | bug | 中（批量 i18n） |
| #290 | 依赖注入统一（koinInject 51 处 / 手动 new 32 处） | bug | 中（重构） |
| #287 | 弹窗滚动支持（72 个 Dialog/Sheet） | bug | 中（批量 UI） |
| #276 | 测试覆盖空白（ViewModel 集成测试 + Compose UI 测试） | bug | 中（测试） |
| #268 | ChatPage 顶层状态收集范围过大（20 处 collectAsState） | bug | 中（性能） |
| #267 | DataStore 写入模式统一（全量覆盖→transform 原子写，8 VM + Slider） | bug | 中（数据层） |

## Requirements

### 通用
- 所有修改目标分支为 `release/rikka-arsucar`
- 合并前必须通过 `--no-daemon` 编译
- issue 关闭前必须发布中文+英文交付评论，含逐条验收勾选
- PR 审查发现问题先在分支修复，通过后再合并

### A. PR 审查合并
- 逐个审查 PR 代码，重点检查：编译正确性、行为回归、边缘案例、命名规范
- PR #299 需验证 Migration 52→53 与现有迁移链衔接正确，索引名与 Room 自动生成一致
- PR #283 需逐处验证降级策略不掩盖真实错误

### B. 无 PR issue 实现
- #296: ChatVM 拆分后行数 ≤400，每个 VM 函数数 ≤15，功能行为不变
- #295: 乐观更新基础设施可复用，点击置顶/收藏 UI <16ms 响应，失败回滚 + Toast
- #294: detekt + ktlint 配置接入，CI 可运行
- #292: 93 处真实 UI 文本迁移到 strings.xml（Preview/ClipData 可忽略）
- #290: 统一 DI 约定，Composable 不直接 koinInject Repository/Manager
- #287: 72 个弹窗内容区添加 verticalScroll + heightIn(max)，底部按钮可达
- #276: 关键 ViewModel（ChatVM、AssistantDetailVM）集成测试 + 核心 Composable UI 测试
- #268: ChatPage 状态收集下推到子组件，流式输出时仅 ChatList 重组
- #267: 废弃 updateSettings(Settings) 全量写，改字段级 transform 原子写

## Acceptance Criteria

### A. PR 合并
- [ ] PR #300 合并，issue #293 关闭（中文+英文评论+逐条勾选）
- [ ] PR #299 合并，issue #288 关闭
- [ ] PR #298 合并，issue #291 关闭
- [ ] PR #297 合并，issue #289 关闭
- [ ] PR #283 合并
- [ ] PR #264 合并

### B. 无 PR issue 实现
- [ ] #296 ChatVM 拆分完成，验收条件全部满足
- [ ] #295 乐观更新机制实现，验收条件全部满足
- [ ] #294 detekt/ktlint 引入，CI 检查可用
- [ ] #292 93 处硬编码字符串迁移完成
- [ ] #290 DI 统一，koinInject/手动 new 收敛
- [ ] #287 72 个弹窗滚动支持添加
- [ ] #276 关键 VM 集成测试 + UI 测试补充
- [ ] #268 ChatPage 状态收集范围优化
- [ ] #267 DataStore transform 原子写迁移

### C. 最终验证
- [ ] 全量 `--no-daemon` 编译通过
- [ ] 安装到设备验证核心功能（聊天、设置、助手详情、弹窗）

## Notes

- 子任务结构：每个 issue/PR 作为独立可验证交付物，按依赖关系排序
- #296（拆分 ChatVM）与 #295（乐观更新）有交叉：拆分后的 VM 承载乐观更新逻辑，建议 #296 先行
- #268（状态收集）与 #296（拆分 ChatVM）有交叉：拆分后状态收集自然下推，可协调
- #267（DataStore 写入）与 #295（乐观更新）有交叉：乐观更新失败回滚涉及 DataStore transform
- 大范围批量改动（#292 的 93 处、#287 的 72 个）用子代理并行处理

# Research: Provider tags / ModelList / Assistant extensions / ThinkTag (Subagent E)

- **Query**: 只读综合审计 — Provider tag、ModelList 过滤、Assistant 扩展 sections、多段 think-tag、标题/建议模型回退
- **Scope**: internal（代码库）
- **Date**: 2026-06-28
- **Repo path**: `D:\2026Code\Group_android\rikkahub`
- **HEAD**: `3ebfbca45893deefb8c61768a4bdb0e01255a5f6` (`3ebfbca4 chore(task): archive 06-28-06-28-fix-review-issues`)
- **Branch**: `release/rikka-arsucar`（本地 ahead 4 vs `origin/release/rikka-arsucar`）

## Commit 统计（审查范围文件）

相对 `origin/release/rikka-arsucar..HEAD`（与里程碑相关的近期改动）：

| 文件 | 变更 |
|------|------|
| `app/.../ModelList.kt` | +489 / -157 行量级重构 |
| `app/.../ThinkTagTransformer.kt` | 多块 think 解析 |
| `ai/.../ProviderSetting.kt` | `tags` 等字段延续 |

近期提交（节选）：

- `8be9f419` — fix: review（remember deps、i18n、a11y 等）
- `6dca48a1` — milestone: provider tags, model filtering, ext sections, multi-segment think-tag
- `b593f7cd` — ModelSelector 状态管理重构

**路径说明**：任务中的 `app/.../data/model/Provider.kt`、`ModelMapping.kt` 在仓库中**不存在**；提供商类型为 `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`（数据）与 `ai/.../Provider.kt`（接口）。

---

## 1. Provider tag 系统

### 数据与持久化

- `ProviderSetting` 密封类在 `ai/.../ProviderSetting.kt` 定义抽象字段 `tags: List<String>`，OpenAI/Google/Claude 子类默认 `emptyList()`，`copyProvider` 传播 `tags`（L32–48, L62, L100–114 等）。
- 提供商列表随 `Settings.providers` 经 `PreferencesStore` 序列化；缺失 JSON 字段时 kotlinx.serialization 默认空列表（设计文档与实现一致）。
- `ProviderSetting.convertTo`（`app/.../setting/components/ProviderConfigure.kt` L82–120）各分支携带 `tags = this.tags`；有 `ProviderConfigureConvertToTest` 覆盖类型转换，**未单独断言 tags**。

### 设置 UI

**列表页** `SettingProviderPage.kt`：

- `selectedFilterTag` + 搜索：`matchesTag = selectedFilterTag == null || provider.tags.contains(selectedFilterTag)`（L111–116）。
- Chip 来源：`settings.providers.flatMap { it.tags }.distinct()`（L186–188）。
- 行内展示：`provider.tags`（约 L692+）。

**详情配置页** `SettingProviderDetailPage.kt` → `SettingProviderConfigPage`：

- `ProviderSetting.withTags` 仅覆盖三种 sealed 类型（L261–265）。
- 增删：`InputChip` + 文本框；新增时 `newTagText.trim()` 且去重（L347–350）。
- 建议标签：`stringArrayResource(R.array.provider_suggested_tags)`（L359–375），点击合并进 `internalProvider.tags`；**保存前仅改 `internalProvider`，需点保存** `onEdit(internalProvider)`（L411–414）。

### 匹配逻辑

- 均为 **精确字符串** `List.contains` / `tags.contains(selectedFilterTag)`，**大小写敏感**，无规范化（trim 仅在新标签输入时）。

---

## 2. ModelList 过滤（tag / 收藏 / 搜索）

文件：`app/.../ui/components/ai/ModelList.kt`（约 1061 行）。

### 行为摘要

| 维度 | 实现位置 | 行为 |
|------|----------|------|
| Tag | L333–339, L544–574 | `selectedModelListTag` → `tagFilteredProviders`；chip 来自**传入的** `providers`（sheet 侧为 `state.filteredProviders`：已启用且含当前 `ModelType`） |
| 搜索 | L347–352 | 在 tag 过滤后的 provider 上按 `displayName` 子串（ignoreCase）过滤模型 |
| 收藏 | L319–328, L596–689 | `settings.value.favoriteModels` 顺序；可拖拽重排；与 tag **无交集过滤** |
| 空集 | L585–593 | `tagFilteredProviders.isEmpty()` 显示 `model_list_no_providers` |
| 组合 | — | **Tag 与搜索为串联**：先 provider tag，再 per-provider 模型搜索 |

### 性能相关（描述性）

- 多处 `remember(...)` 预计算 `associate` 映射（`typeFilteredModelsByProvider`、`searchFilteredModelsByProvider`、`providerPositions`），避免 LazyColumn 项内重复全量扫描。
- `favoriteModels` 的 `remember` 键含 `settings.value.favoriteModels`、`settings.value.providers`、`providers`、`modelType`（L319）；`8be9f419` 意图修复 stale providers。
- 大量模型时仍会对每个 provider 做 `fastFilter`；折叠分组可减少可见 item 数，但首次 `remember` 仍遍历全部模型。

---

## 3. Assistant 扩展 sections

文件：`app/.../assistant/detail/AssistantExtensionsPage.kt`（4 Tab：Quick messages / Mode injections / Lorebooks / Skills）。

### 持久化

- 全局定义：`Settings.quickMessages`、`modeInjections`、`lorebooks`（`PreferencesStore` / `Settings` 数据类）。
- 助手绑定：`Assistant.quickMessageIds`、`modeInjectionIds`、`lorebookIds`、`enabledSkills`（`app/.../data/model/Assistant.kt` L32–46）。
- 更新：`AssistantDetailVM.update` 写回 `settings.assistants` 列表（L163–178）；**不**在每次 toggle 时校验 ID 是否仍存在于全局列表。

### 与 Web / 迁移对比

- `SettingsRoutes` / `ConversationRoutes` 在 API 更新助手或会话注入时 **校验** modeInjection/lorebook/quickMessage ID 集合（未知 ID → 400）。
- **Android 扩展页**仅展示 `settings.*` 中仍存在的项；若用户曾在全局删除某条 prompt，助手上的 **孤立 ID 可残留**（运行时 `PromptInjectionTransformer` 通常忽略无效 ID，有单测覆盖注入逻辑，但助手 JSON 仍脏）。

### 跨设备

- 助手与全局 prompts 同属 Settings 导出/同步范畴；无单独 DB 表拆分扩展绑定。

---

## 4. 多段 ThinkTagTransformer

文件：`app/.../data/ai/transformers/ThinkTagTransformer.kt`。

### 机制

- 正则：`THINKING_REGEX = <think>([\s\S]*?)(?:</think>|$)`（L10），`findAll` 循环（L19–38）。
- 输出：`Text` 段与 `UIMessagePart.Reasoning` 交替；闭合检测 `CLOSING_TAG_REGEX` 在 match 子串上（L29–34）。
- `visualTransform`：流式时未闭合块 `finishedAt = null`；`onGenerationFinish` 用 `Clock.System.now()` 作为闭合时间（L75–98）。
- 注册：`ChatService` 输出 transformer 链含 `ThinkTagTransformer`。

### 边界（代码层面）

- 仅处理 **ASSISTANT** 且含 `UIMessagePart.Text` 的消息；按 **part** 扁平化，不跨 part 拼接。
- 未闭合流式块：依赖 `$` 备选，尾段 `Text` 在块后输出（L40–45）。
- **仓库内无** `ThinkTagTransformerTest` / 多块 think 单测（任务 `06-27-sub-agent-streaming-ui` 中多块项在 implement.md 仍为未勾选）。

### 与子代理关系（架构事实）

- 子代理 UI 转录由 `SubagentHost.buildTranscript` 等路径组装；子流 **不一定** 经过与主聊天相同的逐 chunk `visualTransform`（见任务 `06-27-sub-agent-streaming-ui/research`）。主聊天 assistant 文本仍走 `ThinkTagTransformer`。

---

## 5. 标题 / 建议模型回退

| 场景 | UI（`SettingModelPage.kt`） | 运行时（`ChatService.kt` + `PreferencesStore.kt`） |
|------|------------------------------|-----------------------------------------------------|
| 标题模型 | `titleModelId` 可 null；`ModelSettingItem` 显示 `model_list_select_model`（L128–135, L270–272） | `findModelById(titleModelId, fallback = fastModelId)`（L819）；无模型则 **return** |
| 建议模型 | `suggestionModelId` 可 null；开关 `enableSuggestion`（L178–242） | `enableSuggestion` 为 false 则 return；否则 `findModelById(suggestionModelId, fallback = fastModelId)`（L859–860） |
| Fast 模型 | 必选字段 `fastModelId`（默认 `Uuid.random()`） | 作为 title/suggestion 回退目标 |

`Settings.findModelById`（L790–794）：先主 ID，再 fallback ID，皆 null 则 null。

**文案**：`setting_model_page_fast_model_desc` 说明 fast 亦作 title/suggestion 回退（`values/strings.xml` 约 L879）。

**对比**：`compressModelId` 在 `ChatService` 使用 `findModelById(settings.compressModelId)` **无** fallback 参数（约 L913），与 title/suggestion 策略不一致。

---

## 发现列表（file:line）

### P0

（本次只读扫描 **未发现** 明确崩溃级逻辑错误；以下为需产品/回归确认的高风险项，若团队定义 P0=数据丢失则见 P1 孤立 ID。）

### P1

| ID | 位置 | 描述 |
|----|------|------|
| E-P1-1 | `ModelList.kt:319-327` vs `336-338` | 启用 provider **tag 筛选**后，**收藏区仍展示**所有符合 `modelType` 的收藏，即使其 provider 不满足 `tags.contains(selectedTag)`，与「按 tag 看模型」心智不一致。 |
| E-P1-2 | `AssistantExtensionsPage.kt:112-173` + `AssistantDetailVM.kt:163-178` | 助手上的 `quickMessageIds` / `modeInjectionIds` / `lorebookIds` 在全局条目删除后 **不会自动 prune**；Web API 更新助手会校验，原生扩展页不会。 |
| E-P1-3 | `ThinkTagTransformer.kt:10-36` | 多块解析 **无单测**；回归依赖手工；未闭合/嵌套 `</think>` 边界仅靠正则，子代理流路径覆盖不完整。 |

### P2

| ID | 位置 | 描述 |
|----|------|------|
| E-P2-1 | `SettingModelPage.kt:270-272` + `ChatService.kt:819` | UI 标题模型为空显示「选择模型」，但后台 **静默使用** `fastModelId`，用户可能不知实际用哪个模型。 |
| E-P2-2 | `SettingModelPage.kt:207-209` + `ChatService.kt:860` | 建议模型未选时 UI 同左；启用建议时仍 **fallback fast**。 |
| E-P2-3 | `SettingProviderPage.kt` / `ModelList.kt` tag 逻辑 | Tag 匹配 **大小写敏感**；中英文建议标签与用户自定义混用时易出现「筛不到」。 |
| E-P2-4 | `SettingProviderDetailPage.kt:308-329` | 标签删除 chip 的 trailing `IconButton` `contentDescription = null`（L324），无障碍缺口。 |
| E-P2-5 | `ModelList.kt:347-352` | 搜索 + tag 后 provider 区块仍在，但 `items` 可为空列表 → **空分组**（仅 header），无单独空状态文案。 |

### P3

| ID | 位置 | 描述 |
|----|------|------|
| E-P3-1 | `ModelList.kt:563-568` | `FilterChip` 标签文本无 `TextOverflow.Ellipsis`，超长用户 tag 可能撑破布局。 |
| E-P3-2 | `SettingProviderPage.kt:178` | 搜索框清除按钮 `contentDescription = "Clear"` 硬编码英文。 |
| E-P3-3 | `ChatService.kt:913` | `compressModelId` 无 fallback，与 title/suggestion 策略不一致。 |
| E-P3-4 | `ModelList.kt:333` | `selectedModelListTag` 不持久化，关 sheet 丢失（产品选择，非 bug）。 |
| E-P3-5 | `ProviderConfigureConvertToTest.kt` | `convertTo` 测试未断言 `tags` 保留（实现已带 tags）。 |

---

## 相关 Spec / 任务文档

- `.trellis/tasks/06-27-provider-list-ui-opt/` — tags 字段与 UI 设计
- `.trellis/tasks/06-27-fix-tag-filtering-i18n/` — ModelList tag chip
- `.trellis/tasks/06-28-review-fixes/` — provider 标签 i18n（en `values/strings.xml`）
- `.trellis/tasks/archive/2026-06/06-28-06-28-review-all-changes/research/` — ModelList / SettingProvider 差异审查

---

## Caveats / Not Found

- 未执行编译、仪器测试或真机操作（只读约束）。
- 未逐 locale 核对 `provider_suggested_tags` 与 `model_list_*` 键完整性（并行任务 `subagent-c-i18n` 可能已覆盖）。
- `ModelMapping.kt`：**未找到**；模型映射逻辑分散在 `Model`/`ProviderSetting.models` 与 datastore 查找辅助函数中。

---

## 明早 Top 5

1. **E-P1-1** — ModelList：tag 筛选时收藏列表是否应同步过滤（产品规则 + 一行过滤条件）。
2. **E-P1-2** — Assistant 扩展：删除全局 prompt 后清理助手孤立 ID（对齐 Web 校验或启动时 prune）。
3. **E-P1-3** — 为 `ThinkTagTransformer` 增加多块/流式未闭合单测，并确认子代理展示路径是否需同样解析。
4. **E-P2-1 / E-P2-2** — 设置页展示 title/suggestion 的 **effective model**（含 fast 回退）或明确文案。
5. **E-P2-5** — ModelList 搜索后空 provider 分组：是否隐藏 header 或显示「无匹配模型」。
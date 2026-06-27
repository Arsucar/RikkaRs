# Research: UI 页面未提交改动代码审查（12 文件）

- **Query**: 审查 release/rikka-arsucar 分支上 12 个 UI 页面的 `git diff`
- **Scope**: internal（diff + 路由/调用方交叉引用）
- **Date**: 2026-06-28

## 总览

| 主题 | 涉及文件 | 风险 |
|------|----------|------|
| 子代理 builtin → 全局配置 | Subagent*、ExtensionsPage | 中：列表/启用逻辑未完全传入 `globalProfiles` |
| Provider 标签与筛选 | SettingProvider*、ProviderConfigure | 低–中：UI/持久化一致，部分 UX |
| i18n 清理 | ChatDrawer、ChatList、Translator、WebView | 低：方向正确，个别残留硬编码 |
| SettingVM 全局子代理 API | SettingVM.kt | 低：与扩展页职责一致 |

---

## 1. AssistantSubagentPage.kt

### 改动摘要
- 从 `vm.settings` 读取 `globalSubagentProfiles`，列表与创建校验改为 global 语义（`disabledGlobalSubagents`、`subagent_global_badge`）。
- 全局项点击跳转 `Screen.ExtensionSubagentProfile`；本地项仍走 `AssistantSubagentProfile`。

### Issues

| 级别 | 问题 | 位置（diff/当前文件） |
|------|------|------------------------|
| **高** | `generateCloneName` 仍调用 `subagentListEntries(assistant)`，**未传入 `globalProfiles`**。克隆名冲突检测会忽略全局 profile 名称，可能生成与全局同名的 local id。 | `AssistantSubagentPage.kt` ~L367 |
| **中** | 全局 profile 在助手侧仅「禁用/恢复」，不可克隆；与产品一致，但 `displayName + " (copy)"` 仍为硬编码英文（克隆路径）。 | diff 克隆逻辑 |
| **低** | 多处 `contentDescription = null`（Refresh/Add/Copy/Delete），与仓库其它设置页一致，无障碍仍偏弱。 | trailing actions |

### 建议
- 将 `generateCloneName` 签名扩展为接收 `globalProfiles`，或从 content 层传入 `subagentListEntries(assistant, globalProfiles)` 的 taken 集合。
- 克隆后缀改用 `stringResource`（若已有 key 则复用）。

### 架构 / 导航
- **一致**：与其它 Assistant detail 子页相同（`AssistantDetailVM` + `collectAsStateWithLifecycle` + `Screen.*` 导航）。
- **路由**：`ExtensionSubagentProfile(profileName, false)` 已在 `RouteActivity.kt` 注册，与 `ExtensionsPage` 入口一致。

---

## 2. AssistantSubagentProfilePage.kt

### 改动摘要
- 解析 profile 使用 `SubagentRegistry.resolveProfile(..., globalProfiles)`。
- 抽出 `SubagentProfileForm`（`internal`），支持 `readOnly` / `isGlobalOnly`（仅全局、非助手覆盖时只读 + 提示去扩展编辑）。

### Issues

| 级别 | 问题 |
|------|------|
| **低** | `isGlobalOnly` 判定：`name in globalProfiles` 且不在 `assistant.subagentProfiles`。若助手用同名 local 覆盖，行为正确；若仅 global，只读合理。 |
| **低** | `SubagentProfileForm` 参数较多，符合现有 Form 页拆分方式，可接受。 |

### 建议
- 确认 `ExtensionSubagentProfilePage` 复用 `SubagentProfileForm` 时 `readOnly=false` 且 persist 走 `SettingVM`（审查范围外文件，但属集成点）。

### ViewModel
- 助手更新仍经 `vm.update`；全局编辑不在此页 persist，符合设计。

---

## 3. SubagentUiHelpers.kt

### 改动摘要
- `SubagentListEntry`：`isBuiltin` → `SubagentProfileSource` + `isDisabledGlobal`。
- `subagentListEntries(assistant, globalProfiles)`：全局项（未被 local 同名覆盖）+ 助手 local 列表。
- `assistantHasSpawnableProfile(assistant, globalProfiles)` 增加第二参数（默认 `emptyList()`）。

### Issues

| 级别 | 问题 |
|------|------|
| **高** | **`AssistantSubagentHubSection.kt` 未在本次 diff 中更新**，仍调用 `assistantHasSpawnableProfile(assistant)`。在「仅有全局可 spawn、助手 local 为空」时，`canEnable` 可能误判为 false，与子代理列表页行为不一致。 |
| **中** | 全局 profile 被助手 local **同名覆盖**时，列表只显示 local 项（`filter { it.name !in customByName }`），与解析顺序（local → global）一致；需在 QA 明确这是预期。 |
| **低** | 文件末尾仍无换行（`\ No newline at end of file`）。 |

### 建议
- Hub 区传入 `settings.globalSubagentProfiles`（需在父 Composable 收集 settings，与 `AssistantSubagentPage` 一致）。

---

## 4. ChatDrawer.kt

### 改动摘要
- 「统计数据」→ `R.string.chat_drawer_stats`（label + contentDescription）。

### Issues
- **无功能性 issue**；与 `values/strings.xml` 英文默认及多语言 values 对齐。

---

## 5. ChatList.kt

### 改动摘要
- 批量选择 tooltip / Clear：`chat_list_clear_selection`、`common_select_all`、`chat_list_confirm`、`common_clear`。

### Issues
- **无**；符合 AGENTS.md 中 `stringResource` 方向。

---

## 6. ExtensionsPage.kt

### 改动摘要
- 扩展 CardGroup 增加子代理入口 → `Screen.ExtensionSubagents`；图标 `HugeIcons.Connect`。
- 新增 `MaterialTheme` import（diff 中未见使用，可能为 IDE 自动导入或后续用途）。

### Issues

| 级别 | 问题 |
|------|------|
| **低** | 未使用的 `MaterialTheme` import → lint 警告风险。 |

### 架构
- 与 Skills/Workspace 入口模式一致（`CardGroup` + `navigate(Screen.*)`）。

---

## 7. SettingProviderDetailPage.kt

### 改动摘要
- 标签 UI：`InputChip` / `AssistChip` / `FlowRow`，建议标签来自 `R.array.provider_suggested_tags`。
- `ProviderSetting.withTags` 分支更新三种 sealed 类型。
- `ModelPicker`：`sheetGesturesEnabled = false`。

### Issues

| 级别 | 问题 |
|------|------|
| **中** | 标签 chip 内 `IconButton` 嵌在 `InputChip` 的 `trailingIcon` 中，点击区域小；与 Material 常见模式一致但易误触。 |
| **中** | `sheetGesturesEnabled = false` 会禁用下拉关闭手势，需确认是否为修复误触关闭；影响可发现性（用户需点外部/返回关闭）。 |
| **低** | 标签删除/添加 icon `contentDescription = null`。 |
| **低** | 标签持久化依赖用户点击保存（`onEdit(internalProvider)`）；若页面其它字段也是同一模式则一致。 |

### ViewModel
- 仍使用 `SettingVM` + `koinViewModel()`，与 `SettingProviderPage` 一致。

---

## 8. SettingProviderPage.kt

### 改动摘要
- `LazyRow` + `FilterChip` 按 tag 筛选；搜索与 tag **与** 关系。
- 列表项展示 baseUrl 摘要、chat 模型数 `setting_provider_page_model_count_chat`、provider.tags、AiHubMix 文案资源化。

### Issues

| 级别 | 问题 |
|------|------|
| **中** | `provider.name == "AiHubMix"` 硬编码供应商名；标签化后更宜用 builtIn/标签而非 display name。 |
| **低** | Filter chip 的 tag 文案为用户自定义字符串，无需 i18n。 |
| **低** | `baseUrlSummary` 用 broad `catch (Exception)`，与防御性展示一致。 |

### Compose
- `remember(settings.providers, searchQuery, selectedFilterTag)` 依赖正确。

---

## 9. SettingVM.kt

### 改动摘要
- `deleteGlobalSubagent`、`restoreDefaultSubagents`（合并 `SubagentRegistry.BUILTIN_PROFILES` 中缺失项）。

### Issues

| 级别 | 问题 |
|------|------|
| **低** | `restoreDefaultSubagents` 不删除用户已改名的全局项，只追加缺失 builtin 名，与 PRD 语义一致。 |
| **低** | 与 `ExtensionSubagentsPage` 职责划分清晰（VM 在 setting 模块，扩展页调用）。 |

---

## 10. ProviderConfigure.kt

### 改动摘要
- `convertTo` 各分支增加 `tags = this.tags`。

### Issues
- **无**；与 `ProviderSetting.copyProvider` 已含 `tags` 字段一致，避免类型转换丢标签。

---

## 11. TranslatorPage.kt

### 改动摘要
- 「粘贴文本」「复制翻译结果」→ `translator_page_paste` / `translator_page_copy_result`。

### Issues
- **无**。

---

## 12. WebViewPage.kt

### 改动摘要
- 菜单与 More 按钮文案资源化。

### Issues

| 级别 | 问题 |
|------|------|
| **低** | 下拉项 `leadingIcon` 仍为 `contentDescription = null`（改动前即如此）。 |

---

## 交叉关注点（未在 12 文件 diff 内但影响正确性）

1. **数据迁移**：任务 `06-28-review-fixes` 要求 `disabledBuiltinSubagents` → `disabledGlobalSubagents`；UI 已全部改用后者，需确认 `PreferencesStore` 迁移与 UI 同批发布。
2. **调用方**：全仓库搜索 `subagentListEntries(` / `assistantHasSpawnableProfile(`，确保凡展示「能否启用子代理」处均传入 `globalSubagentProfiles`。
3. **字符串**：本次 diff 引用的 key 在 `values/strings.xml` 多为英文默认；若默认 values 仍含中文（其它任务 R2），与本次 UI 改动独立，但影响非中文用户。

## Caveats / Not Found

- 未对 `ExtensionSubagentsPage.kt` / `ExtensionSubagentProfilePage.kt` 做 diff 审查（不在用户列出的 12 文件内）。
- `.trellis/spec/` 下无独立 Compose/UI 规范文件；对照的是既有 Assistant/Setting 页面模式与 Trellis 任务 PRD。

## 审查结论（按优先级）

1. **应修**：`generateCloneName` 未考虑 global profile 名称；`AssistantSubagentHubSection` 未传入 `globalProfiles` 给 `assistantHasSpawnableProfile`。
2. **建议**：移除 `ExtensionsPage` 未使用 import；克隆 `(copy)` 资源化；评估 `sheetGesturesEnabled = false` 产品意图。
3. **正面**：子代理全局化 UI 与扩展入口、Provider 标签与 i18n 改动整体与仓库模式一致；`SubagentProfileForm` 抽取利于扩展页复用。
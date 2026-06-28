# PRD：夜间审计 i18n 修复（P0/P1）

- **任务目录**：`.trellis/tasks/06-28-audit-i18n`
- **来源**：`.trellis/tasks/archive/2026-06/06-28-nightly-audit/audit-report.md`（Subagent C）
- **研究**：`.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-c-i18n.md`
- **基准**：`app/src/main/res/values/strings.xml`（**1395** 个 `<string name>`）
- **目标 locale**：`values-zh`、`values-zh-rTW`、`values-ja`、`values-ko-rKR`、`values-ru`（`search` 模块已对齐，**不在本任务范围**）

## 1. 背景与问题

Fork 大提交（如 `6dca48a1`、`8be9f419`）在 `values/` 新增大量 key，未统一经 **locale-tui** 同步到各 locale，导致：

| Locale | 相对 en 缺失 key 数 |
|--------|---------------------|
| `values-zh` | **15** |
| `values-zh-rTW` / `ja` / `ko-rKR` / `ru` | 各 **102** |

另有 **Kotlin 硬编码中文**（图生页、TTS/ASR 配置、捐赠页），非英文系统语言下仍显示中文或错误回退。

## 2. 范围

### 2.1 纳入（本 PRD）

| ID | 优先级 | 摘要 |
|----|--------|------|
| L-01 | **P0** | `ImgGenPage.kt` 硬编码 UI / `contentDescription` |
| L-02 | **P0** | 四 locale 缺失 **102** 个 key |
| L-03 | **P0** | `safe_mode_enter_app` 简中亦缺 |
| L-04 | **P1** | 简中缺 **15** 个 key |
| L-05 | **P1** | TTS StepFun 配置页 description / placeholder 硬编码 |
| L-06 | **P1** | ASR 热词 placeholder 硬编码 |
| L-07 | **P1** | 捐赠页「爱发电」标题硬编码 |

### 2.2 不纳入（审计 P2/P3，另立项）

- L-08：`values/strings.xml` en 默认串含中文示例（`743`、`750` 等）
- L-09：发布前 key 覆盖率门禁 / CI
- L-10：zh vs zh-rTW 用语差异（预期）
- L-11～L-13：Debug / Preview / assets 非生产 UI

## 3. 工作流约束（locale-tui）

实施时必须遵循 `.claude/skills/locale-tui-localization/SKILL.md`：

1. **新增** en 文案：优先 `uv run --directory locale-tui src/main.py add <key> "<English>" -m app`，由工具写入各 `values-*/strings.xml`。
2. **仅补翻译**（en key 已存在）：`locale-tui set` 或批量翻译流程；**禁止**只改 en 而不补 5 个目标 locale（除非用户明确要求 `--skip-translate`）。
3. **验收前**：对 `app` 模块跑 key diff（脚本或 `locale-tui list-keys -m app`），`values-zh` / `zh-rTW` / `ja` / `ko-rKR` / `ru` 相对 `values/` **缺失数为 0**（本任务定义的 7 条修复完成后）。

## 4. 需求明细

### L-01（P0）ImgGenPage 硬编码

**文件**：`app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenPage.kt`

**现状**：同文件已部分使用 `stringResource`（如 `:324`、`:845`、`:2593`），但回收站、分组、gpt-image-2 高级设置、列数 a11y 等仍为 `Text("…")` / `contentDescription = "…"`。`values/strings.xml` 仅有基础 `imggen_page_*`（约 `480-502`），**无**下列功能对应 key。

| 区域 | 行号（参考） | 硬编码示例 | 建议资源 key（en 源文案需英文） |
|------|--------------|------------|--------------------------------|
| 菜单 | 363 | 管理图像快捷消息 | `imggen_page_manage_quick_messages` |
| 菜单 | 449 | 回收站 | `imggen_page_trash` |
| 空间 | 560 | 空间 | `imggen_page_space`（或与现有命名对齐） |
| 快捷消息对话框 | 988, 1047, 1105, 1112 | 图像快捷消息 / 添加 / 标题 / 内容 | `imggen_page_quick_messages_*` |
| 分组 | 1489, 1572-1648, 1694, 1703, 1780, 1787 | 新建/重命名/删除分组、确定/取消、未分组等 | `imggen_page_collection_*`（含 `%1$s` 删除确认） |
| 提示词 | 2300, 2312 | 提示词 / 关闭 | `imggen_page_prompt_*` |
| 回收站 UI | 2360, 2371, 2413-2422, 2490, 2516-2525 | 回收站、清空、恢复、彻底删除及确认文案 | `imggen_page_trash_*` |
| gpt-image-2 设置 | 2627-2734, 2982 | 并发、流式预览、质量、格式、压缩、背景、审核、自定义尺寸等 | `imggen_page_gpt_image2_*` / `imggen_page_advanced_*` |
| a11y | **2808**, **2822** | 减少列数 / 增加列数 | `imggen_page_decrease_columns` / `imggen_page_increase_columns` |

**实施要点**：

1. 在 `values/strings.xml` 用 **locale-tui add** 批量新增上表 key（英文 value）；占位符与现有 `imggen_page_*` 风格一致（`%1$s` 用于分组名等）。
2. 将表中所有硬编码替换为 `stringResource(R.string.*)`；`contentDescription` 使用 `stringResource`。
3. 模型下拉中文案（如 `1152-1156` 行）若为用户可见，应拆为 string 或 `stringArray`（可列为 L-01 子项，至少 a11y 与对话框 **必须** 完成）。

**验收标准**：

- [ ] `ImgGenPage.kt` 生产路径无 `Text("…")` / `contentDescription = "…"` 含 CJK（可用 `grep` Han 字符在该文件应为 0 或仅剩注释/Preview）。
- [ ] 系统语言为 en / ja / zh-TW 时，回收站、分组、gpt-image-2 设置、列数按钮 a11y 显示对应 locale，**不出现简体硬编码**。
- [ ] 新增 key 已同步到 **zh、zh-rTW、ja、ko-rKR、ru**。

---

### L-02（P0）四 locale 缺失 102 key

**文件**：

- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/src/main/res/values-ja/strings.xml`
- `app/src/main/res/values-ko-rKR/strings.xml`
- `app/src/main/res/values-ru/strings.xml`

**缺失 key 完整列表**（相对 `values/strings.xml`，四文件相同）：

```
assistant_detail_subagent_desc
assistant_page_local_tools_logs_desc
assistant_page_local_tools_logs_title
assistant_page_tab_subagent
extension_subagent_profile_edit_title
extensions_page_subagent_profiles
extensions_page_subagent_profiles_desc
log_page_export_failed
log_page_export_logs
log_page_export_success
model_list_collapse_all
model_list_collapse_favorites
model_list_collapse_provider
model_list_collapse_provider_tabs
model_list_expand_all
model_list_expand_favorites
model_list_expand_provider
model_list_expand_provider_tabs
model_list_filter_by_tag
model_list_reorder_favorite
model_list_scroll_to_provider
model_list_search
safe_mode_enter_app
subagent_builtin_badge
subagent_can_spawn_no
subagent_can_spawn_yes
subagent_create_profile_title
subagent_delete_profile_desc
subagent_delete_profile_title
subagent_enable_blocked_desc
subagent_enable_blocked_title
subagent_enable_desc
subagent_enable_title
subagent_max_depth_desc
subagent_max_depth_disabled
subagent_max_depth_title
subagent_profile_allowed_paths
subagent_profile_can_spawn
subagent_profile_can_spawn_desc
subagent_profile_card_meta
subagent_profile_description
subagent_profile_description_desc
subagent_profile_display_name
subagent_profile_excluded_tools
subagent_profile_inherit_tools
subagent_profile_inherit_tools_desc
subagent_profile_local_tools
subagent_profile_max_steps
subagent_profile_max_tokens
subagent_profile_max_tokens_inherit
subagent_profile_mcp_servers
subagent_profile_memory
subagent_profile_model
subagent_profile_model_desc
subagent_profile_name
subagent_profile_name_desc
subagent_profile_path_add_hint
subagent_profile_reasoning
subagent_profile_skills
subagent_profile_stream
subagent_profile_summary_continuation_attempts
subagent_profile_summary_continuation_attempts_desc
subagent_profile_summary_min_length
subagent_profile_summary_min_length_desc
subagent_profile_summary_min_length_disabled
subagent_profile_summary_min_length_value
subagent_profile_system_prompt
subagent_profile_system_prompt_desc
subagent_profile_temperature
subagent_profile_temperature_desc
subagent_profile_tool_approval_overrides
subagent_profile_tool_approval_overrides_desc
subagent_profile_tool_auto_approve
subagent_profile_top_p
subagent_profile_workspace_access
subagent_profile_workspace_approval
subagent_profiles_empty
subagent_profiles_section
subagent_profiles_section_desc
subagent_step_text
subagent_step_thinking
subagent_tool_ui_ask_a
subagent_tool_ui_ask_btw_title
subagent_tool_ui_ask_btw_title_with_tokens
subagent_tool_ui_ask_q
subagent_tool_ui_collapse_details
subagent_tool_ui_expand_details
subagent_tool_ui_failed
subagent_tool_ui_reasoning_label
subagent_tool_ui_running
subagent_tool_ui_steps
subagent_tool_ui_token_count
subagent_tool_ui_tool_call
subagent_tool_ui_usage_cached
subagent_tool_ui_usage_completion
subagent_tool_ui_usage_prompt
subagent_workspace_access_full
subagent_workspace_access_none
subagent_workspace_access_read_only
subagent_workspace_approval_auto
subagent_workspace_approval_inherit
subagent_workspace_approval_override
```

**实施要点**：

1. en 定义已在 `values/strings.xml`；对四 locale **批量翻译补齐**（locale-tui 或经审核的翻译表），保持 `%1$s` / `%1$d` 与 en 一致。
2. 可与 L-04 合并流程：先补齐 `values-zh` 的 15 项，再以其为参考加速 zh-rTW（繁体转换 + 用语校对）。

**验收标准**：

- [ ] `values-zh-rTW`、`values-ja`、`values-ko-rKR`、`values-ru` 相对 en **缺失 key = 0**。
- [ ] 非 en 系统语言下：子代理设置/工具 UI、模型列表折叠/筛选 a11y、日志导出、安全模式「进入应用」**不出现英文回退**（在对应 locale 有翻译的前提下）。

---

### L-03（P0）`safe_mode_enter_app`

| 项 | 内容 |
|----|------|
| en 定义 | `app/src/main/res/values/strings.xml:662` — `safe_mode_enter_app` = `Enter App` |
| 调用处 | `app/src/main/java/me/rerere/rikkahub/ui/activity/SafeModeActivity.kt:119` — `stringResource(R.string.safe_mode_enter_app)` |
| 缺失 | `values-zh` 有 `safe_mode_*` 其他 8 项（约 `640-647`），**无**本 key；四 locale 102 项中亦包含 |

**修复**：在 `values-zh` 及 L-02 四 locale 中增加翻译（例：简中「进入应用」）。

**验收标准**：

- [ ] 简中系统语言下安全模式主按钮为中文，非 `Enter App` 字面回退。

---

### L-04（P1）简中缺失 15 key

**文件**：`app/src/main/res/values-zh/strings.xml`

**缺失 key**（与 en 对齐）：

```
model_list_collapse_all
model_list_collapse_favorites
model_list_collapse_provider
model_list_collapse_provider_tabs
model_list_expand_all
model_list_expand_favorites
model_list_expand_provider
model_list_expand_provider_tabs
model_list_filter_by_tag
model_list_reorder_favorite
model_list_scroll_to_provider
model_list_search
safe_mode_enter_app
subagent_step_text
subagent_step_thinking
```

**参考**：en 文案见 `values/strings.xml:526-537`、`662`、`1372-1373`。

**验收标准**：

- [ ] `values-zh` 相对 en 缺失 **0**（至少上述 15 项已存在且为合理简中）。
- [ ] 模型列表展开/折叠、按 tag 筛选、子代理步骤标签在简中 UI 正常。

---

### L-05（P1）TTS StepFun 硬编码

**文件**：`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/TTSProviderConfigure.kt`（`StepTTSConfiguration`，约 `1115-1399`）

| 行号 | 类型 | 硬编码内容 | 建议 key（en） |
|------|------|------------|----------------|
| 1122 | description | 从阶跃星辰官网获取密钥: platform… | `setting_tts_configure_step_api_key_desc`（可与 ASR 对称，en 不含仅中文品牌时可写 StepFun + URL） |
| 1130 | placeholder | 从阶跃星辰官网获取密钥 | `setting_tts_configure_step_api_key_placeholder` |
| 1276 | description | 音频编码格式… | `setting_tts_configure_step_format_desc` |
| 1314 | description | 语速… | `setting_tts_configure_step_speed_desc` |
| 1331 | description | 音量… | `setting_tts_configure_step_volume_desc` |
| 1351 | description | 采样率… | `setting_tts_configure_step_sample_rate_desc` |
| 1388 | description | 全局语境指令… | `setting_tts_configure_step_instruction_desc` |
| 1399 | placeholder | 例如: 语气温柔… | `setting_tts_configure_step_instruction_placeholder` |

**对照**：ASR 侧已有 `setting_asr_configure_step_*`（`values/strings.xml:743-754`）；TTS 应新增对称 `setting_tts_configure_step_*` 并经 locale-tui 写入各 locale。

**验收标准**：

- [ ] `StepTTSConfiguration` 内无中文 `description` / `placeholder` 硬编码。
- [ ] en 系统语言下 TTS Step 配置页为英文；简中/其他 locale 显示对应翻译。

---

### L-06（P1）ASR 热词 placeholder

| 项 | 内容 |
|----|------|
| 文件:行 | `ASRProviderConfigure.kt:529` |
| 现状 | `placeholder = { Text("热词1, 热词2, 热词3") }` |
| 已有资源 | `setting_asr_configure_step_hotwords_desc`（`:754`）为说明文案，**未用于 placeholder** |

**修复**：

1. 新增 `setting_asr_configure_step_hotwords_placeholder`（en 示例：`hotword1, hotword2, hotword3`）或复用 desc 的简短示例串（需在 PRD 实施时二选一，**推荐独立 placeholder key**）。
2. 改为 `placeholder = { Text(stringResource(R.string.setting_asr_configure_step_hotwords_placeholder)) }`。

**验收标准**：

- [ ] `:529` 无 CJK 硬编码；非中文 locale 下 placeholder 为对应语言或英文示例。

---

### L-07（P1）捐赠页「爱发电」

| 项 | 内容 |
|----|------|
| 文件:行 | `SettingDonatePage.kt:119` — `headlineContent = { Text("爱发电") }` |
| 已有 | `:118` `supportingContent` 已用 `R.string.donate_page_afdian_desc`；`values/strings.xml:412` |

**修复**：

1. 新增 `donate_page_afdian_title`（en 建议：`Afdian` 或产品官方英文名；简中可保留「爱发电」）。
2. `headlineContent = { Text(stringResource(R.string.donate_page_afdian_title)) }`。

**验收标准**：

- [ ] en / ja 等用户主标题非强制显示「爱发电」三字；简中可为「爱发电」。

## 5. 优先级与建议实施顺序

```
1. L-02 + L-03 + L-04（资源补齐，可并行 locale-tui 批量翻译）
2. L-01（依赖大量新 key，与 1 可部分并行：先 add en key 再改 Kotlin）
3. L-05、L-06、L-07（小范围 Kotlin + 少量新 key）
```

**P0 完成定义**：L-01、L-02、L-03 全部验收通过。  
**P1 完成定义**：L-04～L-07 全部验收通过。

## 6. 整体验收（任务关闭前）

- [ ] `app` 模块五 locale 与 `values/` key 集合 **一致**（1395 + L-01 新增 key 数）。
- [ ] `./gradlew :app:compileDebugKotlin` 通过。
- [ ] 建议手工抽测：en、zh、ja 各 1 次 — SafeMode 按钮、模型列表 a11y、图生回收站入口、捐赠页标题、Step TTS/ASR 热词框。
- [ ] 变更记录方式：优先 locale-tui；若手工改 xml，在 commit/任务笔记中列出受影响 `values-*` 文件。

## 7. 风险与依赖

- **翻译质量**：102 key 批量机翻需 spot-check 子代理/模型列表术语（`subagent_*`、`model_list_*`）。
- **ImgGen 工作量**：L-01 key 数量多，易漏行；必须以 `grep` Han 字符清零为门禁。
- **父任务**：若存在 `06-28-nightly-audit` 子任务树，本任务仅覆盖 i18n P0/P1，不解决 L-08 及 a11y `contentDescription = null`（属 Subagent B，非本 PRD）。

## 8. 参考索引

- 审计总表：`audit-report.md` § Subagent C（L-01～L-07）
- 研究细节：`research/subagent-c-i18n.md`
- 技能：`.claude/skills/locale-tui-localization/SKILL.md`
# i18n 综合审计报告（Subagent C）

- **Task**: `.trellis/tasks/06-28-nightly-audit`
- **Date**: 2026-06-28
- **Scope**: app + search 模块 `strings.xml`；`app/src/main/java` Kotlin 硬编码；fork 区间 `upstream/master..HEAD`

---

## 审查范围（路径 glob）

| 区域 | Glob |
|------|------|
| App 默认/多语言 | `app/src/main/res/values/strings.xml`, `app/src/main/res/values-{zh,zh-rTW,ja,ko-rKR,ru}/strings.xml` |
| Search 模块 | `search/src/main/res/values*/strings.xml`（5 keys × 6 locale，键集一致） |
| 其他模块 | `ai/`, `document/`, `web/`, `speech/`, `highlight/`, `common/`：**无** `values*/strings.xml` |
| Kotlin 硬编码 | `app/src/main/java/**/*.kt`（`Text("…")` / `contentDescription` / placeholder） |

**App 基准键数量（仅 `<string name>`）**: `values/strings.xml` **1395** 个 key。

---

## Fork 期间 i18n 相关提交（`upstream/master..HEAD`）

### `--grep=i18n`（不区分大小写）

```
8be9f419 fix: resolve review findings — coroutine race, CAS state, remember deps, i18n gaps, a11y
6dca48a1 chore: milestone commit — subagent global config, ext sections, provider tags, model filtering, i18n, multi-segment think-tag
4854e280 i18n: 添加快捷消息、安全模式等新字符串的多语言翻译
c81985b9 i18n: 本地化 UpdateCard
2819c9c4 feat(i18n): 添加多语言支持的提示页角色字符串
fd1d39fe chore: 移除i18n模块
3e40b881 docs: claude.md for i18n tui
95d4ca1c feat: i18n tui
7da62797 feat: i18n tui
4d38629f feat: i18n cli
a430069c feat(i18n): 为翻译功能添加国际化支持并优化相关UI
bb730f21 feat(i18n): 完善i18n
ec4cb3ad chore: search模块i18n
108d20ec chore: tts i18n
0dbf4436 i18n: 国际化设置提供商页面的网络代理相关文本
f1fa941b feat: add i18n support for chat input and improve UI
```

### `--grep=locale-tui`

```
85f7c357 test(locale-tui): 添加针对AI翻译质量的集成测试并改进系统提示词
e8a7e3cd docs: add locale-tui tool usage note
```

### 新增 locale 文件（`--diff-filter=A`）

- `app/src/main/res/values-ja.xml`, `values-zh-rTW`, `values-ko-rKR`, `values-ru`, `values-pt-rBR`（及部分 search 对应文件）

### 近期直接改 `strings.xml` 的 fork 提交（节选）

`8be9f419`, `6dca48a1`, `2884ed55`, `af64a774`, `36f4e7d3`, `65083839`, `77fe1c39`, `7f04141b`, …

---

## 键集同步摘要（相对 `values/`）

| Locale | Key 数 | 相对 en 缺失 |
|--------|--------|----------------|
| `values-zh` | 1380 | **15**（含 `model_list_*` 12 项、`safe_mode_enter_app`、`subagent_step_*` 等） |
| `values-zh-rTW` | 1293 | **102**（整块 subagent + model_list + log export + safe_mode_enter_app 等） |
| `values-ja` | 1293 | **102**（同上） |
| `values-ko-rKR` | 1293 | **102**（同上） |
| `values-ru` | 1293 | **102**（同上） |
| `search/*` | 5/5 | **0**（各 locale 对齐） |

**Plurals**: app 各 locale **无** `<plurals>`。  
**String-array**: 仅 `provider_suggested_tags`（en/zh/zh-rTW/ja/ko/ru 均存在，未做逐项内容 diff）。

**占位符 `%1$s` / `%1$d`**: 对 `values-zh`、`values-zh-rTW` 与 en 逐 key 比对，**未发现**占位符序列不一致（在已存在 key 的子集上）。

---

## 发现（按严重度）

### P0 — 用户可见语言回退为英文（非 en 系统语言）

1. **子代理整块 UI 未翻译（ja / ko-rKR / ru / zh-rTW）**  
   - **证据**: 相对 en 缺失 **102** 个 key，前缀集中在 `subagent_*`、`extensions_page_subagent_*`、`assistant_*subagent*`、`subagent_tool_ui_*`。  
   - **代码引用**: `app/src/main/java/me/rerere/rikkahub/ui/activity/SafeModeActivity.kt:119` 使用 `R.string.safe_mode_enter_app`（该 key 在 zh 亦缺，见下）。  
   - **根因线索**: `6dca48a1` / `36f4e7d3` / `af64a774` 大量新增 en key，未批量走 `locale-tui` 同步到非 en locale。

2. **`safe_mode_enter_app` 在简中亦缺失**  
   - **定义**: `app/src/main/res/values/strings.xml:662`  
   - **zh 有** `safe_mode_title` 等 8 项（`values-zh/strings.xml:640-647`），**无** `safe_mode_enter_app`。  
   - **UI**: `SafeModeActivity.kt:119` — 非英文用户会看到 “Enter App”。

3. **`ImgGenPage.kt` 大量中文硬编码（生产路径）**  
   - **文件**: `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenPage.kt`  
   - **示例行**: `363`（管理图像快捷消息）, `449`（回收站）, `1572-1648`（分组对话框）, `2413-2525`（回收站/删除确认）, `2627-2734`（gpt-image-2 设置项）, `2808`/`2822`（`contentDescription` 增减列数）。  
   - **对比**: 同文件已部分使用 `stringResource`（如 `:324`, `:845`, `:2593`），但扩展功能（分组、回收站、高级参数）未抽离；`values/strings.xml` 仅有基础 `imggen_page_*`（约 `480-502`），**无**回收站/分组/流式预览等 key。  
   - **影响**: 英文/繁体/日语等 locale 下仍显示简体中文。

### P1 — 简中不完整 / 设置页漏翻

4. **简中缺 15 个 en key（含模型列表 a11y/折叠）**  
   - 缺失列表含: `model_list_collapse_all`, `model_list_expand_all`, `model_list_search`, `model_list_filter_by_tag`, `subagent_step_text`, `subagent_step_thinking`, `safe_mode_enter_app` 等。  
   - **对比**: `values-zh` 含 `log_page_export_*`（`1342-1344`），说明部分 fork 功能已翻 zh，但 model_list / subagent_step / safe_mode_enter_app 遗漏。

5. **TTS StepFun 配置页中文硬编码**  
   - `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/TTSProviderConfigure.kt:1122-1399` — `description` / `placeholder` 为中文（密钥、语速、采样率、语境指令等）。  
   - en `strings.xml` 中 ASR Step 相关 **已有** 英文 key（如 `setting_asr_configure_step_*`，`743-754`），TTS 侧未对称抽离。

6. **ASR 热词 placeholder 硬编码**  
   - `ASRProviderConfigure.kt:529` — `placeholder = { Text("热词1, 热词2, 热词3") }`；en 有 `setting_asr_configure_step_hotwords_desc`（`754`）但未用于此处 placeholder。

7. **捐赠页标题硬编码**  
   - `SettingDonatePage.kt:119` — `Text("爱发电")`；同项 `supportingContent` 已用 `R.string.donate_page_afdian_desc`（`:118`）。

### P2 — 默认 en 资源含 CJK / 规范与工具链

8. **`values/strings.xml`（en 默认）含中文/日文展示文案**  
   - `507-511`: `language_japanese` / `language_simplified_chinese` / `language_traditional_chinese` 使用原生语言名（可接受为“语言自名”）。  
   - `743`: `setting_asr_configure_step_api_key_desc` 含 **「阶跃星辰」**。  
   - `750`: `setting_asr_configure_step_itn_desc` 含 **「三百 → 300」** 示例。  
   - **结论**: 非“整页中文”，但 en 默认串混入中文示例，与“en 为纯英文 UI 源”惯例不一致；上一轮仅查 4 key 的缺口已扩大为上述 5 处 Han 字符命中。

9. **locale-tui 工作流与近期大块 en 提交不同步**  
   - 技能/文档要求新 key 走 `uv run --directory locale-tui src/main.py add …`（`.agents/skills/locale-tui-localization/SKILL.md`）。  
   - `6dca48a1`、`8be9f419` 等直接改 xml，导致 ja/ko/ru/zh-TW **102 key 空洞**；zh 部分补齐（如 log export）说明存在**手工补翻**，无统一门禁。

10. **zh vs zh-rTW**  
    - 共有 key **1292** 个；**1096** 个 value 不同（繁体转换/用语差异，属预期）。  
    - **风险**: 102 个 key 在 zh-TW **整段缺失**时，繁体用户比简体用户更早看到英文回退（P0 叠加）。

### P3 — 低优先级 / 非生产

11. **Debug / Preview / 文档中的中文 `Text`**  
    - `DebugPage.kt:203-242`, `DebugVM.kt:84`, `Tag.kt` / `CardGroup.kt` Preview, `permission/README.md`, `RememberPermissionState.kt` 注释示例。  
12. **`assets/html/mark.html:305`** — WebView 不支持 ES Modules 的 `alert` 中文（非 Compose 资源链）。  
13. **`baselineprofile` / 分词字典** — 中文为测试或 NLP 数据，非 UI i18n 缺陷。

---

## locale-tui 一致性（摘要）

| 项 | 状态 |
|----|------|
| 仓库内 `locale-tui/` CLI + skill 文档 | 存在且与 AGENTS.md 一致 |
| fork 大功能（subagent、model_list 扩展、log export） | **部分**仅 en + zh，未全 locale |
| `85f7c357` 翻译质量测试 | 有，但不替代发布前 key 覆盖率检查 |

**建议流程缺口（记录用，非本代理实施）**: 新增 en key 后应对 `values-zh`、`values-zh-rTW`、`values-ja`、`values-ko-rKR`、`values-ru` 跑 key diff 或 `locale-tui list-keys` 对比。

---

## 明早 Top 5

1. **P0** — 用 `locale-tui`（或批量翻译）补齐 **102** 个 subagent/model_list/log/safe_mode 相关 key 到 **ja / ko-rKR / ru / zh-rTW**（与 `6dca48a1` 对齐）。  
2. **P0** — 将 **`ImgGenPage.kt`** 回收站/分组/gpt-image-2 设置/`contentDescription` 全部迁入 `strings.xml` 并 `stringResource`（当前最大单文件硬编码面）。  
3. **P0** — 补 **`safe_mode_enter_app`** 到 `values-zh`（及缺失的非 en locale）。  
4. **P1** — 简中 **15** 个缺失 key（`model_list_*`、`subagent_step_*`）与 en 对齐。  
5. **P1** — **TTSProviderConfigure.kt** / **ASRProviderConfigure.kt** placeholder & description 与 en `setting_*_configure_step_*` 资源对称化；清理 en `strings.xml:743,750` 中文示例或迁至 zh-only 说明串。

---

*审计方法: PowerShell XML key 集合对比、`grep` Han 字符、`git log upstream/master..HEAD`；未修改除本报告外的任何仓库文件。*
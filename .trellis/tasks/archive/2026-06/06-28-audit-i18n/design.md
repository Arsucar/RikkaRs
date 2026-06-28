# Design：夜间审计 i18n 修复（P0/P1）

## 1. 现状摘要（基于源码核对）

| 维度 | 现状 |
|------|------|
| 基准 | `app/src/main/res/values/strings.xml` 约 **1406** 行，en key 已含 `model_list_*`（526–537）、`safe_mode_enter_app`（662）、`subagent_*` 等 |
| `values-zh` | **无** `model_list_collapse_all`、`safe_mode_enter_app`、`subagent_step_text` 等（grep 0 命中）→ 缺 **15** key |
| 四 locale | `zh-rTW` / `ja` / `ko-rKR` / `ru` 相对 en 各缺 **102** key（PRD 列表，en 侧已定义） |
| `SafeModeActivity.kt:119` | 已用 `stringResource(R.string.safe_mode_enter_app)`，问题在 **资源缺失** 非 Kotlin |
| `ImgGenPage.kt` | 约 **100+** 处 Han 字符（含 UI、`contentDescription`、toast、设置项）；部分已用 `stringResource`（如 `R.string.menu`、`:2427` cancel） |
| TTS `StepTTSConfiguration` | `:1122`、`:1130`、`:1276`、`:1314`、`:1331`、`:1351`、`:1388`、`:1399` 等为中文硬编码；模型/音色下拉仍为中文描述（PRD 标为可选子项） |
| ASR | `:529` `placeholder = { Text("热词1, 热词2, 热词3") }`；`setting_asr_configure_step_hotwords_desc` 已在 en（754） |
| 捐赠 | `:119` `Text("爱发电")`；`:118` 已用 `donate_page_afdian_desc` |

**本任务边界**：不处理 `ImgGenVM.kt` / `ImgGenSession.kt` 内错误文案（PRD 未纳入）；L-01 以 `ImgGenPage.kt` 生产 UI 路径为主。

## 2. 总体策略

```
阶段 A：资源对齐（locale-tui 优先，不手改 5 份 xml 抄键）
阶段 B：Kotlin 硬编码 → stringResource（依赖 A 中已存在或新 add 的 key）
阶段 C：门禁验证（key diff + Han grep + compile）
```

**原则**：

1. **先 bulk 补翻译，再改 Kotlin**（L-02/03/04 与 L-05/06/07 的新 key 可先 `add`）。
2. **新增文案**：英文 value + `locale-tui add … -m app`（默认自动翻译 5 个目标 locale）。
3. **仅补已有 en key**：不用 `add`（会冲突）；用 **TUI 批量翻译缺失** 或 `locale-tui set` 单条覆盖。
4. **禁止**只改 `values/strings.xml` 而不补 `values-zh` / `zh-rTW` / `ja` / `ko-rKR` / `ru`（除非用户明确 `--skip-translate`）。

## 3. locale-tui 工作流（分场景）

### 3.1 环境

- 工作目录：仓库根或 `locale-tui/`
- 配置：`locale-tui/config.yml` 已声明 `app` 模块与 6 种 `values-*`
- AI 翻译：需 `locale-tui/.env` 中 `OPENAI_API_KEY`（及可选 `OPENAI_BASE_URL`）；可先 `uv run --directory locale-tui src/main.py test-connection`

### 3.2 场景一：L-02 / L-03 / L-04（en key 已存在，缺翻译）

CLI **`set` 只写单语言**，102 key 不宜逐条手敲。推荐：

1. 启动 TUI：`uv run --directory locale-tui src/main.py`
2. 选择模块 **app**
3. 按 **`m`** 开启 **Missing** 过滤
4. 按 **`t`** 执行 **Translate missing**（对当前缺失条目按 en 源批量机翻并写回各 `values-*/strings.xml`）
5. 按 **`s`** 保存（若 TUI 有未自动落盘变更）
6. Spot-check：`subagent_*`、`model_list_*` 术语；`safe_mode_enter_app` 简中建议「进入应用」

**简中 15 key 加速**（可选，在机翻后）：

```bash
uv run --directory locale-tui src/main.py set safe_mode_enter_app "进入应用" -l values-zh -m app
# 其余 model_list_* / subagent_step_* 可对明显机翻不当项用 set 覆盖
```

**zh-rTW**：机翻后可对照 `values-zh` 做繁体/用语校对（L-10 差异属预期，本任务只求 key 齐全 + 可读）。

### 3.3 场景二：L-01 / L-05 / L-06 / L-07（新 key）

对每个新 key（英文 value）：

```bash
uv run --directory locale-tui src/main.py add <key> "<English>" -m app
```

批量实施时可写临时 shell 脚本逐条 `add`，或 `--skip-translate` 先只写 en + 统一再跑 TUI **`t`**。

**L-01 key 命名**（与现有 `imggen_page_*` 对齐，`480-502` 已有 cancel/confirm/delete 等，**复用** `imggen_page_cancel` / `imggen_page_confirm` / `imggen_page_delete` 等，避免重复建键）：

| 前缀 | 用途 |
|------|------|
| `imggen_page_*` | 图生页菜单、空间、回收站、分组、快捷消息、gpt-image-2 设置、a11y |
| `imggen_page_collection_delete_confirm` | 含 `%1$s` 分组名（对 `:1635`） |
| `imggen_page_decrease_columns` / `imggen_page_increase_columns` | `:2808`、`:2822` |

**L-05**（对称 ASR）：`setting_tts_configure_step_api_key_desc`、`…_placeholder`、`…_format_desc`、`…_speed_desc`、`…_volume_desc`、`…_sample_rate_desc`、`…_instruction_desc`、`…_instruction_placeholder`（en 参考 `setting_asr_configure_step_*` 风格，743+）

**L-06**：`setting_asr_configure_step_hotwords_placeholder`（en：`hotword1, hotword2, hotword3`）

**L-07**：`donate_page_afdian_title`（en：`Afdian`；简中机翻或 `set` 为「爱发电」）

### 3.4 场景三：验收用 key 集合一致性

- 基线：从 `values/strings.xml` 解析全部 `name`
- 对比：`values-zh`、`values-zh-rTW`、`values-ja`、`values-ko-rKR`、`values-ru` 各文件 key 集合
- 辅助：`uv run --directory locale-tui src/main.py list-keys -m app`（仅列 en，不替代 diff）
- 推荐一次性脚本（实施时写入 `implement.md` 命令区）：PowerShell 比较各 locale 与 en 的 key 差集，**差集为空** 为门禁

## 4. Kotlin 改造要点

### 4.1 L-01 `ImgGenPage.kt`

- 模式：`Text("…")` → `Text(stringResource(R.string.…))`
- `contentDescription = "减少列数"` → `stringResource(R.string.imggen_page_decrease_columns)`
- 带插值：`stringResource(R.string.imggen_page_collection_delete_confirm, target.name)`
- **复用**已有 `R.string.imggen_page_cancel` 等替换「取消」类硬编码（`:1595` 等）
- 枚举显示（网格/分组）：新增 `imggen_page_display_mode_grid` / `…_grouped` 或 stringArray
- Toast/搜索框（`:226`、`:280` 等）：PRD 表未逐条列出但 Han grep 门禁会命中 → 实施清单中单独列 key（与 PRD「生产路径无 CJK」一致）

**不在本阶段**：`ImageQualityOption.label` 等若来自 data class 中文 label（`:2679`），需后续将 label 改为 string res 或 `@StringRes`（设计记入 L-01 风险）。

### 4.2 L-05 `TTSProviderConfigure.kt`

`StepTTSConfiguration` 内 `FormItem` 的 `description` / `placeholder` 改为 `stringResource`；英文 label「Response Format」「Volume」等可顺带改为已有 `setting_tts_page_*` 或新 key。

### 4.3 L-06 / L-07

单行替换，见 PRD。

## 5. Key 命名与占位符约定

- **蛇形**、模块前缀：`imggen_page_`、`setting_tts_configure_step_`、`donate_page_`、`model_list_`、`subagent_`（与 en 现有一致）
- 占位符与 en **完全一致**：`%1$s`、`%1$d`；禁止在翻译中增删占位符
- **不新建**与 en 同义重复 key（实施前 `list-keys` / grep `name="` 防冲突）
- `locale-tui add` 若 key 已存在会失败 → 改用 `set` 或只补目标 locale

## 6. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 102 key 机翻质量 | Missing 翻译后人工 spot-check 子代理/模型列表；关键项 `set` 覆盖 |
| L-01 漏行 | 以 `ImgGenPage.kt` Han grep 为硬门禁；分区块提交（菜单→分组→回收站→设置→a11y） |
| `add` 与已有 key 冲突 | 实施前对拟新增 key 在 `values/strings.xml` grep |
| locale-tui 无 API key | 仅用 `set` + 手工翻译 xml（最后手段，须在任务笔记列出文件） |
| TTS 模型/音色列表中文 | PRD 可选；P1 至少完成 description/placeholder 八处 |
| 范围蔓延到 ImgGenVM | 明确不在 PRD；grep 时限定文件路径 |

## 7. 数据流（实施后）

```
values/strings.xml (en 源)
    ↓ locale-tui add / TUI translate missing
values-{zh,zh-rTW,ja,ko-rKR,ru}/strings.xml
    ↓ stringResource(R.string.*)
Compose UI / contentDescription
    ↓ 系统 Locale
用户可见文案
```

## 8. 兼容与发布

- 纯资源 + UI 文案，无 DB / API 变更
- 回滚：git revert 对应 commit（见 `implement.md`）
- Fork 分支：`release/rikka-arsucar` 常规提交流程
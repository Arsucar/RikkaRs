# 导入 SillyTavern 提示词预设 JSON

GitHub issue: #188

## Goal

`PresetSerializer.import` 增加 SillyTavern（酒馆）「提示词预设」JSON 回退解析，把 `prompts[]` +
`prompt_order[]` 映射为 `Preset.entries`（`PresetEntry.Custom`），让 ST 迁移用户在预设页一步导入。

## Background

- ST 角色卡（`AssistantImporter`）与 ST 世界书（`LorebookSerializer.tryImportSillyTavern`）已支持，ST
  提示词预设完全未实现。
- 今天丢一份 ST 预设 JSON 进预设页，`PresetSerializer.import`（`ExportSerializer.kt:117-123`）只试自有格式，
  直接抛 `Unsupported format`，用户看到「导入失败：Unsupported format」。
- #182 已落地 `Preset.entries: List<PresetEntry>`，`PresetEntry.Custom` 的
  content/name/role/position/injectDepth/enabled/order 正好覆盖 ST 条目核心字段，无需新增实体。

## Requirements

### R1 ST 回退解析

- `PresetSerializer.import` 顺序：`tryImportNative` → `tryImportSillyTavernPreset` → 抛 `Unsupported format`，
  照 `LorebookSerializer.import`（`ExportSerializer.kt:153-162`）的写法。
- ST 解析必须是 Context-free 纯函数 `tryImportSillyTavernPreset(json, fileName)`，可 JVM 单测。
- 新增 private DTO：`SillyTavernPreset` / `SillyTavernPrompt` / `SillyTavernPromptOrder` /
  `SillyTavernPromptOrderEntry`，与现有 `SillyTavernLorebook` 同风格（`@Serializable`、字段全带默认值、
  依赖 `ignoreUnknownKeys`）。ST 的 snake_case 字段需 `@SerialName`（现有 lorebook DTO 恰好无需，故本次是首次引入）。
- 识别门槛：JSON 必须含非空 `prompts[]`，否则返回 null（落到 `Unsupported format`）。

### R2 条目映射

- 顺序与启用由 `prompt_order[].order[]` 决定；缺失 `prompt_order` 时回退 `prompts[]` 原序。
- 每个可导入 `prompts[i]` → `PresetEntry.Custom`：
  - `content` → `content`
  - `name`，空则回退 `identifier` → `name`
  - `role` → `MessageRole`（中间值，缺失/非法默认 `SYSTEM`，仅用于 R3 位置判定）；最终条目 role 归一为 `USER`/`ASSISTANT`（`PresetEntry` 契约只承认这两者，SYSTEM 不落条目）
  - `injection_position` / `injection_depth` → `position` / `injectDepth`（见 R3）
  - `enabled` && `prompt_order[].order[].enabled` → `enabled`（任一 false 即禁用）
  - 按最终顺序赋 `order = 0..n`
- `marker = true` 的系统占位条目（`chatHistory` / `worldInfoBefore` / `charDescription` 等）跳过，不生成条目。
- `content` 为空且非 marker 的条目跳过。
- `identifier` 重复保留首个。
- 产出 `Preset(id = Uuid.random(), name = fileName ?: 时间戳, entries = ..., entriesVersion = PRESET_ENTRIES_VERSION)`。
- 导入为追加（`PromptPage` 已有 `currentPresets + imported`），不覆盖同名预设。

### R3 位置映射（不复用 `mapSillyTavernPosition`）

`LorebookSerializer.mapSillyTavernPosition`（`ExportSerializer.kt:213-222`）映射的是 ST **世界书**
`position` 枚举（0=before system、1=after system、2/3=top of chat、4=@depth）。ST **提示词预设**的
`injection_position` 是另一套语义（0=relative 按 `prompt_order` 就地拼接、1=absolute 按深度插入）。
复用同一函数会让 `1` 同时表示 after-system 和 at-depth，语义冲突。→ 新写独立映射函数。

- `injection_position == 1`（absolute）→ `AT_DEPTH`，`injectDepth = injection_depth`（缺省 4）
- `injection_position == 0`（relative）或非法/缺失 → 按 role 分流：
  - `role == SYSTEM` → `AFTER_SYSTEM_PROMPT`
  - `role == USER` / `ASSISTANT` → `TOP_OF_CHAT`

  理由：ST relative 条目在 chat history 之前按序拼接；system 条目归入 system 块，user/assistant 条目若也塞进
  system 块会丢失角色语义，`TOP_OF_CHAT` 更接近 ST 行为。

### R4 未映射内容提示

ST 顶层格式设置（`impersonation_prompt` / `new_chat_prompt` / `new_group_chat_prompt` /
`new_example_chat_prompt` / `continue_nudge_prompt` / `scenario_format` / `personality_format` /
`group_nudge_prompt` / `wi_format`）在 RikkaHub 无落点，v1 不映射。

现有导入链路只有固定成功 Toast + 失败 `error.message`（`PromptPage.kt:209-218`），无法承载警告文案。
→ 把「未导入的 ST 顶层设置名 + 跳过的 marker 条目数」写入 `Preset.description`，用户在预设列表/详情页可见，
不改 UI 链路，且可纯 JVM 单测。

`character_id`、`forbid_overrides` 忽略。

### R5 附带修复

`PresetSerializer.tryImportNative`（`ExportSerializer.kt:125-136`）只 re-random `Preset.id`，未重置
`entries[].id`（`LorebookSerializer.tryImportNative` 是重置了的）。同一份预设导入两次会产生重复条目 id。
本任务顺手修掉。

## Non-Goals

- 不映射 ST 顶层格式设置到功能行为（仅在 description 提示）。
- 不新增导入入口、不改 `ExportHooks` / `rememberImporter`。
- 不引入 ST 预设多版本 parser 策略（`AssistantImporter` 那套不复用）。
- 不做本地化字符串新增（description 提示文案走英文，符合 CLAUDE.md「未明确要求本地化时优先实现功能」）。

## Acceptance Criteria

- [ ] AC1 选择一份 ST 预设 JSON 导入后，预设列表出现新预设，条目带正确的
      注入位置 / 深度 / 角色 / 顺序 / 启用状态，可在 `PresetDetailPage` 查看、编辑、拖拽排序。
- [ ] AC2 含 `marker = true` 条目的预设，marker 条目被跳过不产生注入，其余条目正确导入。
- [ ] AC3 非 ST 预设 / 损坏 JSON / 无可导入条目时，得到清晰失败提示而非崩溃。
- [ ] AC4 存在未映射的 ST 顶层设置时，导入产物的 description 列出这些设置名。
- [ ] AC5 自有格式导入不回归（`tryImportNative` 优先），且自有格式导入现在会重置 `entries[].id`。

## Test Plan

新建 `app/src/test/java/me/rerere/rikkahub/data/export/SillyTavernPresetImportTest.kt`，
JUnit4 + `org.junit.Assert`，内联 `"""` JSON fixture（参照 `ProviderImportDecoderTest.kt` /
`PresetEntrySerializationTest.kt`）。覆盖：

- 完整 ST 预设 → 条目数、顺序、position、injectDepth、role、enabled
- `prompt_order` 决定顺序与 enabled（与 `prompts[].enabled` 取 AND）
- 缺 `prompt_order` → 回退原序
- `prompt_order` 引用不存在 identifier → 跳过
- marker 条目跳过
- 空 content 跳过
- identifier 重复去重
- `injection_position` 0/1/非法 × role 的位置映射矩阵
- 非法/缺失 role → 中间值为 SYSTEM（决定位置），条目 role 归一为 USER
- 无 `prompts` / 空 `prompts` / 全 marker → null
- 顶层设置写入 description
- 自有格式 JSON 不被 ST 分支吃掉

`import(context, uri)` 需要 Android Context，纯 JVM 测试只覆盖 `tryImportSillyTavernPreset` 与映射函数。

## Notes

- 持久化无需改动：`PromptPage.kt:213` 的 `onUpdatePresets` → `PromptVM.updateSettings` → `settingsStore`。
- `.json` 扩展名门禁已在 `ExportHooks.ImporterState.handleUri` 完成。
- `PresetEntry.Custom.role` 默认是 `USER`，不是 SYSTEM——映射时必须显式传 role。

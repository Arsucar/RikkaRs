# Issue 61 skill management tool

## Goal

新增 `skill_tool`（写入型 AI 工具），支持 AI 创建和更新 Skill，可选 `global`（全局）或 `private`（仅当前助手）作用域，补齐 Skill 系统缺失的写入能力。

来源：GitHub issue #61。

## Requirements

- 新建 `data/ai/tools/SkillManagementTools.kt`，导出 `buildSkillManagementTools(assistantId: Uuid, skillManager: SkillManager, autoEnable): List<Tool>`。
- 工具 `skill_tool` 参数：
  - `action`: `create` | `update`（必填）
  - `scope`: `global` | `private`（必填）
  - `name`: string（必填，skill 目录名）
  - `content`: string（必填，SKILL.md 内容，含 YAML frontmatter）
  - `files`: object（可选，`{relativePath: content}` 附加文件）
- 作用域映射：
  - `global` → `SkillManager.saveSkill` / `saveSkillFilesAtomically`
  - `private` → `SkillManager.saveAssistantSkill` / `saveAssistantSkillFilesAtomically`（ownerAssistantId = assistantId）
- 校验：
  - `name` 复用 `SkillPaths.resolveSkillDir`（拒绝 blank / `.` / `..` / 含 `/` `\`）——用 SkillManager 现有落盘方法即可，其内部已调用 resolveSkillDir。
  - `content` 必须含有效 frontmatter（`name` 和 `description` 必填），用 `SkillFrontmatterParser.parse` 校验。
  - `action=create`：目标 skill 已存在 → 报错提示用 update。
  - `action=update`：目标 skill 不存在 → 报错提示先 create。
- `needsApproval = true`（写入型工具需用户确认）。
- 落盘后确认 `skillManager.invalidateListCache()`（SkillManager 落盘方法已内部调用）。
- `action=create` 后可选把 skill name 加入 `Assistant.enabledSkills`，否则 `use_skill` 看不到新建 skill（`autoEnable` 参数，默认 true）。
- 在 `ChatService.kt` 工具装配区与 `createSkillTools` 并列登记。

## Constraints

- 不修改 `use_skill`（`createSkillTools`）的只读语义。
- 通过 `SkillManager` API 落盘，不直接写文件系统（保证缓存失效、安全校验、原子多文件）。
- 附加 `files` 与 SKILL.md 一起原子写入（`saveSkillFilesAtomically` 接收 `Map<path, content>`，SKILL.md 键固定为 `"SKILL.md"`）。

## Acceptance Criteria

- [ ] `skill_tool(action=create, scope=private, ...)` 在 `assistant_skills/{assistantId}/{name}/` 创建 skill。
- [ ] `skill_tool(action=create, scope=global, ...)` 在 `skills/{name}/` 创建 skill。
- [ ] `action=update` 覆盖已有 skill；对不存在目标报错。
- [ ] `action=create` 对已存在目标报错。
- [ ] frontmatter 缺 name/description 时报错。
- [ ] autoEnable 时新建 skill 自动进入当前助手 `enabledSkills`。
- [ ] `:app:compileDebugKotlin` 编译通过。

## Notes

- 参考文件：`data/ai/tools/SkillsTools.kt`、`data/ai/tools/MemoryTableTools.kt`（scope→枚举模式）、`data/files/SkillManager.kt`、`data/files/SkillPaths.kt`、`data/files/SkillLookup.kt`（frontmatter 校验）、`service/ChatService.kt`（~L759 装配区）。
- autoEnable 需要 `SettingsStore` 更新 assistant.enabledSkills；SkillManager 持有 settingsStore，可复用其 update 模式，或在工具内注入 settingsStore 回调。

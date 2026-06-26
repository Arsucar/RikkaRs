# PRD: 实现 / 命令触发 skill 调用功能

## 需求背景

目前 RikkaHub 中 `@` 符号已实现工作区文件引用功能（通过 `WorkspaceCompletionProvider`）。用户希望 `/` 符号也能触发 skill 列表，选择 skill 后将其 `SKILL.md` 内容直接插入输入框。

## 功能描述

在聊天输入框中输入 `/` 时，弹出 skill 自动完成列表，显示当前助手已启用的 skills。用户选择某个 skill 后，将其 `SKILL.md` 的 body 内容插入到输入框中。

## 用户故事

1. 作为用户，我希望在聊天输入框中输入 `/` 时，能看到可用的 skill 列表
2. 作为用户，我希望选择某个 skill 后，其内容能插入到输入框中
3. 作为用户，我希望 skill 列表能显示 skill 名称和描述，方便我选择

## 验收标准

1. [ ] 输入 `/` 后弹出 skill 自动完成列表
2. [ ] 列表显示当前助手已启用的 skills（名称 + 描述）
3. [ ] 支持模糊搜索过滤 skill
4. [ ] 选择 skill 后，其 `SKILL.md` body 内容插入到输入框
5. [ ] 插入后 `/` 符号被替换为 skill 内容
6. [ ] 与现有的 `@` 自动完成互不干扰

## 技术约束

- 复用现有的 `ChatCompletionProvider` 接口
- 参考 `WorkspaceCompletionProvider` 实现方式
- 使用 `SkillManager` 获取 skill 列表和内容
- 图标使用 `HugeIcons` 中的合适图标

## 范围

### 包含
- `/` 触发 skill 自动完成列表
- skill 内容插入到输入框

### 不包含
- skill 内容预览/折叠
- skill 编辑功能
- 自定义命令功能（后续迭代）

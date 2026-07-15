# 本轮新增 UI 简体中文本地化

## Goal

确保本轮新增或修改的所有 UI 文案均通过 Android string resources 提供，并至少在简体中文 `values-zh` 中使用真实中文翻译，不再以英语 fallback 作为简体中文交付。

## Requirements

- 审计提交 `1dba4b9b` 与 `5bc0294d` 涉及的 UI 文件和新增 `R.string` 键。
- UI 中不得残留本轮新增的硬编码用户可见文案。
- 默认 `values/strings.xml` 保持英文源文案；`values-zh/strings.xml` 为自然、准确的简体中文。
- 保持格式化占位符、转义、换行和资源键集合一致。
- 优先使用 `locale-tui` 更新资源；人工复核产品术语和机器翻译结果。
- 不改变业务逻辑、Room schema、导航或交互行为。

## Acceptance Criteria

- [ ] 本轮新增 UI 使用的所有 string key 在默认资源和 `values-zh` 中均存在。
- [ ] `values-zh` 对应值不再与英文源文案相同，专有名词除外。
- [ ] 所有格式化占位符在英文与简体中文之间完全一致。
- [ ] 本轮新增 UI 源码中无未资源化的用户可见硬编码字符串。
- [ ] `:app:compileDebugKotlin`、资源处理和 `git diff --check` 通过。
- [ ] 有可用设备时重新安装 Debug 包。

## Notes

- 重点范围：会话标签管理/筛选、助手 Hook 配置/历史、文件类型不支持提示。
- 日语、韩语、俄语和繁体中文可继续后续完善，但不得破坏资源完整性；本任务最低交付线是简体中文。

# 修复 Issue 123 skill 多行描述

## Goal

正确解析 `SKILL.md` frontmatter 中 `description: |` / `>` 等 YAML block scalar，使所有消费者获得真实描述文本。

## Requirements

1. 支持 literal/folded block scalar 及常见 chomping/indent 指示符，保留单行与引号值兼容。
2. 解析只作用于 frontmatter，不吞掉正文或后续键。
3. UI、斜杠补全与 SkillsTools 复用同一 `SkillMetadata.description`，不增加各处补丁式解析。
4. 对空块、缩进、CRLF、冒号和 Unicode 建立回归测试。

## Acceptance Criteria

- [ ] `|` 保留合理换行，`>` 合理折叠行；展示不再是字面 `|`/`>`。
- [ ] 单行 description 与其余 frontmatter 字段不回归。
- [ ] parser focused tests 覆盖 block scalar 边界。

## Notes

- 轻量解析修复，PRD-only。

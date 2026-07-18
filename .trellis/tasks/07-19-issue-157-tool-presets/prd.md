# 支持工具配置模板与跨助手复制 #157

## Goal

提供助手工具策略模板、跨助手复制和批量编辑，且不泄露资源绑定或 secret。

## Requirements

- 支持用户 preset CRUD、应用前 diff、逐目标结果和四态批量策略。
- 内置“只读研究”“代码工作区需审批”“最小权限”三个只读模板。
- 首版完全不复制 workspace/MCP/skill 绑定，不保存 schema/token/header/cookie。
- 未知工具跳过并报告；新增工具对旧模板默认 INHERIT；首版不提供一次撤销。

## Acceptance Criteria

- [ ] 旧 Settings/备份兼容，未知版本、非法/超大输入被安全拒绝。
- [ ] 同名不同来源不误匹配，权限放宽明确确认，批量结果逐目标报告。
- [ ] 模板和诊断输出不含资源绑定或 secret。

## Notes

- 依赖 #151/#153/#154；最后实现。

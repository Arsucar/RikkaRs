# 夜间审计 - Dispatch Protocol (07 路只读子代理)

## 通用约束（写进每个子代理 dispatch prompt）

```
Active task: .trellis/tasks/06-28-nightly-audit/

## 严格只读约束（违反即视为任务失败）
- 禁止使用 edit / write / patch 类工具改动文件
- 禁止运行任何 gradle / 构建 / 编译命令（最多做 --dry-run / 只读 lint）
- bash 仅允许查询类命令: rg/grep/find/git read/diff/log
- 输出只能写到: .trellis/tasks/06-28-nightly-audit/subagent-<key>/report.md
  (允许 write，但只允许该路径)
- 主报告汇总: .trellis/tasks/06-28-nightly-audit/audit-report.md 不在本代理范围内

## 输出硬要求
1. 报告头部列出审查范围的文件路径 glob
2. 每条发现附: file_path:line_number + 描述 + 修复建议（不强制代码）
3. 每条打 P0/P1/P2/P3 + 一句话理由
4. 末尾给「明早 5 条优先级 Top 行动」清单
5. 自身时间预算: ≤ 25 分钟; 超时留 partial 报告
```

## 7 路任务概述

1. **`subagent-data`** —— SubagentProfile / SubagentTools / Form / 迁移逻辑
2. **`subagent-runtime`** —— 运行时 + PermissionGate
3. **`subagent-ui`** —— UI(chats/settings) + ChainOfThought + Composer
4. **`i18n`** —— strings.xml 三语同步 + 硬编码
5. **`chat-service`** —— 流式 / 取消 / stale cleanup
6. **`log-redaction`** —— LogsTool / LogPage / redact 策略
7. **`infra`** —— CHANGELOG / AGENTS.md / docs / CI

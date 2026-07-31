# 精简 memory_tables schema 注入

GitHub issue: #194

## Goal

在注入渲染路径用 `slimSchemaForInjection()` 精简 schema，仅保留模型寻址所需字段，降低 system prompt 常驻体积，不改存储与 `list_templates`。

## Requirements

- 新增纯函数 `slimSchemaForInjection(schemaJson: String?): String`，只保留 `tables[].name`、`columns[].name`、`columns[].type`、`columns[].primaryKey`。
- `MemoryTableInjectionTransformer.buildMemoryTablePrompt()` 注入时调用该函数，替代原样写入 `template.schemaJson`。
- 存储/`validateMemoryTableSchemaJson`/`list_templates` 路径不变。
- null/空 → `"{}"`；非法 JSON → fallback 原串，不崩溃。
- 默认 schema 单次请求 system 注入体积减少 ≥40%。

## Non-Goals

- 不跨请求缓存 schema；不从存储删除 policy/description。

## Acceptance Criteria

- [ ] AC1: 注入 schema 不含 `injectPolicy`/`updatePolicy`/`maxInjectTokens` 等引擎字段
- [ ] AC2: 存储与 `list_templates` 仍返回完整 schema
- [ ] AC3: 现有 `MemoryTableInjectionTransformerTest` 全过
- [ ] AC4: null/空/非法 JSON 不崩溃
- [ ] AC5: 默认 schema 注入体积减少 ≥40%

## Complexity

Lightweight / 低风险纯逻辑；PRD 足够，可直接 implement；建议单测覆盖 slim 函数。

# 子代理全局配置入口（扩展管理）

## Goal

将子代理 profile 配置从「仅按助手独立配置」扩展为「全局共享 + 按助手覆写」双层级，用户可在设置页的「扩展管理」中统一管理通用子代理 profile，减少重复配置。

## Confirmed Facts

- 当前子代理配置完全挂在 `Assistant` 上（`enableSubagents`, `subagentMaxDepth`, `subagentProfiles`, `disabledBuiltinSubagents`）
- 内置 profiles 在 `SubagentRegistry.BUILTIN_PROFILES` 硬编码，各助手可禁用但不能编辑
- 无独立的 global subagent profile store
- 设置页当前无「扩展管理」入口
- 子代理 profile 的 `chatModelId` 引用全局 `Settings.providers` 中的 model

## Requirements

### R1: 全局子代理 Profile Store

- `Settings` 新增 `globalSubagentProfiles: List<SubagentProfile>` 字段
- 全局 profile 可在设置页 CRUD
- 查找顺序：assistant-local → global → builtin（与现有 `disabledBuiltinSubagents` 合并逻辑一致）

### R2: 扩展管理入口

- 设置页新增「扩展管理」入口（与「模型」「提供商」同层级）
- 扩展管理页内含子代理 profile 列表
- 支持新增 / 编辑 / 删除 / 排序全局 profile

### R3: 全局 Profile 复用到助手级

- 助手级子代理配置页增加「使用全局 profile」选项
- 选择全局 profile 后，该助手继承全局定义；助手级仍可禁用特定全局 profile
- 全局 profile 被编辑后，引用它的助手自动同步

### R4: 数据迁移

- 现有按助手创建的 custom profile 不受影响（原地保留）
- 新增全局 store 无需迁移旧数据

## Acceptance Criteria

- [ ] AC1: 设置页可见「扩展管理」入口，点击进入全局子代理 profile 列表
- [ ] AC2: 全局 profile CRUD 操作正常：创建 / 编辑 / 删除 / 拖拽排序
- [ ] AC3: 助手级子代理页可引用全局 profile，引用后不需要重复配置 model/tools 等
- [ ] AC4: 全局 profile 被编辑后，已引用的助手下次打开时看到更新后的配置
- [ ] AC5: profile 查找优先级正确：assistant-local > global > builtin
- [ ] AC6: 现有助手 custom profile 数据无丢失

## Out of Scope

- MCP 扩展管理（仅子代理）
- 子代理流式输出实现（本任务仅配置层面）
- 子代理 AI 日志系统

## Dependencies

- 依赖 `06-27-sub-agent-streaming-ui` 的 metadata schema 稳定后再实施（避免 data model 冲突）

## Open Questions

- 无

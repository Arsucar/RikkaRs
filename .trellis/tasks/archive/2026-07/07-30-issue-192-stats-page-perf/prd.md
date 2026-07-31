# StatsPage 加载缓慢修复

GitHub issue: #192  
关联：#193（长期方案）

## Goal

消除 StatsPage 进入时对 `message_node` 全表 `json_each` 聚合导致的长时间 Loading。

## Requirements

### 短期（本任务可独立交付，若 #193 同期落地则可合并）

- `StatsVM.loadStats()`：多条独立查询并行；移除无意义 `delay(50)`
- 有缓存时先展示旧数据，再后台刷新（SWR 风格）
- 手动刷新入口（可与 #193 UI 共用）

### 长期（由 #193 交付）

- 引入 `message_stats` 汇总表，读取路径不再 `json_each` 全展开

## Acceptance Criteria

- [ ] 并行查询 + 去掉 `delay(50)`
- [ ] 有缓存时进入不长时间空白 Loading
- [ ] 与 #193 不重复造轮：若 #193 先合，本任务以验证 #192 症状消失并关单为主

## Dependency

优先评估与 #193 合并实现；避免先做半吊子缓存再推倒。

## Complexity

Medium；若只做短期缓解可 PRD-only；与 #193 合并则按 #193 复杂任务流程。

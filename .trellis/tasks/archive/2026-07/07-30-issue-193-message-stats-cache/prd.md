# 统计页面增量统计表与缓存

GitHub issue: #193  
关联：#192（被本方案根治）

## Goal

引入 `message_stats` 汇总表与 StatsVM 缓存，将统计从 O(N×M) 全表 JSON 扫描降为 O(1)/O(log N) 读路径。

## Requirements

- 新增 `message_stats`（及可选 `message_stats_daily`）Room 实体；消息写入/删除事务内增量维护
- StatsPage 读汇总表；不再常规路径 `json_each` 全展开
- StatsVM 缓存 + 顶部「上次更新」+ 手动刷新（全量重算兜底）
- Room Migration：升级时后台全量回填，不阻塞 UI、不丢数据
- 删除会话时汇总行同步清理

## Acceptance Criteria

- [ ] AC1–AC7 对齐 Issue #193（增量表、读路径、秒开、手动刷新、并行、Migration、删除同步）
- [ ] #192 复现路径下进入 StatsPage 可接受延迟（有缓存秒开）

## Non-Goals

- 不在本任务做 API 错误监控（#191）

## Complexity

Complex：需 `design.md` + `implement.md`（DB 迁移、写路径挂钩、UI）。

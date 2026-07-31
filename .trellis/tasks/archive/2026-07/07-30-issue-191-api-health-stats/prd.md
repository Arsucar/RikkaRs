# 统计面板 API 上游错误监控

GitHub issue: #191

## Goal

在 Stats 域新增按 provider/model 颗粒度的 API 调用健康度：次数、成功率、延迟分位、错误类型分布与可用性提示。

## Requirements

- 新表 `api_call_records`：providerId/modelId/status/latency/errorType 等；ChatService 调用前后写入
- StatsPage「API 健康度」Tab/Card + 时间范围筛选 + 详情
- 错误分类：AUTH / RATE_LIMIT / TIMEOUT / CONTENT_FILTER / SERVER / NETWORK / UNKNOWN
- 高失败率模型显著「几乎不可用」提示
- SWR：有缓存先展示；Room Migration 安全

## Non-Goals

- 不修改 messages/conversations 结构
- 不存完整请求/响应正文

## Acceptance Criteria

- [ ] AC1–AC7 对齐 Issue #191

## Dependency

可与 #193 并行设计，建议 #193 写路径/Stats 架构稳定后再合 UI，避免 StatsPage 大改冲突。

## Complexity

Complex：需 `design.md` + `implement.md`。

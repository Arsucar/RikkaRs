# feat: 日志导出精准选择

## Goal

让用户在日志页长按选择多条日志导出（而非全量），仅此而已。不要清洗、不要筛选、不要搜索、不要单条导出图标。

## Background

旧版导出：TopBar 下载按钮 → SAF → `Logging.getRecentLogs().map { it.redacted() }` 全量 JSON。用户排查问题时混杂无关行。

## Requirements

1. **长按多选**：长按任意日志条目 → 进入选择模式并选中该条
2. **多选 UI**：选择模式下卡片显示 checkbox，底部浮现 toolbar（取消 / 全选 / 确认）
3. **导出选中**：确认时仅导出选中项（redacted 后序列化），而非全量
4. **默认行为不变**：未进入选择模式时，TopBar 下载按钮仍导出全量 + redacted
5. **不要**：FilterChip 类型筛选、搜索框、单条导出图标、清洗选项面板、强制截断

## Acceptance Criteria

- [ ] 长按日志条目进入选择模式并选中该条
- [ ] 选择模式底部 toolbar：取消 / 全选 / 确认
- [ ] 卡片在选择模式下显示 checkbox（toggle 选中）
- [ ] 确认导出 → SAF 序列化选中条目（redacted）
- [ ] TopBar 下载按钮（非选择模式）仍导出全量 + redacted
- [ ] 不再有：清洗选项 BottomSheet、单条导出图标、FilterChip、搜索框、LogExportOptions、applyLogExportOptions

## Out of Scope

- 类型筛选 / 关键词搜索 / 时间范围
- 清洗 / 截断 / 剥字段（用户明确不要）
- 单条导出（用户明确不要图标）
- 日志持久化 / 流式刷新

## Rationale（v1 → v2 → v3 演进）

- v1 复刻 ChatList 的"多选+搜索+筛选+默认截断"，被否（筛选对纯 Request 日志没用，搜索价值低，默认截断太霸道）
- v2 改成"单条导出 + 清洗选项面板"，被否（清洗无用，单条导出图标累赘）
- v3 = v1 的多选部分 + v2 的"不要清洗/筛选/搜索" = 纯粹的长按多选导出

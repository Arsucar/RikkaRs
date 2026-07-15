# 完成 Issue 124 模型搜索分组过滤

## Goal

搜索模型时只渲染有命中结果的供应商分组，消除空 header 与错误快捷导航。

## Requirements

1. 从标签过滤结果与现有搜索谓词派生唯一 `visibleProviders`。
2. LazyColumn、sticky header、providerPositions、底部 badge 和全展开/折叠统一使用可见集合。
3. 供应商名命中仍显示该供应商同类型全部模型；模型名命中仅显示匹配模型。
4. 空白搜索恢复全部分组及既有展开状态；收藏过滤不回归 #55。

## Acceptance Criteria

- [ ] 无命中供应商不渲染 header/items，也不出现在 badge 和滚动索引。
- [ ] 清空搜索恢复；标签、收藏、供应商名搜索组合正确。
- [ ] 全展开/折叠仅作用于当前可见供应商。

## Notes

- Empty 文案为建议项，不阻塞核心验收；若增加则必须本地化。

# 优化提供商列表UI信息展示

## Goal

解决模型选择页和提供商设置页信息展示率低的问题：提供商 tab 要可展开全宽显示而非单行滚动；半屏弹窗改为全屏；Provider 列表支持自定义标签分类、折叠/展开；自定义模型多时可高效管理。

## Confirmed Facts

- `ModelListSheet` 是半屏 BottomSheet，provider tab 仅单行横向滑动
- `ProviderItem` 仅展示：icon + name + shortDescription + enabled/disabled tag + model count
- `ModelItem` 展示：displayName + type/modality/ability tags + favorite
- 无折叠/展开能力，无自定义标签，无分类维度
- Provider 设置页长列表无分组

## Requirements

### R1: 模型选择弹窗全屏化

- `ModelListSheet` 从半屏 `BottomSheet` 改为全屏页面/对话框
- 保留搜索、收藏、provider 分组等现有功能

### R2: Provider Tab 可展开

- Provider tab 区域从单行横向滑动改为：点击展开按钮后向上展开为多行/列表
- 支持"全部展开/折叠"全局按钮
- 支持单个 provider 折叠/展开

### R3: Provider 列表自定义标签

- `ProviderSetting` 新增 `tags: List<String>` 字段（默认空）
- Provider 列表支持按 tag 筛选/分组显示
- 用户可在 provider 详情页编辑标签
- 提供预设标签建议（如"常用"、"便宜"、"长上下文"等）

### R4: Provider 设置页增强

- Provider 列表项展示更多信息：已配置模型数清楚地展示、API base URL 摘要、连接状态指示
- 支持按标签分组/筛选
- 支持全局折叠/展开

## Acceptance Criteria

- [ ] AC1: 模型选择弹窗为全屏，内容区域显著增大
- [ ] AC2: Provider tab 点击展开后以多行列表形式弹出，不再是单行滑动
- [ ] AC3: 全局"全部展开/折叠"按钮可用
- [ ] AC4: 单个 provider 可独立折叠/展开
- [ ] AC5: 可为 provider 添加自定义标签，列表可按标签筛选
- [ ] AC6: Provider 设置页 item 展示模型数、base URL 摘要等更多上下文
- [ ] AC7: 现有 provider 数据无丢失，tags 字段默认空

## Out of Scope

- 模型行增加上下文长度/价格信息展示（可后续迭代）
- Provider 连接状态实时检测（仅展示静态配置信息）
- 子代理相关改动

## Dependencies

- 无外部子任务依赖

## Open Questions

- 无

# Issues 122–130 集成交付设计

## Boundaries

- 每个独立 issue 使用一个子任务；#129/#130 共用一个子任务。
- #122 先完成数据隔离基础，#126 在其上验收搜索到记忆的完整路径。
- #127 的持久化基础先于 #128 的并发行为回归测试，避免并发测试建立在易丢上下文的实现上。
- 其余 UI/解析问题互相独立，可在不运行 Gradle 的实现代理间并行；最终只由检查代理运行 Gradle。

## Integration Contracts

- 助手身份必须在 Data/Domain 层形成授权边界，UI 过滤不能作为唯一防线。
- 新持久化字段必须对旧 JSON/Room 数据提供确定性默认值和迁移。
- 工具返回的二进制视觉内容必须进入 `UIMessage`/provider 转换的图片内容类型，不能只返回成功字符串。
- 列表派生集合必须由 UI 展示、快捷导航、位置索引和批量操作共同复用。

## Compatibility and Rollback

- Room migration 只做可向前恢复的数据添加/转换；失败不得静默 destructive migration。
- Assistant JSON 新字段使用缺省值保证旧备份可导入。
- #122 旧模板迁为 GLOBAL，避免猜测归属和数据丢失。
- 每个子任务保持可独立回滚的提交边界；最终集成提交不混入本地 Trellis 配置。

# Issue #132 全局会话标签与筛选

## Goal

为对话提供全局受控、多对多标签基础设施，以及可分页筛选、左抽屉展示/管理和设置页词表管理，同时保持标签与归档、助手、文件夹生命周期解耦。

## Requirements

- 全局标签只能由用户通过词表 CRUD 创建；其他 API 仅引用已存在 `tagId`，未知 ID 不得隐式创建。
- Room 增加标签实体和对话关系表，具备唯一约束、复合主键、反向索引、外键、级联与迁移。
- 名称执行 NFC、trim、连续空白折叠和稳定 case fold；限制 40 code point、全局 100、单会话 20；颜色来自受控 palette。
- 标签增删、merge、删除和 fork 复制使用独立原子事务 API，不通过保存整个 `Conversation` 修改关系。
- 标签筛选在 SQL/PagingSource 分页前执行：标签之间 OR，与 assistant/folder/archive/search 条件 AND。
- 左抽屉提供多选筛选、清空、标题右侧 chip/+N，以及长按标签管理；设置页提供 CRUD、改色、删除引用确认和显式 merge。
- 移动、归档、重命名、删除、fork、完整数据库备份/恢复遵循 issue #132 的生命周期语义。
- 所有 UI 文案本地化并覆盖暗色、TalkBack、错误、空态与恢复状态。

## Acceptance Criteria

- [ ] 37→38 迁移创建空词表/关系表，索引、唯一性、级联和 `foreign_key_check` 通过。
- [ ] 规范化、颜色、100/20/40 边界、重复增删幂等、未知 ID 和并发上限均有测试。
- [ ] merge 原子去重，rename/recolor 保持 tagId，Flow 自动刷新关联 UI。
- [ ] assistant/folder/archive/search 与单/多标签组合分页无重复、无页后过滤且排序稳定。
- [ ] 标签展示不产生逐会话 N+1 查询；关系变化自动更新 chip 和筛选结果。
- [ ] 筛选状态可恢复，切换作用域保留有效标签，标签删除后失效 ID 自动清理。
- [ ] 标签操作不改变归档、助手或文件夹；删除无孤儿；fork 在单 Room 事务中复制关系并可整体回滚。
- [ ] 完整数据库备份恢复保留词表、关系和 tagId；旧备份升级为空表并通过完整性检查。
- [ ] 设置 CRUD、抽屉筛选、长按管理和标题 chip 在小屏/暗色/TalkBack 下可用，且不挤压 pin/loading。
- [ ] 目标测试、Kotlin 编译和可用设备 Debug 安装通过。

## Notes

- Issue：<https://github.com/Arsucar/RikkaRs/issues/132>
- 结构化对话导入的词表合并协议、自动打标签、标签层级、AND 标签筛选和右抽屉标签 UI 不在范围内。

# Issue 处理状态一览

> 基线:分支 `release/rikka-arsucar`,代码已核对(实际文件为准)。
> 说明:所有改动**均未提交**(`git status` 全部为工作区改动);GitHub 上 **33 个 issue 全部 OPEN**,尚无 close。
>
> 统计:**已实现 32 / 未实现 1**(共 33)。
> 更新(2026-07-10):#75 逻辑纠正、#80 修正、#93 补齐;#77 重做(渲染全部标签 + 可添加)、#89 重构(RTL 真右抽屉 + 跟随语义 + fork/迁移随行),均已编译通过 + 单测通过 + 装设备 PJF110。

## 汇总

| 状态 | 数量 | 编号 |
|---|---|---|
| ✅ 已实现 | 32 | #70–#101(除 #68) |
| ❌ 未实现 | 1 | #68(上游 v2.4.1 合并,单独分支) |

## 明细

| # | 类型 | 标题(简) | 状态 | 关键证据 | 如何观测 |
|---|---|---|---|---|---|
| 70 | feat | 点头像进助手配置页 | ✅ | `ChatMessage.kt:181` 头像 onClick → `Screen.AssistantDetail` | 对话流点助手头像,应跳转助手详情页 |
| 71 | bug | 搜索框提示过长换行变高 | ✅ | `ModelList.kt:611` maxLines=1;占位文案缩短 | 打开模型列表,搜索框单行不再变高 |
| 72 | bug | 预设可多开 | ✅ | `ExtensionSelector.kt:122` onToggle 用 `setOf(id)` 单选 | 扩展管理-预设,勾选一个会取消其他 |
| 73 | feat | 默认全量预设 + ModeInjection 按预设隔离 | ✅ | `PreferencesStore.kt:1038` withDefaultPreset();`PromptPage.kt:425` 按 preset 隔离 | 查看默认 Default Preset;切预设时快速注入列表随之变化 |
| 74 | feat | 子代理关联预设 | ✅ | `SubagentProfile.kt:80` presetIds;`AssistantSubagentProfilePage.kt:349` FilterChip | 子代理配置页可勾选关联预设 |
| 75 | feat | 条目直接弹对应编辑弹窗 | ✅ 已纠正 | `ExtensionContent.kt` 各 Content 加 `onEdit`;`AssistantExtensionsPage.kt`/`ExtensionSelector.kt` 接线各编辑弹窗;私有技能按 `ownerAssistantId` 打开 SkillDetail | 点预设→弹编辑预设;点世界书→弹编辑;技能/快捷消息同理直接进编辑;扩展页进技能能看到私有技能 |
| 76 | feat | 子代理 slimPayload 缺 usage | ✅ | `SubagentTools.kt:146` slimPayload 带 usage;`SubagentToolUIs.kt:151` 渲染 token | 子代理工具卡片显示 token 计数 |
| 77 | feat | 移除内置标签→渲染全部自建标签 + 可添加 | ✅ 已重做 | `PreferencesStore.kt`:`effectiveProviderTags()` 去内置词;`providerTagOrder` 沉淀所有 provider.tags;新增 `Settings.addProviderTag()`。`SettingProviderPage.kt`:「管理标签」入口常显(空列表也显示),`ProviderTagManagerSheet` 加输入框 + 添加按钮 | 筛选区显示全部用户自建/已挂载标签供选择;可在「管理标签」里新增标签并加入选择区 |
| 78 | bug | 编辑预设 BottomSheet 半屏弹跳 | ✅ | `PromptPage.kt:431` 禁用 PartiallyExpanded,初始 Expanded | 编辑预设弹窗全屏、不再弹跳 |
| 79 | feat | 子代理弹窗展示传输上下文 | ✅ | `SubagentToolUIs.kt:268` TransferredContextSection;`SubagentTools.kt:126` 写 metadata | 子代理工具弹窗可展开看传给子代理的上下文 |
| 80 | feat | 折叠视图限正文 3 行 | ✅ 已修正 | `ChatList.kt` selectionCollapsed 下发 `selectionCompact`;`ChatMessage.kt` 折叠态正文 `Text(maxLines=3, Ellipsis)` 并隐藏 thinking/tool 块 | 分享多选折叠后每条正文只显 3 行,不再超高 |
| 81 | feat | 记忆表默认模板多列结构化 | ✅ | `MemoryTable.kt:235` DEFAULT_SCHEMA 改 5 列(key/category/summary/evidence/updated_at) | 新建记忆表默认是多列结构 |
| 82 | feat | Provider 单独限速 RPM/TPM | ✅ | `ProviderSetting.kt:17` rateLimit;`ProviderConfigure.kt:75` 编辑 UI;`GenerationHandler.kt:443` await | 提供商配置页可设 RPM/TPM,超限自动延迟 |
| 83 | bug | 模板不校验 schemaJson | ✅ | `MemoryTable.kt` validateMemoryTableSchemaJson,upsertTemplate 写前校验 | 写残缺 schema 会被拦截报错 |
| 84 | bug | conversation scope 写入不可见 | ✅ | `MemoryTableTools.kt:307` 禁 CONVERSATION 写入,回退 ASSISTANT | 会话级写入自动落到助手级(见 #89 说明) |
| 85 | bug | upsert_rows 非原子/不校验 | ✅ | `MemoryTableRepository` upsertDocument 调 validatePayloadJson;apply_ops 原子批写 | 残缺 payload 不会覆盖旧文档 |
| 86 | bug | patch 硬编码 "key" 主键 | ✅ | `MemoryTableTools.kt` resolveRowKey(explicit→primaryKey→key) 按表合并 | 无 key 列的多列表 patch 也能正确行级合并 |
| 87 | bug | 工具错误抛原始异常 | ✅ | `MemoryTableTools.kt:413` memoryTableToolError 封装 {success,error} | 工具报错返回可读结构而非崩栈 |
| 88 | feat | read 列出全部文档 + list action | ✅ | `MemoryTableTools.kt:248` read+filterByScope;`MemoryTools.kt` list action | read 可列全部文档并按 scope 过滤 |
| 89 | feat | 对话级记忆表右侧抽屉 | ✅ 已重构 | RTL 包裹 `ModalNavigationDrawer` 实现真·右侧抽屉(`ChatPage.kt`,可右滑手势 + BackHandler);`ConversationMemoryTableDrawerContent`(scope 标签 + 同步/跟随/断开/行级编辑);修复 `scopeIdFor` 会话级误用 assistantId 的 bug;路由加 `conversationId`;DB 迁移 32→33 加 `source_document_id`/`follow_source`;`ChatService` fork 复制对话级文档 + move 重绑跟随引用 | 顶栏点表格图标→右侧抽屉;助手/全局文档可「同步到对话级」;对话级文档可跟随来源或「断开跟随」独立编辑;fork/迁移会话时对话级记忆表随行 |
| 90 | bug | delete_rows 删整文档语义错 | ✅ | `MemoryTableTools.kt` delete_rows 被 guard;delete_row 只删单行 | 删行不再误删整份文档 |
| 91 | bug | maxRows 实为限文档数误导 | ✅ | `MemoryTableInjectionTransformer.kt:17` maxDocuments 明确限文档数 | 命名/文案改为文档数,不再误导 |
| 92 | feat | 按行删除 delete_row | ✅ | `MemoryTableTools.kt:379` delete_row + deleteMemoryTableRow | 可按 table+行键删单行 |
| 93 | feat | per-table 注入门控 injectPolicy | ✅ 已补齐 | 注入侧 disabledInjectionTables 生效;`MemoryTable.kt` validateMemoryTablePolicy 校验 injectPolicy + readMemoryTableInjectionToggles/setMemoryTableInjectionEnabled;`AssistantMemoryTableDocumentEditorPage.kt` 表头加注入 Switch | 记忆表编辑页每张表有「注入到提示词」开关,关闭后该表不注入 |
| 94 | feat | trigger-send 行级裁剪 | ✅ | `MemoryTableInjectionTransformer.kt` triggerSendTables/filterRowsByRecentText(近 6 条) | trigger-send 表仅注入与近期对话相关的行 |
| 95 | feat | update_template/delete_template | ✅ | `MemoryTableTools.kt:95` action + handler(old.copy / confirm 级联删) | 工具可改/删模板 |
| 96 | feat | 逐轮快照与历史回滚 | ✅ | Repository upsertDocument 写快照+prune;rollbackDocument;SnapshotDAO/Entity/Migration_31_32;DB v32 | 文档改动留快照,可按 revision 回滚 |
| 97 | feat | query 按表/列/值过滤 | ✅ | `MemoryTableTools.kt:253` query → queryMemoryTableRows(column/value/contains) | 可只查匹配行,避免整包 read |
| 98 | feat | 批量写操作数组 apply_ops | ✅ | `MemoryTableTools.kt:272` apply_ops → applyMemoryTableOps(顺序批写,失败整体回滚) | 一次提交多条 insert/update/delete,原子生效 |
| 99 | feat | 注入手动宏/占位符 | ✅ | `MemoryTableInjectionTransformer.kt:26` MEMORY_TABLE_MACRO `{{memory_tables}}`,命中替换否则追加 | 系统提示写 `{{memory_tables}}` 可控注入位置 |
| 100 | feat | 模板/文档 JSON 导出/导入 | ✅ | `MemoryTable.kt` MemoryTableBundle+encode/decode+resolveImport(SKIP/OVERWRITE/DUPLICATE);Repository export/importBundle | 可导出/导入记忆表包,冲突按策略处理 |
| 101 | bug | 压缩后列表定位到隐藏消息 | ✅ | `ChatPage.kt:191/383/397` 滚动索引改 messageNodes.lastIndex+10 | 压缩聊天切窗口后定位到可见底部 |
| 68 | feat | **合并上游 v2.4.1** | ❌ 未实现 | `app/build.gradle.kts:24` versionName 仍 `2.3.22` | 大型上游合并,建议单独分支(见 issue_upstream_sync.md) |

## 未完成项说明

- **#68 上游 v2.4.1 合并**:唯一未着手项。blast radius 大,涉及冲突整理与 fork 定制(release-apk、去 Firebase),应单独分支谨慎处理。

### 本轮(2026-07-10)已处理

- **#75**:纠正逻辑 —— 点击单个条目**直接弹对应编辑弹窗**(预设/世界书/技能/快捷消息),不再跳管理页;并修复扩展页进技能看不到私有技能的一致性 bug(按 ownerAssistantId 打开 SkillDetail)。
- **#77**:从只"移除内置标签"改为**渲染全部用户自建标签**。
- **#80**:折叠态限制每条**正文 3 行**(maxLines=3 + 隐藏 thinking/tool 块)。
- **#93**:补齐 schema 校验覆盖 injectPolicy + 编辑页 per-table 注入开关 UI。
- **#89 重构**(首版是简易覆盖层,不符 issue,已推倒重做):
  - 真·右侧抽屉:RTL 包裹 `ModalNavigationDrawer`,支持右滑手势与返回键关闭。
  - 修复 `scopeIdFor` 会话级作用域误用 assistantId 的 bug,路由透传 `conversationId`。
  - 跟随语义:助手/全局文档「同步到对话级」后 `followSource=true` 跟随来源;可「断开跟随」独立编辑。
  - fork/迁移会话:DB 迁移 32→33 加 `source_document_id`/`follow_source`;`ChatService` fork 复制对话级文档、move 重绑跟随引用,记忆表随会话流转。

## 收尾待办

1. **提交**:全部实现仍在工作区未提交 → `git add` + commit 到 `release/rikka-arsucar`。
2. **#68**:单独分支合并上游(风险高,单独处理)。
3. **关闭 issue**:发版后用 `gh issue close` / `gh issue comment` 逐个处理(当前全 OPEN)。

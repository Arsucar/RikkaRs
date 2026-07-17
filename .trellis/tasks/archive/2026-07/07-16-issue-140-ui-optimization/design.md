# Design: Issue #140 UI 优化

## Scope and Boundaries

- 主要修改 `AssistantMemoryPage.kt` 的记忆表列表、空状态、新增流程和删除确认。
- 复用现有 `AssistantDetailVM` 的顺序持久化接口、现有路由和 repository 协议。
- 不修改 Entity、DAO、数据库 schema 或模板/文档作用域规则。
- 只有在纯 UI 状态无法表达时才调整 VM；默认方案不需要新的持久化方法。

## UI Structure

### 1. Memory table section

- 保留“记忆表格”标题和右侧新增入口，补充本地化 content description。
- 空列表改为带图标、说明和“新增表格”文字 CTA 的空状态；CTA 与标题新增入口调用同一回调。
- 非空列表继续逐文档展示，保持整卡进入编辑页。

### 2. Document card

- 参考仓库 `SkillCard`：左侧使用记忆表/数据库图标容器，中间展示模板名、最多两行模板描述和元数据标签。
- 元数据至少包含文档作用域和 revision；内部 enum 不直接显示。
- 删除移入 overflow menu，菜单项和确认按钮使用危险色；主卡点击目标不与删除按钮竞争。
- 模板不可见时使用现有通用文档名称作为回退，不因展示元数据缺失改变文档可见性规则。

### 3. Layered add-table bottom sheet

- 用 `ModalBottomSheet` 替换现有单个 `AlertDialog`。
- Sheet 内维护两个 UI mode：
  - `TemplatePicker`：可滚动模板列表 + “创建新模板”独立入口。
  - `CreateTemplate`：返回按钮、名称、描述、作用域选择、范围说明、单一“创建”动作。
- Sheet 使用 `imePadding` 和可滚动/惰性列表，保证小屏、横屏和键盘弹出时操作仍可达。
- 保存中禁用重复操作并显示进度；失败停留在当前 mode，保留输入并显示本地化通用错误。

### 4. Template picker rows

- 每项展示模板名称、最多两行描述和模板作用域标签。
- 使用 `primaryDocumentsByTemplate` 判断当前助手是否已有主文档：
  - 未添加：点击调用现有 `createMemoryTableDocument`，成功后关闭 sheet 并导航。
  - 已添加：显示 tick/“已添加”，点击直接打开对应已有文档，不创建新 UUID 文档。
- 若同模板存在额外历史文档，主列表仍全部展示；picker 只打开派生的主文档（助手作用域优先，其次全局），沿用现有纯函数规则。

### 5. Create-template form

- 名称 trim 后必填；描述可选。
- 作用域用显式单选控件表达，默认 `ASSISTANT`，可选 `GLOBAL`。
- 每个作用域显示用户可理解的说明；禁止暴露 conversation scope。
- 提交继续调用现有 `createMemoryTableTemplateAndDocument`，成功后带已持久化文档 ID 导航。

### 6. Delete confirmation and accessibility

- 删除确认展示模板/表格名称和“只删除文档、不删除模板”的语义，不展示 `payloadJson`。
- 新增、更多操作、删除、返回等图标补齐本地化 content description。
- 动态模板名和描述保持原样，不翻译用户数据。

## State and Data Flow

1. `AssistantMemoryContent` 从 effective templates 和 assistant documents 派生统一的 `AssistantMemoryTableDocumentSelection`。
2. 同一份 selection 同时供主列表和新增 sheet 使用，避免“已添加”状态与列表投影不一致。
3. UI 只决定打开已有文档还是调用现有创建接口；成功后的导航仍使用 `MemoryTableDocument.toMemoryTableEditorScreen()`。
4. 所有 Room 写入完成前不关闭 sheet、不导航；失败不暴露原始 exception。

## Template Name Identity

- 在 memory-table domain/repository 层定义单一名称规范化函数：trim、Unicode NFC、折叠连续空白、`Locale.ROOT` 小写。
- 名称冲突判断基于规范化结果而非原始显示文本。
- `ASSISTANT` 新建/编辑检查 GLOBAL + 当前 assistant；`GLOBAL` 新建/编辑检查所有模板。
- Repository 在持久化前执行冲突检查并抛出可识别的冲突异常；UI 与 AI/其他 actor-aware 调用共享同一防线。
- 保留无 actor 的低层导入兼容入口，但助手页创建 GLOBAL 时必须携带 actor assistant ID，走带冲突检查的 scope-aware API。

## Legacy Duplicate Projection

- 完整 effective template 列表继续用于文档投影和模板管理，不能在上游直接按名称去重。
- picker 使用独立纯函数按规范化名称分组并选择 winner：已有主文档优先，其次当前 assistant scope，再按 GLOBAL、更新时间和 ID 稳定排序。
- 被折叠的 loser 仍出现在管理列表，其文档仍出现在主文档列表。

## Template Management

- picker 底部新增“管理模板”入口，与“创建新模板”并列但语义分离。
- 管理层展示所有 effective templates；同名冲突项保留，每项展示名称、描述、scope 和冲突状态。
- 编辑层复用名称/描述表单，scope 只读；保存返回 `Result<MemoryTableTemplate>`，冲突时保留输入并显示本地化错误。
- 删除使用二次确认并返回 `Result<Boolean>`：私有模板提示级联其文档；全局模板额外提示影响所有助手。
- 删除只按模板 ID 执行，绝不按名称批量删除。

## Compatibility and Trade-offs

- Bottom sheet 比单个 dialog 改动更大，但解决模板列表溢出、作用域动作混乱和表单空间不足。
- 本轮不加模板搜索；惰性滚动列表足以解除当前布局上限，搜索可在模板规模实际增长后独立增加。
- Repository 名称冲突检查覆盖正常应用路径，但不提供数据库唯一索引级并发保证；硬唯一与历史迁移仍属于后续数据层任务。
- 不改变 capability gate 的存储和运行时语义，避免 UI 优化顺带修改功能开关行为。

## Validation Strategy

- 扩充纯 Kotlin 测试，验证主文档映射和“已添加”判定仍按助手优先、全局回退处理。
- 静态检查所有新增字符串、content description 和 raw enum/exception 展示。
- 运行资源处理、Kotlin 编译和聚焦 JVM 测试。
- 按 app 模块验收流程连接设备并执行 `:app:installDebug`；设备可用时检查 Bottom Sheet、键盘、深浅主题和删除确认。

## Rollback

- UI 状态和 composable 变更集中在 `AssistantMemoryPage.kt`，资源键为增量添加；可独立回退而不影响 #140 的 VM/repository 顺序持久化实现。
- 若 Bottom Sheet 出现兼容性问题，可回退到旧 dialog，同时保留文档卡、空状态和无障碍改进。

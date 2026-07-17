# Implementation Plan

1. 读取并锁定现有 `AssistantMemoryPage` 状态接线、#140 helper、导航回调和删除确认；确认工作树中的无关 agent 配置改动不被触碰。
2. 将 effective templates 与 document selection 提升为主列表和新增流程共享的 UI 投影，补充已添加模板的主文档解析测试。
3. 重构记忆表主列表：
   - 增加带 CTA 的空状态；
   - 文档卡展示图标、描述和 scope/revision 标签；
   - 删除移入 overflow menu；
   - 补齐无障碍描述。
4. 用分层 `ModalBottomSheet` 替换 `AddMemoryTableDialog`：
   - 模板选择层使用可滚动列表；
   - 展示描述、作用域和已添加状态；
   - 已添加模板打开已有文档，未添加模板走现有创建接口；
   - 提供独立“创建新模板”入口。
5. 实现创建模板层：
   - 名称/描述输入；
   - 默认私有的显式作用域选择和说明；
   - trim 校验、保存进度、失败保留输入；
   - 成功后沿用已持久化文档导航。
6. 重做文档删除确认文案，展示表格名称和文档级删除边界，不展示原始 JSON。
7. 使用 `locale-tui-localization` 工作流更新 default、简中及其余现有 `values*` 资源，校验 key 和 placeholder 一致性。
8. 由实现子代理完成代码后，主会话小范围复核关键 `file:line`，再由最终检查子代理执行静态审查和必要修复。
9. 验证命令（全部 `--no-daemon`）：
   - `git diff --check`
   - `\.\gradlew --no-daemon :app:processDebugResources`
   - `\.\gradlew --no-daemon :app:compileDebugKotlin`
   - `\.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.assistant.detail.AssistantMemoryTableScopeTest"`
   - 按设备流程执行 `adb devices`，设备可用时运行 `\.\gradlew --no-daemon :app:installDebug`；无设备则如实记录只完成编译。
10. 风险与回滚点：
    - Bottom Sheet/IME 布局异常时先回退新增流程 composable，不回退 VM/repository；
    - 本地化资源编译失败时按 key/placeholder 逐套修复；
    - 不使用 `git add .`，不提交 `.codex/agents/*` 的现有用户改动。

## Follow-up: Duplicate Names and Template Management

11. 在 memory-table model/repository 层增加名称规范化与可识别的冲突异常；为私有/全局创建和编辑实现明确的冲突查询规则。
12. 将助手页创建 GLOBAL 模板改为携带 actor assistant ID 的 scope-aware Repository 路径；VM 的模板 upsert/delete 改为 `Result` callback，供管理 UI 保留错误状态。
13. 增加 picker 专用去重投影：按规范化名称分组，优先已有文档、当前助手私有、全局、更新时间和 ID；不得影响完整模板/文档列表。
14. 在 Bottom Sheet 增加“管理模板”层：完整历史模板列表、冲突提示、编辑名称/描述、scope 只读、按 ID 删除。
15. 模板删除确认区分私有/全局；明确级联删除文档及全局影响，失败保留管理层并显示错误。
16. 使用 locale-tui 增加管理入口、编辑、同名冲突、级联删除和全局影响文案，覆盖六套 locale。
17. 增加纯投影测试、Repository 名称冲突测试和 VM/持久化结果测试；最终检查运行资源处理、compile、聚焦测试和 assemble，设备链路不可用时上传 Gofile 供主力机验收。

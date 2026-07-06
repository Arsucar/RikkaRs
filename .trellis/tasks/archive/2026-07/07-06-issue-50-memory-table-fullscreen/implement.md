# Implement: 记忆表格文档全屏表格编辑

## Execution Checklist

### Phase 1: 路由与页面骨架

1. [ ] 在 `RouteActivity.kt` 的 `Screen` 接口中新增：
   ```kotlin
   @Serializable
   data class AssistantMemoryTableDocumentEditor(
       val documentId: String? = null,
       val templateId: String,
       val assistantId: String,
       val scopeType: MemoryTableScopeType = MemoryTableScopeType.ASSISTANT,
   ) : Screen
   ```
2. [ ] 在 `RouteActivity.kt` 的 `entryProvider` 中注册：
   ```kotlin
   entry<Screen.AssistantMemoryTableDocumentEditor> { key ->
       AssistantMemoryTableDocumentEditorPage(
           documentId = key.documentId,
           templateId = key.templateId,
           assistantId = key.assistantId,
           initialScopeType = key.scopeType,
       )
   }
   ```
3. [ ] 新建 `AssistantMemoryTableDocumentEditorPage.kt`，实现：
   - `Scaffold` + `TopAppBar` + `TabRow`
   - 默认显示占位文本“表格模式”与“JSON 模式”
   - 返回按钮先直接 popBackStack

### Phase 2: 数据拉取与状态初始化

4. [ ] 在 `AssistantMemoryTableDocumentEditorPage` 中获取 `AssistantDetailVM`（按 `assistantId` 传参）并收集 `memoryTableTemplates` / `memoryTableDocuments`。
5. [ ] 根据 `documentId` 查找已有文档；未找到则构造 `MemoryTableDocument(
       templateId = templateId,
       scopeType = initialScopeType,
       scopeId = if (initialScopeType == GLOBAL) GLOBAL_MEMORY_ID else assistantId,
   )`。
6. [ ] 根据 `templateId` 查找模板；未找到则使用 `DEFAULT_MEMORY_TABLE_SCHEMA_JSON`。
7. [ ] 初始化 `payloadJson`、`tables`、`editorError`、`hasUnsavedChanges`。

### Phase 3: 表格模式 UI

8. [ ] 在新页面中添加 schema/payload 解析函数（迁移自旧实现）：
   - `parseMemoryTableSchema`
   - `parseMemoryTableEditorTables`
   - `serializeMemoryTablePayload`
   - `validateMemoryTablePayloadJson`
   - `jsonElementToCellText`
9. [ ] 实现 `MemoryTableEditorTable` 等内部数据类及增删改方法。
10. [ ] 实现表格渲染 composable：表头、行内 `OutlinedTextField`、删除行按钮、新增行按钮。
11. [ ] 处理列宽：`string` 160dp、`text` 260dp；整体支持 `horizontalScroll`。
12. [ ] 绑定单元格变更到 `tables` → `serializeMemoryTablePayload` → `payloadJson` → `hasUnsavedChanges = true`。
13. [ ] schema 无表时显示空状态文案。

### Phase 4: JSON 模式与校验

14. [ ] JSON 模式使用 `TextField` 绑定 `payloadJson`。
15. [ ] 实时校验：`validateMemoryTablePayloadJson(it).exceptionOrNull()?.message` 显示错误。
16. [ ] 从 JSON 切换回表格模式时：调用 `parseMemoryTableEditorTables`；失败则停留在 JSON 模式并显示错误。
17. [ ] 从表格切换回 JSON 模式时：直接显示当前 `payloadJson`。

### Phase 5: Scope 切换、保存与返回确认

18. [ ] 在编辑器内添加 scope 切换行（Switch + 文案）。
19. [ ] 已有文档且 scope 为 CONVERSATION 时只读展示。
20. [ ] 保存按钮点击：根据当前 mode 校验/序列化 `payloadJson`，成功后调用 `vm.upsertMemoryTableDocument(document.copy(payloadJson))` 并 pop。
21. [ ] 返回拦截：在 `BackButton` 与系统返回中检查 `hasUnsavedChanges`，弹出确认 Dialog。
22. [ ] 确认 Dialog 提供“放弃”“保存”“取消”选项。

### Phase 6: 替换 AssistantMemoryPage 入口

23. [ ] 移除 `AssistantMemoryPage` 中的 `memoryTableDocumentDialogState` 及相关 `AlertDialog` 代码。
24. [ ] 点击文档卡片时 `navController.navigate(Screen.AssistantMemoryTableDocumentEditor(...))`。
25. [ ] 点击模板“+”新建文档时同样导航。
26. [ ] 保留模板编辑 Dialog 与记忆编辑 Dialog 不变。

### Phase 7: 字符串资源

27. [ ] 在 `values/strings.xml` 与 `values-zh/strings.xml` 中新增/恢复：
   - `assistant_page_memory_table_mode_table`
   - `assistant_page_memory_table_mode_json`
   - `assistant_page_memory_table_add_row`
   - `assistant_page_memory_table_empty_schema`
   - `assistant_page_memory_table_unsaved_title`
   - `assistant_page_memory_table_unsaved_text`
   - `assistant_page_memory_table_discard`
   - 必要时新增页面标题字符串。

### Phase 8: 验证

28. [ ] 运行 `./gradlew --no-daemon :app:compileDebugKotlin`。
29. [ ] 修复编译错误。
30. [ ] 连接设备，运行 `./gradlew --no-daemon :app:installDebug`。
31. [ ] 在设备上验证：新建文档、编辑单元格、新增/删除行、切换 JSON、保存、返回确认。

## Review Gates

- [ ] 新页面不持有 VM 引用导致生命周期泄漏。
- [ ] 所有字符串均来自资源文件。
- [ ] `payloadJson` 是表格模式与 JSON 模式的单一数据源。
- [ ] 保存失败时错误展示给用户，不直接退出。

## Rollback Point

- 若编译或运行时出现不可快速修复的问题，可保留新页面文件但回滚 `AssistantMemoryPage` 的入口为旧的 `memoryTableDocumentDialogState`，并禁用 `RouteActivity` 中的新 entry（注释掉），使功能回到 AlertDialog 编辑状态。

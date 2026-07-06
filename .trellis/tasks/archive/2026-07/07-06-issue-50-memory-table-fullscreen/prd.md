# Issue #50: 记忆表格文档全屏表格编辑

## Goal

为 **助手 → 记忆 → 记忆表格文档** 提供独立全屏编辑页，默认以表格形式展示并编辑 `MemoryTableDocument.payloadJson`，同时保留 JSON 原文编辑模式作为高级入口，降低非开发者用户维护结构化记忆的门槛。

## Requirements

### 功能需求

1. **全屏独立页面**
   - 在 `AssistantMemoryPage` 中点击已有文档卡片或点击模板上的“新增文档”按钮后，直接跳转到独立全屏页。
   - 不再使用 `AlertDialog` / `BasicAlertDialog` 承载编辑器。

2. **顶部 Tab 切换模式**
   - 默认显示 **表格模式**。
   - 提供 **JSON 模式** Tab，供高级用户直接编辑 `payloadJson` 原文。

3. **表格模式展示**
   - 解析 `MemoryTableTemplate.schemaJson` 中的 `tables[].name` 与 `columns[]`。
   - 将 `MemoryTableDocument.payloadJson` 按表名映射为若干表格。
   - 多张表时采用**垂直堆叠**布局，每张表有独立表头、行内编辑单元格、新增行/删除行按钮。
   - 表格区域支持横向滚动（列多/列宽时）与纵向滚动（表多/行多时）。

4. **表格模式编辑**
   - 每个单元格为行内 `OutlinedTextField`。
   - `string` 类型单元格单行，`text` 类型单元格自动加高（`minLines = 3` 左右）。
   - 每行末尾提供删除按钮；表头下方提供“新增行”按钮。
   - 编辑后延迟序列化到内存中的 JSON 草稿，错误时提示。

5. **JSON 模式编辑**
   - 使用多行 `TextField` 编辑 `payloadJson` 原文。
   - 实时校验是否为合法 JSON Object。
   - 切换回表格模式时，若 JSON 不合法或与 schema 不兼容，应提示错误并停留在 JSON 模式。

6. **Scope 切换**
   - 编辑页内保留 `ASSISTANT` / `GLOBAL` 切换（`CONVERSATION` 作用域只读展示）。

7. **保存与返回**
   - TopAppBar 提供“保存”按钮。
   - 系统返回或点击返回按钮时，若存在未保存修改，弹出“放弃/保存”确认 Dialog。
   - 保存时通过现有 `AssistantDetailVM.upsertMemoryTableDocument` → `MemoryTableRepository.upsertDocument` 持久化。

### 非功能需求

- 复用旧实现 `e860756f` 中已验证的 schema/payload 解析与序列化逻辑。
- 遵循项目 Compose / navigation3 路由约定。
- 新增/恢复必要的字符串资源（中/英）。

## Acceptance Criteria

- [ ] 新增 `Screen.AssistantMemoryTableDocumentEditor` 并在 `RouteActivity` 中注册。
- [ ] 从 `AssistantMemoryPage` 点击文档或新建文档可进入该全屏页，默认展示表格模式。
- [ ] 表格模式正确解析 schema，展示表头、行、单元格；支持行内编辑、新增行、删除行。
- [ ] JSON 模式可编辑原文并校验；非法时切换回表格模式会被阻止并提示。
- [ ] 点击保存后，`payloadJson` 正确持久化，revision 正常递增。
- [ ] 未保存修改时返回弹出确认 Dialog。
- [ ] 字符串资源有中英文两种。
- [ ] 编译通过：`.\gradlew --no-daemon :app:compileDebugKotlin`。
- [ ] 安装到设备并验证基本编辑流程：`.\gradlew --no-daemon :app:installDebug`。

## Notes

- 当前工作区中的 `AssistantMemoryPage.kt` 已回退全屏编辑器，本次需重新实现。
- 规划阶段已与用户确认：独立 Destination、顶部 TabRow、垂直堆叠多表、行内编辑、手动保存+返回确认、编辑页内 scope 切换、新建文档也走全屏页。

# Implementation Plan: Issue #122 模板助手隔离

## 1. Model, Room and Migration

1. 为 template model/entity 增加 `scopeType/scopeId`，约束模板仅允许 GLOBAL/ASSISTANT。
2. 新增 `Migration_35_36`、schema 36、数据库版本与 DI 注册。
3. 添加迁移测试，验证旧模板成为 `GLOBAL/__global__` 且索引存在。
4. 更新 bundle v2，兼容 v1 解码与 ownership round-trip。

## 2. Scoped Storage APIs

1. DAO 新增 effective template list/flow、actor-scoped by-ID、guarded update/delete 和事务删除。
2. Repository 建立共享 template visibility predicate，并对 DAO 结果防御过滤。
3. 新建默认 private；update ownership immutable；known-ID 越权失败。
4. 增加全局模板复制为当前助手私有模板 API。
5. 为 AI 工具的 document known-ID mutation 补齐 actor scope 授权，保留上一轮文档隔离。

## 3. Caller and UI Wiring

1. `AssistantDetailVM`、`ChatVM` 和 `ChatService` 全部改用 scoped template API。
2. ChatService 工具闭包捕获当前 assistant/conversation，移除全局 repository method reference。
3. 页面、编辑器和对话选择器只消费 effective templates，并加入防御过滤。
4. 私有模板禁止 GLOBAL 文档；全局模板增加复制到当前助手动作。
5. 保持现有全局模板共享行为和旧模板可见性。
6. 为编辑器增加显式 Loading/Loaded 解析，只有已加载且无授权模板时退出。

## 4. Tests

1. Migration/DAO：35→36、A/B/global 查询、known-ID 越权、原子删除。
2. Repository/model：默认 owner、同名独立模板、污染过滤、不可变 owner、bundle v1/v2。
3. UI/VM：A 仅见 global+A、B ID 无法解析、复制 global、私有模板 global-document 拒绝。
4. Tools/runtime：当前 assistant list/create/update/delete；外部 template/document ID 拒绝；注入仅接收 effective templates。
5. 重跑原 #122 文档 scope 测试。
6. 增加畸形 GLOBAL scope 行不可见/不可删除，以及编辑器首个有效 emission 前不退出的回归测试。

## 5. Validation and Delivery

1. 仅最终 checker 运行 Gradle，所有命令带 `--no-daemon`。
2. 运行 focused JVM tests、`:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`、lint baseline 检查和 `git diff --check`。
3. 生成并核验 Room schema 36，确保 migration 与 migration test 均受版本控制。
4. `adb devices` 有在线设备则 `:app:installDebug`；无设备记录跳过；不运行 `connectedDebugAndroidTest`。
5. 精确 stage 产品文件，提交到 `release/rikka-arsucar`，推送后评论并关闭 #122。

## Risk / Rollback Points

- DAO 删除必须先授权再级联，禁止沿用“先删 documents 后删 template”的顺序。
- 不允许 UI-only 过滤或 by-ID 旁路。
- 不允许导入时把未知 private owner 自动改成 global。
- 不扩大为模板排序、预算或视觉重构。

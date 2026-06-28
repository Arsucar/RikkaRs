# 子代理全局配置入口（扩展管理） — 实施计划

## 依赖

> **必须在 `06-27-sub-agent-streaming-ui` 完成后实施**（metadata schema 稳定 + SubagentProfile 数据模型变更落地）

## 执行顺序

### Phase 1: 数据层

- [ ] **1.1** `PreferencesStore.kt` — Settings 增加 `globalSubagentProfiles`
  - 验证：`.\gradlew :app:compileDebugKotlin`

- [ ] **1.2** `SubagentRegistry.kt` — `resolveProfile` 扩展查找链
  - 增加 `globalProfiles: List<SubagentProfile>` 参数
  - 查找顺序：assistant-local → global → builtin
  - 验证：单元测试

### Phase 2: 导航 + 设置页入口

- [ ] **2.1** `RouteActivity.kt` — 新增 Screen + entry 组合
  - `Screen.ExtensionManagement`
  - `Screen.ExtensionSubagentProfile`

- [ ] **2.2** `SettingPage.kt` — 新增扩展管理入口项
  - 验证：编译 + 导航跳转正常

### Phase 3: 扩展管理页

- [ ] **3.1** 新建 `ExtensionManagementPage.kt`
  - 子代理 profile 列表（CRUD）
  - 添加 / 删除 / 排序
  - 点击进入 profile 编辑
  - 验证：CRUD 功能正常

- [ ] **3.2** 提取/复用 `AssistantSubagentProfilePage` 共享 composable
  - Model selector, tools, workspace, skills 等编辑逻辑
  - 提取为无状态 composable + ViewModel 参数
  - 验证：全局和助手级 profile 编辑均正常

### Phase 4: 助手级引用全局 profile

- [ ] **4.1** `AssistantSubagentPage.kt` — 全局 profile 引用 UI
  - 列表区分为「本助手配置」+「全局配置」（标记来源）
  - 全局 profile 不可在助手级编辑（跳转扩展管理页编辑）
  - 助手级仍可禁用特定全局 profile
  - 验证：引用逻辑 + 来源标识清晰

### Phase 5: 集成验证

- [ ] **5.1** 端到端测试
  - 全局 profile CRUD
  - 助手级引用全局 profile
  - 全局 profile 编辑后助手级自动同步
  - 查找优先级正确
  - 旧数据兼容
  - 验证：`.\gradlew :app:installDebug` + 真机

---

## Validation Commands

```bash
.\gradlew :app:compileDebugKotlin
.\gradlew :app:installDebug
```

## Files to Modify/Create

| File | Action | Change |
|------|--------|--------|
| `app/.../PreferencesStore.kt` | Modify | `globalSubagentProfiles` 字段 |
| `app/.../SubagentRegistry.kt` | Modify | 扩展 `resolveProfile` |
| `app/.../RouteActivity.kt` | Modify | 新 Screen + entry |
| `app/.../SettingPage.kt` | Modify | 扩展管理入口 |
| `app/.../ExtensionManagementPage.kt` | Create | 全局 profile 管理页 |
| `app/.../AssistantSubagentPage.kt` | Modify | 全局 profile 引用 UI |
| `app/.../AssistantSubagentProfilePage.kt` | Refactor | 提取共享 composable |

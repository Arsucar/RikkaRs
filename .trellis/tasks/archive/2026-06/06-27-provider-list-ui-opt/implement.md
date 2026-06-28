# 优化提供商列表UI信息展示 — 实施计划

## 执行顺序

### Phase 1: 数据层

- [ ] **1.1** `ProviderSetting.kt` — 所有 sealed 子类增加 `tags: List<String> = emptyList()`
  - 各 `copyProvider(...)` 增加 `tags` 参数
  - 验证：`.\gradlew :ai:compileDebugKotlin`

- [ ] **1.2** 确认序列化迁移兼容
  - 旧 JSON 无 `tags` 字段 → 默认空列表
  - 验证：安装 debug + 检查已有 provider 数据不丢失

### Phase 2: 全屏 Sheet

- [ ] **2.1** `ModelList.kt` — `ModelListSheet` 全屏化
  - `fillMaxHeight(0.8f)` → `fillMaxHeight()`
  - `skipPartiallyExpanded = true`
  - 内部 `Column padding + fillMaxHeight` → `fillMaxSize`
  - 验证：编译 + 视觉确认全屏

### Phase 3: Provider Tab 展开能力

- [ ] **3.1** `ModelList.kt` — Provider tab 区域改造
  - 新增 `providerTabsExpanded` 状态
  - 折叠态：`LazyRow`（现有）+ 展开按钮
  - 展开态：`FlowRow` 全部 chips + 折叠按钮
  - 全部展开/折叠按钮
  - 验证：tab 展开/折叠交互正常 + 选择滚动定位正常

### Phase 4: Provider 分组折叠

- [ ] **4.1** `ModelList.kt` — 每个 provider header 可折叠
  - `mutableStateMapOf` 记录展开状态
  - Header 右侧箭头 + click 切换
  - 折叠时跳过该 provider 的 model items（或用 `item(span)` 零高度）
  - 验证：分组折叠/展开正常

### Phase 5: ProviderItem 增强

- [ ] **5.1** `SettingProviderPage.kt` — `ProviderItem` 信息增强
  - 新增 base URL 摘要行
  - model count 改为 `X/Y chat` 格式
  - 显示 provider.tags 作为 FilterChip
  - 验证：视觉审查

- [ ] **5.2** `SettingProviderPage.kt` — 标签筛选条
  - 顶部 `LazyRow` / `FlowRow` of `FilterChip`（全部 + 所有已知 tag）
  - 选中 tag → 过滤 provider 列表
  - 验证：标签筛选功能正常

### Phase 6: Provider 标签编辑

- [ ] **6.1** `SettingProviderDetailPage.kt` — 配置 tab 增加标签编辑
  - `FlowRow` + `FilterChip` 展示当前 tags
  - 添加按钮 + 输入框 / 预设建议 chips
  - 删除（× 按钮）
  - 保存到 `SettingsStore`
  - 验证：标签 CRUD 正常

### Phase 7: 集成验证

- [ ] **7.1** 端到端测试
  - 模型选择全屏 sheet 正常
  - Provider tab 展开折叠
  - 分组折叠 + 滚动定位
  - 自定义标签筛选
  - 旧数据兼容
  - 验证：`.\gradlew :app:installDebug` + 真机测试

---

## Validation Commands

```bash
.\gradlew :ai:compileDebugKotlin           # Phase 1
.\gradlew :app:compileDebugKotlin          # Phase 2–5
.\gradlew :app:installDebug                # Phase 6–7
```

## Risk / Rollback Points

| Risk | Mitigation |
|------|------------|
| `tags` 字段导致序列化不兼容 | 默认空列表 + kotlinx.serialization 忽略缺失字段 |
| 全屏 sheet 在小屏设备上体验差 | 保留 `ModalBottomSheet` 滑动关闭手势 |
| `FlowRow` 性能（大量 provider chips） | provider 数通常 < 20，无需虚拟化 |
| 筛选与搜索冲突 | 筛选 + 搜索取交集 |

## Files to Modify

| File | Change |
|------|--------|
| `ai/.../ProviderSetting.kt` | `tags` 字段 + `copyProvider` |
| `app/.../ModelList.kt` | 全屏 sheet + tab 展开 + 分组折叠 |
| `app/.../SettingProviderPage.kt` | ProviderItem 增强 + 标签筛选 |
| `app/.../SettingProviderDetailPage.kt` | 标签编辑 UI |

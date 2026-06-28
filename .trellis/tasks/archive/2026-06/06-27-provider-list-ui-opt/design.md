# 优化提供商列表UI信息展示 — 技术设计

## Architecture & Boundaries

### 改动范围

1. **ModelListSheet** — 半屏 → 全屏；provider tab 单行滑动 → 可展开列表
2. **SettingProviderPage** — ProviderItem 增加信息密度；支持自定义标签筛选
3. **ProviderSetting** — 新增 `tags` 序列化字段

### 不改动的部分

- `ModelItem` 行布局（已足够信息密度）
- `ModelSelector` 触发方式（仍用 `state.open()`）
- 收藏/搜索逻辑
- 导航路由（ModelListSheet 仍是嵌入式组件，非新 Screen）

---

## Data Flow

### Provider Tags

```
ProviderSetting (sealed) 各变体
  → 新增 tags: List<String> = emptyList()
  → 序列化到 DataStore
  → SettingProviderPage 读取并按 tag 分组/筛选
  → ProviderDetailPage 编辑 tags
```

---

## Contracts

### ProviderSetting 变更

所有 sealed 子类（`OpenAI`, `Google`, `Claude`）新增：

```kotlin
val tags: List<String> = emptyList()
```

`copyProvider(...)` 增加 `tags` 参数（默认 `this.tags`）。

**序列化兼容性**：默认空列表，旧数据加载时自动补全。

### Tag 预设建议

```kotlin
val SUGGESTED_PROVIDER_TAGS = listOf("常用", "便宜", "长上下文", "高质量", "免费额度", "国产")
```

---

## UI Design

### R1: 全屏 ModelListSheet

**现有**：`ModalBottomSheet` + `fillMaxHeight(0.8f)` + `SheetValue { Hidden, Expanded }`

**目标**：全屏对话框

```kotlin
@Composable
fun ModelListSheet(...) {
    // 方案: 改为 Dialog 或全屏 ModalBottomSheet
    // ModalBottomSheet + skipPartiallyExpanded + fillMaxHeight()
    ModalBottomSheet(
        onDismissRequest = { state.close() },
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),  // 关键变化
    ) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            ModelList(...)
        }
    }
}
```

- 使用 `skipPartiallyExpanded = true` 跳过半屏
- `fillMaxHeight()` 让 sheet 完全展开
- 从 `Column(padding + fillMaxHeight(0.8f))` 改为 `Column(fillMaxSize)`

### R2: Provider Tab 可展开

**现有**：底部 `LazyRow` 单行水平滑动（L616–662）

**目标**：

```
┌─────────────────────────────┐
│ [全部] [OpenAI] [Google] ... │  ← 折叠态：单行 chips（同现有）
│                       [▼]   │  ← 展开按钮
├─────────────────────────────┤
│ 搜索栏                      │
│ 收藏 │ ProviderA │ ...      │
└─────────────────────────────┘

点击 ▼ 后：

┌─────────────────────────────┐
│ [全部] [OpenAI] [Google] ... │
│ [Anthropic] [DeepSeek] ...  │  ← 展开态：多行 FlowRow
│                        [▲]  │
├─────────────────────────────┤
│ 搜索栏                      │
│ ...                         │
└─────────────────────────────┘
```

**实现**：

- `LazyRow` → `FlowRow`（当展开时） / `LazyRow`（当折叠时）
- 状态：`var providerTabsExpanded by remember { mutableStateOf(false) }`
- 折叠态显示前 N 个 chips + 展开按钮
- 展开态显示全部 chips + 折叠按钮
- 单个 provider chip 仍可点击滚动到对应位置

**全局「全部展开/折叠」**：

- 在 Provider tab 区域右侧添加 `IconButton`（`ExpandMore` / `ExpandLess`）
- 此按钮控制 tab 展开和 provider 分组的 list 折叠联动

### R3: 单个 Provider 分组折叠/展开

**现有**：`LazyColumn` stickyHeader（L538–559）不可折叠

**目标**：每个 provider header 可点击折叠/展开

- 新增 `mutableStateMapOf<String, Boolean>()` 记录各 provider 展开状态
- Header 点击切换展开/折叠
- 折叠时隐藏该 provider 下的 model items
- Header 右侧添加展开/折叠箭头

### R4: ProviderItem 信息增强

**现有**：icon + name + shortDescription + enabled/disabled tag + model count

**目标增加**：

- base URL 摘要（如 `api.openai.com`，截取域名部分）
- chat 模型数 vs 总模型数（如 `5/8 chat`）

```kotlin
FlowRow {
    Tag { enabled/disabled }
    Tag { "${chatModelCount}/${totalModels} chat" }  // 替换纯 model count
    provider.tags.forEach { tag -> Tag { tag } }
}
```

### R5: 自定义标签

- `ProviderDetailPage` 配置 tab 增加标签编辑区
- 使用 `FlowRow` + `FilterChip` 展示已有标签 + 添加按钮
- 输入新标签或从 `SUGGESTED_PROVIDER_TAGS` 选择
- 长按 / 点击 × 删除标签

- `SettingProviderPage` 顶部增加 tag 筛选条（`FilterChip` 横向滑动）
- 筛选逻辑：选中 tag → 仅显示含该 tag 的 provider；"全部" → 显示所有

---

## Compatibility & Migration

- `tags: List<String> = emptyList()` 有默认值，旧 `ProviderSetting` 数据加载无破坏
- `copyProvider` 所有调用处需加入 `tags = this.tags`（或使用命名参数默认值）
- `fillMaxHeight()` 打破了依赖 `0.8f` 固定高度的布局 — 但 inner `LazyColumn` 用 `weight(1f)` 不受影响

---

## Trade-offs

| Decision | Choice | Alternative | Why |
|----------|--------|-------------|-----|
| 全屏方式 | `ModalBottomSheet` + `fillMaxHeight` | `Dialog` | 保留现有 sheet 动画 + dismiss 手势 |
| Provider tab 展开 | `FlowRow` 切换 | 弹出 `DropdownMenu` | FlowRow 更直观、信息更暴露 |
| 标签存储位置 | `ProviderSetting.tags` | 独立 tag store | 简单、跟随 provider CRUD 生命周期 |
| 序列化方案 | `List<String>` 字段 | 序列化逗号分隔 string | JSON 原生支持，可扩展 |

---

## Rollback

- Sheet 高度改回 `fillMaxHeight(0.8f)` 即还原
- `tags` 字段默认空不影响现有逻辑
- 折叠/展开状态是 UI 状态（`remember`），不持久化，重启恢复全展开

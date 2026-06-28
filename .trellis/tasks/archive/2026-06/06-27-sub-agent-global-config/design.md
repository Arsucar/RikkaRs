# 子代理全局配置入口（扩展管理） — 技术设计

## Architecture & Boundaries

### 核心改动

1. **Settings** 新增 `globalSubagentProfiles: List<SubagentProfile>` 全局子代理 profile 存储
2. **设置页** 新增「扩展管理」入口 → 全局子代理 profile 管理页
3. **助手级子代理配置** 可引用全局 profile
4. **查找优先级**：assistant-local → global → builtin

### 不改动的部分

- 现有 `Assistant.subagentProfiles` 不删除或迁移
- `SubagentRegistry.resolveProfile` 逻辑保持，扩展查找链
- 内置 profile 仍硬编码在 `SubagentRegistry.BUILTIN_PROFILES`

---

## Data Flow

### Profile 查找链

```
resolveProfile(name, assistant, settings)
  1. assistant.subagentProfiles.find { it.name == name }  → 本地 custom
  2. settings.globalSubagentProfiles.find { it.name == name }  → 全局 custom
  3. SubagentRegistry.BUILTIN_PROFILES.find { it.name == name }  → 内置
  4. null → not found
```

`resolveProfile` 签名扩展：增加 `settings: Settings` 参数（或 `globalProfiles: List<SubagentProfile>`）。

### 全局 Profile 引用

- 助手级子代理页列出：本助手 custom profiles + 全局 profiles（标记来源）
- 选择全局 profile 时，不复制 profile 内容到 assistant，仅使用全局定义
- 全局 profile 被编辑 → 所有引用它的助手下次使用时自动读取最新版

---

## Contracts

### Settings 变更

```kotlin
data class Settings(
    ...
    val globalSubagentProfiles: List<SubagentProfile> = emptyList(),
    ...
)
```

**序列化兼容性**：默认空列表，旧数据无影响。

### SubagentProfile 复用

全局 profile 和助手级 profile 使用相同的 `SubagentProfile` 数据类，无需新类型。全局 profile 的 `name` 须在全局范围内唯一。

### 导航路由

```kotlin
object ExtensionManagement : Screen(route = "setting/extension_management")
object ExtensionSubagentProfile : Screen(route = "setting/extension_subagent_profile/{profileId}")
```

---

## UI Design

### 设置页 — 扩展管理入口

在 `SettingPage.kt` 的设置项列表中，在「提供商」和「模型」附近增加：

```
🔧 扩展管理
   子代理配置、MCP工具（未来）
```

### 扩展管理页

```
┌─────────────────────────────┐
│ ← 扩展管理                  │
├─────────────────────────────┤
│ 🤖 子代理 Profile            │
│   [添加] [推荐]              │
│ ┌─────────────────────────┐ │
│ │ Researcher  模型: GPT-4o │ │
│ │ Coder       模型: Claude │ │
│ │ ...                     │ │
│ └─────────────────────────┘ │
│                             │
│ （未来: MCP 工具管理）       │
└─────────────────────────────┘
```

### 全局 Profile 编辑页

复用现有 `AssistantSubagentProfilePage` 的布局（model selector, tools, workspace, skills 等），或提取共享 composable。

---

## Compatibility & Migration

- 旧版无 `globalSubagentProfiles` → 默认空列表
- 助手级 custom profile 不受影响
- `resolveProfile` 向后兼容：不传 settings 时只查 local + builtin（旧调用路径）

---

## Trade-offs

| Decision | Choice | Alternative | Why |
|----------|--------|-------------|-----|
| 存储位置 | `Settings.globalSubagentProfiles` | 独立 DataStore | 复用现有序列化 + 迁移简单 |
| Profile 类型 | 复用 `SubagentProfile` | 新建 `GlobalSubagentProfile` | 减少类型重复 |
| 引用方式 | 运行时查找（无硬引用） | 存 profileId 引用 | 避免悬空引用 + profile name 即标识 |
| 页面复用 | 提取共享 composable | 完全独立页面 | 减少代码重复 |

---

## Rollback

- 移除「扩展管理」入口不影响其他设置项
- `globalSubagentProfiles` 默认空列表，不读取即无效
- `resolveProfile` 可移除 global 查找步骤

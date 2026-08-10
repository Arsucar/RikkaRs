# Design: feat(#247) bind-mount 浏览器

## 边界

| In | Out |
|---|---|
| WorkspaceManager LINUX list/read/size/export 重定向 | 写/删/导入映射 |
| Repository 注入 skills_private extra mount | 终端 PRoot 挂载改动 |
| Detail VM/Page 入口助手选择与标注 | 复制挂载源到 FILES |

## 目标数据流

```text
WorkspaceDetailVM.refresh
  → WorkspaceRepository.listFiles(id, LINUX, path, extraMounts?)
  → WorkspaceManager.listFiles(root, path, LINUX, extraMounts)
       abs = "/" + path.trim('/')
       if bind target (ctor mounts + extra):
         fileSystem.list(mount.source, relative)
       else if /workspace:
         fileSystem.list(filesDir, relative)
       else:
         fileSystem.list(linuxDir, path)
  → entries（区域相对 path 供导航）
```

`readText` / `fileSize` / `exportFile` 在 `area == LINUX` 时同样走 `resolveRootfsPath`（或共享 helper）。

## 组件设计

### 1. WorkspaceManager

**文件**: `workspace/src/main/java/.../WorkspaceManager.kt`

- 暴露 `bindMounts()`（构造器全局挂载表）
- LINUX 路径 → rootfs 绝对路径 → 复用 `resolveRootfsPath` 前缀匹配（`== target || startsWith("$target/")`）
- 源不存在：返回空列表，不 throw（与 AC7）
- kernel 路径 `/dev` `/proc` `/sys`：保持现有 error/非映射行为（AC6）

### 2. skills_private extra mount

**形态对齐** `ChatService.assistantPrivateSkillMounts`（约 `ChatService.kt:2117-2137`）：

```text
WorkspaceBindMount(source = assistantSkillsDir, target = "/skills_private")
```

**助手解析**（纯函数，易测）:

```text
assistants.filter { it.workspaceId?.toString() == workspace.id }
  1 → that id
  0 → Settings.getCurrentAssistant() + UI flag currentAssistantFallback
  N → require selection (default current if in set, else first + picker)
```

**文件**:

- `app/.../data/repository/WorkspaceRepository.kt` — 组装 extra mount，传入 manager
- `app/.../data/files/SkillManager.kt` — 复用 `getAssistantSkillsDir`（通常不改 API）
- 小 helper（PreferencesStore 旁或独立）：`assistantsBoundTo(workspaceId)`

### 3. Presentation

**文件**:

- `app/.../ui/.../WorkspaceDetailVM.kt` — bound assistants、selected skills_private assistant、refresh on change
- `app/.../ui/.../WorkspaceDetailPage.kt` — 可选挂载角标、LINUX 路径栏 `/` 前缀、多助手 picker /「当前助手」标签
- `app/src/main/res/values*/strings.xml` — 角标/当前助手/picker 中英

### 4. 测试

- `workspace/.../RootfsPathResolutionTest.kt` — list/read 重定向、空源、workspace 映射、kernel 不变
- 新 app 测试 e.g. `SkillsPrivateEntryAssistantTest.kt` — 0/1/many 解析

## 挂载表来源

| Target | Source | 今日接线 |
|---|---|---|
| `/skills` `/tool_outputs` `/upload` | RepositoryModule bindMounts | 全局 ctor |
| `/workspace` | files area | `resolveRootfsPath` 特例 |
| `/skills_private` | assistant_skills/<id> | 仅 ChatService extra；浏览器需补 |

## 边缘

- 若 rootfs 占位目录缺失：AC1 可能需从 `bindMounts()` **合成** 根条目（issue 假定占位存在；实现时若空 root 则合成）
- 归档助手：按源不存在 → 空态
- 不缓存绑定快照

## 兼容 / 回滚

- 只读行为不变 → 安全回滚为还原 Manager list 路径
- 无 DB migration

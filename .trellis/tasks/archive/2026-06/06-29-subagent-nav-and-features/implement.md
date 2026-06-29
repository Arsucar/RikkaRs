# Implement: Subagent nav restructure + parallel/delegate/extraLocalTools

**Task:** `06-29-subagent-nav-and-features`
**执行顺序**：parallel（轻）→ 导航重组（UI）→ delegate（中）→ extraLocalTools（中-重）
**复杂度递增、风险后置**；每 step 后即验证编译，失败即止。

> 所有 gradle 命令带 `--no-daemon`。子代理派发：每个 step 独立子代理，**仅最后一个子代理允许真正编译通过**（避免主机内存耗尽）；中间 step 只做编辑，编译留到最后统一验证。

---

## Step 0: 前置验证（不改代码）

- [ ] 0.1 grep `buildSubagentTools(` 全部调用点，记录 file:line（design §8 风险）
- [ ] 0.2 核实 `createWorkspaceToolsIfReady` 签名是否已有 `readOnly` 参数；若无，标记需扩展
- [ ] 0.3 核实 `LocalTools.getTools(options)` 签名（确认 `List<LocalToolOption> -> List<Tool>`）
- [ ] 0.4 把 0.1-0.3 结论回填到本文件的"实现期核实结果"小节（下方预留）

**实现期核实结果：**
```
0.1 buildSubagentTools( 调用点：
    - 定义: SubagentPermissionBuilder.kt:114
    - 调用: ChatService.kt:1777 (在 toolsForSubagentProfile L1673 内)
0.2 createWorkspaceToolsIfReady 签名 (ChatService.kt:780):
    private suspend fun createWorkspaceToolsIfReady(workspaceId: String?, cwd: String? = null): List<Tool>
    → 无 readOnly 参数，需扩展为 (workspaceId, cwd, readOnly: Boolean = false)
    → 内部调用 createWorkspaceTools(workspaceId, workspaceRepository, cwd) (L790)
    → 需进一步核实 createWorkspaceTools 是否支持 readOnly（实现期 step 3 处理）
0.3 LocalTools.getTools 确认 (LocalTools.kt:22):
    fun getTools(options: List<LocalToolOption>): List<Tool>  ✅
```

---

## Step 1: parallelToolExecution（轻，先跑通最小路径）

**触及文件**：
- `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`
- `app/src/main/res/values/strings.xml` + `values-zh/strings.xml`（占位先加，UI 在 Step 2 接）

- [ ] 1.1 `Assistant.kt` 在 `subagentMaxDepth` 之后加 `parallelToolExecution: Boolean = false`（design §1.1）
- [ ] 1.2 `GenerationHandler.kt` L258 改 `runInParallel` 公式（design §2.1）
- [ ] 1.3 strings 加 `subagent_parallel_execution_title` / `_desc`（en+zh）
- [ ] 1.4 **不单独编译**（留给 Step 4 统一编译，避免内存峰值）

---

## Step 2: 导航重组 + parallel/delegate UI（D2）

**触及文件**：
- 删 `app/src/main/java/me/rerere/rikkahub/pages/assistant/AssistantSubagentHubSection.kt`
- `AssistantDetailPage.kt`（移除 hub controls item）
- `AssistantSubagentPage.kt`（内联首 Card + 4 项控件）
- `Assistant.kt`（补 `subagentDelegateOnly` 字段）
- strings（加 delegate 字符串）

- [ ] 2.1 `Assistant.kt` 加 `subagentDelegateOnly: Boolean = false`（紧邻 parallelToolExecution，design §1.1）
- [ ] 2.2 删除 `AssistantSubagentHubSection.kt`
- [ ] 2.3 `AssistantDetailPage.kt` 移除 `AssistantSubagentHubControls` 的 `item { }`（L156-161），保留入口行
- [ ] 2.4 `AssistantSubagentPage.kt` 在 `AssistantSubagentContent` 首部、profiles section 之前插入 Card（design §5.2）：
  - FormItem Switch 启用 (`enableSubagents`)
  - HorizontalDivider
  - FormItem Slider 深度 (`subagentMaxDepth`, **1f..5f, steps=3, coerceIn(1,5)**)
  - HorizontalDivider
  - FormItem Switch 仅委派 (`subagentDelegateOnly`, `enabled = enableSubagents`)
  - HorizontalDivider
  - FormItem Switch 并行 (`parallelToolExecution`, `enabled = enableSubagents`)
- [ ] 2.5 strings 加 `subagent_delegate_only_title` / `_desc`（en+zh）
- [ ] 2.6 全局 grep `AssistantSubagentHubControls` 确认无残留引用
- [ ] 2.7 **不单独编译**

---

## Step 3: subagentDelegateOnly 运行时（D1）

**触及文件**：
- `ChatService.kt`
- `SubagentTools.kt`

- [ ] 3.1 `ChatService.kt` 顶层加私有常量 `DELEGATE_ALLOWED_LOCAL_TOOLS`（design §3.1 白名单：TimeInfo/Clipboard/Logs/AskUser）
- [ ] 3.2 `ChatService.kt` 根 `buildList` 内加 `delegateOnly` 局部变量与 effective localTools 过滤（design §3.2）
- [ ] 3.3 workspace 步骤：delegate 时传 `readOnly = true`（若 0.2 显示需扩展，先扩展 `createWorkspaceToolsIfReady` 签名 + 默认 false）
- [ ] 3.4 skills / MCP 步骤用 `if (!delegateOnly) { ... }` 包裹跳过
- [ ] 3.5 `SubagentTools.kt` `createSubagentTools` 加 `delegateOnly: Boolean = false` 参数（design §3.3）
- [ ] 3.6 `SubagentTools.kt` spawn_subagent `systemPrompt` 加 delegate orchestrator 文案分支（design §3.3）
- [ ] 3.7 `ChatService.buildSubagentToolsForChat` 加 `delegateOnly` 形参并透传到 `createSubagentTools`
- [ ] 3.8 `ChatService.kt` L704 调用处传 `delegateOnly = delegateOnly`（depth==0）
- [ ] 3.9 子代理递归调用（depth>0）恒传 `delegateOnly = false`
- [ ] 3.10 **不单独编译**

---

## Step 4: extraLocalTools（中-重）

**触及文件**：
- `SubagentProfile.kt`
- `SubagentHost.kt`
- `SubagentPermissionBuilder.kt`
- `ChatService.kt`（或 `buildSubagentTools` 调用点，依 0.1 结果）
- `AssistantSubagentProfilePage.kt`
- strings

- [ ] 4.1 `SubagentProfile.kt` 紧邻 `localTools` 加 `extraLocalTools: List<LocalToolOption> = emptyList()`（design §1.2）
- [ ] 4.2 `SubagentHost.kt` `buildChildAssistant` inherit 分支加 `addAll(profile.extraLocalTools)`（design §4.1，在 `addAll(parent.localTools)` 之后、`removeAll AskUser` 之前）
- [ ] 4.3 `SubagentPermissionBuilder.kt` `buildSubagentTools` 加形参 `extraLocalToolsProvider: () -> List<Tool> = { emptyList() }`（design §4.2）
- [ ] 4.4 inherit 分支注入 extras：`extras = extraLocalToolsProvider().filter { 非workspace }.filter { 非excluded }`；`base = nonWorkspaceParent + extras`（design §4.3 顺序 base→extra→exclude）
- [ ] 4.5 **不动** non-inherit 分支（design §4.5）
- [ ] 4.6 在 `buildSubagentTools` 所有调用点（依 0.1）传入 `extraLocalToolsProvider = { if (profile.inheritTools) localTools.getTools(profile.extraLocalTools) else emptyList() }`
- [ ] 4.7 `AssistantSubagentProfilePage.kt` L515-546 `if (resolved.inheritTools)` 分支内、`excluded_tools` 之后加 extraLocalTools 多选区块（design §6）；候选 = `LocalToolOption.entries - AskUser`
- [ ] 4.8 strings 加 `subagent_profile_extra_local_tools_title` / `_desc`（en+zh）
- [ ] 4.9 **不单独编译**

---

## Step 5: 统一编译验证（仅此步允许编译通过）

> 子代理策略：Step 1-4 各派独立子代理仅做编辑；Step 5 主代理亲自编译。

- [ ] 5.1 `.\gradlew :app:compileDebugKotlin --no-daemon` 通过
- [ ] 5.2 若失败，定位错误并修（常见：strings key 拼写、`delegateOnly` 未透传、`extraLocalToolsProvider` 调用点遗漏）
- [ ] 5.3 `adb devices` 确认设备
- [ ] 5.4 `.\gradlew :app:installDebug --no-daemon` 成功

---

## Step 6: 真机验收（对照 PRD 验收标准）

- [ ] 6.1 打开助手详情 → 确认**无**启用/深度控件，只有"子代理"入口行
- [ ] 6.2 进入子代理页 → 顶部 Card 含 4 项（启用/深度 1..5/delegate/parallel），风格统一
- [ ] 6.3 开启 `parallelToolExecution` → 触发一次让模型发 2 个搜索 tool call 的请求 → 日志确认 `executing N tools in parallel`
- [ ] 6.4 开启 `subagentDelegateOnly` → 让模型回答一个需执行的问题 → 确认根代理仅 spawn_subagent + 只读工具（无 MCP/skills/可写 workspace）
- [ ] 6.5 delegate 模式下查看 spawn_subagent systemPrompt → 确认含 "Delegation-Only Mode" 文案
- [ ] 6.6 某 profile `inheritTools=true` + 选 1 个 extra local tool → spawn 该 profile → 子代理能用该 extra tool
- [ ] 6.7 同 profile `inheritTools=false` → extraLocalTools 区块不显示；子代理不注入 extra
- [ ] 6.8 extra 工具名加入 `excludedTools` → 子代理不注入该 extra tool（excluded 优先）
- [ ] 6.9 旧助手数据（升级前已存在）打开子代理页不崩（默认值生效）

---

## 回滚点

| step | 回滚动作 |
|------|----------|
| 1-4 | 单 commit；`git checkout -- <files>` 即回滚到现状 |
| 5 编译失败 | 修到通过为止；不回滚 design（除非发现 design 缺陷，此时回 Phase 1 修 design.md） |
| 6 真机失败 | 视严重度：bug → 修；design 缺陷 → 回 Phase 1 |

---

## 子代理派发清单（dispatch 模式）

> 若使用子代理派发，按 step 拆分；每个子代理 prompt 必须以 `Active task: .trellis/tasks/06-29-subagent-nav-and-features` 开头，并附 design.md 对应小节。

| 子代理 | 范围 | design 小节 | 允许编译？ |
|--------|------|------------|-----------|
| step-1-parallel | parallel 字段+公式+strings | §1.1, §2.1 | 否 |
| step-2-nav | 导航+UI+delegate 字段+strings | §1.1, §5 | 否 |
| step-3-delegate | ChatService+SubagentTools 运行时 | §3 | 否 |
| step-4-extra | Profile+Host+PermissionBuilder+ProfilePage | §1.2, §4, §6 | 否 |
| 主代理 step-5 | 统一编译+装设备 | — | **是** |

---

## 验证命令速查

```powershell
# 编译
.\gradlew :app:compileDebugKotlin --no-daemon
# 装设备
.\gradlew :app:installDebug --no-daemon
# grep 调用点（step 0.1）
rg "buildSubagentTools\(" app/src/main/java
rg "createWorkspaceToolsIfReady" app/src/main/java
rg "AssistantSubagentHubControls" app/src/main/java
```

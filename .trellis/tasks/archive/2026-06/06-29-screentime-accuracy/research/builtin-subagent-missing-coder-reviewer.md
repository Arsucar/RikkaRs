# Research: 默认子代理只剩 explore（coder/reviewer 丢失）

- **Query**: fork `release/rikka-arsucar` 合并子代理后，用户侧只剩 explore，coder/reviewer 被删；查根因与恢复路径
- **Scope**: internal（代码 + git 历史 + DataStore 迁移）
- **Date**: 2026-06-29

## Findings

### 1. 当前 `BUILTIN_PROFILES` 实际内容（代码层）

**结论：源码里仍是 3 个内置 profile，没有 commit 删掉 coder/reviewer。**

| name | displayName | 备注 |
|------|-------------|------|
| `explore` | Explorer | working tree 仅多了 explore 的 `systemPrompt` 文案（未提交） |
| `coder` | Coder | 与 `HEAD` 一致 |
| `reviewer` | Reviewer | 与 `HEAD` 一致 |

- 文件：`app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentRegistry.kt`
- `git diff HEAD` 对该文件：**仅** `explore` 的 `systemPrompt` 增加验证/总结段落，**无** profile 增删。
- 自 `36f4e7d3 feat(subagent): add data model + settings UI (Phase A)` 起，`BUILTIN_PROFILES` 即为 explore/coder/reviewer 三个。

### 2. Git 历史：谁改了 Registry / 全局子代理模型

| Commit | 对子代理的影响 |
|--------|----------------|
| `36f4e7d3` | 引入 `BUILTIN_PROFILES`（3 个）+ `builtinByName` 解析 |
| `6dca48a1` | **里程碑**：内置改为走 `Settings.globalSubagentProfiles`；`resolveProfile`/`allProfiles` 不再直接读 `BUILTIN_PROFILES`（除非 fallback） |
| `8be9f419` | 增加 `effectiveGlobalProfiles(global)`：`global` **整表为空** 时回退 `BUILTIN_PROFILES` |
| `3fcb369d` / `17f0b37a` 等 | ChatService/测试等，未删减 Registry 内置列表 |

**没有任何 commit 从 `SubagentRegistry.kt` 移除 `coder` 或 `reviewer`。**

### 3. 架构变更根因（「合并子代理」后用户看到的行为）

`6dca48a1` 起，UI 与运行时默认列表来源从「硬编码内置」变为 **DataStore 持久化的 `globalSubagentProfiles`**：

```635:656:app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt
internal fun migrateSubagentBuiltinsIfNeeded(settings: Settings): Settings {
    if (settings.init || settings.subagentBuiltinMigrated) {
        return settings
    }
    val existingNames = settings.globalSubagentProfiles.map { it.name }.toSet()
    val toAdd = SubagentRegistry.BUILTIN_PROFILES.filter { it.name !in existingNames }
    val globalSubagentProfiles = settings.globalSubagentProfiles + toAdd
    // ... disabledBuiltin -> disabledGlobal ...
    return settings.copy(
        globalSubagentProfiles = globalSubagentProfiles,
        subagentBuiltinMigrated = true,
        assistants = assistants,
    )
}
```

- 扩展页列表：`ExtensionSubagentsPage` 直接 `val profiles = settings.globalSubagentProfiles`（**不**再单独展示 `BUILTIN_PROFILES`）。
- 删除全局子代理：`SettingVM.deleteGlobalSubagent` 从 `globalSubagentProfiles` **物理移除** 该项（无「内置不可删」保护）。

```28:44:app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingVM.kt
    fun deleteGlobalSubagent(name: String) {
        settingsStore.update { settings ->
            settings.copy(
                globalSubagentProfiles = settings.globalSubagentProfiles.filter { it.name != name },
            )
        }
    }

    fun restoreDefaultSubagents() {
        // 仅把 BUILTIN 里「当前 global 中不存在」的 name 补回
        val toAdd = SubagentRegistry.BUILTIN_PROFILES.filter { it.name !in existingNames }
        settings.copy(globalSubagentProfiles = settings.globalSubagentProfiles + toAdd)
    }
```

### 4. 为何运行时也会「只剩 explore」（不仅是 UI）

`effectiveGlobalProfiles` **只在 `globalSubagentProfiles` 完全为空时** 才回退三个内置：

```67:68:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentRegistry.kt
    internal fun effectiveGlobalProfiles(global: List<SubagentProfile>): List<SubagentProfile> =
        global.ifEmpty { BUILTIN_PROFILES }
```

若 DataStore 里是 **`[explore]` 一条**（coder/reviewer 曾被删除）：

- 扩展页只显示 explore ✓
- `resolveProfile("coder", …)` / `mergeSubagentProfiles` **也拿不到** coder/reviewer（不会回退 BUILTIN）
- 与「默认子代理被删掉」的用户描述一致

### 5. coder/reviewer 丢失的根因归类

| 假设 | 是否成立 | 说明 |
|------|----------|------|
| 代码层删掉 BUILTIN 里的 coder/reviewer | **否** | Registry 始终 3 个；git 无删除 commit |
| migration 只写入 explore | **否** | `toAdd` 来自完整 `BUILTIN_PROFILES`；测试 `migration_copiesBuiltinToGlobal` 要求 `containsAll(builtinNames)` |
| migration 未跑 / 已迁移但 global 被改 | **是（持久化）** | `subagentBuiltinMigrated=true` 后 migration **不再**自动补全；global 以 DataStore 为准 |
| 用户/操作删除了 global 中的 coder、reviewer | **最符合** | `deleteGlobalSubagent` 无内置保护；删后只剩 explore 典型 |
| working tree 未提交改动导致 | **否** | 未提交改动仅 explore prompt 文案 |
| 助手级 `disabledGlobalSubagents` | **部分** | 只影响该助手是否启用，**不**让扩展页 global 列表少项；若用户看的是助手子代理页且两项被 disable，可能误以为「没了」，但 global 设置页仍应能看到三项（除非已从 global 删除） |

**综合根因**：`6dca48a1` 子代理全局化合并后，**默认可用集合 = `globalSubagentProfiles` 持久化内容**；内置三件套仅在「global 整表为空」时 fallback。一旦 global 里只留了 explore（例如删过 coder/reviewer，或从未成功持久化完整迁移后又手动改过），就会出现「只剩 explore」，且 **不是** `SubagentRegistry` 删了定义。

### 6. 与「刚刚合并」时间线的关系

- 「合并」对应 `6dca48a1`（subagent global config + Extension 子代理页 + migration）。
- 归档任务 `06-28-06-28-review-all-changes` 已记录：global 非空时不再隐式合并 BUILTIN（`effectiveGlobalProfiles` 在 `8be9f419` 才补上 **仅 empty** 的 fallback）。
- 用户说的「被删除两个默认子代理」更符合 **DataStore 列表被删/不完整**，而非 Registry 合并写错成只 migrates explore。

### 7. 恢复方案建议（按根因，不改代码仅建议）

1. **用户侧立刻恢复（已有功能）**  
   扩展 → 子代理页 → 溢出菜单 **「恢复默认子代理」**（`restoreDefaultSubagents`），会把 `BUILTIN_PROFILES` 中缺失的 `coder`、`reviewer` 追加回 `globalSubagentProfiles`。

2. **若菜单恢复无效**  
   - 查 DataStore：`global_subagent_profiles` JSON 是否仅含 explore；`subagent_builtin_migrated` 是否为 true。  
   - 可清除应用数据或手动把三项 profile 写回（开发/调试）。

3. **产品/代码加固（后续 implement，非本调研范围）**  
   - 对 `explore`/`coder`/`reviewer` 禁止 `deleteGlobalSubagent` 或改为「重置为内置默认」而非移除。  
   - 或：`effectiveGlobalProfiles` 在 global 非空时仍 **union** 缺失的 BUILTIN names（与 restore 逻辑一致，启动时自动兜底）。  
   - 或：migration 在版本升级时 re-sync 缺失 builtin（即使 `subagentBuiltinMigrated` 已为 true）。

### 8. 相关文件清单

| 路径 | 角色 |
|------|------|
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentRegistry.kt` | `BUILTIN_PROFILES`、`effectiveGlobalProfiles`、`resolveProfile` |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` | `GLOBAL_SUBAGENT_PROFILES`、`migrateSubagentBuiltinsIfNeeded`、settings flow |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingVM.kt` | `deleteGlobalSubagent`、`restoreDefaultSubagents` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionSubagentsPage.kt` | 展示/删除 global 列表、恢复默认入口 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/SubagentUiHelpers.kt` | `subagentListEntries`（assistant + global） |
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt` | `mergeSubagentProfiles` |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | `manageSubagentProfile` delete → `disabledGlobalSubagents`（助手级禁用，非 global 删） |
| `app/src/test/java/me/rerere/rikkahub/data/ai/subagent/SubagentModelTest.kt` | migration / builtin 测试 |

### Related Specs / Tasks

- `.trellis/tasks/archive/2026-06/06-27-sub-agent-global-config/` — global 子代理设计
- `.trellis/tasks/archive/2026-06/06-28-06-28-review-all-changes/research/subagent-core-diff-review.md` — global 非空时不含 builtin 的行为说明
- `.trellis/tasks/archive/2026-06/06-27-builtin-subagent-global/prd.md` — R-9 恢复默认子代理

## Caveats / Not Found

- 未读取本机真机 DataStore 文件，无法 100% 证实是「用户删除」还是「某次迁移 persist 竞态」；代码路径上 **删除 global** 与 **partial global + 无 union fallback** 足以解释现象。
- 若用户指的是 **Trellis/OpenCode 的 research/coder/reviewer 子代理**（`.trellis/agents/`），与本 Android `SubagentRegistry` 是不同概念；本次按任务描述查了 `SubagentRegistry.kt`。
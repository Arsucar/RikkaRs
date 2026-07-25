# Implementation Plan（#182）

> 大特性，建议分 5 个可独立推进的工作块。数据模型是所有块的前置依赖，先落地并冻结契约再并行其余块。网关稳定时可子代理并行 B/D；否则串行。

## Block A — 数据模型 + 迁移（前置，必须先做）

1. 新增 `PresetEntry` sealed（Custom/Builtin/Reference），`Preset` 追加 `entries`，保留旧字段。
2. 懒迁移函数：旧 `modeInjectionIds/disabledEntryIds` → `Custom` 快照（内容从全局 modeInjections 复制），priority → order 稳定映射；幂等。
3. 单测：迁移正确性（含 disabled 条目、空预设、已迁移不重复）、序列化往返（旧 JSON 无 entries 可读）。
风险文件：`data/model/Assistant.kt`、`data/datastore/PreferencesStore.kt`、`data/export/*Serializer`。

## Block B — 注入 transformer 改造（依赖 A）

1. `collectInjections`/`transformMessages` 改为按 PresetEntry 展开；排序键 priority→order（同 position 组内）。
2. Builtin 解析（override + 动态宏，未知/空跳过）；Reference 解析全局（失效跳过）。
3. 单测：三区块注入顺序、system 拼接顺序与 order 一致、AT_DEPTH 按 depth+order、动态宏失败降级不崩。
风险文件：`data/ai/transformers/PromptInjectionTransformer.kt`、`PromptInjectionTransformerTest.kt`。

## Block C — BuiltinPromptRegistry（依赖 A，可与 B 并行）

1. 新建注册表，收敛 `data/ai/prompts/` 内置模板 key/默认内容/role/STATIC|DYNAMIC/overridable/supportedVariables。
2. 至少：回复草稿、建议回复、记忆表向导、工作区向导（AC4 最低集）。
3. ChatService/各 transformer 对接动态宏解析入口。
风险文件：`data/ai/prompts/*.kt`、`service/ChatService.kt`。

## Block D — PresetDetailPage UI（依赖 A，UI 独立）

1. 新建 `ui/pages/extensions/PresetDetailPage.kt` + VM；App 导航图注册 route。
2. 三区块 + 组内 `sh.calvin.reorderable` 拖拽 + 编辑 Sheet + 变量 chips + 滑删 + FAB + 空/错误态。
3. `PromptPage.PresetCard.onClick` 改为导航到详情页。
风险文件：`ui/pages/extensions/PromptPage.kt`、`PromptVM.kt`、导航图。

## Block E — 收尾（依赖 B/C/D）

1. 本地化 en/zh（key 前缀 `preset_detail_`），需全量时补 ja/zh-rTW/ko-rKR/ru。
2. `--no-daemon :app:compileDebugKotlin`（跳过 web-ui）+ `:app:testDebugUnitTest` + `git diff --check`。
3. 有设备时 `:app:installDebug` 真机验收三区块拖拽/开关/迁移。

## 验收门（对齐 prd 的 AC1–AC6）

- 注入顺序单测 + 迁移单测通过。
- 旧预设迁移后行为不回归（priority→order 映射锁定）。
- 未启用功能对应 Builtin 不注入且不报错。

## 最大风险

排序语义 priority→order 变更影响所有已有注入排序。迁移映射 + 注入单测是防回归关键，任何 Block 落地前先补锁定测试。

# Technical Design: #187 Preset Review Regressions

## 1. Reference Deduplication

`resolvePresetEntry` 返回内部解析结构，包含最终 `ModeInjection` 与 `deduplicationId`：

- Custom：`deduplicationId = entry.id`。
- Builtin：`deduplicationId = entry.id`（当前 config-only 会提前返回 null）。
- Reference：`deduplicationId = target.id`。

`collectInjections` 继续让直连路径先占用全局 id；entries 路径按解析结构的 `deduplicationId` 去重。
因此直连与 Reference 指向同一目标时直连语义胜出，与迁移 Custom 的现有行为一致；不同 Custom 不会按内容误合并。

## 2. Workspace Default Single Source

将完整 Workspace 提示生成函数放在 prompts 边界，输入 `workspaceName` 与可空 `cwd`。注册表默认内容调用同一函数，
但传入 `{{workspace_name}}` / `{{cwd}}` 宏；Workspace transformer 无 override 时也调用该函数并传真实运行时值。

有 override 时只在 transformer 内替换两个宏。这样注册表展示的是完整安全契约，运行时数据仍不泄漏到通用注册表，
且 `/upload`、skills、权限和工具说明只有一处文本来源。

`resolveContent` 对原始模板做单次宏匹配：只替换匹配位置，不对插入值再次扫描，避免 workspace 名称中的
`{{cwd}}` 被误当作模板。未知宏保持原样，已提供空值仍替换为空串。

兼容边界：`cwd` 为空时运行时默认继续省略 current-working-directory 行；用户保存含 `{{cwd}}` 的自定义模板时，
宏按既有契约替换为空串。

## 3. Accessible Reordering

保留 `longPressDraggableHandle`。拖拽视觉节点不再伪装成空点击按钮，使用准确的本地化“拖动排序”描述。

现有条目溢出菜单增加“上移”和“下移”：

- 可用性由当前条目在同 subtype 分组内的索引计算。
- 点击提交与拖拽相同的稳定 ID 相对 mutation，并复用 `moveInGroup`，保持跨组 no-op 与最新值写入契约。
- 首项禁用上移，末项禁用下移；单条组两者均禁用。

溢出菜单命令为 TalkBack、键盘和开关控制提供非手势等价路径；触摸用户仍可长按拖拽。

## 4. Verification

- Runtime unit：直连 + Reference 同目标去重，两个 Custom 不误去重。
- Prompt unit：注册表 Workspace 默认经宏解析后等于完整运行时生成结果，并断言关键安全段存在。
- Prompt unit：运行时变量包含宏形文本时保持字面内容，防止二次解析。
- UI helper unit：按稳定 ID 上移/下移、边界 no-op、跨组不变。
- Static/resource：英文/简中手柄描述，Compose 语义无空 click action。
- Final Gradle：合并运行资源、生产 Kotlin、完整 JVM 单测和 AndroidTest Kotlin 编译；之后按设备流程安装。

## Rollback

三个修复块互不依赖，可分别回退。不得回退 `07-27` 已完成的 latest-value mutation、Builtin config-only、
迁移版本 sentinel 或现有触摸拖拽实现。

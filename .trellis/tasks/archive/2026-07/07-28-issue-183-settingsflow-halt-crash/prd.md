# 修复 #183 settingsFlow 收集异常触发 Runtime.halt(1) 杀进程

## Goal

utils/CoroutineUtils.kt:25 的 toMutableStateFlow 在收集流失败时调用 Runtime.getRuntime().halt(1) 直接杀进程，跳过所有清理与 SafeMode。settingsFlow 使用该函数，DataStore 读取或 JSON 反序列化异常即静默闪退且无法进入安全模式。修复：移除 halt，改为有限次 retryWhen 后 fallback 初始默认值并记录日志，崩溃兜底交 SafeModeActivity。

## Requirements

- 移除 `utils/CoroutineUtils.kt` 中 `toMutableStateFlow` 的 `Runtime.getRuntime().halt(1)`。
- 收集失败时改为有限次重试（`retryWhen`，带次数上限与退避），仍失败则保留/回退到 `initial` 默认值，保证 StateFlow 继续可用。
- 全程记录可读错误日志（Log.e + 堆栈），异常不再吞掉后静默杀进程。
- 崩溃兜底继续交由既有 SafeModeActivity 常规流程，不在工具函数内直接终止进程。
- 不改变 `toMutableStateFlow` 的公开签名与正常路径行为，避免影响其他调用方。

## Acceptance Criteria

- [ ] `CoroutineUtils.kt` 中不再出现 `Runtime.getRuntime().halt`。
- [ ] 注入反序列化/收集异常时 App 不崩溃，StateFlow 回退默认值，日志可见可读。
- [ ] 现有 `toMutableStateFlow` 调用方（含 `settingsFlow`）行为不回归。
- [ ] `:app:compileDebugKotlin` 通过；相关聚焦单测（若新增）通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.

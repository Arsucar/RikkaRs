# PRD: 拆分子代理返回 JSON 的 steps 字段

## 问题

子代理返回的 JSON 中 `steps` 字段实际值是 `result.steps.coerceAtLeast(result.transcript.size)`，即**对话 transcript 条目数**，而不是受 `maxSteps` 控制的实际工具循环步数。导致用户设 `maxSteps=20` 后看到 `steps: 34` 产生困惑。

## 目标

返回 JSON 里分开报告以下字段：
- `steps` → **实际工具循环步数**（受 maxSteps 控制）
- `tool_loop_steps` → 同上（别名，清晰表明含义）
- `transcript_size` → transcript 条目数（旧语义）

## 改动范围

| 文件 | 改动 |
|------|------|
| `SubagentProfile.kt` | `SubagentResult` 新增 `toolLoopSteps` |
| `SubagentHost.kt` | 从 assistant 消息数计算实际工具循环步数 |
| `SubagentTools.kt` | JSON 输出拆分三个字段 |
| `SubagentToolUIs.kt` | UI 解析/展示用 `toolLoopSteps` |
| 测试文件 | 更新 round-trip 数据 |

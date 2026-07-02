# Research: CompressContextDialog.kt

- **Query**: 压缩上下文对话框是否存在及参数
- **Scope**: internal
- **Date**: 2026-07-02

## File

`app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt`

## 职责

- 纯 UI：收集 `additionalPrompt`、`targetTokens`、`keepRecentMessages`。
- `onConfirm` 返回 `Job`，由 `ChatPage` 绑定 `vm.handleCompressContext`（L296–296）。
- 压缩进行中显示 loading，可取消 Job（L193–199、L277–280）。

## 与 bug 关系

- **不读写** `messageNodes` / `updateCurrentMessages`。
- `keepRecentMessages` 传入 `compressConversation`，决定 `keepMessageIds` 与 hidden 范围，从而影响 `insertAt` 与 hidden 前缀长度，**放大**物理/可见下标偏移，但不改变根因机制。

## 关键 API (L175–177)

```kotlin
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job
)
```
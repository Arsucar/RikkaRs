# Research: ChatList.kt

- **Query**: `items(conversation.messageNodes)`、`node.hidden` UI
- **Scope**: internal
- **Date**: 2026-07-02

## File

`app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt`

## 列表数据源 (L322–325)

```kotlin
itemsIndexed(
    items = conversation.messageNodes,
    key = { index, item -> item.id },
) { index, node ->
```

- UI **按物理 `messageNodes` 顺序**渲染，不是 `currentMessages`。
- 压缩后：前段 hidden 节点仍在列表中；摘要节点出现在 `insertAt` 位置。

## `hidden` 表现 (L329–340)

```kotlin
if (node.hidden) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .heightIn(min = 32.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
Column(
    modifier = Modifier
        .weight(1f)
        .alpha(if (node.hidden) 0.4f else 1f),
) {
    ChatMessage(
        node = node,
        ...
        loading = loading && index == lastMessageIndex,
```

- Hidden 节点仍显示 `ChatMessage`（半透明 + 左侧竖条），**不会从列表移除**。
- `loading` 绑定 **`messageNodes.lastIndex`**（L278、L358），与「最后一条可见消息」可能不一致（摘要/隐藏存在时），属次要 UX 问题。

## 与错乱/丢失现象的关联

若 `updateCurrentMessages` 把**正在流式更新的 assistant** 写入物理下标 0/1 的 **hidden 节点**：

- 用户看到靠前位置出现半透明「旧消息槽位」却装着新 assistant 内容 → **排序错乱**。
- 真正应更新的末尾节点未收到 chunk → 表现为 **生成内容丢失或停在旧状态**。
- `currentMessages` 从错位节点读 `selectIndex` 时，可见列表与 UI 物理顺序进一步分裂。

## 搜索模式 (L634–638)

搜索时用 `messageNodes.mapIndexed`，与主列表一致，同样暴露物理布局；修复写回逻辑后 UI 顺序与内容应重新对齐。
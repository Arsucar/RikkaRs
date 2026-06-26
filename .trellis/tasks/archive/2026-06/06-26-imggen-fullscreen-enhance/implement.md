# Implement: 图片生成全屏预览 UI 增强

## Plan Summary

扩展 `ImagePreviewDialog` 以支持顶部显示模型名称，底部添加"复制提示词"按钮。

## Architecture

### 改动点
1. **`ImagePreviewDialog.kt`** — 扩展组件参数签名 & UI
2. **`ImgGenPage.kt`** — 5 处调用传入新增参数

### 参数扩展方案

```
ImagePreviewDialog(
    images: List<String>,
    labels: List<String> = emptyList(),         // 现有：日期
    models: List<String> = emptyList(),         // 新增：模型名称
    promptsLabels: List<String> = emptyList(),    // 新增：提示词（用于复制）
    onUseAsReference: ((String) -> Unit)? = null,
    onDismissRequest: () -> Unit,
)
```

### UI 变更要点

#### 顶部 Label（当前行 99-115）
- 将 `labels` 和 `models` 对应索引拼接为 `"日期 · 模型名"` 格式
- 保持 Surface + Text 结构不变，仅修改 `text` 内容

#### 底部 Row（当前行 117-154）
- 在现有 `IconButton` 前新增一个复制图标按钮
- 点击时使用 `LocalClipboardManager` 复制当前页 `promptsLabels[state.currentPage]`
- 复制成功后弹出 `Toaster` 提示

## Files to Modify

| 文件 | 改动 |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/components/ui/ImagePreviewDialog.kt` | 扩展组件签名、顶部 label 拼接模型名、底部新增复制按钮 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenPage.kt` | 5 处 `ImagePreviewDialog` 调用新增 `models` 和 `promptsLabels` 参数 |

## Implementation Checklist

- [ ] 修改 `ImagePreviewDialog.kt`
  - [ ] 新增 `models` 和 `promptsLabels` 参数（默认空列表）
  - [ ] 顶部 label 区域：当 `models` 有对应值时，拼接日期 + 模型名
  - [ ] 底部按钮行：新增复制图标按钮，点击复制提示词
  - [ ] 导入 `LocalClipboardManager`、`AnnotatedString`
- [ ] 修改 `ImgGenPage.kt` — 5 处调用
  - [ ] 找到所有 `ImagePreviewDialog(...)` 调用位置
  - [ ] 为每张图片传入对应的 `model` 和 `prompt`
- [ ] 验证编译通过（`./gradlew app:compileDebugKotlin`）

## Risk
- 较低：纯 UI 改动，不涉及数据层变更
- 回滚：还原两个文件即可

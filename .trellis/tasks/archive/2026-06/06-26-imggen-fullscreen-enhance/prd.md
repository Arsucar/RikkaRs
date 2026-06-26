# PRD: 图片生成全屏预览 UI 增强

## Goal
在图片生成模块的图片全屏预览 (`ImagePreviewDialog`) 中，增强信息展示和操作能力，让用户能够快速查看生成模型名称并复制提示词。

## Background

当前 `ImagePreviewDialog` 作为通用组件，仅在顶部显示一个 `label`（日期），底部只有两个操作按钮（引用图、下载）。用户在全屏预览时，需要知道该图片的生成模型，并希望便捷地获取原始提示词以复用或修改。

### Confirmed Facts

- **组件**：`ImagePreviewDialog` (`app/src/main/java/me/rerere/rikkahub/ui/components/ui/ImagePreviewDialog.kt`)
  - 顶部通过 `labels: List<String>` 参数显示当前页日期
  - 底部通过 `Row` 排列 `IconButton`（引用图、下载）
  - 目前共 5 处调用，分散在 `ImgGenPage.kt` 各处

- **数据模型**：`GeneratedImage` (`app/.../ImgGenVM.kt`)
  - 已有 `model: String` 字段（模型 ID/名称）
  - 已有 `prompt: String` 字段
  - 已有 `timestamp: Long` 字段

- **调用处**：`ImgGenPage.kt` 中 5 处调用均通过 `previewImages.map { ... }` 传入参数

## Requirements

### R1 — 顶部显示模型名称
在 `ImagePreviewDialog` 顶部日期 Surface 中，横向并排显示当前图片的生成日期和模型名称。

格式要求：日期 和 模型名 在同一行，用适当分隔符（如 `·` 或 `|`）区分，样式与现有日期样式统一。

### R2 — 添加复制提示词按钮
在 `ImagePreviewDialog` 底部按钮行（`Row`）中，添加一个"复制提示词"按钮。

交互：点击后将当前页图片的提示词复制到剪贴板，并提示 Toast "已复制提示词"。

## Acceptance Criteria

- [ ] 全屏预览图片时，顶部 label 同时显示日期和模型名称（如 `2025-01-28 15:30 · kimi-k2.5`）
- [ ] 底部按钮行在现有按钮旁新增"复制提示词"按钮
- [ ] 点击复制提示词按钮后，提示词正确复制到剪贴板并弹出成功 Toast
- [ ] 所有 5 处 `ImagePreviewDialog` 调用均正确传入新参数
- [ ] 不影响非图片生成场景（如聊天中的图片预览）的现有行为

## Out of Scope
- 不在全屏预览中添加提示词编辑功能
- 不修改图片生成的其他流程
- 不引入新的数据库迁移

## Notes
- 使用 Compose 的 `LocalClipboardManager` 实现复制到剪贴板功能
- 模型名称取 `GeneratedImage.model`，该字段在数据模型中已存在

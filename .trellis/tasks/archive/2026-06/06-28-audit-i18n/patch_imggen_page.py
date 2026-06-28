#!/usr/bin/env python3
"""Patch ImgGenPage.kt hardcoded Chinese to stringResource."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
path = ROOT / "app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenPage.kt"
text = path.read_text(encoding="utf-8")

replacements = [
    ('text = "搜索图片关键字"', 'text = stringResource(R.string.imggen_page_search_keyword_placeholder)'),
    ('toaster.show(message = "已添加为引用图"', 'toaster.show(message = stringResource(R.string.imggen_page_reference_added)'),
    ('toaster.show(message = "引用图片失败：${error.message}"',
     'toaster.show(message = stringResource(R.string.imggen_page_reference_failed, error.message ?: "")'),
    ('contentDescription = "Search images"', 'contentDescription = stringResource(R.string.imggen_page_search_images)'),
    ('text = { Text("管理图像快捷消息") }', 'text = { Text(stringResource(R.string.imggen_page_manage_quick_messages)) }'),
    ('MenuSectionLabel("显示模式")', 'MenuSectionLabel(stringResource(R.string.imggen_page_display_mode))'),
    ('ImageGalleryDisplayMode.GRID -> "网格"', 'ImageGalleryDisplayMode.GRID -> stringResource(R.string.imggen_page_display_mode_grid)'),
    ('ImageGalleryDisplayMode.GROUPED -> "分组"', 'ImageGalleryDisplayMode.GROUPED -> stringResource(R.string.imggen_page_display_mode_grouped)'),
    ('text = { Text("回收站") }', 'text = { Text(stringResource(R.string.imggen_page_trash)) }'),
    ('Text("空间")', 'Text(stringResource(R.string.imggen_page_space))'),
    ('text = if (job.isEdit) "编辑" else "生成"',
     'text = if (job.isEdit) stringResource(R.string.imggen_page_job_edit) else stringResource(R.string.imggen_page_job_generate)'),
    ('text = if (job.isAwaitingPermit) "排队中…" else "生成中…"',
     'text = if (job.isAwaitingPermit) stringResource(R.string.imggen_page_job_queued) else stringResource(R.string.imggen_page_job_generating)'),
    ('.ifBlank { "未命名" }', '.ifBlank { stringResource(R.string.imggen_page_untitled) }'),
    ('title = { Text("图像快捷消息") }', 'title = { Text(stringResource(R.string.imggen_page_image_quick_messages)) }'),
    ('text = "暂无图像快捷消息"', 'text = stringResource(R.string.imggen_page_image_quick_messages_empty)'),
    ('Text("添加")', 'Text(stringResource(R.string.imggen_page_add))'),
    ('title = "添加图像快捷消息"', 'title = stringResource(R.string.imggen_page_add_image_quick_message)'),
    ('title = "编辑图像快捷消息"', 'title = stringResource(R.string.imggen_page_edit_image_quick_message)'),
    ('label = { Text("标题") }', 'label = { Text(stringResource(R.string.imggen_page_title_label)) }'),
    ('label = { Text("内容") }', 'label = { Text(stringResource(R.string.imggen_page_content_label)) }'),
    ('keys += ungroupedKey to "未分组"', 'keys += ungroupedKey to ungroupedLabel'),
    ('keys += orphanId to "已删除分组"', 'keys += orphanId to deletedCollectionLabel'),
    ('text = "收藏 ${favorites.size} 张"',
     'text = stringResource(R.string.imggen_page_favorites_count, favorites.size)'),
    ('Text(if (allSectionsExpanded) "全部折叠" else "全部展开")',
     'Text(if (allSectionsExpanded) stringResource(R.string.imggen_page_collapse_all) else stringResource(R.string.imggen_page_expand_all))'),
    ('Text("新建分组")', 'Text(stringResource(R.string.imggen_page_new_collection))'),
    ('subtitle = "${sectionItems.size} 张"',
     'subtitle = stringResource(R.string.imggen_page_images_count, sectionItems.size)'),
    ('title = { Text("新建分组") }', 'title = { Text(stringResource(R.string.imggen_page_new_collection)) }'),
    ('label = { Text("分组名称") }', 'label = { Text(stringResource(R.string.imggen_page_collection_name)) }'),
    (') { Text("确定") }', ') { Text(stringResource(R.string.confirm)) }'),
    ('{ Text("取消") }', '{ Text(stringResource(R.string.imggen_page_cancel)) }'),
    ('title = { Text("重命名分组") }', 'title = { Text(stringResource(R.string.imggen_page_rename_collection)) }'),
    ('title = { Text("删除分组") }', 'title = { Text(stringResource(R.string.imggen_page_delete_collection)) }'),
    ('Text("将删除分组「${target.name}」，其中收藏会移入未分组，不会取消收藏。")',
     'Text(stringResource(R.string.imggen_page_collection_delete_confirm, target.name))'),
    ('Text("删除", color = MaterialTheme.colorScheme.error)',
     'Text(stringResource(R.string.imggen_page_delete), color = MaterialTheme.colorScheme.error)'),
    ('text = { Text("重命名") }', 'text = { Text(stringResource(R.string.imggen_page_rename)) }'),
    ('text = { Text("删除分组", color = MaterialTheme.colorScheme.error) }',
     'text = { Text(stringResource(R.string.imggen_page_delete_collection_action), color = MaterialTheme.colorScheme.error) }'),
    ('?.name ?: "未分组"', '?.name ?: stringResource(R.string.imggen_page_ungrouped)'),
    ('Text("分组", style = MaterialTheme.typography.labelSmall)',
     'Text(stringResource(R.string.imggen_page_collection), style = MaterialTheme.typography.labelSmall)'),
    ('text = { Text("未分组") }', 'text = { Text(stringResource(R.string.imggen_page_ungrouped)) }'),
    ('text = "暂无收藏"', 'text = stringResource(R.string.imggen_page_no_favorites)'),
    ('Text(if (allGroupsExpanded) "全部折叠" else "全部展开")',
     'Text(if (allGroupsExpanded) stringResource(R.string.imggen_page_collapse_all) else stringResource(R.string.imggen_page_expand_all))'),
    ('"${formatImageDate(group.timestamp)} · ${group.images.size} 张 · ${group.variants.size} 变体"',
     'stringResource(R.string.imggen_page_group_meta_variants, formatImageDate(group.timestamp), group.images.size, group.variants.size)'),
    ('"${formatImageDate(group.timestamp)} · ${group.images.size} 张"',
     'stringResource(R.string.imggen_page_group_meta, formatImageDate(group.timestamp), group.images.size)'),
    ('text = "${formatImageDate(section.timestamp)} · ${section.model} · ${section.images.size} 张"',
     'text = stringResource(R.string.imggen_page_section_meta, formatImageDate(section.timestamp), section.model, section.images.size)'),
    ('message = if (added) "已收藏" else "已取消收藏"',
     'message = if (added) stringResource(R.string.imggen_page_favorite_added) else stringResource(R.string.imggen_page_favorite_removed)'),
    ('message = "收藏失败：${error.message ?: "未知错误"}"',
     'message = stringResource(R.string.imggen_page_favorite_failed, error.message ?: stringResource(R.string.imggen_page_unknown_error))'),
    ('prompt.ifBlank { "无提示词" }', 'prompt.ifBlank { stringResource(R.string.imggen_page_no_prompt) }'),
    ('title = { Text("提示词") }', 'title = { Text(stringResource(R.string.imggen_page_prompt_title)) }'),
    ('Text("关闭")', 'Text(stringResource(R.string.imggen_page_close))'),
    ('title = { Text("回收站") }', 'title = { Text(stringResource(R.string.imggen_page_trash)) }'),
    ('contentDescription = "Close")', 'contentDescription = stringResource(R.string.imggen_page_close))'),
    ('Text("清空")', 'Text(stringResource(R.string.imggen_page_trash_clear))'),
    ('text = "回收站为空"', 'text = stringResource(R.string.imggen_page_trash_empty)'),
    ('title = { Text("清空回收站？") }', 'title = { Text(stringResource(R.string.imggen_page_trash_clear_title)) }'),
    ('text = { Text("将彻底删除回收站内的所有图片和记录，无法恢复。") }',
     'text = { Text(stringResource(R.string.imggen_page_trash_clear_message)) }'),
    ('Text("清空", color = MaterialTheme.colorScheme.error)',
     'Text(stringResource(R.string.imggen_page_trash_clear), color = MaterialTheme.colorScheme.error)'),
    ('Text("恢复")', 'Text(stringResource(R.string.imggen_page_restore))'),
    ('contentDescription = "Permanently delete"',
     'contentDescription = stringResource(R.string.imggen_page_delete_permanently)'),
    ('title = { Text("彻底删除图片？") }', 'title = { Text(stringResource(R.string.imggen_page_delete_permanently_title)) }'),
    ('text = { Text("此操作会删除本地文件和记录，无法恢复。") }',
     'text = { Text(stringResource(R.string.imggen_page_delete_permanently_message)) }'),
    ('Text("彻底删除", color = MaterialTheme.colorScheme.error)',
     'Text(stringResource(R.string.imggen_page_delete_permanently), color = MaterialTheme.colorScheme.error)'),
    ('label = "张"', 'label = stringResource(R.string.imggen_page_unit_images)'),
    ('label = { Text("并发请求数") }', 'label = { Text(stringResource(R.string.imggen_page_concurrent_requests)) }'),
    ('description = { Text("同时进行中的 API 请求上限；成功或失败的任务卡片不占名额，可继续发送") }',
     'description = { Text(stringResource(R.string.imggen_page_concurrent_requests_desc)) }'),
    ('label = "个"', 'label = stringResource(R.string.imggen_page_unit_items)'),
    ('label = { Text("流式预览") }', 'label = { Text(stringResource(R.string.imggen_page_streaming_preview)) }'),
    ('description = { Text("开启后边生成边显示部分图；若你的接口/中转不支持流式(SSE)，请关闭") }',
     'description = { Text(stringResource(R.string.imggen_page_streaming_preview_desc)) }'),
    ('label = { Text("gpt-image-2 尺寸") }', 'label = { Text(stringResource(R.string.imggen_page_gpt_image2_size)) }'),
    ('description = { Text("支持 auto、常用尺寸和自定义宽x高；自定义会在生成前校验") }',
     'description = { Text(stringResource(R.string.imggen_page_gpt_image2_size_desc)) }'),
    ('label = { Text("质量") }', 'label = { Text(stringResource(R.string.imggen_page_quality)) }'),
    ('description = { Text("仅在模型 ID 为 gpt-image-2 时发送 quality 字段") }',
     'description = { Text(stringResource(R.string.imggen_page_quality_desc)) }'),
    ('label = { Text("输出格式") }', 'label = { Text(stringResource(R.string.imggen_page_output_format)) }'),
    ('description = { Text("对应 output_format；默认 png，jpeg/webp 可配置压缩") }',
     'description = { Text(stringResource(R.string.imggen_page_output_format_desc)) }'),
    ('label = { Text("输出压缩") }', 'label = { Text(stringResource(R.string.imggen_page_output_compression)) }'),
    ('description = { Text("仅 jpeg/webp 发送 output_compression，范围 0-100") }',
     'description = { Text(stringResource(R.string.imggen_page_output_compression_desc)) }'),
    ('label = { Text("背景") }', 'label = { Text(stringResource(R.string.imggen_page_background)) }'),
    ('description = { Text("gpt-image-2 不适配透明背景，仅保留 auto/opaque") }',
     'description = { Text(stringResource(R.string.imggen_page_background_desc)) }'),
    ('label = { Text("审核") }', 'label = { Text(stringResource(R.string.imggen_page_moderation)) }'),
    ('description = { Text("对应 moderation；auto 为默认过滤，low 较宽松") }',
     'description = { Text(stringResource(R.string.imggen_page_moderation_desc)) }'),
    ('text = "列数"', 'text = stringResource(R.string.imggen_page_columns)'),
    ('contentDescription = "减少列数")', 'contentDescription = stringResource(R.string.imggen_page_decrease_columns))'),
    ('contentDescription = "增加列数")', 'contentDescription = stringResource(R.string.imggen_page_increase_columns))'),
]

for old, new in replacements:
    if old not in text:
        print(f"MISSING: {old[:60]}...")
        continue
    text = text.replace(old, new)

# Inject labels before sectionKeys remember in ImageSpaceScreen
needle = "    val showThumbnailActions = columns <= IMAGE_THUMBNAIL_ACTIONS_MAX_COLUMNS\n\n    val sectionKeys = remember(favorites, collections) {"
if needle in text:
    insert = """    val showThumbnailActions = columns <= IMAGE_THUMBNAIL_ACTIONS_MAX_COLUMNS
    val ungroupedLabel = stringResource(R.string.imggen_page_ungrouped)
    val deletedCollectionLabel = stringResource(R.string.imggen_page_deleted_collection)

    val sectionKeys = remember(favorites, collections, ungroupedLabel, deletedCollectionLabel) {"""
    text = text.replace(needle, insert)
else:
    print("WARN: sectionKeys needle not found")

path.write_text(text, encoding="utf-8")
print("done")
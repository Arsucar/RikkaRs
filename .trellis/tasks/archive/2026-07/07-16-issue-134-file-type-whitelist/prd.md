# Issue #134 恢复聊天文件类型白名单

## Goal

恢复上游普通聊天“文件”入口的 MIME/扩展名白名单，在本地阻止不受支持的媒体、可执行文件、压缩包和未知二进制进入 `Document` 附件发送链路，同时不影响工作区保存任意文件。

## Requirements

- 在 `ChatUtil.kt` 恢复上游 `ALLOWED_MIME_TYPES`、`ALLOWED_FILE_EXTENSIONS` 和 `isAllowedFileType(fileName, mime)`。
- 允许已列出的文档 MIME、`text/*` 或已列出的文本/代码扩展名满足任一规则。
- 普通文件入口在复制并创建 `UIMessagePart.Document` 前逐项校验。
- 不支持文件不加入附件，并使用现有本地化资源逐文件提示。
- MIME 缺失保持 #111 的 `application/octet-stream` 安全回退，不恢复 `text/plain` 回退。
- 图片、视频、音频专用入口和工作区任意文件保存流程不变。

## Acceptance Criteria

- [ ] 文本、代码、PDF、Office、EPUB 等白名单文件仍可加入普通聊天附件。
- [ ] GIF、视频、APK、普通 ZIP、未知 MIME 和无扩展名二进制不创建 `Document`，且逐项提示不支持。
- [ ] 多选混合文件时仅支持项加入附件，不支持项均获得明确提示。
- [ ] `isAllowedFileType` 对 MIME、`text/*`、扩展名、大小写、未知 MIME 和无扩展名有单元测试。
- [ ] `:app:compileDebugKotlin`、目标单测和 `git diff --check` 通过；设备可用时完成安装。

## Notes

- Issue：<https://github.com/Arsucar/RikkaRs/issues/134>
- 只恢复上游最小校验，不设计 Provider/模型附件能力系统。

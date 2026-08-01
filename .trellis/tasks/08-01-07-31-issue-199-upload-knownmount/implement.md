# 执行计划：/upload knownMount

## 步骤

1. **主代理 knownMounts**：`ChatService.kt:1940-1948` 追加 `/upload`（source = `File(context.filesDir, FileFolders.UPLOAD)`，必要时 mkdirs）。
2. **子代理 knownMounts**：`ChatService.kt:3077-3083` 追加同条目。若两处重复代码明显，抽 ChatService 私有 `uploadKnownMount()` helper。
3. **工具描述**：`WorkspaceTools.kt:83-87` description 补 `/upload` 只读声明。
4. **单测**：`WorkspaceKnownMountTest` 增 `/upload` 映射 + `..` 越界；fork 新 UUID 路径解析用例（构造 upload/ 下 UUID 文件，验证 resolveKnownMountFile 返回 host 文件）。
5. 修改需与 #200（DocumentAsPromptTransformer 上下文）冲突协调时以主代理为准。

## 验证

- 只允许最后一个检查子代理跑 Gradle；本任务实现阶段只改代码不编译。
- 最终由主代理合并一次 Gradle 调用验证编译 + 相关单测。

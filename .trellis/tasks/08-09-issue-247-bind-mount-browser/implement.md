# Implement: feat(#247) bind-mount 浏览器

## 前置

- `research/implementation-plan-issue-247.md` 及同目录其他 research
- 相关 research: `workspace-manager-listfiles-bind-mounts.md`, `skills-private-and-assistant-binding.md`, `workspace-detail-ui-linux-browser.md`, `rootfs-path-resolution-test-pattern.md`

## Checklist（建议顺序）

### 1. WorkspaceManager 重定向

- [ ] `bindMounts()` 暴露
- [ ] LINUX `listFiles` / `readText` / `fileSize` / `exportFile` 经 `resolveRootfsPath`（或共享 helper）
- [ ] 空/缺失 source → 空列表
- [ ] `/dev` `/proc` `/sys` 不改
- [ ] 单测扩 `RootfsPathResolutionTest.kt`（list 重定向、空源、`/workspace`、kernel）

**文件**: `workspace/.../WorkspaceManager.kt`, `workspace/.../RootfsPathResolutionTest.kt`

### 2. skills_private 入口助手

- [ ] 纯函数：bound assistants 0/1/many + current fallback
- [ ] Repository：LINUX 路径在 `skills_private` 下注入 `WorkspaceBindMount`
- [ ] 复用 `SkillManager.getAssistantSkillsDir`
- [ ] 单测 `SkillsPrivateEntryAssistantTest.kt`（或等价）

**文件**: `WorkspaceRepository.kt`, helper, `SkillManager.kt`（只读复用）

### 3. Detail UI

- [ ] VM：加载绑定助手、selected id、切换后 refresh
- [ ] Page：多助手选择；0 绑「当前助手」标注
- [ ] 可选：挂载角标、路径栏 Rootfs `/` 前缀
- [ ] strings 中英

**文件**: `WorkspaceDetailVM.kt`, `WorkspaceDetailPage.kt`, `values*/strings.xml`

### 4. 验收

- [ ] 手动 AC1–AC7
- [ ] 单测 AC8
- [ ] `.\gradlew --no-daemon` 聚焦 test + compile（仅最终检查子代理）
- [ ] 有设备：`.\gradlew --no-daemon :app:installDebug`

## 不做

- 写/删/导入 LINUX
- RepositoryModule 全局挂载重做（已正确则不动）
- 终端挂载

## Rollback

Manager 重定向可独立 revert；UI 选择层可独立 revert。

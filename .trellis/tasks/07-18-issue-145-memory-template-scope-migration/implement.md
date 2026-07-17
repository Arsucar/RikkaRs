# Issue #145：记忆表模板 Scope 迁移执行计划

- [x] 扩展 DAO guarded UPDATE，一次写普通字段与 `scope_type/scope_id`，同步 fake/instrumented tests。
- [x] 调整 actor-aware Repository upsert 的 nullable target 语义，保留 actorless import，并实现权限、幂等和目标 namespace 冲突。
- [x] 更新 tool scope schema/解析和 create/update handler，ChatService callback 传递 scope presence。
- [x] 更新 ViewModel 编辑 API，Compose EDIT 使用局部 scope 并在变化时显示迁移确认/加载/错误反馈。
- [x] 为 GLOBAL 模板接入独立“复制到本助手”入口，生成新 ID、可编辑本地化副本名称并保留源行。
- [x] 使用 `locale-tui-localization` 写入源 key；自动翻译因区域 403 失败后，人工补齐简中、繁中、日文、韩文、俄文并验证 XML/placeholder。
- [x] 更新 Repository、DAO、Tool、UI pure/state 测试；保留 #122/#140/capability 回归。
- [x] 完成 `git diff --check`、resources、聚焦 JVM 测试、`:app:compileDebugKotlin` 和 androidTest 编译；设备 offline，安装未执行；lint 两次分别在 120s/300s 超时，未声称通过。
- [ ] 更新 CHANGELOG，提交推送后按中英文规范评论并关闭 #145，重新读取评论确认。

## Risk and Rollback Points

- nullable target 是跨层合同，任何 callback 丢失“未传/显式”区别都会让 update 意外迁移。
- DAO WHERE 必须按更新前 owner 授权，SET 才写目标 owner。
- copy 与 migrate 不能共用同一数据库动作：前者新 ID，后者原 ID。
- 文档 scope 不变是明确产品合同，不得在实现中顺手级联。

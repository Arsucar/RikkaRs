# 技术设计：合并上游 2.5.0

## 合并策略
单 merge commit（`git merge upstream/master`），分模块解决冲突后统一编译修复。
不采用逐 tag 多次合并（2.4.13→2.5.0 直接合 master 即可，tag 之间无里程碑差异）。

## 冲突解决原则（逐文件取含策略）
| 冲突区 | 策略 |
|---|---|
| README / README_ZH_TW / README_ZH_CN | fork 为准（保留 fork 现状；README_ZH_CN 保持 fork 已删除的决定，冲突时取 `ours` 不恢复） |
| gradle/libs.versions.toml, app/build.gradle.kts | 手工融合：引入上游依赖升级，保留 fork 的包名/签名/无 Firebase/web 模块 preBuild 配置 |
| ai/ GoogleProvider, ResponseAPI, Message.kt | 上游为主 + 回植 fork 定制（ResponseAPI 注意 a61d116d 自定义路径功能与 fork 改动叠加） |
| speech/ 10 个文件 | 逐 provider 精细仲裁：默认上游新逻辑（自动重试/双向流式），fork 曾有的本地修改逐条判定去留 |
| app/ service + GenerationLoop + ConversationSession | 上游新（发送队列/前台服务/审批修复）为骨架，fork 的会话分支逻辑不回退 |
| di/AppModule, DataSourceModule | 融合：上游新增 Koin 注册项带入，保留 fork 的注册 |
| data/sync S3/WebDav | 上游为主，fork 定制点（若有）回植 |
| ui/pages/* 冲突 | 逐处语义融合，不上演整体覆盖 |
| web-ui/extension-picker.tsx | fork 的 web-ui 特性为准，上游新增 picker 项补入 |
| workspace/ProotShellRunner.kt | 上游 stdin EOF 修复带入，保留 fork 改动 |
| 测试类 | 随实现走，融合双方断言 |

## 数据/兼容性
- DataStore/DB 无上游 schema 破坏性迁移（需在实现时核对 2.4.13~2.5.0 是否改 schema/entity，发现则单独评估）。
- PreferencesStore 冲突须保证 fork 设置键不丢。

## 回滚
- 合并在独立 merge commit 上进行；出错直接 `git merge --abort` / reset 到 984366a5，无不可逆操作。
- push 前一切可回滚；push 后出问题按 fork 规约 revert。

## 子代理分工（主线程只做整合）
1. trellis-implement × N：按模块分组解冲突（构建配置组、ai 组、speech 组、app-核心组、app-UI+杂项组）。
2. trellis-check（最后唯一可编译者）：assembleDebug + 聚焦 JVM 测试 + 安装。

## 约束
- 所有 gradle/adb 命令 `--no-daemon`。
- 只有最后一个检查子代理允许编译/测试/安装。
- 实现子代理只做冲突解决与必要小修，不新增验证性编译。

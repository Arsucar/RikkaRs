# issue-215 implement

## Checklist

1. [ ] FeatureSpec + Registry + resolve() + unit tests
2. [ ] Settings.experimentalFeatures + DataStore key + partial write + migration-from-legacy booleans
3. [ ] Assistant.experimentalFeatureOverrides + serialization (rides Assistants JSON)
4. [ ] Screen.Experiments / AssistantExperiments + RouteActivity entries
5. [ ] SettingPage + AssistantDetailPage 入口
6. [ ] Experiments UI（警告、列表、确认、Empty、checkpoint interval 附属）
7. [ ] 消费点改 resolve：ChatKeepAliveController/ChatService keepalive；checkpoint；isVariableSystemEnabled
8. [ ] 删除通知页 keepalive/checkpoint Switch；删除记忆页 variable Switch
9. [ ] 中英 strings
10. [ ] compile + *Experimental* tests；installDebug if device

## Verify

```powershell
.\gradlew --no-daemon :app:testDebugUnitTest --tests "*Experimental*"
.\gradlew --no-daemon :app:compileDebugKotlin
.\gradlew --no-daemon :app:installDebug
```

## Review gates

- [ ] 默认全关
- [ ] 作用域隔离
- [ ] 旧用户开启状态不丢
- [ ] 散落 UI 已删
- [ ] #202 写路径

## Effort

**M–L**（1.5–2d）

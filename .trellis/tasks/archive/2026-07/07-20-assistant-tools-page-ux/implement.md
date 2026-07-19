# Implement: Assistant tools page UX (#163)

## Checklist

1. [ ] Add pure helpers: user-facing stats, readable diagnostics line, multi-select state rules (testable).
2. [ ] Localize built-in preset names + connection states (strings.xml + mapping).
3. [ ] Restructure `AssistantToolsContent` IA: groups first; advanced collapsed.
4. [ ] Fix diagnostics ListItem (no dual copySummary).
5. [ ] Fix tool rows: description + status; collapse when group off; long-press multi-select.
6. [ ] MCP configuredOnly default + show all toggle.
7. [ ] Preset apply confirm + snackbar feedback.
8. [ ] Update `empowermentToolStats` / tests.
9. [ ] Unit tests for helpers + existing page tests green.
10. [ ] Focused compile/test; install only from last check agent / main final.

## Validation

```powershell
.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.assistant.detail.AssistantToolsPageTest" --tests "me.rerere.rikkahub.data.model.ToolPermissionPolicyTest"
.\gradlew --no-daemon :app:compileDebugKotlin
```

Final install (main or last check only):

```powershell
adb devices
# if empty: adb connect 100.99.129.110:5555
.\gradlew --no-daemon :app:installDebug
```

## Domain freeze (do not edit)

- `ToolPermissionPolicy.applyAssistantToolPermissions`
- Catalog effective resolution core
- `copySummary()` format string

## Rollback

Revert `AssistantToolsPage.kt` + string keys + helper tests.

# Implementation Plan

## Checklist

1. Audit current code against #43-#48 and record missing acceptance items.
2. Load relevant pre-development specs before source edits.
3. Implement #46/#48 backend/provider fixes.
4. Implement #45/#47 shell command visibility and subagent transcript clarity.
5. Implement #43 chat clear removal and Skills list/card style improvements.
6. Implement #44 text filename heuristics and read-only Markdown rendering.
7. Run focused tests/compile checks:
   - `.\gradlew --no-daemon :ai:test`
   - `.\gradlew --no-daemon :app:compileDebugKotlin`
   - If an Android device is connected, `.\gradlew --no-daemon :app:installDebug`
8. Review diff against issue acceptance criteria.
9. Close #43-#48 with concise verification comments.

## Risky Files

- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/ResponseAPI.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/WorkspaceToolUIs.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUIs.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceDetailPage.kt`

## Rollback Points

- Backend/provider fixes are independent of UI styling and can be reverted
  separately.
- Workspace Markdown rendering should remain isolated behind read-only mode.
- Skills styling should avoid changing navigation or repository calls.

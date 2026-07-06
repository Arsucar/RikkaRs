# Implementation Plan

1. Implement array row merge in `MemoryTableTools.kt`.
2. Add `MemoryTableToolsTest` coverage for preserving distinct rows and updating matching row keys.
3. Add the full-screen memory table document editor in `AssistantMemoryPage.kt`, reusing existing upsert callbacks.
4. Update message edited-file and document attachment read-only Markdown open paths to render preview.
5. Run focused JVM tests for memory table tools.
6. Run `.\gradlew --no-daemon :app:compileDebugKotlin`.
7. If compile succeeds and a device is attached, run `.\gradlew --no-daemon :app:installDebug`.
8. Close GitHub issues #49, #50, and #51 only after validation supports each acceptance criterion.

## Risk Notes

- `AssistantMemoryPage.kt` is large; keep the editor self-contained and avoid unrelated layout refactors.
- Table schema support should be intentionally narrow: table names plus string/text columns. Unsupported or malformed data must fall back to JSON editing.
- Only the final validation agent or main session should run Gradle.

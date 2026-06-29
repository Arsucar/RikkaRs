# Research: ScreenTime tool status (impl / UI / persistence / permission)

- Query: ScreenTime UI and data save paths in rikkahub fork
- Scope: internal
- Date: 2026-06-29

## 1. ScreenTimeTool

- app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/ScreenTimeTool.kt:27 name get_screen_time
- :37-74 params begin/end/range/top
- :133 queryAndAggregateUsageStats (NOT queryEvents)
- :162-166 resolveAppName via PackageManager
- No launcher exclusion; no OEM/event fallback

Warnings: aggregate API bias; total_ms sums all stats but apps is take(top); INVALID_* only in JSON text.

## 2. UI

- BuiltinToolUIs.kt:419 GetScreenTimeToolUI; :509 ScreenTimePreview LazyColumn all apps
- ChatMessageTools.kt:77-91 ToolUIContext; loading DotLoading+shimmer
- NO_PERMISSION in Summary only; INVALID_TIME/RANGE use DefaultToolPreview JSON
- Empty apps: no summary string; Preview is raw JSON
- total_minutes vs top-N list mismatch

## 3. LocalToolOption

- LocalToolOption.kt:29-30 screen_time
- LocalTools.kt:39-40 gated by assistant.localTools
- SubagentTools.kt:300 screen_time mapping; unknown keys dropped silently

## 4. Settings save

- Assistant.kt:39 localTools per assistant
- PreferencesStore.kt ASSISTANTS JSON list
- AssistantDetailVM.kt:164 update replaces assistant in settings
- AssistantLocalToolPage.kt:84-93 enables switch even without permission (toast+settings only)

## 5. Permission

- AndroidManifest.xml:15-17 PACKAGE_USAGE_STATS
- ContextUtil.kt:76-98 hasUsageStatsPermission; :103 openUsageAccessSettings
- No PermissionManager; no HOME queries in manifest yet


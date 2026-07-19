# #153 Implementation Plan

- [x] Define stable source/key, reason, approval, descriptor, and snapshot models in a pure app data-model package.
- [x] Define pure builders for built-in/local/memory/recent/workspace/skills/MCP/subagent source metadata.
- [x] Add an effective-ID to runtime-name projection and a generation-time consistency assertion/test hook.
- [x] Replace fixed workspace/tool group counts in `AssistantToolsPage` with the catalog projection while preserving the existing entry.
- [x] Add configured/available/reason text and dynamic source counts with localized resources.
- [x] Keep all preview/catalog paths free of MCP sync, tool factory execution, filesystem creation, Room reads, and Android service access.
- [x] Add table-driven JVM tests and #152 cross-layer fake tests.
- [x] Run final resource/Kotlin/JVM/AndroidTest source checks with one final check agent; install or record APK boundary.

# Execution Plan

1. Complete #131 verification task, push its existing fix, publish bilingual evidence comments, and close it.
2. Complete #132 planning context, activate its child task, dispatch implementation and static review agents, then let the final check agent run targeted tests/build/install.
3. Commit and push #132, publish bilingual comments, reread them, close the issue, and archive the child task.
4. Refresh #133 against the actual #132 public API, activate it, implement through sub-agents, and run its final quality/install gate.
5. Commit and push #133, publish bilingual comments, reread them, close it, and archive the child task.
6. Implement and verify newly opened #134, publish bilingual comments, reread, and close it.
7. Run parent integration review: the issue set discovered during this session is closed, branch clean except preserved user changes, task artifacts complete, and all validation claims match command output.

## Shared Validation Rules

- Use `git diff --check` for every child.
- All Gradle commands use `--no-daemon`.
- Do not run `connectedDebugAndroidTest` unless explicitly needed and approved.
- For app changes: `adb devices`, fallback `adb connect 100.99.129.110:5555`, then `:app:installDebug` when a device is available.
- Record failures and unrun checks exactly; never reuse a generic issue comment.

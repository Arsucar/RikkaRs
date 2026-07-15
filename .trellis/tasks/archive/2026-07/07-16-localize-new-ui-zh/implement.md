# Execution Plan

1. Diff commits `1dba4b9b` and `5bc0294d` to enumerate changed UI files and new resource keys.
2. Search those UI files for user-visible hardcoded strings.
3. Use locale-tui and manual review to write natural Simplified Chinese translations in `values-zh/strings.xml`.
4. Compare key coverage and formatting placeholders between default and Simplified Chinese resources.
5. Run resource/Kotlin compilation, `git diff --check`, and the install-to-device flow.
6. Commit and push the localization change, then archive the task and record the session.

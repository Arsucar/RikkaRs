# Execution Plan

1. Compare the current fork with upstream commit `55d144a6a` and locate the #111 removals.
2. Restore the whitelist helper while preserving `application/octet-stream` for unknown MIME.
3. Validate every selected ordinary file before copying/creating a `Document`; reuse the existing unsupported-type string.
4. Add focused unit tests for accepted MIME/text/extensions and rejected GIF/video/APK/ZIP/unknown/no-extension cases.
5. Run the targeted test, Kotlin compile, `git diff --check`, and device install flow when available.
6. Commit/push, publish distinct Chinese and English delivery comments, reread, and close #134.

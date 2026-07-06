# Design

## Affected Areas

- `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceDetailPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageEditedFiles.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ui/TextArea.kt`
- `app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceRepository.kt`

## UI Contract

- Extract a shared fullscreen text viewer/editor composable from `TextArea.kt`.
- Viewer is readonly with selection and scroll.
- Editor provides save/cancel and writes back only when the caller supplies a save handler.

## Text Detection

Use a conservative MIME/extension allowlist: `text/*`, markdown, JSON, YAML, XML, source-code extensions, logs, and shell scripts. Unknown or large binary files should not be loaded into the text viewer.

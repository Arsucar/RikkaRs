# File fullscreen view and edit actions

## Goal

Add in-app fullscreen viewing for text-like files and fullscreen editing for writable workspace text files.

## Issues

- #40: file menus need fullscreen view/edit for text files in workspace file lists, edited-file chips, and document chips.

## Requirements

- Text-like files in workspace file row menus can be opened in fullscreen view.
- Writable workspace text files can be edited fullscreen and saved back through workspace repository APIs.
- `EditedFilesList` bottom sheet exposes view/edit actions where a workspace path can be resolved.
- `UIMessagePart.Document` text-like attachments prefer in-app readonly fullscreen view before falling back to external `ACTION_VIEW`.
- Reuse or extract existing fullscreen text components rather than duplicating UI.

## Acceptance Criteria

- [ ] Workspace text file row has fullscreen view action.
- [ ] Workspace writable text file row has fullscreen edit action that persists changes.
- [ ] Edited file chips offer view and edit where workspace binding exists.
- [ ] Text document attachments can be viewed in-app readonly.
- [ ] Binary/unknown files keep existing export/share/open behavior.
- [ ] UI handles loading, errors, and large text gracefully enough for common source/log files.

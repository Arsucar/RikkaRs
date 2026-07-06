# Design

## Boundary

This task is scoped to Android backup rule coverage for app-specific workspace files. It should not change workspace storage APIs, migration behavior, sync providers, or terminal/rootfs persistence.

## Current Data Flow

- `resolveWorkspaceFilesBaseDir(context, PRIVATE)` stores workspace files under `files/workspaces`.
- `resolveWorkspaceFilesBaseDir(context, EXTERNAL)` stores workspace files under app-specific external storage: `external/workspaces`.
- Android backup is enabled in the manifest and controlled by:
  - `res/xml/backup_rules.xml` for legacy full backup behavior.
  - `res/xml/data_extraction_rules.xml` for Android 12+ cloud/device transfer behavior.

## Proposed Rule Shape

If the product decision is to include external workspace files:

- Add `<include domain="external" path="workspaces/" />` to `backup_rules.xml`.
- Add the same include to `data_extraction_rules.xml` under `cloud-backup`.
- Consider whether `device-transfer` should mirror `cloud-backup`; if left absent, document that only cloud rules are explicit.

If the product decision is to exclude external workspace files:

- Add `<exclude domain="external" path="workspaces/" />` to both XML rule sets.
- Add user-facing explanation only if the backup page currently implies all user data is covered.

## Compatibility

- Preserve the existing `files/upload/` include.
- Because Android backup include rules are allow-lists once present, every intended backed-up path must be listed explicitly.
- XML-only changes should be backward-compatible and avoid schema or database migrations.

## Risks

- Workspace files can be large. Including them may increase backup size or hit provider limits.
- Workspace content can contain generated artifacts or sensitive files created by tools. The implementation should avoid broad `external "."` includes.
- Android 12+ ignores legacy `fullBackupContent` when `dataExtractionRules` is present, so both files must be kept aligned.

# feat: backup module coverage for external workspace files

## Goal

Make backup behavior explicit for workspace files stored under the app-specific external storage option, so users do not lose workspace content unexpectedly after restore or device transfer.

## Confirmed Facts

- Workspace file storage can be `PRIVATE` or `EXTERNAL`.
- Private workspace files resolve to `context.filesDir/workspaces`.
- External workspace files resolve to `context.getExternalFilesDir("workspaces")`.
- The manifest enables Android backup with `android:allowBackup="true"`, `android:fullBackupContent="@xml/backup_rules"`, and `android:dataExtractionRules="@xml/data_extraction_rules"`.
- Current `backup_rules.xml` includes only `domain="file"` path `upload/`.
- Current `data_extraction_rules.xml` has an empty `cloud-backup` section, so Android 12+ behavior is not explicitly aligned with the legacy full-backup rules.
- Android backup rule syntax supports `domain="external"` for app-specific external files, and adding any `<include>` means only listed paths are included.

## Requirements

- Decide and document the intended backup policy for external workspace files: include `external/workspaces/` or intentionally exclude it.
- Keep Android 11-and-lower `fullBackupContent` and Android 12+ `dataExtractionRules` behavior consistent.
- Preserve the existing `files/upload/` backup inclusion unless a later product decision explicitly changes it.
- Avoid backing up caches, generated build output, or terminal/rootfs state unless the product decision explicitly includes them.
- Prefer a narrow XML rule change if the decision is to include workspace files; do not add custom `BackupAgent` behavior unless XML rules are insufficient.

## Acceptance Criteria

- [ ] The task states whether external workspace files should be backed up or excluded.
- [ ] If included, both `backup_rules.xml` and `data_extraction_rules.xml` explicitly include the external workspace path.
- [ ] If excluded, both XML files explicitly exclude the external workspace path and the UI/docs explain the behavior where relevant.
- [ ] Existing upload backup coverage remains unchanged.
- [ ] Validation includes an XML/resource build check, at minimum `.\gradlew :app:processDebugResources` or a broader build.

## Out of Scope

- Redesigning the backup page UX.
- Implementing cloud sync for workspace files.
- Migrating workspace storage location.
- Backing up non-app-specific shared external files.

## Product Decision

- Include `external/workspaces/` in backup rules because the storage option is app-specific user workspace content, not a disposable cache. Trade-off: backups may become larger and may include user-created project files.

# Android Backup Rules

## Scenario: App-Owned User File Backup

### 1. Scope / Trigger

- Trigger: editing `app/src/main/res/xml/backup_rules.xml` or `app/src/main/res/xml/data_extraction_rules.xml`.
- Scope: user-owned app files that should survive Android cloud backup or device transfer.

### 2. Signatures

- Legacy full backup: `app/src/main/res/xml/backup_rules.xml`.
- Android 12+ extraction rules: `app/src/main/res/xml/data_extraction_rules.xml`.
- Manifest bindings:
  - `android:fullBackupContent="@xml/backup_rules"`
  - `android:dataExtractionRules="@xml/data_extraction_rules"`

### 3. Contracts

- `files/upload/` remains included in both rule files unless a product decision changes upload retention.
- App-specific external workspace files live under `external/workspaces/` and are user content.
- Any `<include>` rule turns the file into an allow-list, so every intended backed-up path must be listed explicitly.
- Keep `cloud-backup` and `device-transfer` aligned unless the task explicitly documents a different policy.

### 4. Validation & Error Matrix

- Missing path from `backup_rules.xml` -> Android 11 and lower backup behavior diverges.
- Missing path from `data_extraction_rules.xml` -> Android 12+ cloud or device transfer behavior diverges.
- Broad external include such as `path="."` -> generated or sensitive tool files may be backed up unintentionally.

### 5. Good/Base/Bad Cases

- Good: include `domain="external" path="workspaces/"` in both XML files when workspace content should be backed up.
- Base: preserve the existing `domain="file" path="upload/"` include when adding new paths.
- Bad: add only `backup_rules.xml` and leave `data_extraction_rules.xml` unchanged.

### 6. Tests Required

- XML-only backup rule changes: run `.\gradlew :app:processDebugResources --no-daemon --no-configuration-cache --console=plain`.
- Broader app code changes nearby: also run the focused Kotlin or unit-test command relevant to the changed code.

### 7. Wrong vs Correct

#### Wrong

```xml
<full-backup-content>
  <include domain="external" path="workspaces/" />
</full-backup-content>
```

Only legacy full backup is updated.

#### Correct

```xml
<full-backup-content>
  <include domain="external" path="workspaces/" />
</full-backup-content>
```

```xml
<data-extraction-rules>
  <cloud-backup>
    <include domain="external" path="workspaces/" />
  </cloud-backup>
  <device-transfer>
    <include domain="external" path="workspaces/" />
  </device-transfer>
</data-extraction-rules>
```

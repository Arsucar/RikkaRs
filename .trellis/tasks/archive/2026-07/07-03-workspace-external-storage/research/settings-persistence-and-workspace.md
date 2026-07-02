# Research: Settings persistence & workspace storage hooks

- **Query**: Where to add `workspaceFilesStorage` (PRIVATE/EXTERNAL); persistence, Room, FileFolders, WorkspaceRepository, WorkspaceShellContext, tests
- **Scope**: internal
- **Date**: 2026-07-03

## Findings

### 1. Main settings data class (not `AppSettings`)

There is **no** `AppSettings` type. App-wide user preferences live in:

| Path | Role |
|------|------|
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` | `SettingsStore` + `data class Settings` |
| Same file | Nested types: `DisplaySetting`, `ImageGenerationSettings`, `WebDavConfig`, etc. |

`Settings` is a `@Serializable data class` (lines ~569–627). Representative fields:

```kotlin
@Serializable
data class Settings(
    @Transient val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val enableWebSearch: Boolean = false,
    val chatModelId: Uuid = Uuid.random(),
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val launchCount: Int = 0,
    // ... many more model/MCP/TTS/ASR/image fields
)
```

**Implication for `workspaceFilesStorage`**: Add a field on `Settings` (e.g. enum with `@Serializable` + `@SerialName`), plus a matching `PreferencesKey` in `SettingsStore.Companion`, map it in `settingsFlowRaw`, and persist in `SettingsStore.update(settings)`.

Workspace **instances** are separate: `WorkspaceEntity` in Room (`app/.../data/db/entity/WorkspaceEntity.kt`), not part of `Settings`.

---

### 2. How settings are persisted

**Mechanism**: Android **DataStore Preferences** (not Room, not protobuf schema).

- Extension: `Context.settingsStore` via `preferencesDataStore(name = "settings", produceMigrations = { ... })` (`PreferencesStore.kt` ~70–78).
- Migrations: `PreferenceStoreV1Migration`, `V2`, `V3` under `app/.../data/datastore/migration/`. Version tracked with `intPreferencesKey("data_version")` (`SettingsStore.VERSION`); V3 runs when `version == null || version < 3`.

**Load path**:

- `settingsFlowRaw`: `dataStore.data` → map each `Preferences` key into `Settings` (scalar keys + `JsonInstant` for complex lists/objects).
- Post-processing: defaults merge, dedupe, `migrateSubagentBuiltinsIfNeeded`, then `settingsFlow` = `distinctUntilChanged().toMutableStateFlow(...)`.

**Save path**:

- Primary API: `suspend fun update(settings: Settings)` — writes every known key in `dataStore.edit { ... }` (`PreferencesStore.kt` ~416–490).
- Functional API: `suspend fun update(fn: (Settings) -> Settings)` → `update(fn(settingsFlow.value))`.
- Partial updates exist for some keys (e.g. `updateAssistant` only touches `SELECT_ASSISTANT`).

**DI**: `SettingsStore` is a Koin `single` in `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` (~50–52).

**Not settings**: Conversations, workspaces metadata, managed files, etc. use **Room** (`AppDatabase`).

---

### 3. Room (workspaces metadata only)

| Path | Detail |
|------|--------|
| `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt` | `@Database(..., version = 27)` |
| Entities | `ConversationEntity`, `WorkspaceEntity`, `ManagedFileEntity`, … (8 entities) |
| Auto-migrations | Declared on `@Database` from 1→2 through 23→24 (with specs `Migration_8_9`, `Migration_16_17`, `Migration_22_23`) |
| Manual migrations | Registered in `DataSourceModule.kt`: `Migration_6_7`, `11_12`, `13_14`, `14_15`, `15_16`, `24_25`, `25_26`, `26_27` |

`WorkspaceEntity` is its own `@Entity(tableName = "workspaces")` — **not** embedded in `Settings`.

```kotlin
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("name") val name: String,
    @ColumnInfo("root") val root: String,  // filesystem key under WorkspaceManager baseDir
    @ColumnInfo("shell_status") val shellStatus: String = ...,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
    @ColumnInfo("last_access_at") val lastAccessAt: Long? = null,
    @ColumnInfo("tool_approvals", defaultValue = "{}") val toolApprovals: String = "{}",
)
```

**Implication**: A global PRIVATE/EXTERNAL preference belongs in **DataStore `Settings`**, not a Room migration—unless you also store per-workspace storage location in `WorkspaceEntity` (not present today).

---

### 4. `FileFolders`

**Path**: `app/src/main/java/me/rerere/rikkahub/data/files/FilesManager.kt` (bottom of file)

```kotlin
object FileFolders {
    const val UPLOAD = "upload"
    const val SKILLS = "skills"
    const val FONTS = "fonts"
    const val TOOL_OUTPUTS = "tool_outputs"
}
```

**`RepositoryModule.kt`** binds these under `context.filesDir` into proot:

```kotlin
WorkspaceManager(
    baseDir = File(context.filesDir, "workspaces"),
    shellRunner = ProotShellRunner(
        extraBindMounts = listOf(
            WorkspaceBindMount(File(context.filesDir, FileFolders.SKILLS), "/skills"),
            WorkspaceBindMount(File(context.filesDir, FileFolders.TOOL_OUTPUTS), "/tool_outputs"),
            WorkspaceBindMount(File(context.filesDir, FileFolders.UPLOAD), "/upload"),
        ),
    ),
)
```

Workspace **content** roots today: `baseDir = filesDir/workspaces/<root>/` (see `WorkspaceManager`).

---

### 5. `WorkspaceRepository` & roots

**Path**: `app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceRepository.kt`

| API | Behavior |
|-----|----------|
| `listFlow()` | `dao.listFlow()` |
| `getAll()` equivalent | `dao.getAll()` used in `checkIntegrity()` — returns all `WorkspaceEntity` |
| `create(name)` | New `id`/`root` = random UUID string; `manager.ensureWorkspace(workspace.root)`; `dao.upsert` |
| `ensureWorkspace` usage | Before file ops / commands: `manager.ensureWorkspace(workspace.root)` |
| `checkIntegrity()` | For each entity: if `manager.workspaceDir(root)` missing → delete row; if rootfs missing → reset shell status |

There is **no** `getAllRoots()` on repository; roots are `WorkspaceEntity.root` from `dao.getAll()` or each row’s `root` field (`create` sets `root = id`).

**Path**: `workspace/src/main/java/me/rerere/workspace/WorkspaceManager.kt`

```kotlin
fun ensureWorkspace(root: String): File {
    val dir = workspaceDir(root)
    filesDir(root).mkdirs()
    linuxDir(root).mkdirs()
    tempDir(root).mkdirs()
    return dir
}

fun workspaceDir(root: String): File = File(baseDir, root)
fun filesDir(root: String): File = File(workspaceDir(root), FILES_DIR)
fun linuxDir(root: String): File = File(workspaceDir(root), LINUX_DIR)
fun tempDir(root: String): File = File(workspaceDir(root), TEMP_DIR)
```

`baseDir` is injected in Koin as `File(context.filesDir, "workspaces")`.

---

### 6. `WorkspaceShellContext`

**Path**: `workspace/src/main/java/me/rerere/workspace/WorkspaceShellRunner.kt`

```kotlin
data class WorkspaceShellContext(
    val root: String,
    val command: String,
    val cwd: String,
    val filesDir: File,
    val linuxDir: File,
    val tempDir: File,
    val workingDir: File,
    val timeoutMillis: Long,
    val stdin: ByteArray? = null,
)
```

Built in `WorkspaceManager` when executing commands (uses `filesDir(root)`, `linuxDir(root)`, `tempDir(root)`).

---

### 7. Workspace module tests

| File | What it tests |
|------|----------------|
| `workspace/src/test/java/me/rerere/workspace/ExampleUnitTest.kt` | `WorkspaceFileSystem` read/write/list/grep; path escape; `ensureWorkspace` + `hasRootfs`; `RootfsInstaller` HTTP download; `executeCommand` host shell + stdin; proot without rootfs; output truncation; `RootfsPatcher` |
| `workspace/src/test/java/me/rerere/workspace/RootfsInstallerTest.kt` | Tar/gzip extract edge cases (OTHER/sparse entries, directories) with `TemporaryFolder` + real `RootfsInstaller` |
| `workspace/src/test/java/me/rerere/workspace/WorkspaceShellPolicyTest.kt` | `evaluateShellCommand` allow/reject lists (security policy), pure JUnit `Assert` |

**Conventions**: JUnit4 (`@Test`, `@Rule TemporaryFolder` where needed); temp dirs via `Files.createTempDirectory` or `TemporaryFolder`; construct `WorkspaceManager(baseDir)` with no Android framework; assert with `org.junit.Assert`.

---

## Caveats / Not Found

- No existing `workspaceFilesStorage` or external storage path for workspaces in codebase (task is greenfield on `Settings` + `WorkspaceManager` base dir).
- `SettingsStore.VERSION` / PreferenceStore migrations: adding a new preference key does **not** automatically require a DataMigration unless you need to transform old data; new fields can default in `Settings` + read mapping.
- Room version **27** with gap: auto-migrations stop at 23→24; 24–27 are manual only—unrelated to DataStore enum unless you add Room columns.
- `getAllRoots()` does not exist; use `WorkspaceDAO.getAll()` or add repository helper.

## Related Specs

- Task design: `.trellis/tasks/07-03-workspace-external-storage/design.md` (references `WorkspaceRepository`, storage switch)
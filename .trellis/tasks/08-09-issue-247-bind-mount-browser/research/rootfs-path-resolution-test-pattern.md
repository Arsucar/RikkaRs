# Research: RootfsPathResolutionTest pattern

- **Query**: Test patterns for bind-mount path resolution to extend for #247
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `workspace/src/test/java/me/rerere/workspace/RootfsPathResolutionTest.kt` | Primary bind-mount resolution tests |
| `workspace/src/test/java/me/rerere/workspace/WorkspaceManagerFilesDirTest.kt` | Manager filesBaseDir / lock / program tests |
| `app/src/test/.../data/ai/tools/WorkspaceKnownMountTest.kt` | App-layer known mount resolve (tools) |
| `app/src/test/.../data/files/SkillPathsTest.kt` | skills_private path helpers |

### Code Patterns

#### Fixture style (JUnit4 + TemporaryFolder)

```kotlin
class RootfsPathResolutionTest {
    @get:Rule val tempFolder = TemporaryFolder()
    private val root = "test-workspace"

    private fun createManager(): WorkspaceManager {
        skillsDir = tempFolder.newFolder("skills")
        val uploadDir = tempFolder.newFolder("upload")
        return WorkspaceManager(
            baseDir = tempFolder.newFolder("workspaces"),
            bindMounts = listOf(
                WorkspaceBindMount(source = skillsDir, target = "/skills"),
                WorkspaceBindMount(source = uploadDir, target = "/upload"),
            ),
        ).also { it.ensureWorkspace(root) }
    }
}
```

#### Covered cases today

| Test | Asserts |
|---|---|
| `readsFileWrittenThroughBindMountPath` | write host skills dir → `rootfsFileSize` + `exportRootfsFile` `/skills/...` |
| `bindMountTargetDoesNotMatchLongerSiblingPrefix` | `/skills` vs `/skillsets` longest-prefix |
| `workspacePathStillResolvesToFilesArea` | `/workspace/notes.txt` → filesDir |
| `unknownAbsolutePathFallsBackToRootfsInterior` | `/etc/hostname` under linuxDir |
| `traversalOutOfBindMountIsRejected` | `/skills/../secret.txt` escapes |
| `kernelFilesystemPathIsRejectedWithHint` | `/proc/version` → IllegalStateException + workspace_shell |
| `missingFileReportsOriginalAbsolutePath` | message keeps Rootfs path |
| `directoryPathIsNotReadableAsFile` | dir via rootfsFileSize rejected |

#### Gaps vs #247 AC8

Missing tests (to add in same style):

1. **`listFiles(LINUX, path matching mount)`** redirects to mount source contents.
2. **`listFiles(LINUX, "")`** behavior at root — placeholders vs synthetic mount entries (per product choice).
3. **`listFiles` under `/workspace` mapping** when path is `workspace` or `workspace/...` in LINUX area (maps to files area content).
4. **`readText` / `fileSize` / `exportFile` with LINUX + mount-relative path** after redirect.
5. **Empty / missing mount source** → empty list or non-crash empty dir.
6. **KERNEL_FS paths** still not redirected as mounts.
7. **skills_private dynamic mount** + assistant resolution (likely **app** module unit test, not workspace module — needs Settings/SkillManager or pure functions).

#### App-layer parallel: WorkspaceKnownMountTest

- Tests `resolveKnownMountFile` with `/skills`, `/upload`, traversal, symlink roots.
- Pattern for app-side path helpers; skills_private assistant resolution tests can live near Settings/Assistant tests.

### Related Specs

- None.

## Caveats / Not Found

- No existing `listFiles` mount tests.
- No WorkspaceDetailVM unit tests.
- `RootfsPathResolutionTest` does not cover `tool_outputs` or `skills_private` (constructor table only skills+upload).

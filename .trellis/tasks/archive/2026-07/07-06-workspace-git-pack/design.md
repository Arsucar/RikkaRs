# Design

## Affected Areas

- `app/src/main/java/me/rerere/rikkahub/data/repository/WorkspaceStorageMigrator.kt`
- `app/src/main/java/me/rerere/rikkahub/di/RepositoryModule.kt`
- `workspace/src/main/java/me/rerere/workspace/WorkspaceManager.kt`
- `workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt`
- `WorkspaceFilesBaseDir` and settings around `workspace_files_storage`.

## Investigation Tracks

- Confirm whether failures happen only on EXTERNAL storage.
- Confirm whether `--link2symlink` is required for rootfs but harmful for `/workspace` Git pack operations.
- Check whether `.l2s.tmp_pack_*` files indicate link2symlink rename translation failure.
- Evaluate whether `/workspace` can be bound differently from rootfs paths.

## Product Fallback

If Android external app-specific storage cannot support Git pack writes reliably through proot, the app should prefer PRIVATE storage for Git-heavy workspaces or show a warning when EXTERNAL is selected.

## Root Cause Notes

- `ProotShellRunner` runs every workspace shell with `--link2symlink` and bind-mounts the selected files root at `/workspace`.
- Device validation on `100.99.129.110:5555` with PRIVATE storage (`/data/user/0/.../files/workspaces/<id>/files`, f2fs) passed `git init`, local `git clone`, and `git pull` inside `/workspace`.
- The current debug app stores the active workspace files in app-specific EXTERNAL storage (`/storage/emulated/0/Android/data/.../files/workspaces/.../files`). Android exposes that path through external-storage/FUSE semantics; this is the unsupported combination reported by #35 for Git pack rename/link behavior.
- EXTERNAL storage remains useful for file-manager/USB editing, but the app must not imply reliable Git pack writes there.

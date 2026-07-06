# Implementation Plan

## Checklist

- [x] Inspect proot runner flags and bind mount construction.
- [x] Inspect workspace storage setting and migrator.
- [x] Reproduce on a device/emulator if available with PRIVATE and EXTERNAL storage.
- [x] Test minimal Git operations: `git init`, local `git clone`, `git pull`.
- [x] Implement the smallest reliable fix or user-facing guardrail.
- [x] Document remaining limitations.

## Validation

- [x] `.\gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.model.WorkspaceFilesStorageTest" --tests "me.rerere.rikkahub.data.repository.WorkspaceStorageMigratorTest" --no-daemon`
- [x] `.\gradlew :app:compileDebugKotlin --no-daemon`
- [x] `.\gradlew :app:installDebug --no-daemon` installed `app-arm64-v8a-debug.apk` on `100.99.129.110:5555`.
- [x] Device/rootfs manual Git check on `100.99.129.110:5555`: PRIVATE storage passed `git init`, local `git clone --no-local`, and `git pull --ff-only` under `/workspace` with `packs=1 l2s=0 files=1`.
- [x] EXTERNAL storage limitation documented: active workspace files are under `/storage/emulated/0/Android/data/...`; app now warns that Git pack writes may fail there.

## Implementation Notes

- Root cause: `--link2symlink` applies globally to the proot process. Git finalizes object pack files with hard-link/rename flows under `.git/objects/pack`; on bind-mounted `/workspace`, link2symlink leaves `.l2s.*` metadata/symlink artifacts in the pack directory and can break subsequent pack finalization.
- Fix: normal workspace shells no longer pass `--link2symlink` by default. The runner keeps an explicit compatibility flag for future rootfs package-management cases that need hard-link emulation.
- Product guardrail: EXTERNAL workspace files storage is still advertised as not supporting reliable Git pack writes through the workspace shell; the UI warns users to use PRIVATE storage for Git-heavy workspaces.
- Device comparison: the same private `/workspace` smoke test with `--link2symlink` left `.l2s.*` artifacts (`packs=1 l2s=6`).

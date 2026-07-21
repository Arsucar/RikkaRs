# Implementation Plan

- [x] Merge `origin/release/rikka-arsucar` into the current release branch and verify the three dirty skill files remain intact.
- [x] Inspect the merged draft/ASR code and established tests; add the smallest reusable eligibility/completion policy needed for deterministic tests.
- [x] Disable draft generation in edit mode and during active ASR states; preserve cancellation and late-chunk guards.
- [x] Treat blank draft completion as failure, restore original input safely, and add localized feedback if a new message is required.
- [x] Correct memory tool response descriptions, add `template_id` to template responses, reject create-time `expected_revision`, and add tool tests.
- [x] Add Repository-level CAS tests using the established Room/repository test pattern, including revision, snapshot, and stale conflict checks.
- [x] Make UI task-artifact wording conditional across the guide, workflow, app index, and relevant Trellis skills; preserve direct-edit verification requirements.
- [x] Perform static cross-layer review and run one consolidated Gradle verification with `--no-daemon`.
- [x] Run `git diff --check`, inspect final diff, and execute the repository device-install flow.
- [x] Update task/spec records if implementation exposes an additional reusable rule; commit the completed changes without pushing.

## Risk And Rollback Points

- Merge conflicts in `ChatService.kt` must be resolved by preserving both #170 CAS wiring and #169 draft generation.
- Draft cancellation must keep generation-token invalidation before coroutine cancellation.
- Tool response additions must remain backward compatible; do not rename existing serialized fields.
- Do not claim visual/device verification if installation or interaction checks cannot be completed.

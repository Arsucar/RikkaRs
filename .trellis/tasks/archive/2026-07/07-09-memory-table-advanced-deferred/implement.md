# Deferred Memory Table Advanced Features Implementation Plan

## Final-Phase Split

- [ ] Create a child task for #93 per-table `injectPolicy.enabled`.
- [ ] Create a child task for #97 query action.
- [ ] Create a child task for #98 `apply_ops`.
- [ ] Create a child task for #94 trigger-send row filtering.
- [ ] Create a child task for #99 macro/manual injection placement.
- [ ] Create a child task for #100 import/export bundle.
- [ ] Create a child task for #96 revision snapshots and rollback.

## Validation Expectations

- Injection-only tasks: focused transformer tests plus `:app:compileDebugKotlin`.
- Tool read/write tasks: focused `MemoryTableToolsTest` plus repository tests if
  repository behavior changes.
- DB/migration tasks: Room migration tests and broader `:app:testDebugUnitTest`.
- UI or app-module functionality: install debug if a device is connected.

## Current Batch Decision

- [x] Do not implement these advanced features in the current broad issue-fix
      batch.
- [x] Record dependency order after foundation, row semantics, and observability
      packages are complete.
- [x] Keep #68 upstream merge separate from this final-phase memory-table plan.

# Provider Configuration Batch

## Goal

Clean up provider tag behavior and add provider-specific client-side rate limit
configuration.

## Requirements

- Implement #77: remove built-in provider suggested tags from the source of
  truth so filter chips and tag management show user-created/used tags only.
- Implement #82: support per-provider RPM/TPM limits and suspend requests until
  quota is available without blocking the UI thread.

## Acceptance Criteria

- [x] Fresh installs without user tags do not show default suggested provider
      tags in provider/model filters.
- [x] Existing user tags remain visible and manageable.
- [x] Provider settings can store independent RPM/TPM limits.
- [x] LLM requests for a provider observe that provider's configured limit.
- [x] Concurrent requests and subagent/tool flows share the same provider
      limiter semantics.

# Provider Configuration Batch Design

## Scope

This task resolves provider tag cleanup (#77) and provider-level rate limiting
(#82). It is limited to provider settings, provider/model filter tag sources,
and the unified text generation entry point.

## Design

- Provider tags:
  - Treat `ProviderSetting.tags` and `Settings.providerTagOrder` as the only
    visible provider tag sources.
  - Keep `hiddenProviderTags` only as a user preference for hiding remembered
    order entries that are not currently used.
  - Stop merging bundled `provider_suggested_tags` into filter or management
    chips.

- Rate limits:
  - Add a serializable `ProviderRateLimit` value to every provider setting.
  - Preserve the value when converting between provider types.
  - Add RPM and TPM fields to the provider configuration UI; blank or non-positive
    values mean unlimited.
  - Enforce limits in `GenerationHandler` immediately before invoking the
    provider implementation, so normal chats, subagents, and tool-initiated
    generation share the same limiter behavior.

## Compatibility

- Existing provider JSON should decode with default unlimited limits.
- Existing user-created provider tags should remain visible.
- The bundled suggested tag resource may remain for now if no longer referenced
  by provider filter/management code.

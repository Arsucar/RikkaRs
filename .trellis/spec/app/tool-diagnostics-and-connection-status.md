# Tool Diagnostics And Connection Status

- Diagnostics are read-only projections over an already sampled capability snapshot.
- Reason chains and repair targets contain stable domain values; UI maps them to localized labels and navigation.
- Copy summaries are allowlisted and must exclude IDs, runtime names, URLs, headers, cookies, tokens, and raw config.
- A failed source is isolated from healthy sources and records only an exception class name.
- Connection probes may authenticate and call `listTools`, but never `callTool`, mutate settings, or replace long-lived clients.
- Connection results are keyed by server ID and revision; obsolete revisions must be rejected.
- Aggregated status prioritizes authorization and transport failures above connecting, success, empty, and idle.

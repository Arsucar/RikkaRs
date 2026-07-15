# Workspace Image Tool Result Design

## Data Flow

- Detect images after sandboxed path resolution and bounded file read.
- Return structured image content using the same UI message content abstraction used by user attachments, carrying canonical MIME and bytes/base64 or an authorized local reference.
- Tool-result merging and provider adapters preserve the image part; textual status is supplemental metadata, not the only output.

## Compatibility and Safety

- Text and non-image binary behavior remains unchanged.
- Enforce existing workspace permission boundary, maximum size and supported MIME validation before allocating/encoding.
- Providers/models without image tool-result support receive explicit fallback/error behavior rather than a false visual-success claim.

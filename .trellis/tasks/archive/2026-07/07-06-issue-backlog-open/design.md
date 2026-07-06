# Resolve Open Issue Backlog Design

## Scope and Boundaries

The implementation is split into four low-overlap slices:

1. Chat and Skills UI (#43) under `app/.../ui/components/ai` and
   `app/.../ui/pages/extensions/skills`.
2. Workspace text preview and Markdown read-only rendering (#44) under
   `TextFileUtil`, `WorkspaceDetailPage`, and reusable text/Markdown components.
3. Workspace shell/subagent presentation (#45/#47) under message tool UI and
   subagent transcript construction.
4. Tool execution/provider fixes (#46/#48) under `ChatService`, subagent tool
   creation, and OpenAI provider serialization.

No database migrations, remote API contract changes, or Android manifest changes
are expected.

## Data Flow and Contracts

- `ModelSelector.allowClear` is a UI affordance only. Removing it from chat input
  must not change `ChatVM.setChatModel` or assistant settings pages.
- Workspace text eligibility remains a name/MIME heuristic before reading the
  file. File contents are still bounded by repository read limits and
  `MAX_TEXT_FILE_VIEW_BYTES`.
- Markdown rendering is selected from the opened file name and read-only mode.
  Editing continues to use `FullScreenTextEditor` to avoid mixing preview and
  editor state.
- Shell command display should derive from parsed tool input when available and
  fall back to an explicit loading/unknown label while streaming.
- Subagent transcript records should preserve useful command snippets without
  exposing unbounded tool input.
- OpenAI tool schemas should normalize `null` to a JSON object schema at the
  provider/core boundary so tools like `finish_work` are valid for Responses API.

## Compatibility

- Existing assistant pages that intentionally use `allowClear = true` keep that
  behavior.
- Existing Markdown source editing behavior is preserved in edit mode.
- Parallel subagent execution remains opt-in via `assistant.parallelToolExecution`.
- Chat Completions can share the same normalized parameter serialization even if
  it was less strict than Responses API.

## Risks and Rollback

- UI changes can regress layout density on narrow screens; verify compile and
  inspect code paths for stable Compose sizing.
- Dotfile/extensionless text heuristics can over-admit binary files; keep the
  allowlist conservative and retain size/read failure handling.
- Provider serialization touches request payloads; prefer a small shared helper
  and focused tests if test infrastructure allows it.

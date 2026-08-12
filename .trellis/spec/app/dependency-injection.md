# Dependency Injection Conventions

> Unified rules for the four DI patterns used in the app module, so new code
> picks the right one and existing inconsistencies stop spreading.

## Patterns in use

| Pattern | API | Where it is correct | Where it is wrong |
|---------|-----|---------------------|-------------------|
| 1. VM constructor injection | `koinViewModel { ... }` + `viewModelOf(::X)` in `ViewModelModule` | Business dependencies (Repository / Manager / Store / Coordinator / UseCase) consumed by a screen | — |
| 2. `koinInject<T>()` in Composable | `org.koin.compose.koinInject` | UI-only leaf dependencies in hooks/components with **no** owning VM (e.g. `rememberCustomTtsState`) | Business dependencies a screen VM already exposes |
| 3. `by inject<T>()` in Activity | `org.koin.android.ext.android.inject` | Activity-scoped singletons (e.g. `OkHttpClient` in `RouteActivity`) | Composables or non-Activity classes |
| 4. Manual `new` inside a VM | `private val x = Foo(...)` | Concurrency primitives and UI state holders only — see "Keep inside the VM" | Business objects that have a Koin registration |

## Rules

### Rule 1 — Business dependencies go through VM constructors

A "business dependency" is any class that talks to the database, network,
DataStore, file system, or orchestrates multiple such calls:
`Repository`, `Manager`, `Store`, `Coordinator`, `UseCase`, `Service`.

- Register the dependency in the matching Koin module
  (`dataSourceModule`, `repositoryModule`, `appModule`).
- Inject it through the VM constructor and expose derived state from the VM.
- Screens obtain it via `koinViewModel` and pass values down, never the
  dependency itself.

This is the only pattern that survives configuration changes correctly and
keeps the dependency graph testable.

### Rule 2 — `koinInject` is allowed only when no VM owns the call site

`koinInject` in a Composable is acceptable when **all** are true:

- The Composable is a reusable leaf component / hook, not a page.
- There is no page-level VM that could own the dependency instead.
- The dependency is a UI-level concern (sound effects, emoji data, app
  scope, the `SettingsStore` read by `rememberSettingsState`).

When a page-level VM already exists and a sub-Composable needs the same
dependency, pass the value from the VM down — do not call `koinInject` again.

> Historical note: the codebase currently has ~51 `koinInject` call sites.
> Full migration is high-risk and tracked separately. The rule above applies
> to **new** code; existing call sites are acceptable until the owning VM
> gains the field.

### Rule 3 — Activity-scoped singletons use `by inject<T>()`

Use `org.koin.android.ext.android.inject` only inside `Activity` / `Service`
subclasses for process-wide singletons (e.g. `RouteActivity.okHttpClient`,
`RouteActivity.settingsStore`, `WebServerService`).

Never use it in a Composable or a non-Activity class. For non-Activity classes
that need lazy Koin resolution, implement `KoinComponent` (see
`CustomTtsStateImpl`).

### Rule 4 — Keep manual `new` only for non-business objects

These categories may stay as manual construction inside a VM:

| Category | Examples | Why it stays in the VM |
|----------|----------|------------------------|
| Concurrency primitives | `Mutex()`, `AtomicLong`, `MutableStateFlow` | Not a dependency; tied to the VM lifecycle |
| UI state holders | `ChatInputState()`, `WorkspaceDetailState()` | Compose state that must not outlive the VM |
| VM-scoped caches | `gitStatusGeneration`, `loadedGitWorkspaceId` | Plain fields, not Koin-managed |

Anything else constructed with `Foo(...)` inside a VM is a candidate for a
Koin module registration followed by constructor injection.

### Rule 5 — VM-scoped business objects

Some business objects are **intentionally** constructed inside the VM because
their state is per-conversation or per-assistant, not process-wide. They are
**not** registered in Koin.

| Object | Owner | Why it is VM-scoped |
|--------|-------|---------------------|
| `AssistantSwitchCoordinator` | `ChatVM` | Holds a per-conversation switch generation + mutex + settled assistant id; two ChatVMs must not share it |
| `ToolConnectionStatusStore` | `AssistantDetailVM` | Holds per-assistant connection revisions; one store per assistant detail screen |

When adding a similar VM-scoped business object, keep its construction in the
VM and document the reason in a comment, the same way these two do.

## Module layout

| Module file | What lives here |
|-------------|-----------------|
| `DataSourceModule.kt` | DB, HttpClient, DataStore, DAOs, low-level infrastructure, transformers |
| `RepositoryModule.kt` | Repositories, `FilesManager`, `SkillManager`, workspace glue |
| `AppModule.kt` | Services (`ChatService`), top-level managers (`WebServerManager`, `TTSManager`, `McpManager`-adjacent), hook wiring, app-scoped objects |
| `ViewModelModule.kt` | `viewModel` / `viewModelOf` registrations only |

When adding a business dependency, pick the module by what the dependency
**is**, not by who consumes it.

## Adding a new business dependency

1. Construct it in the matching module with `single { ... }`.
2. Add it as a `private val` parameter to the consuming VM.
3. If the VM uses `viewModelOf(::X)`, the parameter is resolved automatically.
4. If the VM uses `viewModel { params -> X(...) }`, pass `get()` explicitly.
5. Expose derived `StateFlow` / functions from the VM; the UI never sees the
   dependency directly.

## Verification

- `.\gradlew :app:compileDebugKotlin --no-daemon` after any DI change.
- A new `koinInject` call site in a page-level Composable is a smell — review
  whether the page VM should own the dependency instead.
- A new manual `Foo(...)` inside a VM must be categorised as a non-business
  object (Rule 4) or a documented VM-scoped business object (Rule 5).

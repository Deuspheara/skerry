# Contributing

Read [AGENTS.md](AGENTS.md) first: it holds the rules for everyone, humans and coding agents alike.

## Setup

- JDK 21 (Temurin) to build; the code targets Java 17 bytecode.
- Android SDK with platform 37. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.

## Everyday commands

```sh
./gradlew check            # detekt, spotless (ktlint), Android Lint, verifyCommonMain, unit tests: must pass before any commit
./gradlew spotlessApply    # fix formatting
./gradlew verifyCommonMain # no Android / java.* / System.currentTimeMillis / String.format in shared commonMain
./gradlew detektBaseline   # only to record existing issues, never to hide new ones
```

## Module map and where code goes

- `shared/core` (models, API, repositories, platform interfaces) <- `shared/stream`, `shared/chat`, `shared/player-api`
  <- `shared/presenters` <- `app`. A module never depends on one to its right.
- Logic with no UI and no platform API goes in the matching shared module's `commonMain`, with a test in `commonTest`
  (`kotlin.test`, Ktor `MockEngine`, the fakes in `shared/testing`). Package names stay `com.twitchplayer.tv.*`.
- `verifyCommonMain` (part of `check`) fails on `java.*`, `javax.*`, `android.*`, `System.currentTimeMillis`,
  `String.format`, `Math.`, `ConcurrentHashMap`, `@Synchronized` in any shared `commonMain`. Use the interfaces in
  `core.platform` or `expect/actual`.
- Screens: a presenter in `shared/presenters` exposing `StateFlow<UiState>` and `onEvent(...)` (see
  [docs/PRESENTERS.md](docs/PRESENTERS.md)); the Android UI renders the state and sends events. State classes that reach
  composables are listed in `config/compose/stability.conf` (check with `-PcomposeReports`).

### Adding a platform binding

1. Declare the interface in `shared/core` (`core.platform`), a `fun interface` when it has one method.
2. List it in the KDoc of the Koin module that consumes it (`sharedCoreModule`, `sharedStreamModule`, ...) and add a fake
   to that module's `Shared*ModuleTest` so the graph still resolves against exactly the documented list.
3. Implement it in `app` (Android) and bind it in `core/di/AppModule.kt`; `ProductionGraphResolutionTest` must stay green.
4. Add a row to the bindings table in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Commits and PRs

- Conventional Commits (`feat:`, `fix:`, `refactor:`, `build:`, `ci:`, `chore:`, `docs:`, `test:`, `perf:`), small and focused.
- No attribution trailers of any kind.
- A PR needs green CI. UI changes come with screenshots.

## Branch protection (repository owner, GitHub settings)

Settings > Branches > add rule for `main`:

- Require a pull request before merging
- Require status checks to pass: `static`, `test`, `build-release`; require branches to be up to date
- Require linear history; block force pushes and deletions
- Do not allow bypassing the rules (temporarily disable only when rewriting history)

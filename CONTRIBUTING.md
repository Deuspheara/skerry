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
  <- `shared/presenters` <- `platform-android` <- `app` (TV) and `phone`. A module never depends on one to its right.
- Apps and tools: `app` = Android TV UI; `phone` = Android phone UI (own screens, own `phoneModule`, the TV's translations through
  the `shared-strings` plugin, never the TV's code); `demo-android` = fictional demo data (debug and benchmark variants only);
  `benchmark` / `benchmark-phone` = Macrobenchmark and Baseline Profile generation for each app (`com.android.test` modules,
  `benchmark` and `nonMinified` build types that mirror the app's). Performance guardrails: AGENTS.md; phone numbers: docs/perf-phone.md.
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

### Regenerating the Baseline and startup profiles

Do it after a change that moves much of the startup or journey code (a new screen, a new library on the startup path), on the emulator only:

```sh
ANDROID_SERIAL=emulator-5554 benchmark/tools/generate-profiles.sh          # TV   -> app/src/main/baselineProfiles/
ANDROID_SERIAL=emulator-5554 benchmark-phone/tools/generate-profiles.sh    # phone -> phone/src/main/baselineProfiles/
./gradlew :phone:assembleRelease :phone:bundleRelease && python3 -I benchmark/tools/check-profile-packaging.py phone phone
```

The script records the startup run (becomes the startup profile, which drives DEX layout) and the journeys (merged with the startup run and
the hand-written rules in `*/tools/extra-profile-rules.txt` into the Baseline Profile). `ANDROID_SERIAL` is mandatory in spirit: Gradle's
connected tasks install on every adb device, a real TV or phone included. Commit the two text files; never weaken R8, keep rules or the
profile settings without asking (AGENTS.md). Then re-run the benchmarks twice (see docs/perf-phone.md for how and what to compare).

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

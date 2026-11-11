# KMP migration log (Phase 1)

One entry per step: what moved, decisions, what was verified. Baseline numbers: `docs/perf-baseline.md`.

## Step 1a: consumer keep rules and Compose stability file (before any code moves)

- `twitchplayer.kmp-library` publishes `consumer-rules.pro` (AGP `optimization { consumerKeepRules }`) when the file exists.
  `shared/core/consumer-rules.pro` now carries the `-dontwarn` rules for kotlinx.serialization and Ktor core
  (slf4j, `io.ktor.util.debug`, `java.lang.management`). OkHttp-related `-dontwarn` rules and the `Log` stripping stay in
  `app/proguard-rules.pro`. No `-keep` rules anywhere.
- Verified: the release R8 `configuration.txt` includes the section "Local project :::shared:core
  (extracted file: .../consumer_proguard_dir/androidMain/exportAndroidMainConsumerProguardFiles/lib0/proguard.txt)"
  with those rules; `:app:analyzeReleaseR8Config` still runs.
- `config/compose/stability.conf` (wired through `twitchplayer.compose`) marks the immutable data types that will live in
  shared modules as stable. Compose report of the app before any move (debug, `-PcomposeReports`):
  233 stable / 30 runtime / 44 unstable classes (unstable = services, view models, activities), 130 restartable skippable
  composables.

## Step 1b: `:shared:core` populated

Moved with `git mv`, package names unchanged (profile class names stay valid), `@Immutable` removed:

- `core/data/model/Models.kt`, `core/network/api/*`, `core/network/auth/{DeviceCodeAuth,TokenValidator}`,
  `core/data/repository/*` (LiveWatcher, SessionRepository, SettingsRepository, SnapshotCache, TtlCache, TwitchMappers,
  TwitchRepository), `feature/vod/data/VodProgressStore`, `RoomState` (own file, was in ChatModels.kt),
  `formatUptime` / `formatTwitchDuration` / `parseTwitchDurationSeconds` (file kept as `Formatters.kt`, so the profile's
  `FormattersKt` entries still match; no `String.format`, identical output).
- New in `core.platform`: `AppConfig` (client id, player host/parent, native-player flag, default engine),
  `SnapshotStorage` (replaces Context/File in `SnapshotCache`), `SettingsDefaults` (per-device chat defaults),
  `WallClock` (replaces `System.currentTimeMillis`). New in `core.di`: `sharedCoreModule()` and the `DataStores`
  qualifiers; the app module now only binds Android pieces (engine = OkHttp, `AndroidKeystoreTokenStore`,
  `AndroidSnapshotStorage`, the two `preferencesDataStore` stores, `AppConfig` from `BuildConfig`, low-RAM defaults).
- Stays in the app: `AndroidKeystoreTokenStore` (implements the new `TokenStore` interface), `AndroidSnapshotStorage`,
  `ViewerFormatters.kt` (`formatViewers`, ICU) and `PlayerEngine.buildDefault()` as a companion extension reading
  `BuildConfig` (call sites unchanged except for one import in two view models).

Decisions and deviations from the spec:

- `PlayerEngine.buildDefault()` could not stay in the model (it reads `BuildConfig`). The model now has
  `PlayerEngine.Fallback`; the repository gets the build's default injected through `AppConfig`.
- `TwitchRepositoryImpl.avatars`: `ConcurrentHashMap` became an immutable map snapshot in a `MutableStateFlow`
  updated with `update { it + ... }` (lock-free reads, atomic writes) rather than a `Mutex`, because `toLive(avatars)`
  reads it from non-suspending mappers.
- `parseTwitchTime` uses `kotlin.time.Instant.parse` instead of `SimpleDateFormat` (multiplatform, no API-level limit);
  the existing timestamp tests pass unchanged.
- Tests moved to `shared/core`: LiveWatcher/schedule, FreshThumbnail, Formatters (common, `kotlin.test`), VodProgressStore
  (jvmTest, temp dir instead of `TemporaryFolder`, injected clock instead of `Thread.sleep`). New: `SnapshotCacheTest`.
  `ChatSettingsTest`, `EmoteProvidersTest` etc. stay in the app (they also exercise app code).
- detekt: `MagicNumber` excludes now cover KMP test source sets. Baselines of the moved legacy findings are recorded per
  detekt task in `shared/core/detekt-baseline*.xml` (legacy only; my new files have none: `TtlCache` condition and the
  `DataStores` file name were fixed instead of baselined). `app/lint-baseline.xml` regenerated: still 20 issues (the
  entries were keyed by file path and the Keystore file was renamed).
- Compose stability: `config/compose/stability.conf` lists `core.data.model.*`, `VodProgress`, `RoomState`,
  `DeviceCodeResponse`.

Verification (release R8 build, commit state of this step) against `docs/perf-baseline.md`:

| Item | Baseline | Now |
|---|---|---|
| APK (unsigned) | 3,952,530 B | 3,968,914 B (+0.41 %) |
| `classes.dex` / `classes2.dex` | 1,824,696 / 3,240,128 B | 1,812,132 / 3,257,440 B (sum +0.09 %) |
| `assets/dexopt/baseline.prof` / `.profm` | 14,206 / 990 B | 13,560 / 1,005 B |
| `-keep` rules in `app/proguard-rules.pro` | 0 | 0 (only `-keepattributes`) |

- `baseline.prof` / `.profm` are packaged in the APK and the AAB (AAB: `BUNDLE-METADATA/com.android.tools.build.profiles/`).
  The compiled profile is 646 B smaller: rules whose methods changed no longer match (SnapshotCache coroutine lambdas,
  AppModule lambdas, constructors with new parameters, `TokenStore` -> `AndroidKeystoreTokenStore`). Class names are
  unchanged so the bulk of the profile (including all moved code) is intact. **Action for the end of Phase 1:**
  regenerate with `benchmark/tools/generate-profiles.sh` on the emulator (not run here: no connected tasks allowed in this step).
- Compose report (debug): unstable composable parameters identical to before (5, same set), 130 restartable skippable
  composables as before; app-level class counts dropped only because the moved classes left the app module.
- `./gradlew check` green (192 unit tests, 0 failures, `jvmTest` + `testAndroidHostTest` + app); `:app:analyzeReleaseR8Config` runs;
  `:app:bundleRelease` succeeds; Lint: no new issues (17 errors + 2 hints baselined, the `NewApi` in `PlaybackStats` is untouched).
- Not verified: runtime behaviour on a device/emulator (no connected tests, no TV); Macrobenchmark; R8 analyzer scores.

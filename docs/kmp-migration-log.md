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

## Step 1c: fixes from the independent review of step 1

1. **SnapshotCache dispatcher.** `read`, `write` and `clear` run entirely in `withContext(Dispatchers.IO)` again (JSON
   encode/decode, `runCatching` and the storage calls); the platform implementation keeps only file I/O.
2. **Catalog.** Removed `kotlinxDatetime`, `atomicfu`, `kotlinx-datetime` and `ktor-client-cio` (unused until the commit
   that needs them). `koin-test` was added because the Koin graph tests use it.
3. **`sarifReport` removal is deliberate.** AGP 9.5 deprecates `lint { sarifReport = true }` ("Lint reports are now always
   generated"). Verified by deleting `app/build/reports/lint-results-*.sarif` and re-running `:app:lintDebug`:
   `app/build/reports/lint-results-debug.sarif` is produced (valid SARIF 2.1.0), which is the path the CI workflow uploads.
   CI also now runs the shared module's checks (`detektMainJvm`, `detektMainAndroid`, `detektTestJvm`, `verifyCommonMain`),
   its tests (`jvmTest`, `testAndroidHostTest`) and uploads its detekt SARIF.
4. **`parseTwitchTime` strictness.** Preserved: only the exact `yyyy-MM-dd'T'HH:mm:ss'Z'` shape is parsed (regex first,
   then `kotlin.time.Instant.parse`); offsets, fractions, spaces, lower case and date-only inputs return 0, as before.
   The one difference: shape-valid but calendar-invalid values (month 13, Feb 30) were rolled over by the lenient
   `SimpleDateFormat`; they now return 0. Helix never sends them. Pinned by `FormattersTest.onlyTheExactHelixShapeIsAccepted`.
5. **Tests added.**
   - Shared (`commonTest`): `TtlCacheTest` (TTL boundary, force, invalidate, concurrent callers share one load, driven by
     `TestTimeSource`), `SessionRepositoryTest` (no tokens, valid tokens, refresh then validate, failed refresh clears
     everything and notifies, sign-out, scopes; Ktor `MockEngine`), `TwitchRepositoryAvatarTest` (avatars requested once,
     only unknown ids later), `SnapshotCacheTest`, `StorageNamesTest` (literals deliberately duplicated), `SharedCoreModuleTest`
     (whole shared graph resolved against exactly the platform bindings listed in its KDoc).
   - Shared (`jvmTest`): `SettingsRepositoryTest` (low-end defaults through `SettingsDefaults`, engine default through
     `AppConfig`, unknown stored enum values fall back), `VodProgressStoreTest` (on-disk fixture `vod_<id>` =
     `pos:dur:ts`, and the saved format).
   - App: `AndroidSnapshotStorageTest` (temp dir; constructor now takes a `File`), `AppKoinGraphTest` (Koin `verify` of
     `sharedCoreModule` + `coreModule` + `viewModelModule`) and `DemoKoinGraphTest` (adds `demoModule`; lives in
     `src/testDebug` because the demo sources only exist for debug-like variants). Mutation check: removing the
     `SnapshotStorage` binding makes `AppKoinGraphTest` fail with "Missing definition ... SnapshotStorage".
   - Persisted names are constants in `StorageNames` (`settings`, `vod_progress`, `twitch_secure_tokens`, `snapshots`,
     `vod_`) used by the code; the test pins the literals.
   - Not testable without instrumentation: `AndroidKeystoreTokenStore` (Android Keystore).
   - `verify` could not be satisfied for `EmoteRepository(providers: List)` through `extraTypes` (Koin 4.2.2 quirk); it is
     declared with `injectedParameters(definition<EmoteRepository>(List::class))` instead.
6. **Stability.** `config/compose/stability.conf` is commented (what is covered and why; `SessionState` and `TwitchTokens`
   deliberately absent: no composable takes them, confirmed by the report). Compose report regenerated at HEAD
   (`-PcomposeReports`, debug): **5 unstable parameters, the same set as before the migration** (`Context`,
   `WebViewVideoPlayer?`, `RailEdgeGuard?`, `StreamPreview`, `Any?`), **130 restartable skippable composables**.
7. **API surface.** `shared/core` keeps `api` only for types in public signatures (coroutines, serialization, Koin,
   Ktor client core, DataStore); content negotiation and the Ktor JSON serializer are `implementation`; collections-immutable
   and websockets are dropped until shared code uses them (the app declares its own). `AppConfig` now holds only
   `clientId` and `defaultPlayerEngine`: the player host/parent and the native-player flag are read only by the Android UI
   from `BuildConfig`. The platform bindings `sharedCoreModule` requires are listed in its KDoc and checked by
   `SharedCoreModuleTest`.
   Also found and fixed: the schedule filter still defaulted to `System.currentTimeMillis()` in `commonMain` (it only
   compiled because both targets are JVM). `verifyCommonMain` (in the `kmp-library` convention, part of `check`) now fails
   on `java.*` / `javax.*` / `android.*` imports, `System.currentTimeMillis|nanoTime`, `String.format` and
   `SimpleDateFormat` in `commonMain`; verified by planting a violation.
8. **detekt baselines.** Confirmed empirically (baseline hidden, task then fails; with it, passes): `detekt-baseline.xml`
   is used by `detekt`, `-mainJvm` by `detektMainJvm`, `-mainAndroid` by `detektMainAndroid`. `detekt-baseline-testJvm.xml`
   was unused after test code was excluded from `MagicNumber` and `InjectDispatcher` and is deleted;
   `detektHostTestAndroid` never needed one. Every remaining entry was compared with the app baselines from before the
   move: all exist there except `InjectDispatcher:SharedCoreModule.kt:Default`, the `CoroutineScope(... Dispatchers.Default)`
   moved verbatim out of `AppModule` (whose entry was baselined). A false positive that my new constructor parameter
   triggered (`UnusedPrivateProperty`) was removed by restructuring `DataStoreSettingsRepository`, not baselined. Known
   limit: detekt's type resolution does not see kotlinx.serialization's generated `serializer()` (9 compiler messages in
   `SnapshotCache`, same as before the move); rules needing those types are less accurate there.

Verification: `./gradlew check --rerun-tasks` at the final tree: BUILD SUCCESSFUL; shared `jvmTest` 38 tests, shared
Android host tests 28, app debug unit tests 167, 0 failures, 0 skipped; Lint: no new issues (17 errors + 2 hints
baselined). The baseline profile was not regenerated (planned for the end of Phase 1).

## Step 2: `:shared:stream`

Moved with `git mv`, package names unchanged: `GqlPlaybackResolver`, `GqlVodResolver`, `GqlClipResolver`, `MasterPlaylistParser`,
`RetryPolicy`, `StreamModels`, `TwitchPlaybackResolver` (feature/player/data/stream). Depends on `:shared:core` only
(`api(project(":shared:core"))`; nothing else is exposed or added to the catalog).

Replacements and behaviour:
- **Logging.** New `fun interface Logger { fun warn(tag, message) }` in `core.platform`. The code only ever logged at warn
  level (`Log.w`), so the interface has exactly that. The Android binding in `coreModule` calls `Log.w`: warn survives R8's
  stripping of `Log.v/d/i` (rule untouched). The resolvers' `log: (String) -> Unit` constructor parameter is replaced by a
  required `logger: Logger` (second parameter), so the shared code cannot silently log nowhere; `GqlClipResolver` gained the
  same parameter (it used `Log.w` directly). Messages and tags are byte-identical.
- **Device id.** `UUID.randomUUID().toString().replace("-", "")` -> `Uuid.random().toHexString()` (`kotlin.uuid.Uuid`, stable
  in Kotlin 2.4.21: compiles with `allWarningsAsErrors` and no opt-in). Same format: 32 lower-case hex characters.
- **URL encoding.** `java.net.URLEncoder.encode(v, "UTF-8")` -> `formUrlEncode` (own 20-line implementation). Ktor's
  `encodeURLParameter` was *not* used: it keeps `~` and encodes `*`, the JDK does the opposite, which would change the usher
  URLs. `FormUrlEncodingTest` pins a table (including `~`, `*`, space, JSON, 2-4 byte UTF-8) and a JVM-only
  `FormUrlEncodingJdkParityTest` compares against `URLEncoder` for all 128 ASCII characters and 2,000 random strings.
  Only strings with unpaired surrogates are unsupported (tokens are ASCII); documented in the KDoc.
- **IOException.** `java.io.IOException` -> `kotlinx.io.IOException` (a typealias of the JDK class on the JVM and Android, so
  OkHttp errors are still caught by `RetryPolicy.isTransient`; `RetryPolicyTest` and the resolver tests still use real
  `IOException`s). A new test pins the retry-vs-no-retry split for `StreamAccessException(retryable = ...)`.
- **Regex.** `[a-z0-9_]{1,25}`, `[A-Za-z0-9_-]{1,100}`, `[0-9]{1,20}` and the playlist attribute pattern use only constructs
  common regex supports (no look-behind, no inline flags).
- `@Immutable` removed from `ResolvedVod`, `ResolvedStream`, `StreamVariant`; the stability config lists `ResolvedVod`
  because the Compose report showed it as an unstable composable parameter right after the move (the only regression).

Koin: `sharedStreamModule()` (needs `HttpClient` from `sharedCoreModule()` and a `Logger`); `SharedStreamModuleTest`
resolves the three resolvers against exactly those bindings; `AppKoinGraphTest` and `DemoKoinGraphTest` include the module.
`DemoModule` passes the logger to its wrapped `GqlVodResolver`.

Tests moved to `shared/stream`: `RetryPolicyTest`, `GqlPlaybackResolverTest`, `MasterPlaylistParserTest`, `ClipSourcesTest`,
`ClipQueryTest` run in `commonTest` (kotlin.test + Ktor `MockEngine`; converted with a script that reorders JUnit's
message-first assertions and rewrites `assertThrows`); `GqlVodResolverTest` is `jvmTest` because it reads the recorded
fixtures as class-path resources (`src/jvmTest/resources/vod`, moved from the app). The `VodError` mapping test that lived in
that file belongs to `VodModel` (UI side, still in the app) and moved to `VodModelTest`.

Verification (tree at the step-2 commit):
- `./gradlew check`: BUILD SUCCESSFUL. `shared:stream` jvmTest 40 tests, Android host tests 24, 0 failures (corrected after the fact: an earlier version of this line said 39 / 23, counted before `SharedStreamModuleTest` was added); Lint: no new
  issues (17 errors + 2 hints baselined, unchanged). detekt baselines for `shared:stream` contain only moved legacy findings
  (the new files `FormUrlEncoding`, `DeviceId`, `SharedStreamModule` have none: a magic-number finding in `FormUrlEncoding` was
  fixed with named constants); the app baselines only lost the entries of the moved files.
- Release: `:app:assembleRelease :app:bundleRelease :app:analyzeReleaseR8Config` OK; APK 3,968,914 B (+0.41 % vs
  docs/perf-baseline.md); `classes.dex` 1,824,028 + `classes2.dex` 3,246,308 = 5,070,336 B (+0.11 %); `baseline.prof` 14,167 B /
  `baseline.profm` 993 B packaged in the APK, `baseline.prof` in the AAB; the `:shared:core` consumer rules are in R8's
  `configuration.txt`; `app/proguard-rules.pro` still has no `-keep` rule (only `-keepattributes`).
- Compose report (debug, `-PcomposeReports`): 5 unstable parameters, same set (`context`, `controller`, `edgeGuard`,
  `preview`, `token`), 130 restartable skippable composables.
- Not verified: runtime behaviour on a device/emulator, the profile (not regenerated), R8 analyzer scores.

## Step 3: `:shared:chat`

Moved with `git mv`, package names unchanged: `data/chat/{ChatModels, EventSubChatClient}`, `data/emotes/{Emote, EmoteRepository,
HttpJson, BttvProvider, FfzProvider, SevenTvProvider}`, `data/{ChatAssetsRepository, ChatSource}` (`ChatSource.kt` also holds
`EventSubChatSource`). Depends on `:shared:core` (`api`); `ktor-client-websockets`, content negotiation and the Ktor JSON
serializer are `implementation` (the chat client installs the WebSockets plugin itself); no new catalog entries.

Replacements and behaviour:
- **Logging.** `EventSubChatClient` takes a `logger: Logger` (4th parameter); the one `Log.w("EventSub", "bonus subscription
  refused: ...")` is now `logger.warn` with the same tag and message.
- **HTTP engine.** `EventSubChatClient.createHttpClient()` hardcoded OkHttp; it is now `createHttpClient(engine)` (same WebSockets
  and JSON plugins). `EventSubChatSource` takes `tokens, clientId, engine, logger` instead of reading `BuildConfig` and OkHttp;
  the module binds it from `AppConfig.clientId` and the platform `HttpClientEngineFactory`.
- **7TV event stream.** `SevenTvProvider` built its own OkHttp client with `readTimeout(0)` (the stream is quiet between ~46 s
  heartbeats). It now takes `eventsHttp: () -> HttpClient` (no default) and the new `EventStreamClientFactory` platform binding
  provides it; the Android binding is exactly the old client (`HttpClient(OkHttp) { engine { config { readTimeout(0, SECONDS) } } }`).
- **Shared mutable state.** `EmoteRepository.cache` and `SevenTvProvider.setIds` were `ConcurrentHashMap`s written from concurrent
  coroutines; both are now copy-on-write `MutableStateFlow<Map>` snapshots updated with `update {}` (lock-free, same visibility;
  the maps are tiny: three providers times the channels visited). `EmoteRepository` records `now()` once before the update so a
  CAS retry cannot skew the stored time. Its default clock is `WallClock.System::nowMillis` (what `System::currentTimeMillis`
  was). `@Volatile` -> `kotlin.concurrent.Volatile`; `java.util.LinkedHashSet` -> `kotlin.collections.LinkedHashSet` (the default).
- **Time and backoff.** `EventSubChatClient` reconnects with `delay(retry * 1000L)` (retry doubles, capped at 30 s); `SevenTvProvider`
  with `delay(backoff)` (2 s doubling to 60 s) and a 150 s dead-connection timeout. All are coroutine delays (virtual-time friendly,
  no wall-clock reads): unchanged.
- `@Immutable` removed from `Emote` and `ChatAssets`; the stability config lists `Emote` because the Compose report showed it as
  an unstable composable parameter right after the move (the only regression); `ChatAssets` and the chat models are not
  composable parameters.

Koin: `sharedChatModule()` binds `EmoteRepository` (providers in precedence order 7TV, BTTV, FFZ), `ChatAssetsSource` and
`ChatSource` (moved out of `coreModule` and `viewModelModule`). Platform bindings it needs besides `sharedCoreModule()`:
`HttpClientEngineFactory<*>`, `Logger`, `EventStreamClientFactory` (documented in its KDoc and checked by `SharedChatModuleTest`);
`AppKoinGraphTest` and `DemoKoinGraphTest` include `sharedChatModule()` (the demo overrides of `ChatSource` / `ChatAssetsSource`
still win because the demo module loads last).

Tests moved to `shared/chat` (commonTest, kotlin.test + Ktor MockEngine): `ChatModelsTest`, `EventSubSignalsTest`,
`EmoteProvidersTest`, `EmoteRepositoryTest` (TTL, precedence, provider isolation, refresh). `ChatPayloadTest` stays in the app: it
tests the presentation mapping (`toLine`). New tests for the new seams: `SevenTvEventStreamTest` (no stream is opened while the
set id is unknown; after `channel()` learned it, the factory client is used once, the SSE URL is the documented one and a
`dispatch` event becomes an `EmoteChange`), `ChatHttpClientTest` (the chat client is built on the injected engine with the
WebSockets and ContentNegotiation plugins), `SharedChatModuleTest`. One existing test that built an OkHttp client now uses a
Ktor mock engine factory.

Verification (tree at the step-3 commit):
- `./gradlew check --rerun-tasks --continue`: BUILD SUCCESSFUL (the known spotless / `--rerun-tasks` race did not occur).
  Tests, 0 failures, 0 skipped: shared:core jvm 38 / Android host 28; shared:stream jvm 40 / host 24; shared:chat jvm 27 / host 27;
  app debug unit tests 113 (was 167 before steps 2 and 3 moved their tests out). Lint: no new issues (17 errors + 2 hints
  baselined, unchanged). detekt: baselines for `shared:chat` hold only moved legacy findings (9-10 entries per task: magic numbers
  and complexity of `EventSubChatClient` / `SevenTvProvider`); the new `EventStreamClientFactory`, `SharedChatModule` have none
  (a `MatchingDeclarationName` finding on the first draft was fixed by giving the factory its own file); the app baselines only
  lost entries.
- `verifyCommonMain` caught two comments of mine that named the forbidden JVM map; reworded.
- Release: `:app:assembleRelease :app:bundleRelease :app:analyzeReleaseR8Config` OK; APK 3,968,914 B (+0.41 % vs
  docs/perf-baseline.md; identical to the step-2 total although rebuilt: it is a different dex split); `classes.dex` 1,812,780 +
  `classes2.dex` 3,259,364 = 5,072,144 B (+0.15 %); `baseline.prof` 14,112 B / `baseline.profm` 992 B packaged in the APK, `baseline.prof`
  in the AAB; `:shared:core` consumer rules merged in R8's `configuration.txt`; `app/proguard-rules.pro` still has no `-keep` rule
  (only `-keepattributes`); `:shared:stream` and `:shared:chat` need no consumer rules of their own (no reflection; their
  `@Serializable` types use the serialization library's rules).
- Compose report (debug, `-PcomposeReports`): 5 unstable parameters, same set (`context`, `controller`, `edgeGuard`, `preview`,
  `token`), 130 restartable skippable composables.
- Not verified: the `EventSubChatClient.run` loop itself (WebSocket session, reconnect, welcome / keepalive / revocation
  handling) has no test, as before the move (it needs a fake WebSocket server; the logic is byte-identical apart from the logger and
  the engine); `EventSubChatSource`; runtime behaviour on a device or the TV; the profile (not regenerated); R8 analyzer scores.

## Step 4a, part 0: follow-ups from the review of steps 2-3

- `EventSubChatClient.run` is now covered. The session loop talks to an internal `EventSubTransport` (`connect(url)`,
  `subscribe(...)`) and `EventSubSocket` (`receive(): String?`, `close()`); `KtorEventSubTransport` is the thin real adapter
  (same Ktor calls and Helix request as before). The public constructor is unchanged, an `internal` primary constructor takes the
  transport. The loop body only changed where it touched the socket type. `EventSubSessionTest` (virtual time, scripted sockets)
  covers: welcome -> chat subscription first, then the bonus ones with their conditions and versions; notifications and the
  dedup window (`maxMessages`); keepalive and non-text frames ignored; an undecodable event does not end the session;
  `session_reconnect` migration (old socket closed only after the replacement welcomed), a replacement that does not welcome,
  a reconnect frame without URL; revocation of the chat subscription (ends the session) vs a bonus revocation (ignored);
  a refused chat subscription (error + retry) vs refused bonus subscriptions (warnings only); a welcome without session id;
  backoff 1, 2, 4, 8, 16, 30, 30 s; `stop()`; blank ids.
- Behaviour notes found while writing them (unchanged, documented): the state turns `Connected` as soon as the socket opens (before the
  welcome); the original has no keepalive timeout (`session_keepalive` is ignored), so a silent dead socket is only noticed when the
  socket closes. A keepalive watchdog would be a behaviour change and is left for a later step.
- `SharedChatModule` builds the 7TV stream client provider with an explicit lambda.
- Follow-up recorded: **detekt baseline burn-down.** `shared/{core,stream,chat}/detekt-baseline*.xml` and `app/detekt-baseline*.xml`
  hold only moved/legacy findings (MagicNumber, complexity, generic exception handling). They should shrink over time: when a
  file is touched for a feature, fix its baselined findings in the same PR; never add entries.

## Step 4a, parts 1-2: `:shared:player-api`, `:shared:testing`, `:shared:presenters`

`:shared:player-api` (dependencies: coroutines, serialization-json; it does not need `:shared:core`, so it does not depend on it):
`VideoPlayer`, `VideoQuality(ies)`, `PlaybackStats`, `AdBreak`, `HlsAds`. `HlsAds` now parses `START-DATE` with `kotlin.time.Instant`
instead of `java.time.Instant` (API 26 vs minSdk 24): the `NewApi` Lint finding is gone from `app/lint-baseline.xml` (20 -> 18 issues,
none `NewApi`). For the shapes Twitch sends (with or without fractional seconds) the result is identical; the new parser also reads an
explicit UTC offset (`+01:00`), which the old one did too on API 26+. Tests moved to `commonTest` (`kotlin.test`) and a test pins the
accepted shapes.

`:shared:testing`: the fakes (`FakeRepository`, `FakeSession`, `FakeSettings`, `stream()`, `category()`), moved from
`app/src/test/.../testing/Fakes.kt`; `commonMain` of a KMP library so shared and app tests can both depend on it. Two detekt findings
in the moved file were fixed (a named constant, a `val`).

`:shared:presenters` (depends on `:shared:core` and collections-immutable; it will depend on `:shared:stream`, `:shared:chat` and
`:shared:player-api` when the player presenter moves in step 4b, not before): `Load`/`Loader`/`FlowBatch`, and 8 presenters replacing the
ViewModels (Auth, Home, Browse, Category, Channel, Follows, Search, Settings) following `docs/PRESENTERS.md`.
Seams: `DeviceProfile` (core.platform; Android: `Locale.getDefault().toLanguageTag()`; replaces `java.util.Locale` in the category
screen: `twitchLanguage(languageTag)` has the same mappings, tested for `fr-FR`, `nb-NO`, `zh-HK`, `zh-Hant-HK`, `fil`, legacy `in`/`iw`),
`VoiceInput` (replaces the Android `VoiceRecognizer` interface; `PlatformVoiceRecognizer` implements it). `SnapshotCache` got an
optional `io: CoroutineDispatcher = Dispatchers.IO` (same default, makes the stale-while-revalidate flow testable). Home, Follows,
Category and Channel presenters got the optional `loadDispatcher` Browse already had.

Behaviour notes (intentional, documented):
- Category and Channel expose one combined state instead of several flows (`CategoryScreenState(load, language, languageOnly)`,
  `ChannelUiState(details, tail, progress)`); the channel's `progress` source keeps its `WhileSubscribed(5 s)` lifetime. The channel
  screen's focus memory (`lastClip`, `lastReplay`) stays in the Android holder.
- `SettingsUiState.playerEngine` starts on the build default, passed in from `AppConfig` (it was `PlayerEngine.buildDefault()`).
- UI call sites changed from `viewModel.fn()` to `presenter.onEvent(...)`; visuals and behaviour are unchanged.
- The app's baseline profile still names the old `*ViewModel` constructors and `VoiceRecognizer`: it is regenerated at the end of
  Phase 1 (planned).

Tests: 47 presenter tests on each of the JVM and Android-host targets (Home incl. snapshot flow, Follows, Browse, Category, Channel, Search, Settings, Auth, Load/Loader,
FlowBatch, `SharedPresentersModuleTest`). The app's `AppKoinGraphTest` / `DemoKoinGraphTest` include `sharedPresentersModule()`.

Compose stability: moving the state classes out of the app made `HomeContent`, `FollowsContent`, `BrowseUiState`, `CategoryUiState`,
`SearchUiState`, `SettingsUiState` unstable in the first report (6 new unstable parameters); they are now listed in
`config/compose/stability.conf`, and the report is back to the same 5 unstable parameters (`context`, `preview`, `edgeGuard`,
`controller`, `token`) and 130 skippable composables.

detekt: `shared/presenters/detekt-baseline*.xml` hold only the 9 legacy entries that moved with `Load.kt`, `SearchPresenter`,
`ChannelPresenter` (same rule IDs as in the pre-move app baselines); the app baselines only shrank (removed 10 / 13 / 2 / 13, added 0).

## Step 4a, part 3: verification, and a release-build regression caught by it

**The regression.** The first release build after the presenters commit was 2,045,171 B instead of 3,968,914 B (dex 2.3 MB instead of
5.1 MB, `baseline.prof` 7 KB instead of 14 KB, Media3 4,796 classes -> 3, `feature.player.ui` 407 -> 18). Cause (found by bisecting in an
isolated worktree and reading the `-dontobfuscate` dex): `TwitchPlayerApp` started Koin with a hand-written module list that did not
contain `sharedPresentersModule()`. The graph tests listed the module themselves, so they passed, and `verify` cannot look inside
`viewModel { ... get<XPresenter>() ... }` lambdas. At runtime the first `koinViewModel()` would have thrown
`NoDefinitionFoundException`; R8 saw that no presenter could ever be instantiated, rewrote `Scope.resolve(...)` results to
`throw ClassCastException` and removed everything after the failing call (the whole UI). Debug builds would have crashed at launch.
Fix and guards:
- `productionModules()` (app, `core/di/ProductionModules.kt`) is the single list; `TwitchPlayerApp`, `AppKoinGraphTest` and
  `DemoKoinGraphTest` all use it.
- `ProductionGraphResolutionTest` starts Koin with the real `productionModules()` (only the Android-`Context` bindings replaced by
  fakes) and constructs all eight screen ViewModels, so their presenters are resolved. Verified to fail with
  `NoDefinitionFoundException: AuthPresenter` when the module is removed from the list.
- Lesson recorded: after a change that adds a Koin module or moves code between modules, compare the release APK size and the dex
  package census with `docs/perf-baseline.md` (a 50 % drop is never a good sign).

**Numbers on the final tree** (`./gradlew check --rerun-tasks --continue` passed in 1 min 59 s; the Spotless `Could not read path` race
under `--rerun-tasks` is fixed: the Spotless Kotlin and Gradle-script targets now use file trees that never descend into `build/`):
- Unit tests, 0 failures, 0 skipped: app 87; shared:core jvm 38 / host 28; shared:stream 40 / 24; shared:chat 41 / 41; shared:player-api 7 / 7;
  shared:presenters 47 / 47.
- Release: `:app:assembleRelease :app:bundleRelease :app:analyzeReleaseR8Config` OK. APK 3,968,914 B (+0.41 % vs the 3,952,530 B baseline);
  `classes.dex` 1,818,908 + `classes2.dex` 3,266,420 = 5,085,328 B (+0.41 %); `baseline.prof` 14,272 B / `baseline.profm` 1,008 B packaged in
  the APK and in the AAB; no `-keep` rule in `app/proguard-rules.pro`; the R8 configuration is identical to the pre-presenters build
  (diffed); Media3 4,796 classes, `feature.player.ui` 454 classes (dex census as before).
- Compose report: 5 unstable parameters (`context`, `preview`, `edgeGuard`, `controller`, `token`), 130 skippable composables, same as
  before the migration (after adding the presenter state classes to `config/compose/stability.conf`).
- Lint: no new issues (15 errors + 2 hints baselined; the `NewApi` finding is gone).
- Emulator (TwitchTV_API33, demo data, debug build, never the TV): screenshots of Home (Following / For you rows), Browse (filters, sort,
  poster grid), a Category (header, `English` language chip from `DeviceProfile`, Favorite, stream cards), Search (keyboard, mic, Trending)
  and Settings (account, chat modes, animations, language, player engine, quit) all render as before. Screenshots were reviewed by
  eye; they are not committed.
- `:app:connectedDebugAndroidTest` on the emulator: 14 tests, 2 failures, both on the known pre-existing list
  (`TvFocusFlowTest.searchOpensOnTheVoiceKey`, `TvFocusFlowTest.backClosesTheZapperBeforeLeavingThePlayer`); the third known one
  (`chatOptionsOpenFromSettings...`) passed on this run. No new failure.

Not verified: the real TV; the R8 analyzer scores (HTML renders them with JS); a long session on the emulator (only a short navigation
tour); the baseline/startup profiles still name the old `*ViewModel` constructors and `VoiceRecognizer`, so they are stale for those
classes until they are regenerated at the end of Phase 1 (planned; sizes already show the profile applies: 14,272 B).

## Step 4b: the player presenter, chat feed and chat lines (2026-11-11)

**Review fixes from step 4a (all committed first):**
- `ChannelPresenter.state` was a `combine(...)` under `WhileSubscribed(5 s)`; the original exposed `details` as `Loader.state`
  (itself `Lazily`, never stopping). It is `Lazily` now (loading begins at the first collector, never stops, no stale first frame).
  Tests: progress merge; "returning after more than the old stop timeout shows fresh state with no collector" (fails with
  `WhileSubscribed`, checked); "not loaded before the first collector".
- `PresenterLifetimeTest`: cancelling the scope given to a presenter stops Home's load, Search's debounce and voice job, Auth's sign-in
  job and Channel's loads / replay paging; nothing is emitted afterwards and events sent to a dead presenter are no-ops.
- `AndroidDeviceProfile`: `Locale.toLanguageTag()` gives "und" for the root locale where the old code read `locale.language` (empty).
  It returns "" now; the locale is injectable; tests include the root locale and `twitchLanguage("")`.
- `SharedChatModule` lambda (4a): `get<EventStreamClientFactory>()::create` became `val streamClients = get<...>(); { streamClients.create() }`.
  Behaviour-neutral: the factory is still resolved once, when the `EmoteRepository` singleton is built, and `create()` is called on
  each use either way; the explicit form just does not hide the resolution in a bound reference.
- `verifyCommonMain` is registered by the `twitchplayer.kmp-library` convention in **every** shared module (core, stream, chat,
  player-api, presenters, testing: confirmed with `./gradlew check --dry-run` on each) and is part of `check`. Run it alone with
  `./gradlew verifyCommonMain`. It now also rejects `java.lang.Math`.

**Moved to `:shared:presenters`:** `PlayerViewModel` -> `PlayerPresenter` (`state`, `chat`, `room`, settings projections, `sent`, `commands`,
`onEvent(PlayerEvent)`, `handleBack()`), `ChatFeed`, `ChatLine`, `PlayerModels`, `ChannelKeyBus` (a singleton of `sharedPresentersModule()`),
and the VOD seek / speed helpers (`feature.vod.ui`, now public). The app keeps a thin `PlayerViewModel` holder (activity-scoped, key
"player"); `PlayerScreen` / `TwitchTvApp` send `PlayerEvent`s. New seams: `NumberFormatter` (core.platform; the Android implementation is the
old `DecimalFormat("0.##")`, tested equal to the inline call for four locales) and `timeOfDay` (`expect/actual`, Calendar on JVM and Android).
`PlayerPresenter`'s constructor takes `defaultEngine` (from `AppConfig`) and a `PlayerTuning` (idle time, chat batch interval, mapping dispatcher).

Replacements, with the reason each is behaviour-identical: `Calendar` clock -> `timeOfDay` (same Calendar call; JVM parity test over 300 random
instants); `Math.floorMod` -> `Int.mod` (parity tests for the cycle index and the nick-colour index over random logins, negative hash codes
included); `Math.pow` -> `kotlin.math.pow`; `String.format` on the emote CDN template -> a string template (existing URL assertions
unchanged); `AtomicReference` in `ChatFeed.run` -> `MutableStateFlow`; `System.currentTimeMillis` default clock -> `WallClock`.
**`kotlinx-datetime` was tried and dropped:** it kept 171 classes in the release dex (+74 KB, APK +1.24 %) for a 5-character clock string.

**Compose stability:** the first report after the move showed `PlayerUiState` (3 composables), `ZapRow`, `ChatLine`, `ImmutableList<ChatLine>`,
`PlayerPresenter` (4) and `ChannelKeyBus` unstable. The player state classes were added to `config/compose/stability.conf` one by one;
`PlayerPresenter` and `ChannelKeyBus` too, documented as the two reference types composables take as parameters (only `val` public
properties, never change identity, as the ViewModel / bus they replace). Report after: 5 unstable parameters (`context`, `controller`,
`edgeGuard`, `preview`, `token`), 130 skippable composables, identical to the report generated at the pre-4b commit in a scratch worktree.

**detekt:** the app baselines shrank (moved findings left; three `ViewModelForwarding` ids follow the parameter rename); the presenters
baselines gained only the moved legacy findings (ChatLine magic numbers / complexity, ChatFeed, `TooManyFunctions` of the player class). Two
findings caused by the new code were fixed, not baselined: the 12-parameter constructor (test-only knobs grouped into `PlayerTuning`) and
`NestedBlockDepth` in a new test. `CyclomaticComplexMethod` now has `ignoreSimpleWhenEntries: true` (a `when` of one-line branches such as
the 22-event dispatcher counts as one decision; applies repo-wide, only ever reduces findings).

**Tests:** moved to shared (kotlin.test, run on JVM and Android host): `PlayerPresenterTest` (28 + 6 new for `SendChat`, `EnsureChatVisible`,
`CloseChatSettings` / `CloseZapper`, `SetPreferredQuality` and the engine default), `ChatFeedTest`, `ChatLineTest` (+ clock and zone tests),
`ChatPayloadTest`, `VodPlaybackTest`, `VodProgressTest` (core), `ChannelKeyBusTest`, `ChatLineJvmParityTest`. The Koin tests cover the player
presenter (`SharedPresentersModuleTest`) and the activity-scoped holder plus `NumberFormatter` / `ChannelKeyBus` resolution
(`ProductionGraphResolutionTest`).

**Numbers on the final tree** (`./gradlew check --rerun-tasks --continue`, 2 min 12 s, 0 failures, 0 skipped): app 37; core 39 jvm / 29 host;
stream 40 / 24; chat 41 / 41; player-api 7 / 7; presenters 124 jvm / 121 host; total 510 test executions (407 at the end of 4a).
Release: APK 3,968,914 B (+0.41 % vs 3,952,530), dex 1,809,856 + 3,281,400 = 5,091,256 B (+0.52 % vs 5,064,824), `baseline.prof` 14,159 B /
`baseline.profm` 1,003 B packaged in the APK and the AAB, one `-keepattributes` line and no `-keep` rule in `app/proguard-rules.pro`, shared
consumer rules merged into R8's `configuration.txt`, Lint: no new issue and the baseline file is unchanged since 4a (the "listed in the baseline but not found" note
printed by `bundleRelease` comes from `lintVitalRelease` reading a baseline recorded on another variant). Mapping: no `kotlinx.datetime` class; 68 classes in `feature.player.presentation`.

**Emulator** (TwitchTV_API33, demo data, debug build, `ANDROID_SERIAL=emulator-5554`, the TV untouched), screenshots reviewed by eye:
Home; a Category (Valmoor: header, language chip, favourite, cards); the player on the native demo clip with the compact chat column (badges,
emotes, subscriber notice, mention highlights) and with the control bar (back, pause, mute, full screen, channel -/+, chat, write, quality);
channel zapping (`Next` / `Previous` events: MaîtreKoi, then back); pause / play toggle; the chat list focus ring; full-screen video with no
overlay and no chat (Back peels the layers: bar, chat, then leaves); the zapper (Following and category rows, current channel marked, bar lifted);
the Quit dialog at the root. **Not driven through the UI:** chat mode Overlay (key navigation to the chat-mode button was unreliable; covered by
the presenter tests), the VOD and clip screens (their only change is `speedLabel(speed, formatter)`; the `NumberFormatter` binding is covered
by `ProductionGraphResolutionTest`).

**Instrumented tests** (`./gradlew :app:connectedDebugAndroidTest`, emulator only): 14 run, 12 pass, 2 fail, both on the known
pre-existing list (`TvFocusFlowTest.searchOpensOnTheVoiceKey`, `TvFocusFlowTest.backClosesTheZapperBeforeLeavingThePlayer`); the third
known one (`chatOptionsOpenFromSettings...`) passed again. No new failure. `ChatPanelTest` (6) passes. The emulator was shut down at the end.

**Not verified:** the real TV; the R8 analyzer scores (the HTML renders them with JavaScript); chat Overlay mode, the VOD and the clip
screens through the UI (see above); a long session; the Baseline / Startup Profiles are still the pre-migration ones (the old
`*ViewModel` constructors and `VoiceRecognizer` are stale in them) and are regenerated at the end of Phase 1.

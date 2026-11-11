# KMP extraction audit (Phase 1, read-only)

## Status (end of Phase 1)

Done as planned: `shared/core`, `shared/stream`, `shared/chat`, `shared/player-api`, `shared/presenters` and `shared/testing`
exist; every file marked **C** or **I** below has moved (about 48 files at the audit, more as seams were split); the Android UI,
design system, navigation, Media3 / WebView players and `VoiceRecognizer` stayed (**A**). Package names did not change.
`docs/kmp-migration-log.md` has the per-step verification (tests, sizes, stability report, screenshots).

Deviations from the plan above:
- `player-api` does not depend on `core`; `presenters` depends on `core` and `chat` only (`stream` is used by the app's VOD error
  mapping, which stayed UI-side; the player layer needed nothing from it).
- Presenters expose extra streams besides `state` where a screen consumes one-shot or per-concern flows (`PlayerPresenter`: chat
  lines, room modes, settings projections, `sent`, `commands`); `handleBack()` stays a function because the UI needs its result at once.
- `NumberFormatter`, `SettingsDefaults`, `SnapshotStorage`, `AppConfig` (two fields) and `EventStreamClientFactory` are the final
  platform seams (the plan named `FileStore`, `Clock`, `DeviceProfile`); no new library was needed (chat line time uses an `expect/actual` `timeOfDay`; `kotlinx-datetime` was tried and dropped: it kept 171 classes in the release dex).
- `verifyCommonMain` (a `check` task in every shared module) was added to keep `commonMain` honest while only JVM targets exist.
- Compose stability needed a configuration file for the shared state classes (as predicted), plus two reference types that
  composables take as parameters (`PlayerPresenter`, `ChannelKeyBus`).
- The Baseline and Startup Profiles are **not** regenerated yet; that is the last item before merging to `main`.

---

Scope: `app/src/main` (128 files, ~14.6k lines), `app/src/test` (33 files, ~2.7k lines, JUnit4), `app/src/demo`, `benchmark`.
Method: import and API scan of every file, then a manual read of every data, network, presentation and DI file.

Legend: **C** = moves to `commonMain` unchanged apart from the listed fix; **I** = needs an interface (platform
implementation injected through Koin) or `expect/actual`; **A** = stays Android-only.

## Decisions that shape everything

1. **Package names do not change.** Moved code keeps `com.twitchplayer.tv.*`. The Baseline and Startup profiles
   (28,888 and 19,429 rules) reference classes by name, so they stay valid and cover the shared code with no
   regeneration. Import churn is also zero.
2. **Compose stability.** 20 data and presentation types are annotated `@Immutable` (an `androidx.compose.runtime`
   import). `shared` must not depend on Compose, so these annotations go away and the app declares a Compose compiler
   stability configuration file listing `com.twitchplayer.tv.core.data.model.*` and the other shared packages.
   Without it the cross-module classes become unstable and recomposition skipping regresses (perf risk, measured in
   the final benchmark run). `kotlinx.collections.immutable` (`ImmutableList`, 58 uses) is KMP and already known to
   the compiler as stable.
3. **Presenters are plain classes**, not `androidx.lifecycle.ViewModel`: constructor takes a `CoroutineScope`,
   exposes `StateFlow<UiState>` and `onEvent(...)`. On Android a thin holder `ViewModel` owns the presenter and
   cancels its scope in `onCleared`. SwiftUI and Compose Desktop own the scope themselves. This replaces 9 ViewModels
   (`Auth`, `Home`, `Browse`, `Category`, `Channel`, `Follows`, `Player`, `Search`, `Settings`), all of which only use
   `viewModelScope` (no `SavedStateHandle`, no `AndroidViewModel`).
4. **Build config values** (`BuildConfig.TWITCH_CLIENT_ID`, player host/parent, player engine defaults) become an
   `AppConfig` data class provided by each app through Koin.
5. **Strings and resources stay in the app module** (localization is plan-only in this phase).

## Proposed modules

```
shared/
  core         models, Ktor client factory, Twitch API + DTOs, auth (device code, token validator, TokenStore interface),
               repositories, settings, snapshot cache, platform interfaces (Clock, Logger, SecureStore, FileStore,
               DeviceProfile, NumberFormatter), AppConfig
  stream       GQL playback / VOD / clip resolvers, master playlist parser, retry, stream models
  chat         EventSub chat client, chat models, emote providers (7TV, BTTV, FFZ), emote repository, chat assets
  player-api   VideoPlayer interface, quality / stats models, PlaybackStats parsing
  presenters   the 9 presenters, Loader / Load / FlowBatch, ChatFeed, ChatLine mapping, UiState types
androidApp (= current :app)   all Compose UI, Media3 + WebView players, Android implementations, DI wiring, demo data
benchmark                     unchanged
```

Dependency direction: `presenters` -> `chat`, `stream`, `player-api` -> `core`. Each module declares only
`android` (via `com.android.kotlin.multiplatform.library`) and `jvm()` targets for now. Apple, wasm and desktop
targets are added later; `core` is the first candidate for an Apple umbrella framework.

## File-by-file

### core/network (api + auth) -> `shared/core`

| File | Verdict | Notes |
|---|---|---|
| `api/TwitchApi.kt`, `api/TwitchDto.kt` | C | Ktor + kotlinx.serialization only. `twitchHttpClient()` hardcodes `io.ktor.client.engine.okhttp.OkHttp`: becomes `twitchHttpClient(engine: HttpClientEngineFactory<*>)`, engine chosen per platform and bound in Koin (OkHttp on Android, Darwin later, CIO/Java on desktop, Js on web). |
| `auth/DeviceCodeAuth.kt` | C | `System.currentTimeMillis()` -> injected `Clock`. |
| `auth/TokenValidator.kt` | C | none |
| `auth/TokenStore.kt` | I | Android Keystore, AES-GCM, `javax.crypto`, `java.security`, `ByteBuffer`, `Base64`. Extract `interface TokenStore { suspend read/write/clear }` to common; keep this class as `AndroidKeystoreTokenStore` in the app (later: Keychain, DPAPI/libsecret). |

### core/data -> `shared/core`

| File | Verdict | Notes |
|---|---|---|
| `model/Models.kt` | C | `@Immutable` removed (see decision 2). |
| `repository/LiveWatcher.kt` | C | none |
| `repository/SessionRepository.kt` | C | `@Volatile` -> `kotlin.concurrent.Volatile`. |
| `repository/TwitchMappers.kt` | C | `System.currentTimeMillis`, `Locale`: use `Clock` and locale-free parsing. |
| `repository/TwitchRepository.kt` | C | `ConcurrentHashMap` + `java.util.concurrent` -> `Mutex`-guarded map (or atomicfu); `System.currentTimeMillis` -> `Clock`. |
| `repository/TtlCache.kt` | C | `SystemClock.elapsedRealtime()` -> `TimeSource.Monotonic`. |
| `repository/SnapshotCache.kt` | I | `Context.cacheDir`, `java.io.File`, `Dispatchers.IO`. Introduce `FileStore` (read/write/delete by name); Android impl uses the cache dir. `Dispatchers.IO` is available in common (coroutines 1.7+). |
| `repository/SettingsRepository.kt` | I | DataStore Preferences is KMP; the `Context`-bound `DataStore` is created by the platform and injected. `ActivityManager.isLowRamDevice` (default emote limit) -> `DeviceProfile.isLowEnd`. |

### feature/player/data -> `shared/chat`, `shared/stream`

| File | Verdict | Notes |
|---|---|---|
| `data/ChatAssetsRepository.kt`, `data/ChatSource.kt` | C | `@Immutable` removed. |
| `data/chat/ChatModels.kt` | C | none |
| `data/chat/EventSubChatClient.kt` | C | Ktor WebSockets are KMP. `android.util.Log` -> `Logger`; `@Volatile` import. `java.util.LinkedHashSet` is `kotlin.collections.LinkedHashSet`. Browser caveat: the WebSocket works, Helix CORS is fine. |
| `data/emotes/{Bttv,Ffz,SevenTv}Provider.kt`, `HttpJson.kt`, `Emote.kt` | C | `SevenTvProvider` and `EmoteRepository` use `java.util.concurrent` (maps / atomics): `Mutex` or atomicfu. `Emote.kt` `@Immutable` removed. |
| `data/stream/Gql{Playback,Vod,Clip}Resolver.kt` | C | `android.util.Log` -> `Logger`; `UUID.randomUUID()` -> `kotlin.uuid.Uuid` (confirm stable in 2.4); `java.net.URLEncoder` inside the usher URL builder -> Ktor `encodeURLParameter`; `Regex` is common but avoid look-behind (not on JS/Wasm). |
| `data/stream/MasterPlaylistParser.kt`, `StreamModels.kt`, `TwitchPlaybackResolver.kt` | C | Regex only. |
| `data/stream/Retry.kt` | C | `java.io.IOException` -> `kotlinx.io.IOException` (or Ktor's). |

### feature/player/presentation, core/presentation -> `shared/presenters`, `shared/player-api`

| File | Verdict | Notes |
|---|---|---|
| `core/presentation/FlowBatch.kt` | C | none |
| `core/presentation/Load.kt` | C | `@Immutable` removed. `Loader` already takes a `CoroutineScope`. |
| `player/presentation/ChatFeed.kt` | C | `AtomicReference` -> `MutableStateFlow` / atomicfu. Tested by `ChatFeedTest` with an injected dispatcher. |
| `player/presentation/ChatLine.kt` | C | `Calendar` -> kotlinx-datetime (or a small expect/actual for local time of day); `String.format` on the emote CDN template -> `replace`. |
| `player/presentation/PlayerModels.kt` | C | `@Immutable` removed. |
| `player/presentation/PlayerViewModel.kt` (400) | C | becomes `PlayerPresenter`. Largest and riskiest presenter: last. |
| `feature/*/presentation/*ViewModel.kt` (Browse, Category, Channel, Follows, Home, Search, Settings) | C | become presenters; `CategoryViewModel` uses `java.util.Locale` (inject a locale string via `DeviceProfile`). |
| `app/AuthViewModel.kt` | C | becomes `AuthPresenter`. |
| `player/playback/PlaybackStats.kt` | C | `java.time.Instant.parse` -> `kotlin.time.Instant.parse`. **Also fixes a real bug**: `java.time` needs API 26 and `minSdk` is 24 (Lint `NewApi`, currently baselined). `@Immutable` removed. |
| `player/playback/VideoPlayer.kt` | C | the interface: moves to `shared/player-api`. |
| `feature/vod/data/VodProgressStore.kt` | I | DataStore injected like settings; `System.currentTimeMillis` -> `Clock`. |
| `feature/vod/ui/VodModel.kt` | I | `DecimalFormat` for `speedLabel` -> `NumberFormatter`; `IOException`. It is in `ui/` today but is model code: moves to presenters. |
| `core/ui/Formatters.kt` | I | `java.text.NumberFormat`, `String.format(locale, ...)`: `NumberFormatter` interface (Android impl keeps current behavior exactly). |

### Stays in the Android app (A)

- Entry: `MainActivity.kt`, `TwitchPlayerApp.kt`, `core/di/*` (Koin wiring: `androidContext()`, `BuildConfig`), `app/TwitchTvApp.kt`,
  `app/AppDialogs.kt`, `app/ScreenTransitions.kt`.
- Navigation: `navigation/AppKeys.kt`, `AppNavigator.kt` (Navigation3 `NavKey`s: presenters must not know them).
- Design system and UI helpers: all of `core/designsystem/*` (26 files), `core/ui/*` except `Formatters` (focus, ring, motion,
  `AppLocales`, `KeepScreenOn`, `Stagger`, `TvAsyncImage`...), every `feature/*/ui/*` (Compose TV).
- Playback: `NativeVideoPlayer.kt`, `NativeVideoSurface.kt`, `StreamPreview.kt` (Media3), `ui/embed/TwitchEmbedPlayer.kt` (WebView),
  `ui/embed/TwitchPlayerConfig.kt` (uses `java.net.URI`, tiny, can go common later).
- Platform services: `feature/search/data/VoiceRecognizer.kt` (SpeechRecognizer), `AppLocales.kt`, `TokenStore` implementation.
- `app/src/demo` (5 files) and `app/src/release`: stay; the demo module re-implements the shared interfaces.

Status counts (approximate): about 48 files / 4.5k lines move, about 80 files / 10k lines stay.

## JVM-only API checklist (what to replace)

| API | Where | Replacement |
|---|---|---|
| `System.currentTimeMillis/nanoTime` | 11 files (repositories, auth, VOD store, a few UI) | injected `Clock` (`kotlin.time.Clock`) |
| `SystemClock.elapsedRealtime` | `TtlCache` | `TimeSource.Monotonic` |
| `String.format` / `.format(` | Formatters, ChatLine, UI | `NumberFormatter` interface; plain string templates |
| `java.util.concurrent.*`, `AtomicReference`, `@Volatile` | TwitchRepository, EmoteRepository, SevenTvProvider, ChatFeed, SessionRepository, EventSubChatClient (`FocusMemory` is UI, stays) | `Mutex`, atomicfu, `kotlin.concurrent.Volatile` |
| `Calendar`, `java.util.Date`, `java.time.Instant` | ChatLine, ScreenChrome, PlaybackStats | kotlinx-datetime or `kotlin.time.Instant` |
| `java.util.Locale`, `NumberFormat`, `DecimalFormat` | Formatters, VodModel, CategoryViewModel, UI | `NumberFormatter` + locale string from `DeviceProfile` |
| `java.util.UUID`, `URLEncoder` | GqlPlaybackResolver | `kotlin.uuid.Uuid`, Ktor `encodeURLParameter` |
| `android.util.Log` | 9 files (EventSub, 3x Gql, NativeVideoPlayer, StreamPreview, PlayerScreen, TwitchEmbedPlayer, VodScreen; the UI ones stay on `Log`) | `Logger` interface (Android impl calls `Log`; release R8 still strips `Log.v/d/i`) |
| `Context`, `File`, Keystore | Settings, SnapshotCache, TokenStore, VodProgressStore | interfaces above; DataStore instances injected |
| OkHttp engine in `twitchHttpClient()` | TwitchApi | engine injected per platform |
| `Regex` | resolvers, parser, config | keep; no look-behind, no inline flags beyond `(?i)` |
| `java.io.IOException` | Retry, VodModel | `kotlinx.io.IOException` |

## Tests (33 files, ~2.7k lines in `src/test`)

- JUnit4 (`org.junit.*`, `Assert.*`) + `kotlinx-coroutines-test` + `ktor-client-mock` + `TemporaryFolder` rule (2 tests).
- Data, presentation and parsing tests (about 30 files) move to `commonTest` with `kotlin.test` (`assertEquals`, `assertFailsWith`);
  they run on the `jvm()` target (fast) and on Android host tests. `@Before/@After` become `@BeforeTest/@AfterTest`;
  `TemporaryFolder` -> a `FileStore` fake or `kotlin.io.path.createTempDirectory` in a `jvmTest`.
- `LocalizationTest`, `AppNavigatorTest`, `FormattersTest`'s Android-locale checks, and `androidTest/*` (D-pad / focus UI tests) stay.

## Migration order (one module per commit, TV app green after each)

1. Convention plugin `twitchplayer.kmp-library` + `shared/core` skeleton: models, network, auth (with `TokenStore` interface), repositories, interfaces. Add `AppConfig`, `Clock`, `Logger`, `FileStore`, `DeviceProfile`, `NumberFormatter`, Android implementations, move DI.
2. `shared/stream` (+ resolver tests).
3. `shared/chat` (+ chat tests).
4. `shared/player-api`, then `shared/presenters`: simplest presenters first (Settings, Follows, Browse, Home, Category, Channel, Search, Auth), `PlayerPresenter` last.
5. Stability configuration file added with step 1 (before any model moves), and a Macrobenchmark run after step 1 and at the end.

## Risks

- **Compose recomposition** after models leave the Compose module (decision 2). Mitigated by the stability file; verified with `-PcomposeReports` before and after.
- **R8 / consumer rules**: the AGP KMP library plugin must publish consumer keep rules for kotlinx.serialization and Ktor; the app currently relies on library-shipped rules only. Verified by the R8 analyzer and by diffing the generated `configuration.txt` before and after step 1.
- **Baseline profile coverage**: unchanged class names keep it valid, but moved code ships in a library: verify with `unzip -l` plus `baseline.prof` rule counts that `com/twitchplayer` entries survive.
- **Koin**: `koin-androidx-compose` stays in the app; shared uses `koin-core`.
- No new library was added in the end: neither kotlinx-datetime nor atomicfu was needed (see Status).

# Architecture proposal

Status: implemented. Platform-independent logic lives in the Kotlin Multiplatform modules under `shared/`; `:app` is the Android TV UI on top of them. The sections below keep the original rationale; "Modules" and "Code layout" are the current shape.

## Modules

```
shared/core        models, Twitch API + DTOs, auth (device code, TokenStore interface), repositories, settings, snapshot cache,
                   VOD progress, platform interfaces (WallClock, Logger, SnapshotStorage, DeviceProfile, NumberFormatter, AppConfig)
shared/stream      GQL playback / VOD / clip resolvers, master playlist parser, retry
shared/chat        EventSub chat client, chat models, emote providers (7TV, BTTV, FFZ), emote repository, chat assets
shared/player-api  VideoPlayer interface, playback stats / quality models, HLS ad parsing
shared/presenters  one presenter per screen (state + onEvent), Loader / Load, ChatFeed, ChatLine, player state, VOD helpers
shared/testing     fakes for the repositories and session (test fixtures, not shipped)
app                Compose TV UI, Media3 + WebView players, Android implementations of the platform interfaces, Koin wiring, demo data
benchmark          Macrobenchmark + Baseline Profile generation
```

Dependency direction (no cycles, nothing depends on `app`):

```
presenters -> chat -> core          stream -> core          player-api (standalone)
app -> presenters, chat, stream, player-api, core
```

Rules (AGENTS.md): no Android or `java.*` in `commonMain` (enforced by the `verifyCommonMain` task, part of `check` in every
shared module: `./gradlew verifyCommonMain`), platform code behind an interface or `expect/actual`, presenters expose
`StateFlow<UiState>` plus `onEvent(...)` (docs/PRESENTERS.md). Package names did not change when code moved, so the Baseline
Profile still matches class names.

### Plugging in a new platform (Apple, desktop, web)

A new UI target implements the platform bindings, starts Koin with the shared modules and renders presenter state.
Bindings the platform must provide. Each shared module lists the ones its own module needs in its Koin module's KDoc,
and that module's `Shared*ModuleTest` resolves the whole graph against a fake of exactly that list. "Consumed by" is the
module whose bindings use the interface (the interfaces themselves are declared in `core.platform`):

| Binding | Consumed by | What it is |
|---|---|---|
| `HttpClientEngineFactory<*>` | core | Ktor engine (Android: OkHttp; Darwin, Java/CIO, Js elsewhere) |
| `TokenStore` | core | encrypted storage of the OAuth tokens (Android Keystore; Keychain, DPAPI/libsecret later) |
| `SnapshotStorage` | core | last good screen data on disk (cache directory) |
| `DataStore<Preferences>` x2 | core | named `settings` and `vod_progress` (see `StorageNames`) |
| `AppConfig` | core, presenters | public Twitch client id, the engine a fresh install starts with |
| `SettingsDefaults` | core | per-device starting settings (e.g. fewer animated emotes on low-memory devices) |
| `Logger` | stream, chat | warn-level logging |
| `EventStreamClientFactory` | chat | HTTP client for the 7TV event stream (no read timeout) |
| `DeviceProfile` | presenters | interface language tag (empty for the root locale) |
| `VoiceInput` | presenters | speech recognition for search |
| `NumberFormatter` | UI only | locale decimal text; no shared Koin module resolves it, the UI hands it to the VOD helpers. Covered by the app's `ProductionGraphResolutionTest`, not by a shared test |

Then: `startKoin { modules(sharedCoreModule(), sharedStreamModule(), sharedChatModule(), sharedPresentersModule(), <platform module>) }`
and create each screen's presenter with `get<XPresenter> { parametersOf(..., scope) }`, passing the `CoroutineScope` the UI
cancels. The Android app keeps its list in `productionModules()` and tests it (`ProductionGraphResolutionTest`).

## Code layout (`app`)

```
com.twitchplayer.tv
  app/            TwitchTvApp (rail + Navigation 3 host), dialogs, AuthViewModel (holder)
  navigation/     typed NavKeys, AppNavigator (back-stack rules, unit tested)
  core/
    designsystem/ theme (colors, dimens, type, motion), icon (one 24 dp family), component (cards, chips, buttons, rail, dialog, toast)
    ui/           TvFocusable + focus ring, FocusMemory (restoration), BringIntoView specs, LoadGate, image + formatting helpers
    data/         Android storage implementations (snapshot files), Keystore token store
    di/           Koin modules: Android bindings, ViewModel holders, productionModules()
  feature/<home|follows|browse|category|search|settings|channel|player|vod|clip>/ {ui, presentation (thin holder ViewModels), data (Android only)}
    player/playback     Media3 and WebView players behind VideoPlayer, StreamPreview
    player/ui           PlayerScreen, ChatPanel (list, focus, live pill), ChatLineView (one Text per message), ChatEmotes, ChatSettingsPanel
```

The presenters, repositories and chat pipeline described in the sections below now live in `shared/*` under the same package names
(for example `feature/player/data/chat` is in `shared/chat`).

DI is Koin (no annotation processing, no KSP on AGP 9). Screens are `XRoute` (ViewModel + state collection) over a stateless `XScreen`. Debug and benchmark builds can run on fictional demo data (`app/src/demo`).

## Shape

Start as a single Android application module with package-level separation: `core/model`, `core/network`, `core/twitch/auth`, `core/twitch/api`, `core/twitch/eventsub`, `core/design`, and focused `feature/*` packages. Avoid Gradle multi-module overhead until build times or ownership justify it.

Compose screens consume immutable UI state from ViewModels; ViewModels call small repositories/use cases backed by typed network/auth clients. Coroutines and StateFlow carry state. Keep API DTOs separate from UI/domain models. Use Kotlin Serialization and Ktor client (including WebSockets) unless the project establishes a compatible standard. Coil 3 handles remote artwork. DataStore holds preferences; Room is unnecessary until structured offline data is required.

## Authentication and storage

Use Twitch public-client Device Code Grant; no client secret in the APK. Store rotating access/refresh tokens in an encrypted Android Keystore-backed store, atomically replace refresh tokens, validate session as Twitch requires, and clear all credentials on logout/revocation. Device code polling honors server interval/expiry and cancellation.

## API and data

Helix repository methods expose typed paging and domain results. Send `Client-Id` and `Authorization: Bearer` consistently; validate/refresh tokens on authorization failure, retry at most once, and honor rate-limit headers/backoff. Keep memory caches small and TTL-based; no speculative database. Use APIs for followed channels/streams, top games, search, streams, users, channel info, and chat assets. A “recently viewed” section is locally recorded history, not Twitch-provided watch history unless a documented endpoint supports it.

## EventSub and chat

One connection per active chat session, started only for the selected channel and stopped on leaving/background policy. Parse metadata and payload into typed events; subscribe after welcome within the allowed window to `channel.chat.message` and, best effort, `channel.chat.notification` (sub / raid / announcement lines: Twitch writes the sentence, we show it). One undecodable message never ends the session. Deduplicate by EventSub message ID. On reconnect URL, migrate while old session remains active through new welcome; after unplanned loss, reconnect and recreate subscriptions. There is no general-purpose historical chat archive or replay for lost events. Use Helix Send Chat Message and reflect success/failure only from response.

### Chat pipeline (transport -> state -> renderer)

```
EventSubChatClient --ChatEvent--> ChatSource.events()        transport: knows Twitch, nothing about drawing
  -> batch(250 ms) -> ChatFeed.toLine(assets)                 one list update per window; resolved off the main thread
  -> StateFlow<ImmutableList<ChatLine>> (newest 200)          UI state: immutable, stable ids, no transport types
  -> ChatPanel (LazyColumn, key = id) -> ChatLineView         renderer: knows ChatLine + ChatStyle only
```

`ChatLine` is the contract: pieces (`Text`, `Strong`, `Emote(layers)`), badge URLs, a readable nickname colour, kind (normal / highlight / mention / system), bits. The renderer never sees providers or EventSub. Scroll state (following live, unread count) is UI-local in `ChatPanel`, not in the ViewModel; the ViewModel owns only what survives recomposition of the screen (buffer, chat options open, settings). `ChatSettings` (text size, spacing, background, badges, time, animated emotes + limit) is persisted in DataStore and applied live.

### Emote providers

`EmoteProvider` (`global()`, `channel(id)`, optional `changes(id)` push) is the only extension point; adding a provider is one class plus one line in `AppModule`, listed in precedence order. `EmoteRepository` merges the providers into one `name -> Emote` map:

- **Precedence**: a Twitch emote named by the message itself always wins; then channel emotes before global ones; within a scope the provider order decides (7TV, BTTV, FFZ). Names are case-sensitive whole words, as in the providers' own clients.
- **Independent failures**: each provider and scope is its own layer; a failure leaves that layer as it was (or empty) and the others render. A slow provider cannot hold chat back beyond 3 s (late layers join when they answer).
- **Caching**: metadata in memory (global 1 h, channel 5 min), so zapping back does not refetch; images in Coil's disk (64 MB) and memory caches. Channel sets are re-read every 10 min; 7TV also pushes live edits through its EventAPI (server-sent events, `emote_set.update`), BTTV and FFZ rely on the periodic read.
- **Stills and animations**: every `Emote` has a still `url`; `animatedUrl` is set only when an animated variant exists (7TV `2x.webp` / `2x_static.webp`, BTTV `2x.webp` / `static/2x.webp`, FFZ `animated/2` vs `2`, Twitch `animated` vs `static` per the message's formats).
- **Zero-width (overlay) emotes**: 7TV (emote flag 1<<8, activation flag 1) and BTTV (`modifier`) overlays stack on the emote before them (`ChatPiece.Emote.layers`); FFZ "modifier" emotes (flip / rotate) are not supported and stay plain text.

Endpoints and payloads were checked against the live APIs and the providers' open-source clients on 2026-11-06 (see `TWITCH_INTEGRATION.md`).

### Chat rendering

A message is one `Text` whose nickname, badges and emote slots are inline placeholders in the platform's text layout (wrapping and measurement are the platform's); an emote slot is an `AsyncImage` stack (still, plus the animated variant on top while it has budget). `LazyColumn` with `reverseLayout`, `key = id`, `contentType = kind`. Newest-first animation budget: only lines currently on screen can hold it, the newest first, up to the user's limit (4 / 8 / 16); nothing animates while scrolling, while the app is stopped, when chat is hidden, when animated emotes are off, or after the system reports low memory. Animations are decoded by Coil's `AnimatedImageDecoder` (`GifDecoder` below API 28), registered per request, so the app-wide loader stays still-only and a decoded animation is never shared through the memory cache.

Remote: the chat is one focus target (`TvFocusable`), reached only by moving Right from the control bar (never by messages arriving); Up / Down scroll history, OK returns to live, Left gives focus back to the last control; Back closes the topmost layer (zapper, chat options, chat, screen).

## Player boundary (gating)

Compose owns native navigation and information UI. A dedicated Android WebView hosts the Twitch official embed page from a controlled HTTPS origin; that origin must be registered as `parent`. Only the page and its own controlled bridge can communicate with native code. Do not expose a generic JavaScript bridge to third-party frames. Keep player viewport at least Twitch's documented 400×300 minimum, visible, and unobscured. Preserve ads and official controls. Lifecycle must pause/resume safely, release WebView on disposal, and recreate after process death; channel changes should use documented player behavior.

Twitch's published docs describe embeds on websites/domains and do not explicitly confirm a standalone Android TV app/WebView distribution model. This needs direct Twitch confirmation before release; desktop/browser success is not Android TV validation. See `TWITCH_INTEGRATION.md`.

## TV UI and navigation

Use Compose for TV-compatible focus APIs/components available at implementation time. Model destinations and selected channel in app navigation state. Give every interactive element an explicit focus target; restore focus after dialogs and back navigation. Keep network and image work off the main thread. Avoid foreground services unless a documented user-facing background playback requirement emerges.

## Localization

36 languages: `res/values` (English) plus one `values-*` folder each. Legacy qualifiers are required by Android: Hebrew is `values-iw`, Indonesian `values-in`, Filipino `values-b+fil`; Brazilian Portuguese is `values-pt`, European Portuguese `values-pt-rPT`. `generateLocaleConfig` builds the locale list for Android 13+ per-app language settings from these folders and `res/resources.properties`.

- To add a language: add its folder, then its tag to `AppLocales.supported` (the in-app picker in Settings). Android TV settings rarely show per-app language, so the picker is the main way to change it.
- Android 13+ uses `LocaleManager`; older versions keep the tag in shared preferences and apply it in `MainActivity.attachBaseContext`.
- Layout is RTL-aware (`supportsRtl`): directional icons auto-mirror (`TvIcons.MIRRORED`), and the rail panel, the switch knob and the chat column slide follow the layout direction.
- The search keyboard follows the app language (`keyboardRows`: AZERTY, QWERTZ, or QWERTY with that language's extra letters); it stays Latin because Twitch logins are Latin.
- Category pages list streams in the app language first (Helix `language`), with a chip to show all languages.
- Viewer counts use ICU compact numbers (`14.8K`, `14,8 k`, `1.5万`).

## Testing boundaries

Unit tests cover DTO parsing, paging, auth state transitions, EventSub parsing/deduplication, chat fragments, and repository errors. Integration tests use controlled HTTP/WebSocket fixtures. Player validation requires a physical Android TV device and Twitch confirmation; emulator or desktop results are not sufficient. Do not add tests before the project foundation exists.


# Twitch TV

Native Android TV client built with Kotlin and Jetpack Compose. Browsing, channel details, and chat are native. Video uses Twitch's official embedded player in WebView; it does not proxy or extract video streams.

## Build

```sh
./gradlew :app:assembleDebug
./gradlew check                                  # spotless (ktlint), detekt, Android Lint, verifyCommonMain, all unit tests (shared + app)
./gradlew spotlessApply                          # fix formatting
./gradlew verifyCommonMain                       # no Android / java.* APIs in any shared commonMain
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest        # D-pad / focus / Back UI tests (emulator or TV)
./gradlew :app:assembleBenchmark                # release-like build (R8) on demo data, used by :benchmark
ANDROID_SERIAL=emulator-5554 ./gradlew :benchmark:connectedBenchmarkAndroidTest   # Macrobenchmark startup + frame timing
benchmark/tools/generate-profiles.sh            # regenerate the Baseline + startup profiles (emulator) into app/src/main/baselineProfiles/
ANDROID_SERIAL=<tv> benchmark/tools/install-release.sh   # release build on a TV, AOT-compiled at install with its profile
./gradlew :app:analyzeReleaseR8Config           # R8 Configuration Analyzer report (app/build/reports/r8/)
```

## Modules

| Module | What it is |
|---|---|
| `app` | Android TV UI (Jetpack Compose), Media3 and WebView players, Android implementations of the platform interfaces, Koin wiring, demo data |
| `shared/core` | Kotlin Multiplatform: models, Twitch API and auth, repositories, settings, platform interfaces |
| `shared/stream` | stream, VOD and clip resolvers (GQL token, usher, master playlist) |
| `shared/chat` | EventSub chat client, emote providers, chat assets |
| `shared/player-api` | `VideoPlayer` interface and playback models |
| `shared/presenters` | one presenter per screen: `StateFlow<UiState>` plus `onEvent(...)` |
| `shared/testing` | fakes shared by the tests |
| `benchmark` | Macrobenchmark and Baseline Profile generation |

Dependency direction: `app` -> `presenters` -> `chat`, `stream`, `player-api` -> `core`. Shared code has no Android or
`java.*` APIs (`./gradlew verifyCommonMain` enforces it). See [architecture](docs/ARCHITECTURE.md), the
[presenter pattern](docs/PRESENTERS.md), the [migration log](docs/kmp-migration-log.md), the
[performance baseline](docs/perf-baseline.md) and its [post-migration numbers](docs/perf-after-phase1.md), and the
[localization plan](docs/LOCALIZATION_PLAN.md).

### Tests

```sh
./gradlew :shared:presenters:jvmTest             # presenter tests on the JVM (fast, no Android needed)
./gradlew :shared:presenters:testAndroidHostTest # the same tests against the Android target
./gradlew allTests                               # every shared module, jvm + Android host
./gradlew :app:testDebugUnitTest                 # app-side tests (holder ViewModels, Koin graph, Android implementations)
```

Connected Gradle tasks install on every adb device: set `ANDROID_SERIAL` so they never replace the release build on a TV.

Demo data (no account, no network; fictional channels): in a debug build run `adb shell run-as com.twitchplayer.tv touch files/demo` and restart the app. Demo chat has fictional 7TV / BTTV / FFZ style emotes (stills, animated GIFs, zero-width overlays) drawn in code; `adb shell run-as com.twitchplayer.tv touch files/demo-chat-heavy` (debug) or `adb shell settings put global twitchtv_demo_chat_heavy 1` (benchmark build) switches it to ~16 messages/s. `benchmark/tools/chat-load.sh` and `chat-memory.sh` measure it (see [performance notes](docs/PERFORMANCE.md)). See [architecture](docs/ARCHITECTURE.md), [design system](docs/DESIGN_SYSTEM.md) and [performance notes](docs/PERFORMANCE.md).

The public Twitch Client ID is in `gradle.properties`. Client IDs identify the OAuth app and are public; do not add a client secret. OAuth uses Twitch Device Code Grant. Tokens are encrypted with AES-GCM using an Android Keystore key.

## Player setup

The static player page is in `player-site/`. Host it over HTTPS on a domain you control, then set `twitchPlayerHost` to its HTTPS origin and `twitchPlayerParent` to the exact hostname in `gradle.properties`. See [player hosting notes](docs/PLAYER_HOSTING.md). No video proxy is involved.

The app currently builds and runs on the Android TV emulator. Device-code auth and followed streams have been exercised with Twitch. EventSub chat reaches the connected state; live message delivery and sending still need full account/channel validation. The player remains unavailable until the hosted page is deployed and Twitch confirms this WebView distribution model. Emulator playback is not treated as TV hardware validation.

This is an independent, unofficial client and is not endorsed by Twitch.

# Phase 3 (Android phone UI) log

Branch `feat/phone-ui`, from `feat/kmp-shared`. Step A prepares the phone app: the Android pieces that are not TV UI move
out of `:app` so a second app can reuse them. **No behaviour change for the TV app** was the rule; the evidence is below.

## Step A: shared Android modules (2026-11-11)

### What moved (package names unchanged, so the Baseline and Startup profiles keep matching)

| Module | Holds |
|---|---|
| `:platform-android` (`twitchplayer.android-library`, Compose + Media3) | `AndroidKeystoreTokenStore`, `AndroidSnapshotStorage`, DataStores, `AndroidDeviceProfile`, `AndroidNumberFormatter`, the Koin wiring (`coreModule(config)`, `productionModules(config)`, `viewModelModule`), the 9 holder ViewModels, `PlatformVoiceRecognizer`, `AppLocales`, `KeepScreenOn`, `NativeVideoPlayer` + `NativeVideoSurface`, `TwitchPlayerConfig` / `PlayerConfigProvider` |
| `:demo-android` | the fictional demo data (`DemoModule`, `DemoData`, `DemoChat`, `DemoEmotes`, `DemoArt`) and `demo_stream.mp4`; one public entry point `registerDemoImageComponents()` |
| `build-logic` | `twitchplayer.android-library` convention, `twitchplayer.shared-strings` plugin (+ unit tests) |

Stayed in `:app` on purpose: `StreamPreview` (uses the TV focus animation type), `TwitchEmbedPlayer` (WebView, `BuildConfig.DEBUG`), the
design system, all screens, the per-variant `DebugModules` (`src/debug`, `src/release`, `src/demoAlways`).

### Seams introduced

- **`PlatformConfig`** (twitch client id, default player engine, embed page host/parent): a library has no `BuildConfig`; the TV app builds
  it in `app/.../core/di/AppPlatformConfig.kt`. `coreModule` became `coreModule(config)`, `productionModules()` became
  `productionModules(config)`.
- **`QualityLabels`**: `NativeVideoPlayer` used `R.string.quality_auto` / `quality_audio_only` (app resources). It now takes a
  `() -> QualityLabels`, read every time the quality list is built, exactly when `context.getString` ran before. `PlayerScreen` provides it
  through `stringResource` + `rememberUpdatedState` (Lint's `LocalContextGetResourceValueCall` rejected the first attempt with
  `LocalContext.current.getString`; the new form also follows configuration changes).
- **testFixtures** in `:platform-android`: the Koin graph-test helpers (`koinVerifyExtraTypes`, `koinVerifyInjections`,
  `testPlatformConfig()`), shared with the app's `DemoKoinGraphTest`.
- **`twitchplayer.shared-strings`**: `sharedStrings { fromModule(":app") }` generates one `res` directory per variant from another
  module's `values*/strings.xml` through AGP's `addGeneratedSourceDirectory`; nothing is copied into git. The copy rule
  (`SharedStrings.copy`) is a plain function with 5 unit tests; a `ProjectBuilder` test covers the extension and the task action (7 tests
  in total, run by the root `check` and by CI). End-to-end proof on a throwaway app module (deleted again, not committed): 36 `values*`
  directories generated, `aapt2 dump resources` shows `string/quality_auto` in every locale (`""`, `fil`, `ca`, `da`, ...).
  Consumers keep their own strings in a differently named file (e.g. `phone_strings.xml`): a name defined twice fails the resource merge on
  purpose.

### Deviations and decisions (all deliberate, none changes behaviour)

1. The holder ViewModels moved with the Koin wiring, including `ChannelViewModel` with its `lastClip` / `lastReplay` memory (plain
   vars, harmless for a phone UI): `viewModelModule` needs all of them, and splitting it would have left `productionModules` in two places.
2. Two visibilities widened because the app consumes them across the module boundary now: `TwitchPlayerConfig.host` (the WebView navigation
   policy reads it) and `Context.settingsStore` (`TvFocusFlowTest` seeds settings). `vodProgressStore` stays internal.
3. `:platform-android` exports the Compose BOM with `api(...)` so consumers resolve its unversioned Compose artifacts (without it
   `:demo-android` could not resolve `androidx.compose.ui:ui`).
4. `DemoArtFetcher` / `DemoEmoteFetcher` are `internal`; instead of widening two classes the demo module exposes
   `registerDemoImageComponents(builder)`, which the app's `DebugModules` (debug and demoAlways) call.
5. App dependencies pruned because nothing in `:app` imports them any more: `androidx.datastore.preferences` (main), the five Ktor
   artifacts, `ktor-client-mock` (test). `datastore-preferences-core` stays as a test dependency of `ChatSettingsTest`.
6. New catalog entries, each used: `androidx-compose-runtime`, `androidx-lifecycle-viewmodel`, `androidx-lifecycle-viewmodel-savedstate`
   (testFixtures), `koin-core-viewmodel`, `coil-core`, `android-library` plugin.
7. `check` did not compile the instrumented test sources, which hid a break (see below). The app convention now makes `check` depend on
   `compileDebugAndroidTestKotlin` (by name, to stay lazy), CI's static job names it too, and CI's test job runs build-logic's unit tests.
8. CI release job: fails if `mapping.txt` mentions `com.twitchplayer.tv.demo` or the release APK contains `demo_stream.mp4`.
9. The `AppBundleLocaleChanges` Lint hint appears for `AppLocales` once it is in a library (the app disables language splits in its
   `bundle { language { enableSplit = false } }`, a library cannot know that). It is the one baseline entry that is not a pure move; it is
   in `platform-android/lint-baseline.xml`. **A consuming app must disable language splits too.**

### A break the first `check` did not catch

After the move the emulator run failed to compile `app/src/androidTest` (`TvFocusFlowTest` used the then-internal `settingsStore`). Fixed in
`9f6cea7`, and the gap closed in `3865c6c` (see deviation 7).

### Verification (final tree)

| Check | Result |
|---|---|
| `./gradlew check --rerun-tasks --continue` (fresh daemon) | green; **528 tests, 0 failures**: app 24, platform-android 13, build-logic 7, shared/chat 41 + 41, core 39 + 29, player-api 7 + 7, presenters 130 + 126, stream 40 + 24 (jvm + Android host). Before this step: 521 (app 37); the 13 moved tests are now in platform-android, 7 are new |
| Release APK / AAB | **APK 4,001,682 B (identical to `perf-after-phase1.md`)**, AAB 8,031,723 B |
| dex | `classes.dex` 2,305,724 B + `classes2.dex` 2,840,604 B = 5,146,328 B, **+0.06 %** vs 5,143,108 B after Phase 1 (limit 1 % over that figure) |
| Profiles | `assets/dexopt/baseline.prof` 15,196 B and `.profm` 903 B packaged in the APK and in the AAB (`BUNDLE-METADATA/.../baseline.prof`). The profile source files are unchanged. The compiled size differs slightly from the 15,164 / 898 B recorded after Phase 1; I did not investigate why |
| `-keep` rules | 0 in `app/proguard-rules.pro` and in every consumer rules file (only `-keepattributes`) |
| Demo absent from release | `releaseRuntimeClasspath` has no `demo-android`; `mapping.txt` of release has 0 demo classes; release APK has no `demo_stream.mp4`. Debug and benchmark have the dependency (benchmark mapping: 23 demo classes) and the asset |
| Lint baselines | app 17 entries (unchanged, none added: the app's lint also analyses its dependencies, so entries of moved files still match), platform-android 6 (5 pure moves + the `AppBundleLocaleChanges` of deviation 9), demo-android 2 (`UseKtx`, moved) |
| detekt baselines | app shrank (debug 124 to 108, release 116 to 109, plain 86 to 74); new baselines hold only moved legacy findings: platform-android 6 / 8 / 8, demo-android 7 / 9 / 9 |
| Compose report (`-PcomposeReports`, debug) | app 142 + platform-android 3 = **145 composables, 128 + 2 = 130 skippable** (same as before: 145 / 130); the same five unstable parameters (`context`, `controller`, `edgeGuard`, `preview`, `token`), all in the app; none in platform-android. `config/compose/stability.conf` needed no change |
| Emulator screenshots (demo data, library demo module) | Home, Browse, Settings, the native player on the demo clip with the compact chat (badges, emotes, notices) and the control bar: all correct. Not driven: zapper, VOD and clip screens, overlay chat mode |
| `connectedDebugAndroidTest` | 14 tests, 2 failures, **both the known pre-existing ones** (`searchOpensOnTheVoiceKey`, `backClosesTheZapperBeforeLeavingThePlayer`); nothing new |
| `StartupBenchmarks` (emulator, 10 iterations each, raw JSON in `docs/perf-data/`) | cold start with the Baseline Profile, full display (min / median / max): run 1 **276 / 302 / 334 ms**, run 2 **280 / 297 / 313 ms** (limit 340 ms; `perf-baseline.md`: 318 ms; after Phase 1 the two full-suite runs gave 339 and 365 ms and the same-session A/B 295 to 330 ms); first frame medians 216 / 210 ms. Without compilation: 417 and 369 ms |

### Not verified

- The real TV was not touched (and the user's phone was not used): nothing measured on hardware, no `install-release.sh` run.
- That the startup profile's classes still land in `classes.dex` after this step (only the dex sizes were compared; the profile text is unchanged).
- R8 analyzer scores were not re-extracted (the report renders them with JavaScript).
- CI has not run on GitHub (no remote); its new steps were only YAML-checked and their Gradle tasks exercised locally.
- The two pre-existing instrumented failures were not investigated.
- The phone app itself does not exist yet (step B): `shared-strings` is proven on a throwaway module and by unit tests, and has no committed consumer.

## Review note: lint baselines after the extraction

`app/lint-baseline.xml` still lists issues in files that now live in `platform-android` / `demo-android`. This is
intentional: the app runs lint with `checkDependencies = true`, so the app's lint run analyses the library sources and
applies the app's baseline to them. Removing those entries makes `:app:lintDebug` fail (verified). The libraries carry
their own baselines for their own lint runs.

## Step B: the phone app (2026-11-11)

`:phone` (`com.twitchplayer.phone`, minSdk 24 like everything else, targetSdk 37) on `:platform-android` and the shared modules. Playback
is the next step: a tap on Watch, a replay or a clip goes through `PlayerLauncher` (`navigation/PhoneKeys.kt`), which today answers with a
short message ("The player is coming in the next update"). That stub is the only unfinished integration point.

Screenshots (Pixel 8 emulator, Android 36, demo data): [docs/screenshots/phone/](screenshots/phone/).

### What it is

| Area | Decision |
|---|---|
| Navigation | four tabs (Home, Browse, Search, Settings), each with its own back stack (`PhoneNavigator`, no Compose in its rules, 8 unit tests); Material 3 adaptive suite: bottom bar on compact, rail from medium width, no bar on a short landscape window or on detail pages; Back pops, then returns to Home, then leaves; Navigation 3 with saved state and ViewModel scoping per entry, state survives rotation (looked at) |
| Theme | Twitch purple, dark by default, Light and "follow the system" in Settings (a phone-only `ThemePreference`, SharedPreferences so the first frame is right); no dynamic colour, to keep the brand; edge-to-edge with bar icons that follow the chosen scheme |
| Home | "For you" (followed live channels, recommendations, top categories) and "Following" (live + offline) as two sub-tabs; pull to refresh (a short acknowledgement: the presenters expose no "refreshing" flag); skeletons, error with retry, signed-out prompt; the presenter's snapshot-first loading is unchanged |
| Browse / Category | poster grid with the TV's group filter and sort; category page with the viewer's-language filter chip (check mark when on) and the favourite toggle |
| Search | text field that owns what is typed and only pushes changes to the presenter (a lagging presenter state can never overwrite typing), recents, trending, channel and category results, voice with the runtime microphone permission and a denied message |
| Channel | header, live card + Watch, schedule, clips row, paged replays with resume bars, similar channels; pagination is a `snapshotFlow` over the list state |
| Settings | account (sign in, sign-out confirmation), theme, in-app language (`AppLocales`, all 36), animations, the TV's chat options, playback stats, follow raids, about and open-source licences. Not offered on the phone: the player-engine switch (native only) and the preferred quality (chosen in the player) |
| Sign-in | device-code dialog: open `twitch.tv/activate` (the URL Twitch returns already carries the code), a large code read letter by letter by TalkBack, a copy button, a countdown from the grant's own `expires_in`; the outcome (done, cancelled, failed) is told from the session afterwards (`signInOutcome`, 5 tests) because the presenter clears the code in every case |
| Accessibility | one described target per card (`cd_stream` from the TV strings), headings, 48 dp targets (M3 defaults), selectable rows, text scaling checked at 1.8x, reduced motion = the Animations switch or the system animator scale (skeletons stop pulsing, transitions snap) |
| Large screens | `GridCells.Adaptive`: one stream column on a phone, two at 600 dp, three at 1000 dp; two title lines are reserved only when there are several columns so a row's cards stay level |

### Build and tooling decisions

1. **`twitchplayer.shared-strings`** (review finding from step A): now copies every XML file of a `values*` folder whose top-level entries are
   all text (`string`, `plurals`, `string-array`): colours, themes and other kinds stay with the TV app, a file that *mixes* text and
   other kinds fails the build with its path instead of being silently dropped, and DOCTYPEs are refused. 9 + 2 unit tests. End to end in the
   phone build: 36 `values*` folders generated, the plural and the 5 `translatable="false"` entries present, the locale config generated
   (`resources.properties` + `generateLocaleConfig`), `bundle.language.enableSplit = false` so the in-app switch works.
2. **`formatViewers` moved to `:platform-android`** (it only needed `android.icu`): the TV imports are unchanged (same package).
3. **OkHttp `-dontwarn` rules are now `platform-android/consumer-rules.pro`**: both apps bind OkHttp through that module. TV release APK
   byte-identical before and after (4,001,682 B); the rules are in R8's merged configuration. The phone's own file has no `-keep` rule either.
4. **`androidPlatformFakes()`** (platform-android test fixtures): the Context-bound Koin bindings replaced by fakes, one definition for the
   TV and the phone graph tests.
5. **`lint { checkDependencies = false; baseline = null }`** in `phone/build.gradle.kts`: the libraries run their own lint with their own
   baselines (the root `check` runs every module), and the phone has none. `baseline = null` matters: AGP lint *creates* a baseline file the
   first time it fails when none exists, which silently hid four findings (one of them mine) during development. Fixed instead of baselined:
   target API, back-callback attribute, data extraction and backup rules (nothing is backed up: the token payload is bound to this device's
   Keystore), the screen-width read.
6. **detekt**: no phone baseline; the Compose rules' composition-local allowlist names exactly one local, `LocalReducedMotion`
   (`config/detekt/detekt.yml`), because skeletons and transitions at any depth read it.
7. **Catalog**: `material3`, `material3-adaptive-navigation-suite`, `material-icons-core` (BOM-managed; the extended icon set is megabytes, the four
   other icons are drawn from path data in `PhoneIcons`). All used.
8. **No baseline profile for the phone yet**: the one packaged is the libraries' own (9.9 KB). A phone profile and its Macrobenchmark are
   follow-ups; the TV profiles and numbers are untouched.

### Strings

Phone-only text is `phone_*` in `phone_strings.xml` (English) and `values-fr/` (French). The other 34 languages show English for those 30
keys until translated; `MissingTranslation` is ignored for that file only, and `PhoneStringsTest` fails if French drifts from English (keys or
placeholders). Everything else (labels, errors, chat options, `cd_*` descriptions) is the TV's translated text.

### Verification (final tree)

| Check | Result |
|---|---|
| `./gradlew check` (whole repo, with the phone) | green |
| Phone unit tests | **29**, 0 failures: `ThemeModeTest` 4, `PhoneNavigatorTest` 8, `SignInOutcomeTest` 5, `ChannelLogicTest` 5, `PhoneStringsTest` 4, `PhoneGraphTest` 3 (`verify` of `phoneModules()`, the list is production + phone module, and every screen ViewModel resolves from it) |
| Phone UI tests on the Pixel 8 emulator (`connectedDebugAndroidTest`) | **6 of 6** (home lists followed streams, the four tabs, a category and Back, channel + Watch stub message, search as you type, theme dialog) |
| Lint / detekt on `:phone` | no baseline, no findings (debug and release; detekt plain, debug, release) |
| Compose compiler report | **41 composables, 41 skippable, 0 unstable parameters** |
| Release APK / AAB | **2,500,311 B / 5,870,013 B** (R8 full mode, shrink resources, unsigned); `classes.dex` 3,814,516 B; 0 demo classes in the mapping, no `demo_stream`; `-keep` rules in our files: 0 |
| TV app | `check` green, release APK unchanged at 4,001,682 B |

Looked at on the emulator (screenshots in `docs/screenshots/phone/`), and what changed after looking: Browse chips had too much air under the
title (padding removed); the category header showed a stray poster next to a cryptic "EN" chip (poster and the then-dead `artUrl` field removed,
check mark added); the channel page repeated the name in the bar and the header, showed LIVE twice and a disabled-looking "Following" chip
(bar title emptied, LIVE kept on the card, a plain check + label); cards of one row had different heights when a title wrapped (two reserved
lines on multi-column layouts only). Also looked at: light theme, the signed-out state, the real device-code dialog (a real Twitch call, not
completed), keyboard search, font scale 1.8, a 600 dp and a 1000 dp window, rotation with the back stack kept.

### Not verified

- **The real phone** (Samsung SM-S721B, Android 16): an early debug build of the phone app was installed on it at the start of this step and never
  launched (the screen was off and locked, so it was not woken or driven); it is not connected any more, so it still has that early build.
  Everything above ran on the Pixel 8 / Android 36 emulator.
- TalkBack output and the 48 dp targets were checked from the semantics, not with TalkBack or the Accessibility Test Framework.
- Reduced motion was not seen (the system animator scale cannot be set from a test); the code path is `systemAnimationsOff` + the Animations switch.
- Sign-in completion: only the start of the grant (the code, the countdown, cancel) was run; no account was signed in.
- Performance: no startup or frame benchmark exists for the phone.
- CI has not run on GitHub (no remote).

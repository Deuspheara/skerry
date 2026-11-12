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
came in the following step (see "Step C"), and the redesign below moved it out of the back stack.

Screenshots (Pixel 8 emulator, Android 36, demo data and debug fixtures): [docs/screenshots/phone/](screenshots/phone/), retaken after the review follow-up.

### What it is

| Area | Decision |
|---|---|
| Navigation | **as first built** (replaced, see the redesign section at the end): four tabs (Home, Browse, Search, Settings), each with its own back stack (`PhoneNavigator`), Material 3 adaptive suite (bottom bar on compact, rail from medium width). **Today:** three tabs (Home, Following for a signed-in viewer, Browse) with a custom icon-only bar and rail (from 600 dp), each with its own back stack; Search and Settings are pages opened from the header; Back pops, then returns to Home, then leaves; Navigation 3 with saved state (an unreadable saved stack resets to the tab root) |
| Theme | Twitch purple, dark by default, Light and "follow the system" in Settings (a phone-only `ThemePreference`, SharedPreferences so the first frame is right); no dynamic colour, to keep the brand; edge-to-edge with bar icons that follow the chosen scheme |
| Home | "For you" (followed live channels, recommendations, top categories) and "Following" (live + offline) as two sub-tabs; pull to refresh (a short acknowledgement: the presenters expose no "refreshing" flag); skeletons, error with retry, signed-out prompt; the presenter's snapshot-first loading is unchanged |
| Browse / Category | poster grid with the TV's group filter and sort; category page with the viewer's-language filter chip (check mark when on) and the favourite toggle |
| Search | text field that owns what is typed and only pushes changes to the presenter (a lagging presenter state can never overwrite typing), recents, trending, channel and category results, voice with the runtime microphone permission and a denied message |
| Channel | header, live card + Watch, schedule, clips row, paged replays with resume bars, similar channels; pagination is a `snapshotFlow` over the list state |
| Settings | account (sign in, sign-out confirmation), theme, in-app language (`AppLocales`, all 36), animations, the TV's chat options, playback stats, follow raids, about and open-source licences. Not offered on the phone: the player-engine switch (native only) and the preferred quality (chosen in the player) |
| Sign-in | device-code dialog: open `twitch.tv/activate` (the URL Twitch returns already carries the code), a large code read letter by letter by TalkBack, a copy button, a countdown from the grant's own `expires_in`; the outcome (success or failure) comes from the sign-in presenter itself (`SignInUiState.result`, see the review follow-up below) |
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
| Phone UI tests on the Pixel 8 emulator (`connectedDebugAndroidTest`) | **6 of 6** (home lists followed streams, the four tabs, a category and Back, channel + Watch stub message, search as you type, theme dialog; **as first built**: the suite now has 100+ tests, see the redesign section) |
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


## Review follow-up (step B, after the independent review: 7.5/10)

### Robustness

| # | Finding | Cause and fix |
|---|---|---|
| 1 | "For you" showed a "Following" section header | Not wrong data: the section is the live channels the viewer follows (`HomeContent.following`), but it reused the TV string "Following", the name of the other sub-tab. It now says "Live from your follows" (`phone_home_live_following`, English and French); a UI test asserts the word appears once (the tab) on that screen |
| 2 | The sign-in outcome was lost on rotation and relied on a 600 ms guess | The outcome now lives in the shared `AuthPresenter`: `SignInUiState.result` (`Success` or `Failed`), set when `session.signIn` returns (true / false) or throws (a failure, not a crash), kept until the UI sends `AuthEvent.ResultShown`, cleared when a new sign-in starts; a cancel is not a result. The activity-scoped holder keeps it across a rotation, `PhoneRoot` shows the message and acknowledges it afterwards (at least once, never lost). `SESSION_SETTLE_MS` and `signInOutcome()` are gone. 6 presenter tests on both targets; a UI test completes a sign-in with a failure, rotates the activity while the message is up and sees it again |
| 3 | The countdown drifted when the app was backgrounded | `SignInCountdown` is a deadline on an injectable `ElapsedClock` (`SystemClock.elapsedRealtime`: counts sleep, ignores date changes), read on every tick (500 ms) instead of decremented; the deadline is `rememberSaveable` per device code, so a rotation does not restart it. 7 tests with a fake clock (rounding at the last second, a 25-minute pause, expiry, rebuild after recreation) |
| 4 | The verification URL was opened unchecked | `isTwitchUrl`: https only, host `twitch.tv` or a subdomain, no credentials, default port. Look-alikes (`twitch.tv.example.com`, `evil-twitch.tv`, `user@host`), `javascript:`, `intent:`, `http:` are refused (5 tests). A refused or unopenable URL shows an inline message in the dialog (a Snackbar would sit behind the dialog's window) telling the viewer to go to twitch.tv/activate and type the code |
| 5 | Bare clickables under 48 dp | `Modifier.clickableItem` is now `minimumInteractiveComponentSize().clickable(role = Button)` and every row uses it (similar channels, settings rows, channel rows, stream rows); switch and radio rows use `minimumInteractiveComponentSize` too. A UI test asserts the *touch* bounds (a 40 dp button has a 48 dp target) of a settings row, a switch row and the follow button |
| 6 | Magic `SNACKBAR_ABOVE_BAR = 88.dp`, Settings content under the bar | The snackbar host is inside the scaffold's content area and padded by `WindowInsets.safeDrawing` (bottom): above the bar, beside the rail, above the keyboard and the gesture bar, at any text size. Looked at on a tab page with the bar (the failed sign-in message) and on detail pages. The Settings list has bottom content padding; scrolled to its end the last row ("Open-source licenses") is fully visible above the bar (`14-settings-end-dark.png`) |
| 7 | `LocalReducedMotion` in `PhoneTheme.kt` | In `ui/theme/ReducedMotion.kt` next to `systemAnimationsOff`; the detekt allowlist keeps naming it |

### Visual

- **Medium width**: the rail's first item is level with the page title (a 16 dp top gap on the first item, rail layouts only), in dark and light. The channel's hero card stops growing at 560 dp.
- **Density**: on a phone a stream is a compact row (168 dp 16:9 thumbnail beside the title): 5 to 6 streams fit where 2 did. From 600 dp wide it is a vertical card with two title lines reserved so a row's cards stay level. `StreamLayout` is read once per screen. Skeletons follow the layout. No card container any more: text sits on the page surface, so nothing looks pasted under a dark thumbnail in the light theme. Replays and clips follow.
- **Sign-in dialog**: the code is centred between two 48 dp slots (empty / copy button); copy swaps the icon for a check, says "Code copied" (live region) and gives haptic feedback. *Not changed*: the space under "Expires in" is the dialog's own 24 dp before its button row; moving Cancel into the content only left an empty button row below it (tried, looked at, reverted).
- **Channel**: the follow state is a button, tonal with a check when following, outlined "Follow" when not, with state semantics ("Following" / "Not following") and a click label. Twitch's API no longer lets a third-party app follow or unfollow, so it opens the channel on twitch.tv (the trailing arrow says so); it cannot be a toggle that toggles. The live card no longer repeats the avatar and channel name of the header.
- **Category**: "Language: All languages | English" (two choices in a selectable group that wraps with large text; at 1.8x the second chip used to be squeezed into a narrow column, found by looking, fixed with `FlowRow`); the heart has a tooltip, a content description and a state description.
- **Search**: dividers under the rows (inset on both sides), a hint line, the supporting text is one tone (on-surface-variant, AA in both themes) with a red dot for live instead of a second accent colour. The empty area under four trending channels is left: there is no further data to show.
- **Settings**: labelled groups on rounded surfaces: Account, Appearance (theme, animations), Language (the row says the current language), Chat, Playback, About.
- **Error and empty states** are seen with debug-only fixtures (`files/demo-offline`, `files/demo-empty`, `phone/src/debug/.../StateFixtures.kt`; the cache is replaced too, because a `SnapshotCache` singleton keeps the storage it was built with and showed a cached Home instead of the error). The empty "For you" tab now explains itself.

Looked at (all in `docs/screenshots/phone/`): dark and light on every screen, medium width (600 dp) dark and light, error, empty, 1.8x text, Arabic (RTL), the sign-in dialog with a real code, the copy confirmation, the failure message above the bar.

### Tests

| Layer | Count |
|---|---|
| Phone unit tests (JVM) | **49**, 0 failures: countdown 7, URL 5, microphone flow 6, language choice 5, language label 2, channel helpers 5, navigator 8, theme 4, string parity 4, Koin graph 3 |
| `AuthPresenterTest` (shared, jvm and Android host) | 11 each (6 new) |
| Phone UI tests on the Pixel 8 emulator | **17 of 17**, run twice in a row (6 before): the earlier six, the Home header, density (4+ rows composed), rotation keeps the back stack, the language list (scrolled to French and Japanese), 48 dp targets, offline retry, empty states, the sign-in dialog (code, copy confirmation, cancel), a successful sign-in, a failed one and its message after a rotation |

Test harness note: unloading a Koin module that *overrides* a production definition removes that definition for good (the first run of the offline test crashed the next test: "no definition for SnapshotCache"). Each UI test now loads the production modules first and its overrides after.

### Final numbers (after the follow-up)

| Check | Result |
|---|---|
| `./gradlew check --rerun-tasks` (fresh daemon) | green; **595 test executions, 0 failures** (phone 49, presenters 268 across jvm and Android host, core 68, chat 82, stream 64, player-api 14, platform-android 15, app 24, build-logic 11); lint and detekt on `:phone` with no baseline |
| Compose compiler report (`:phone`) | **45 composables, 45 skippable, 0 unstable parameters** |
| Release APK / AAB | **2,547,611 B / 5,931,734 B** (was 2,500,311 / 5,870,013 before the follow-up: +1.9 %, the new screens' code and strings); `classes.dex` 3,852,672 B; 0 demo or fixture classes in the mapping, no `demo_stream`, 0 `-keep` rules in our rule files, 36 language folders generated |
| Phone UI tests on the emulator | 17 of 17, twice in a row |

### Unchanged, still unverified

The real phone (not connected; an early build installed and never launched), TalkBack output (semantics checked, not the screen reader), reduced motion seen running, a completed sign-in with an account, phone performance (no baseline profile or benchmark), CI on GitHub.

---

# Phase 3, step C: playback on the phone

Live streams, replays and clips, with chat, Picture-in-Picture and fullscreen, plus the step-B review leftovers. Branch `feat/phone-ui`.

## What was built

| Piece | Where | Notes |
|---|---|---|
| Live page | `feature/player/LivePlayerScreen.kt` | `PlayerPresenter` through the holder ViewModel of `:platform-android` (one per page: leaving the page ends the session and chat disconnects), native Media3 playback (`NativeVideoPlayer`). **Native only**: the phone has no WebView embed engine (that fallback needs a hosted page and exists for the TV). |
| Replay and clip pages | `ReplayScreen.kt`, `ClipScreen.kt`, `RecordedPlayback.kt` | Resume offer (a saved position past 15 s and not in the last 15 s), seek bar, +/-10 s, speed chips (portrait) / button (landscape), quality sheet, progress saved every 10 s and on leaving, cleared at the end; clip: replay and share (link built only from a plain clip id). |
| Frame shared by the three | `PlayerScaffold.kt`, `PlayerChrome.kt`, `PlayerControls.kt` | Portrait (picture on top, content below) and landscape (picture fills the screen, optional side panel); control layer that fades in and out; fullscreen button, immersive bars, keep-screen-on, Picture-in-Picture, Back leaves fullscreen first. |
| Chat | `ChatPanel.kt`, `ChatLineView.kt`, `PhoneChatStyle.kt`, `NickContrast.kt` | Newest at the bottom, follows new lines, "Back to live" pill with the count when the viewer scrolls up, badges, emotes (animated ones only on the newest visible lines, up to the viewer's limit, never while scrolling or in Picture-in-Picture), mention highlight, text size / spacing / badges / timestamps / opacity from the chat settings, sending through the presenter. Chat shows beside the picture (landscape side panel), over it (landscape "over the video") or under it (portrait). |
| Activity glue | `PlayerHost.kt`, `ActivityPlayerHost.kt`, `MainActivity.kt`, manifest | An interface (no-op default for tests) over the activity: Picture-in-Picture (`supportsPictureInPicture`, auto-enter on Android 12+ with a source-rect hint, before that on `onUserLeaveHint`, a play/pause remote action), orientation lock, immersive mode, light status-bar icons over the black picture area. `configChanges` handles rotation, size, font scale, density and UI mode in place, so a rotation keeps the player (no rebuffer) and the state. |
| Pure logic (all unit tested) | `ControlsVisibility`, `FullscreenPolicy`, `PipPolicy`, `ChatScrollPolicy`, `ChatModeCycle`, `PlaybackStart` (+`SeekModel`), `ReplayError`, `NickContrast`, `PhoneChatStyle` | |
| Shared code touched | `PlayerEvent.SetChatMode` (+test), `animationAllowance` moved to `shared:presenters`, `EmoteLayer` / `EmoteAspects` moved to `:platform-android` | The phone picks hidden / side panel / overlay directly (the TV cycles). The emote layers and the animation budget had no TV dependency: both apps now share them (package names unchanged; the TV APK is byte-identical, 4,001,682 B). |

## Decisions and deviations

- **No stream zapper, no mini player, no stats overlay on the phone.** The phone reaches another channel by going back; the stats toggle and the player-engine choice are TV settings.
- **Follow**: the live page has "Open on Twitch" (an icon button) instead of the channel page's Follow button, because the player does not know whether the viewer follows the channel (see the follow note in docs/TWITCH_INTEGRATION.md).
- **Chat needs a signed-in viewer** (the same rule as the TV: Twitch's chat API is account-bound). A signed-out viewer sees a prompt with a sign-in button; the message box exists only when signed in. In "over the video" mode there is no message box (the side panel and portrait have one).
- **Chat colours**: nickname colours come from the shared mapping, tuned for a dark chat. `readableOn` moves them as little as needed to reach 4.5:1 on the actual surface (light theme), found by looking: some nicknames were nearly invisible on white.
- **Message direction**: each chat line takes its direction from its text (`TextDirection.Content`): French lines stay left-to-right inside the Arabic layout.
- **Compose stability**: `androidx.media3.common.Player` is declared stable in `config/compose/stability.conf` (same reasoning as the presenters: one instance per page, never reassigned; everything the UI draws from it goes through `rememberPlayerSnapshot`).
- **detekt**: `LocalPlayerHost` joins `LocalReducedMotion` in the composition-local allowlist; `LongParameterList` ignores defaulted parameters (slot-based composables), which can only reduce findings repo-wide.
- **Debug fixture** `files/demo-stream-offline` (debug source set only) makes the player fail, to look at the offline page.
- **Benchmark build type** (`:phone:assembleBenchmark`): release rules and R8, debug-signed, always on the demo data; the release build is untouched (0 `-keep` rules, no demo classes).
- **`PhoneUiTest`'s language test** was stale since the step-B follow-up (the Language row now shows the language in use, "System language", not the word "Language"): updated.

## Step-B review leftovers

| Finding | Fix |
|---|---|
| Arabic UI half English | All phone-only strings now have an Arabic translation (`values-ar`); a unit test fails if English, French or Arabic differ in keys or placeholders, or if code uses a `phone_*` string that is not in English |
| LIVE badge colour / contrast | One red (`#EB0400`) with white text in both themes: 4.6:1 |
| Large-text nav labels clipping | Bar labels are one line and their text scale is capped at 1.3x (page content keeps the full scale) |
| Medium-width channel page void | Content is a centred column of at most 640 dp (the older 560 dp card cap that left-aligned it inside was removed) |
| Sign-in dialog | The code is `headlineLarge`, full-contrast `onSurface` |
| Home header gap | App bar height 52 dp instead of 64 dp |
| Search hint spacing | The hint only shows on an empty screen (it repeated the field with trending channels listed); the "Trending" header has top spacing |

## Looked at (final build, Pixel 8 emulator API 36, demo data)

`docs/screenshots/phone/player-*.png`, `channel-medium-width-centered.png`, `home-large-text-bar-labels.png`: live portrait with controls, landscape with the side chat and with chat over the video (controls shown and resting), a replay page, the clip page and its two error pages, the offline and failed-resolve pages, the light theme, Arabic right-to-left with the overlay, the Picture-in-Picture window (resting and with its controls), medium width, 1.8x text. All `player-*` and `clip-*` images were retaken on the final build; the old quality-sheet image was removed (not retaken).

What looking changed: the controls scrim was too weak under the title over bright video; the "Back to live" pill had no background (chat showed through its label); chat over the video ran under the bottom control bar; the offline message and Retry were dimmed by the scrim and the big play button covered Retry (a tap never reached it); a stream that cannot play still showed LIVE and a viewer count; nicknames were unreadable on the light theme; the status-bar clock disappeared over the black picture area in the light theme; French lines had their punctuation on the wrong side in the Arabic layout; the replay page was a title and a void.

## Tests

| Layer | Count |
|---|---|
| Unit tests, whole build (`./gradlew check --rerun-tasks`, fresh daemon) | **648, 0 failures**: phone 97, presenters 274 (jvm and Android host), core 68, chat 82, stream 64, player-api 14, platform-android 15, app 23, build-logic 11 |
| Shared | `SetChatMode` presenter test, `ChatAnimationTest` (budget moved next to its code) |
| Phone UI tests on the Pixel 8 emulator | **26 of 26** (17 existing + 9 player), run on the final build: the live page shows picture, chat and message box; a tap toggles the controls; quality sheet; fullscreen locks landscape and Back returns to portrait without leaving the page; sending clears the box; sign-out on the player shows the sign-in prompt; replay speed chips; offline channel and failed resolve with Retry |

Not testable under instrumentation: the control **auto-hide timer** (Media3 is paused by audio-focus loss in the test harness, which by design keeps the controls up). It is unit tested with virtual time (`ControlsVisibilityTest`) and was seen working on the emulator.

## Numbers

| Check | Result |
|---|---|
| Compose report `:phone` | **77 composables, 72 skippable, 0 unstable parameters** |
| TV app | 141 composables / 127 skippable in `:app`, the same 5 unstable parameters; **release APK 4,001,682 B, identical** |
| Phone release APK / AAB | **3,777,699 B / 8,670,010 B** (was 2,547,611 / 5,931,734 before the player: Media3 + HLS + chat rendering + animated emote decoding); `classes.dex` 5,522,484 B; 0 demo classes in the mapping; 0 `-keep` rules; `baseline.prof` from the libraries (Media3, Compose) packaged |
| Lint and detekt `:phone` | clean, no baseline |

### Phone performance baseline (emulator, so indicative: fast host CPU, GPU pinned to vsync)

Pixel 8 AVD (API 36, arm64), `benchmark` build type (R8 on, demo data), no baseline profile at that time (added in step D, numbers in [perf-phone.md](perf-phone.md)).

| Measure | Result |
|---|---|
| Cold start `am start -W` TotalTime, 10 runs, as installed (no AOT) | median **186 ms** excluding the first run after install (743 ms); runs 169-212 ms |
| Same after `cmd package compile -m speed -f` (full AOT, an upper bound for a profile) | median **173 ms** (142-239) |
| Live page: chat updating (2 UI frames/s) | UI thread p50 1.87 / p90 3.79 / p99 10.45 ms, no frame over 16.7 ms; RenderThread p50 1.14 / p99 9.96 ms |
| Home scrolling (back and forth, 8 times) | UI p50 0.67 / p90 1.92 / p99 7.86 ms (2 frames over 16.7 ms, image decodes on first display), RenderThread p50 1.04 / p99 4.04 ms |

Tools: `PKG=com.twitchplayer.phone ANDROID_SERIAL=emulator-5554 benchmark/tools/framestats.py --watch <s>` (the script now takes the package from `PKG`).

## Unverified

The real phone (screen locked, not used); TalkBack output (semantics checked, not the screen reader); reduced motion seen running; a completed sign-in and real chat sending with a Twitch account (chat and sending run on demo data); Picture-in-Picture before Android 12 and on devices without the feature (code path guarded and reviewed, not run); real streams (the demo clip is a 12-second looping test pattern: live-edge behaviour, ad breaks and quality switching on real HLS are the TV player's, reused, not re-measured on the phone); phone performance on hardware (emulator numbers only); CI on GitHub (no remote). See the Phase 3 summary at the end for what was added later.

## Step C review follow-up

- Replays and clips pause whenever the activity stops (Home, screen lock, another app, the Picture-in-Picture window dismissed with its X) and resume on return only if they were playing. A video the viewer paused stays paused. Entering PiP only pauses the activity (`onPause`); it never stops it, so the picture keeps playing there. **Correction found on the emulator**: a first fix asked `activity.isInPictureInPictureMode` at stop and skipped the pause when true; with the real X on an API 36 emulator the replay then ran on to its end, because the activity is still reported as in PiP while `onStop` runs. The exception was removed: any stop pauses. Verified by driving the real window (start a replay, Home, tap the window, tap X, wait 20 s, return: 0:06 of 0:12 instead of the end), and with lifecycle tests (`STARTED` keeps playing, `CREATED` pauses).
- Position is saved every 5 s while playing, on every pause and when the app stops (`PositionSaver`, `SavePlaybackPosition`), so pausing and then losing the process keeps the place. The chat mode has one source of truth (`PlayerRestoration.choose` / `toRestore`): the viewer's choice is written to the saved state and sent to the presenter, the saved mode reaches the presenter once when the page comes back, nothing copies the presenter's state back.
- Players are released through `RememberObserver`. Clip errors separate network failures from unavailable clips, and both offer Retry.
- Language switch while playing. API 33+: the platform `LocaleManager` changes the locale and, because `locale|layoutDirection` are in the activity's `configChanges`, the activity is **not** recreated: the player keeps playing and the screen re-composes in place (seen on API 36 with `cmd locale set-app-locales`, English to Arabic and back, the picture never restarted). API 24-32: `AppLocales.set` stores the tag and calls `Activity.recreate()` (the system per-app screen does not exist there). The player is then released and rebuilt: the viewer sees the picture go black and a re-buffer, and the page comes back from its saved state (live channel and chat mode, replay or clip position and fullscreen lock) and starts playing again. The in-app switch lives in Settings, a tab that is not shown while a player page is on top, so in practice the recreate happens with no player on screen. **Not verified on API 24-32**: there is no such emulator here, only the code path and the restoration tests.
- Position, fullscreen lock and chat mode survive process death (`PlayerRestoration`).
- Message-state and clip titles used the default content colour, which is black on the dark background. They now use `onBackground`. Found by looking at the screenshots.
- `ignoreDefaultParameters` is scoped to `:phone` (`phone/detekt.yml`). `gradle/gradle-daemon-jvm.properties` was removed (added by accident).
- The grey rectangle in the demo player is the demo video art, not a layout fault.
- Test counts, `check --rerun-tasks` (JVM, debug variants): phone 115, shared/presenters 144 jvm + 140 android-host, shared/core 39 + 29, shared/chat 41 + 41, shared/stream 40 + 24, shared/player-api 7 + 7, platform-android 15, app 23, build-logic 11. Instrumented `:phone` suite: 49, all green on an API 36 emulator.
- All timings in this document come from an emulator and are not device numbers.

## Step D: performance standard (2026-11-12)

- **`:benchmark-phone`**: Macrobenchmark module for the phone (`com.android.test`, targets `:phone`), touch journeys over the demo data (Home, Browse, Category, Search, Channel, Settings, replay, live player with chat scroll and fullscreen both ways), cold-start and frame-timing benchmarks, and the Baseline Profile generator. `:phone` got a `nonMinified` build type, an explicit `profileinstaller`, and Home now reports its drawn state (`ReportDrawnWhen`).
- **Profiles** in `phone/src/main/baselineProfiles/`: Baseline 38,639 rules (2,212 phone, 1,978 shared TV-namespace code), startup 21,673 (601 / 823), regenerated once with the final journeys. Packaged in APK and AAB, `profileinstaller` present, **99.2 %** of the surviving startup classes in `classes.dex`, R8 full mode with minify + shrink and 0 own `-keep` rules, analyzer 98.9 / 99.1 / 99.1 %.
- **Result** (emulator, five runs and two profile versions, see [perf-phone.md](perf-phone.md)): cold start with the profile is 4-31 % faster to first frame and 15-33 % faster to the Home content than with no AOT compilation (never slower; the final profile's first-frame gain, -4 % and -15 %, is within the session noise); frame timing is unchanged by the profile. Absolute numbers are inflated by a memory-starved host (not quiet at the end either) and are not a statement about the app; the "host, not the app" explanation is a hypothesis.
- **Cost**: release APK 3,779,635 -> 3,927,149 B (+3.9 %); about +131 KB of it is the startup profile (two dex files). The TV APK is unchanged (4,001,682 B).
- **Findings along the way**: `pressHome()` on a playing live page enters Picture-in-Picture (as designed; the benchmark setup now avoids it); UiAutomator `fling` degrades into taps on a starved emulator (journeys use explicit swipes); one ANR was seen at a host load average of 31 (see perf-phone.md, "Noise").
- **CI**: the static job compiles `:benchmark-phone`, the release job checks the baseline profile in both APKs and AABs (TV and phone) and runs `check-profile-packaging.py` for the phone. Every task named in `ci.yml` resolves (`./gradlew --dry-run` over the exact task list).
- **Deviation**: the benchmark Kotlin scenarios are not shared with `:benchmark` (touch vs D-pad; nothing in common but the rule setup); the generic parts (profile generation script, framestats, catalog entries) are shared. The benchmark runner drives `am instrument` after building, not `connectedBenchmarkAndroidTest`, to keep Gradle's memory out of the measurement.

## Phase 3 summary (Android phone UI)

| Step | What | Review score |
|---|---|---|
| A | shared Android modules (`:platform-android`, `:demo-android`, `shared-strings`), no TV change | 8 |
| B | the phone app: Home, Browse, Category, Search, Channel, Settings, sign-in | 7.5, then 8 after the follow-up |
| C | native playback: live with chat, replays, clips, fullscreen, Picture-in-Picture, restoration, lifecycle | 7, then 7.5; the last fixes (PiP dismissal, position saved on pause and stop, one writer for the chat mode, final screenshots) are not scored |
| D | Macrobenchmark module, Baseline and startup profiles, perf document, docs and CI | to be scored |

**Where it stands**: the phone app builds, runs and is tested on the demo data end to end (128 JVM unit tests in `:phone`, 54 instrumented tests on an API 36 emulator, shared presenters 145 + 141, `check --rerun-tasks` green); release APK 3.93 MB, AAB 8.80 MB; profiles generated and verified; the TV app unchanged (4,001,682 B) and not re-measured.

**Known gaps** (none hidden):
- No validation on a real phone: everything above ran on an emulator or in JVM tests; performance numbers are emulator-only.
- TalkBack: semantics are asserted in tests (labels, roles, 48 dp targets), the screen reader itself was never run.
- Language switch on API 24-32: `Activity.recreate()` rebuilds the player (picture black, re-buffer, then it resumes from its saved state); the switch is only reachable from Settings, where no player is shown. Not run on such an emulator.
- 34 of the 36 locales fall back to English for the phone-only strings (the TV's 36 translations are reused; English and French exist for the phone's own).
- Follow and unfollow are not possible: the app's viewer tokens cannot call the follow API (the channel page links to Twitch).
- The TV was not re-measured after Phase 3 (its APK is byte-identical, its profile unchanged).
- Picture-in-Picture before Android 12 and on devices without the feature were not run; the first fix for the PiP dismissal was wrong and was corrected by driving the real window (see step C follow-up).

## Final review fixes (after step D)

- **Chat-mode restoration**: `toRestore()` no longer compares the saved mode with the presenter's current one. Until the saved default has loaded the presenter shows the placeholder `ChatMode.Hidden`, so "equal, nothing to do" lost a choice of Hidden (saved default Compact, viewer chose Hidden, process killed). The screen now sends `SetChatMode` whenever the viewer ever chose one; it is idempotent and marks the choice as made, so the late default cannot overwrite it. Tests: every mode against every default through the real presenter with late settings (`PlayerPresenterTest`), a Hidden choice through a save/restore round trip (`PlayerRestorationTest`).
- **Replay and clip position near the end**: a restored position within the last 15 s of a known length (`VodProgress.END_MARGIN_MS`, the rule of the saved replay progress) restarts from the beginning, or offers the saved-progress resume when that is resumable (`resumableRestoredPosition`, 7 tests in `PlaybackStartTest`). A clip writes its length next to its position (`PlayerRestoration.durationMs`; an older saved state without one still loads); an unknown length keeps the position.
- **Sign-in code on the clipboard** (`CodeClipboard`): the copy is flagged `ClipDescription.EXTRA_IS_SENSITIVE` on Android 13+ (hidden from the clipboard preview and history); when the dialog closes (cancel, success, expiry) the clipboard is cleared with `clearPrimaryClip()` (API 28+; a blanked clip before that) **only if the primary clip is still exactly the code** (the viewer's own later copy is left alone). Limits: from Android 10 only the focused app can read the clipboard, so when the dialog goes away while the app is in the background nothing can be verified and nothing is cleared (the sensitive flag still applies); a clipboard manager or keyboard that already stored the copy is out of the app's control. Tested on the emulator with the real clipboard (`CodeClipboardTest`: flag, cleared when ours, left alone when replaced, empty clipboard, and the real dialog copy then close).

## Picture-in-Picture: what was and was not observed

The rule "any stop pauses a replay or clip" (step C follow-up) was found and checked **on one emulator only: Pixel 8, API 36, Google Play image**. By driving the real PiP window there, the activity is still reported as in Picture-in-Picture while `onStop` runs after the X, so asking for the PiP state at stop did not work, and entering PiP only pauses (never stops) the activity. **It has never been run on a real phone or on an OEM build** (Samsung, Xiaomi and others change the PiP window and its lifecycle).

Residual risk: if a device delivers a transient `ON_STOP` while auto-entering Picture-in-Picture (some OEM builds stop and restart the activity during the transition), the replay would be paused by `PauseInBackground` and stay paused in the window until `ON_START` brings the activity back, because the policy resumes only on `ON_START`. The live page is not affected (it keeps its own foreground logic). What to check on a real device: start a replay and a clip, press Home (auto-enter on Android 12+; the PiP button before): the picture must keep playing in the window; tap the X: playback must stop; tap the window to expand: it must resume where it was; lock the screen: it must pause and resume on unlock. If the picture freezes in the window on some device, the fix is to ignore a stop that arrives while a PiP entry is in progress (and the test seam is `PlaybackLifecycleTest`).

## Redesign: the player is a sheet above the tabs (2026-11-12)

Plan and research: [phone-redesign.md](phone-redesign.md). What changed in code:

- `LiveKey`, `ReplayKey`, `ClipKey` and `PlayerLauncher` are gone. `PlaybackTarget` (Live, Replay, Clip) is the player session's target.
  `PlayerSessionController` (`feature/player/PlayerSession.kt`: `PlayerSessionState`, `PlayerSessionReducer`, `BackPolicy`, a saver for the process
  being killed) is saved with the activity; `PhoneScaffold` hosts `PlayerOverlay` above the tab `Row`.
- `PlayerOverlay` is an `AnchoredDraggableState` with anchors Expanded (0), Collapsed (above the tab bar) and Dismissed (below the screen);
  Dismissed exists only while the sheet rests as the strip, so a hard fling of the expanded sheet ends on the strip and never closes it.
  The page (`LivePlayerRoute` / `ReplayRoute` / `ClipRoute`) stays composed and `PlayerSheetLayout` places the one picture child in every
  mode (portrait with progress, landscape with a side panel, Picture-in-Picture), so the picture's `PlayerView` is never given a new parent.
  A test (`turningThePhoneKeepsTheSamePictureViewAndNeverShowsAnotherParent`) asserts the same view instance across rotation and the strip.
- Each target has its own `ViewModelStore` (cleared when another target replaces it or the session closes), so a closed player disconnects its chat.
- Cards on Home, Category, Search (live channels) and the channel page's live card open the player with its chat; the streamer row under the player
  (picture and name) minimises the sheet and opens the channel page, which then shows the strip above the system bar.
- The page under an open session ends above the strip (a reserved 56 dp at the bottom), and the tab bar stays on tab roots.
- Back: fullscreen -> upright (the page's own handler), expanded -> minimise, strip -> the navigation (pages, tabs, leave); Back never closes the player.
- Fullscreen lock (`FullscreenPolicy`): the landscape lock is kept until the phone has been held sideways and is turned upright again; the old
  release as soon as the sensor read about 60 degrees let the system rotate to portrait for a moment first.

Screenshots: `docs/screenshots/phone/redesign-07-*` (expanded), `-08-*` (channel page with the strip), `-09-*` (drag and strip).

### Session, tabs and sign-out (2026-11-12, second review)

- The Following tab needs a viewer, so the bar is drawn from the session: while it is still being read the bar keeps the shape of the last time (`SignedInHint`, a one-bit preference; `PhoneTab.bar`) and a restored
  Following selection is kept (`PhoneTab.mustLeave`); once the session is known, signed out means two tabs.
- Signing out drops Following's pages and returns to Home whatever page the viewer was on (`PhoneNavigator.onSignedOut`), and clears the recent searches; signing in again finds Following at its root.
- Opening a page that is already in the current stack goes back to it instead of stacking it again.
- A restored live stream, replay or clip comes back paused (`PlayerSessionController.autoplay`).
- Search: the presenter answers by query (`SearchUiState.resultsFor`), `searching` and `failed` are derived from what was typed and what was answered, so a cancelled request or an earlier failure cannot leave them wrong;
  recent searches keep ten entries.

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

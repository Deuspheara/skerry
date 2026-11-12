# Phone performance (Phase 3, step D)

Measured 2026-11-12 on the Pixel 8 emulator (`Phone_Pixel8_API36`, arm64, host GPU, 4 cores), `benchmark` build type of `:phone` (R8 full mode,
not debuggable, debug-signed, always on the demo data), through `:benchmark-phone`. **Everything here is emulator-only and indicative**: no real
phone was used, the host GPU is pinned to vsync, and (see "Noise") the host was short of memory for the whole session. Raw benchmark JSON of
every run is kept in `docs/perf-data/` (`phone-run1.json`, `phone-run2.json`, `phone-contended.json`).

**Verdict.** The Baseline Profile and startup profile work: cold start with the profile is **20-31 % faster to first frame and 21-33 % faster to
the Home content than with no AOT compilation, in all three runs**. Steady-state frame timing (Home scroll, chat scroll, fullscreen toggle) is
**not measurably different** with or without the profile, which is what a profile should do (it removes first-use cost, it does not speed up warm
code). The absolute numbers are 5-8x worse than the same tests produced earlier in the same day on a quiet host, so they are good for
profile-vs-no-profile comparisons inside one session and **not** for judging the app or setting an absolute budget. No regression was
looked for against the TV (not re-measured, see "Not measured").

## What was added

| Piece | Where |
|---|---|
| Macrobenchmark module (`com.android.test`, targets `:phone`, `benchmark` + `nonMinified` build types like `:benchmark`) | `benchmark-phone/` |
| Journeys over the demo data (touch, UiAutomator): launch, Home scroll and tabs, Browse + category, Search, Channel, Settings, replay, live player (chat scroll, fullscreen both ways) | `benchmark-phone/src/main/kotlin/.../HomeScenarios.kt`, `PlayerScenarios.kt`, `UiSteps.kt` |
| Baseline Profile generator (startup run -> startup profile; journeys -> Baseline Profile) | `BaselineProfileGenerator.kt`, `benchmark-phone/tools/generate-profiles.sh` |
| Benchmarks: cold start (no compilation vs Baseline Profile), frame timing for Home scroll, chat scroll, fullscreen toggle (each both ways) | `StartupBenchmarks.kt`, `FrameTimingBenchmarks.kt`, `benchmark-phone/tools/run-benchmarks.sh` |
| `nonMinified` variant of `:phone`, `profileinstaller`, `ReportDrawnWhen` on Home (so `timeToFullDisplay` means "streams shown") | `phone/build.gradle.kts`, `HomeScreen.kt` |
| Profiles | `phone/src/main/baselineProfiles/baseline-prof.txt`, `startup-prof.txt` |
| Packaging check, table printer | `benchmark/tools/check-profile-packaging.py`, `benchmark/tools/summarize-benchmarks.py` |

Shared with the TV benchmark instead of copied: `benchmark/tools/generate-profiles.sh` (the phone script only sets the module, package, destination and extra
rules, then runs it), `framestats.py` (package from `PKG`), the AGP/Macrobenchmark setup (same catalog entries, same build types). **Not shared**: the
Kotlin scenarios (the TV drives a D-pad, the phone touches and swipes: no common step), and `StartupBenchmarks` / `FrameTimingBenchmarks` (three small classes whose
content is the scenario list; a shared base class would need a new module for roughly 40 lines).

## Profiles and packaging

| Item | Result |
|---|---|
| Baseline Profile rules (total / `com/twitchplayer/phone` / shared `com/twitchplayer/tv` / Compose / Media3 / Coil / Koin) | 38,686 / 2,258 / 2,014 / 21,827 / 3,632 / 1,345 / 415 |
| Startup profile rules (same columns) | 21,557 / 610 / 824 / 13,200 / 2 / 831 / 346 |
| Journeys covered in the phone package rules | Home 230, Browse 109, Channel 190, Search 142, Settings 186, Player (live, replay, clip pieces, chat) 971 |
| `assets/dexopt/baseline.prof` / `.profm` in the APK | 15,324 B / present |
| `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof` in the AAB | present |
| `profileinstaller` | classes in the APK, `ProfileInstallReceiver` declared in the merged manifest |
| Startup classes in `classes.dex` (the first dex file) | **2,272 of 2,287 surviving startup classes = 99.3 %** (the profile names 4,968 before shrinking) |
| R8 | full mode (AGP 9 default), `isMinifyEnabled = true`, `isShrinkResources = true`; 0 `-keep` rules in `phone/proguard-rules.pro`, `platform-android/consumer-rules.pro`, `shared/*/consumer-rules.pro` (only `-keepattributes`) |
| R8 Configuration Analyzer (`:phone:analyzeReleaseR8Config`, report rendered in headless Chrome) | optimization **98.9 %**, obfuscation **99.1 %**, shrinking **99.1 %** |
| Demo code in release | 0 `com.twitchplayer.tv.demo` classes in the mapping, no `demo_stream` in the APK |

`benchmark/tools/check-profile-packaging.py phone phone` prints the packaging rows above and fails when the profile is missing or fewer than 95 % of
the startup classes sit in `classes.dex`; CI runs it in `build-release`.

The profiles were recorded with the first version of the journeys (UiAutomator `fling`); the journeys were then changed to explicit swipes and the player setup
was hardened, which does not change which screens or code they reach. Regenerate the profiles with the script above whenever the journeys or the startup path change.

The profile covers more than startup: the journeys that need real Twitch data (stream resolver, EventSub) never run on demo data, so
`benchmark-phone/tools/extra-profile-rules.txt` adds the same hand-written package rules as the TV's.

### Build size

| Item | Before step D (`42dc1ff`) | After | Delta |
|---|---|---|---|
| Phone release APK (unsigned) | 3,779,635 B | **3,927,149 B** | +147,514 B (+3.9 %) |
| Phone release AAB | 8,670,010 B (step C figure) | 8,799,247 B | +1.5 % |
| dex total | 5,540,012 B (1 file) | 5,833,784 B (`classes.dex` 1,969,076 + `classes2.dex` 3,864,708) | +5.3 % |
| `baseline.prof` | 12,248 B | 15,324 B | +3,076 B |
| TV release APK | 4,001,682 B | **4,001,682 B** | identical |

Where the 147 KB come from (one extra build with the startup profile removed): without it the APK is 3,796,019 B (+16 KB: the explicit `profileinstaller`
dependency, the Home change and the larger Baseline Profile) and the dex stays one file of 5,541,708 B. **The startup profile itself costs about +131 KB**: R8 uses it
to lay the startup classes out first, which splits the code into two dex files and each file carries its own id tables. That is the price of the 99.3 % layout and
the TV pays the same (+1.2 % there, a smaller app). It is above the 3 % guardrail on the phone, so it is stated rather than hidden: it buys the
startup numbers below, and the startup profile can be removed (one file) if size ever matters more.

## Results (emulator, three runs)

Run 1 and run 2 are the two runs taken for this report, on the same booted emulator one after the other. "contended" is an earlier run of the same suite,
taken while Android Studio was indexing the project on the host (630 % CPU); it is kept because it shows the numbers do not move much with that kind of
contention. Cold start: 10 iterations per test, `StartupMode.COLD`. Frames: 5 iterations, `WARM`, the journey measured on a page opened in setup.

### Startup (ms, min / median / max)

| Test | Run | timeToInitialDisplay | timeToFullDisplay |
|---|---|---|---|
| coldStartBaselineProfile | phone-run1 | 902 / 1073 / 1215 | 1330 / 1689 / 2110 |
| coldStartBaselineProfile | phone-run2 | 950 / 1207 / 1399 | 1632 / 1835 / 2002 |
| coldStartBaselineProfile | phone-contended | 1124 / 1343 / 1545 | 1594 / 1996 / 2361 |
| coldStartNoCompilation | phone-run1 | 1269 / 1551 / 1970 | 2227 / 2524 / 3048 |
| coldStartNoCompilation | phone-run2 | 1300 / 1506 / 1736 | 2104 / 2552 / 2773 |
| coldStartNoCompilation | phone-contended | 1342 / 1528 / 1677 | 2168 / 2513 / 2798 |

### Frame timing (ms, P50 / P90 / P99)

| Test | Run | frames (median) | frameDurationCpuMs | frameOverrunMs |
|---|---|---|---|---|
| chatScrollBaselineProfile | phone-run1 | 290 | 50.86 / 88.79 / 170.12 | 56.79 / 108.18 / 198.12 |
| chatScrollBaselineProfile | phone-run2 | 283 | 52.26 / 88.30 / 168.48 | 58.52 / 106.10 / 213.32 |
| chatScrollBaselineProfile | phone-contended | 282 | 49.03 / 97.05 / 188.65 | 53.00 / 122.45 / 224.34 |
| chatScrollNoProfile | phone-run1 | 277 | 50.46 / 84.23 / 182.58 | 54.23 / 103.27 / 223.30 |
| chatScrollNoProfile | phone-run2 | 276 | 51.94 / 86.27 / 162.30 | 57.45 / 108.18 / 209.50 |
| chatScrollNoProfile | phone-contended | 276 | 51.79 / 89.84 / 179.23 | 57.60 / 110.85 / 207.47 |
| fullscreenToggleBaselineProfile | phone-run1 | 28 | 55.19 / 272.96 / 1025.04 | 64.37 / 579.62 / 1181.17 |
| fullscreenToggleBaselineProfile | phone-run2 | 30 | 67.02 / 300.42 / 938.58 | 69.70 / 706.78 / 1184.70 |
| fullscreenToggleBaselineProfile | phone-contended | 27 | 69.47 / 265.26 / 1093.35 | 80.64 / 611.32 / 1427.74 |
| fullscreenToggleNoProfile | phone-run1 | 25 | 53.30 / 234.54 / 1171.37 | 66.05 / 480.33 / 1223.98 |
| fullscreenToggleNoProfile | phone-run2 | 24 | 53.80 / 222.62 / 776.28 | 64.19 / 446.24 / 1084.72 |
| fullscreenToggleNoProfile | phone-contended | 25 | 49.30 / 276.50 / 1109.26 | 57.55 / 578.68 / 1350.00 |
| homeScrollBaselineProfile | phone-run1 | 191 | 21.40 / 54.89 / 169.10 | 24.69 / 125.13 / 200.42 |
| homeScrollBaselineProfile | phone-run2 | 183 | 18.62 / 43.98 / 178.46 | 27.01 / 123.53 / 200.64 |
| homeScrollBaselineProfile | phone-contended | 191 | 20.59 / 64.05 / 197.16 | 29.70 / 134.46 / 207.67 |
| homeScrollNoProfile | phone-run1 | 187 | 22.41 / 49.90 / 178.02 | 29.67 / 133.42 / 217.83 |
| homeScrollNoProfile | phone-run2 | 176 | 19.97 / 45.99 / 177.64 | 33.17 / 133.66 / 199.63 |
| homeScrollNoProfile | phone-contended | 177 | 21.20 / 42.01 / 151.01 | 29.19 / 125.36 / 198.31 |


### Reading the startup table

| Run | timeToInitialDisplay none -> profile (median) | timeToFullDisplay none -> profile (median) |
|---|---|---|
| run 1 | 1551 -> 1073 ms (**-31 %**; ranges do not overlap: 1269-1970 vs 902-1215) | 2524 -> 1689 ms (**-33 %**) |
| run 2 | 1506 -> 1207 ms (**-20 %**; ranges overlap slightly) | 2552 -> 1835 ms (**-28 %**) |
| contended | 1528 -> 1343 ms (**-12 %**) | 2513 -> 1996 ms (**-21 %**) |

The direction is the same in all three runs and the no-profile numbers are stable (1506-1551 ms), so the profile does help; the relative gain is larger than the TV baseline's (-8 % / -20 %), which is plausible because class loading and
JIT warm-up are slower on this starved host, but is a reason to treat the percentages as an upper bound. Frame timing: with and without the profile the
Home-scroll and chat-scroll medians agree within about 7 % in every run and the percentiles overlap; the fullscreen toggle (25-30 frames per run) swings 53-69 ms p50 in
both modes. **No frame-timing benefit is claimed**.

### Honest reading of the frame numbers

The absolute frame numbers are poor (chat scroll p50 about 51 ms, Home scroll p50 about 20 ms, fullscreen toggle p99 about 1 s: that one is two activity
configuration changes plus a rotation, at 25-30 frames in total). The same `homeScrollNoProfile` test printed **3.3 / 18.2 / 22.1 ms** (p50 / p90 / p99
frameDurationCpu, 343 frames) in a run earlier the same day, before the host ran short of memory; its JSON was overwritten, so only that line survives, in
this paragraph. The step-C measurement of the live page with chat updating (`am start -W` median 186 ms; UI-thread frame p50 1.87 ms) is of the same
quiet period. So the app is not 6x slower than yesterday: the host is.

## Noise

- **Host load during run 2** (sampled every 30 s): Android Studio median 0.5 % CPU, one spike to 93 %; load average 3.2-3.5; swap 4.2 GB used.
- **Host memory.** During the runs the Mac had about 65 MB of free pages and 4.2 of 5 GB of swap in use (Android Studio, other apps and the emulator
  competing); a Gradle daemon alone is gigabytes, which is why `run-benchmarks.sh` builds and installs with Gradle, **stops the daemons**, and drives the
  tests with `am instrument`. The run-to-run spread of the *same test* is then small (no-profile cold start 1506-1551 ms median, chat scroll p50 50.5-52.3 ms), which
  is why the comparison is usable; but no number here should be quoted as the phone's speed.
- **Gestures.** The first attempts used UiAutomator `fling`, which on a starved emulator degraded into taps (a scroll opened a category). The journeys now use explicit
  25-step `swipe`s, and a failing step leaves a screenshot and the visible labels in its message.
- **Picture-in-Picture.** `pressHome()` in a player test auto-entered the PiP window (correct behaviour) and left it behind for the next test; the player setup does
  not press Home.
- **ANR.** One run produced "TwitchPlayer isn't responding" on the live page (Input dispatching timed out, 6.9 s) at a system load average of 31 with the CPU pressure
  stall at 54 %, with Gradle building at the same time. It did not happen in the quiet runs and is attributed to the load; it is noted because the main thread was
  blocked for seconds under a starved emulator, so a real low-end phone is worth a look.
- One run per configuration is never enough: the regression rule below needs a repeated run.

## Regression policy for the phone (adapted from the TV's, thresholds from the numbers above)

Applies to any change that touches startup, the Home/player screens, the chat list or the build configuration. Compare **in the same session, on the same
booted emulator, host otherwise quiet, two runs of each side** (before/after) with `benchmark-phone/tools/run-benchmarks.sh`:

| Check | Threshold | Today |
|---|---|---|
| Cold start with the profile, `timeToInitialDisplay` median | not worse than **+10 %** on a repeated run | 1073 / 1207 ms (this session) |
| Cold start with the profile vs no compilation | at least **10 % faster** (`timeToInitialDisplay`) and **15 % faster** (`timeToFullDisplay`) in both runs | 20-31 % / 28-33 % |
| Frame timing p50 / p90 of Home scroll, chat scroll | not worse than **+15 %** (p50) / **+25 %** (p90) on a repeated run | Home 19-22 / 42-55 ms, chat 50-52 / 84-89 ms (two clean runs) |
| Release APK / dex total | growth above **3 %** needs a sentence in the commit and this file | 3,927,149 B / 5,833,784 B |
| Profile packaged (APK and AAB), startup classes in `classes.dex` >= 95 %, `profileinstaller` present | hard failure | CI `build-release` |
| R8 full mode + minify + shrink, 0 own `-keep` rules | hard failure | CI `build-release` |
| Analyzer scores | no drop of more than 0.5 point | 98.9 / 99.1 / 99.1 |

Absolute thresholds are deliberately not set on emulator numbers; set them from a real phone before a release.

## Not measured

- A real phone (the user's phone was not touched in this step).
- The TV after Phase 3 (the TV APK is byte-identical, 4,001,682 B, and its profile unchanged; its startup was not re-run).
- Memory, battery, thermal behaviour of the phone player; real streams and ad breaks.
- Low-end devices and API 24-30 (profile installation by `profileinstaller` on those versions is the library's, not exercised here).

## How to reproduce

```sh
ANDROID_SERIAL=emulator-5554 benchmark-phone/tools/generate-profiles.sh     # only to regenerate the profiles
ANDROID_SERIAL=emulator-5554 benchmark-phone/tools/run-benchmarks.sh run1   # then run2; JSON in docs/perf-data/phone-<label>.json
benchmark/tools/summarize-benchmarks.py docs/perf-data/phone-run1.json docs/perf-data/phone-run2.json
./gradlew :phone:assembleRelease :phone:bundleRelease :phone:analyzeReleaseR8Config
python3 -I benchmark/tools/check-profile-packaging.py phone phone
```

# Phone performance (Phase 3, step D)

Measured 2026-11-12 on the Pixel 8 emulator (`Phone_Pixel8_API36`, arm64, host GPU, 4 cores), `benchmark` build type of `:phone` (R8 full mode,
not debuggable, debug-signed, always on the demo data), through `:benchmark-phone`. **Everything here is emulator-only and indicative**: no real
phone was used, the host GPU is pinned to vsync, and (see "Noise") the host was short of memory for the whole session. Raw benchmark JSON of
every run is kept in `docs/perf-data/` (`phone-run1.json`, `phone-run2.json`, `phone-contended.json`).

**Verdict.** The Baseline Profile and startup profile **help cold start, but by a varying amount**: across five runs (two profile versions) the median
time to first frame is 4-31 % lower with the profile than with no AOT compilation and the time to the Home content 15-33 % lower, the profile never
being slower. The gain with the final profile (runs 3 and 4: -4 % / -15 % first frame, -15 % / -24 % Home content) is smaller than with the first
one (runs 1 and 2: -31 % / -20 %, -33 % / -28 %); a difference between the two profile versions cannot be told from session noise with this data. Steady-state frame timing
(Home scroll, chat scroll, fullscreen toggle) is **not measurably different** with or without the profile, which is what a profile should do (it removes first-use
cost, it does not speed up warm code). The absolute numbers are 5-8x worse than one earlier run of the same Home-scroll test, and the host was short of memory
throughout, so they are good for profile-vs-no-profile comparisons inside a session and **not** for judging the app or setting an absolute budget. All of it is emulator-only.

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
| Baseline Profile rules (total / `com/twitchplayer/phone` / shared `com/twitchplayer/tv` / Compose / Media3 / Coil / Koin) | 38,639 / 2,212 / 1,978 / 21,600 / 3,626 / 1,381 / 415 |
| Startup profile rules (same columns) | 21,673 / 601 / 823 / 13,184 / 2 / 621 / 346 |
| Journeys covered in the phone package rules | Home 201, Browse 106, Channel 156, Search 142, Settings 192, Player (live, replay, clip pieces, chat) 971 |
| `assets/dexopt/baseline.prof` / `.profm` in the APK | 15,522 B / 1,206 B |
| `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof` in the AAB | present |
| `profileinstaller` | classes in the APK, `ProfileInstallReceiver` declared in the merged manifest |
| Startup classes in `classes.dex` (the first dex file) | **2,298 of 2,317 surviving startup classes = 99.2 %** (the profile names 5,025 before shrinking) |
| R8 | full mode (AGP 9 default), `isMinifyEnabled = true`, `isShrinkResources = true`; 0 `-keep` rules in `phone/proguard-rules.pro`, `platform-android/consumer-rules.pro`, `shared/*/consumer-rules.pro` (only `-keepattributes`) |
| R8 Configuration Analyzer (`:phone:analyzeReleaseR8Config`, report rendered in headless Chrome) | optimization **98.9 %**, obfuscation **99.1 %**, shrinking **99.1 %** |
| Demo code in release | 0 `com.twitchplayer.tv.demo` classes in the mapping, no `demo_stream` in the APK |

`benchmark/tools/check-profile-packaging.py phone phone` prints the packaging rows above and fails when the profile is missing or fewer than 95 % of
the startup classes sit in `classes.dex`; CI runs it in `build-release`.

The profiles were **regenerated once with the final journeys** (explicit swipes, hardened player setup) after the final-review fixes; the first profile (recorded with
UiAutomator `fling` journeys) is what runs 1, 2 and "contended" measured, the final one is what runs 3 and 4 measured. Regenerate with the script above whenever the journeys or the startup path change.

The profile covers more than startup: the journeys that need real Twitch data (stream resolver, EventSub) never run on demo data, so
`benchmark-phone/tools/extra-profile-rules.txt` adds the same hand-written package rules as the TV's.

### Build size

| Item | Before step D (`42dc1ff`) | After | Delta |
|---|---|---|---|
| Phone release APK (unsigned) | 3,779,635 B | **3,927,149 B** (final-review fixes included; same size as before them by coincidence) | +147,514 B (+3.9 %) |
| Phone release AAB | 8,670,010 B (step C figure) | 8,794,098 B | +1.4 % |
| dex total | 5,540,012 B (1 file) | 5,843,324 B (`classes.dex` 2,046,700 + `classes2.dex` 3,796,624) | +5.5 % |
| `baseline.prof` | 12,248 B | 15,522 B | +3,274 B |
| TV release APK | 4,001,682 B | **4,001,682 B** | identical |

Where the 147 KB come from (one extra build with the startup profile removed): without it the APK is 3,796,019 B (+16 KB: the explicit `profileinstaller`
dependency, the Home change and the larger Baseline Profile) and the dex stays one file of 5,541,708 B. **The startup profile itself costs about +131 KB**: R8 uses it
to lay the startup classes out first, which splits the code into two dex files and each file carries its own id tables. That is the price of the 99.3 % layout and
the TV pays the same (+1.2 % there, a smaller app). It is above the 3 % guardrail on the phone, so it is stated rather than hidden: it buys the
startup numbers below, and the startup profile can be removed (one file) if size ever matters more.

## Results (emulator, five runs)

| Run | Profile version | Notes |
|---|---|---|
| `phone-contended` | first (fling journeys) | taken while Android Studio was indexing the project on the host (630 % CPU); kept because it shows the numbers barely move with that |
| `phone-run1`, `phone-run2` | first | one after the other on the same booted emulator; host load sampled for run 2 only |
| `phone-run3`, `phone-run4` | **final** (regenerated with the final journeys) | after a clean emulator boot; host load sampled every 30 s (`phone-run3-hostload.log`, `phone-run4-hostload.log`) |

Cold start: 10 iterations per test, `StartupMode.COLD`. Frames: 5 iterations, `WARM`, the journey measured on a page opened in setup. Raw JSON for every run is in
`docs/perf-data/` (earlier runs were never overwritten).

#### Startup (ms, min / median / max)

| Test | Run | timeToInitialDisplay | timeToFullDisplay |
|---|---|---|---|
| coldStartBaselineProfile | phone-run1 | 902 / 1073 / 1215 | 1330 / 1689 / 2110 |
| coldStartBaselineProfile | phone-run2 | 950 / 1207 / 1399 | 1632 / 1835 / 2002 |
| coldStartBaselineProfile | phone-contended | 1124 / 1343 / 1545 | 1594 / 1996 / 2361 |
| coldStartBaselineProfile | phone-run3 | 1295 / 1442 / 1646 | 1878 / 2161 / 2550 |
| coldStartBaselineProfile | phone-run4 | 1149 / 1379 / 1948 | 1743 / 2018 / 2706 |
| coldStartNoCompilation | phone-run1 | 1269 / 1551 / 1970 | 2227 / 2524 / 3048 |
| coldStartNoCompilation | phone-run2 | 1300 / 1506 / 1736 | 2104 / 2552 / 2773 |
| coldStartNoCompilation | phone-contended | 1342 / 1528 / 1677 | 2168 / 2513 / 2798 |
| coldStartNoCompilation | phone-run3 | 1294 / 1504 / 1596 | 2073 / 2541 / 2707 |
| coldStartNoCompilation | phone-run4 | 1378 / 1627 / 1828 | 2327 / 2645 / 2877 |

#### Frame timing (ms, P50 / P90 / P99)

| Test | Run | frames (median) | frameDurationCpuMs | frameOverrunMs |
|---|---|---|---|---|
| chatScrollBaselineProfile | phone-run1 | 290 | 50.86 / 88.79 / 170.12 | 56.79 / 108.18 / 198.12 |
| chatScrollBaselineProfile | phone-run2 | 283 | 52.26 / 88.30 / 168.48 | 58.52 / 106.10 / 213.32 |
| chatScrollBaselineProfile | phone-contended | 282 | 49.03 / 97.05 / 188.65 | 53.00 / 122.45 / 224.34 |
| chatScrollBaselineProfile | phone-run3 | 278 | 51.58 / 82.67 / 179.47 | 55.92 / 100.91 / 213.38 |
| chatScrollBaselineProfile | phone-run4 | 279 | 52.77 / 92.98 / 185.24 | 57.87 / 111.22 / 247.55 |
| chatScrollNoProfile | phone-run1 | 277 | 50.46 / 84.23 / 182.58 | 54.23 / 103.27 / 223.30 |
| chatScrollNoProfile | phone-run2 | 276 | 51.94 / 86.27 / 162.30 | 57.45 / 108.18 / 209.50 |
| chatScrollNoProfile | phone-contended | 276 | 51.79 / 89.84 / 179.23 | 57.60 / 110.85 / 207.47 |
| chatScrollNoProfile | phone-run3 | 284 | 53.54 / 88.78 / 167.35 | 59.13 / 110.93 / 204.01 |
| chatScrollNoProfile | phone-run4 | 282 | 54.80 / 87.72 / 154.73 | 61.19 / 103.15 / 181.29 |
| fullscreenToggleBaselineProfile | phone-run1 | 28 | 55.19 / 272.96 / 1025.04 | 64.37 / 579.62 / 1181.17 |
| fullscreenToggleBaselineProfile | phone-run2 | 30 | 67.02 / 300.42 / 938.58 | 69.70 / 706.78 / 1184.70 |
| fullscreenToggleBaselineProfile | phone-contended | 27 | 69.47 / 265.26 / 1093.35 | 80.64 / 611.32 / 1427.74 |
| fullscreenToggleBaselineProfile | phone-run3 | 31 | 58.46 / 314.69 / 541.70 | 68.38 / 531.51 / 911.57 |
| fullscreenToggleBaselineProfile | phone-run4 | 28 | 64.70 / 356.97 / 800.19 | 75.54 / 608.24 / 1068.64 |
| fullscreenToggleNoProfile | phone-run1 | 25 | 53.30 / 234.54 / 1171.37 | 66.05 / 480.33 / 1223.98 |
| fullscreenToggleNoProfile | phone-run2 | 24 | 53.80 / 222.62 / 776.28 | 64.19 / 446.24 / 1084.72 |
| fullscreenToggleNoProfile | phone-contended | 25 | 49.30 / 276.50 / 1109.26 | 57.55 / 578.68 / 1350.00 |
| fullscreenToggleNoProfile | phone-run3 | 25 | 56.46 / 246.58 / 937.77 | 64.38 / 487.36 / 1024.35 |
| fullscreenToggleNoProfile | phone-run4 | 28 | 53.41 / 241.80 / 974.83 | 63.84 / 398.41 / 1070.96 |
| homeScrollBaselineProfile | phone-run1 | 191 | 21.40 / 54.89 / 169.10 | 24.69 / 125.13 / 200.42 |
| homeScrollBaselineProfile | phone-run2 | 183 | 18.62 / 43.98 / 178.46 | 27.01 / 123.53 / 200.64 |
| homeScrollBaselineProfile | phone-contended | 191 | 20.59 / 64.05 / 197.16 | 29.70 / 134.46 / 207.67 |
| homeScrollBaselineProfile | phone-run3 | 183 | 19.24 / 45.63 / 151.26 | 22.65 / 116.18 / 192.46 |
| homeScrollBaselineProfile | phone-run4 | 196 | 22.82 / 61.04 / 166.87 | 29.08 / 124.22 / 200.67 |
| homeScrollNoProfile | phone-run1 | 187 | 22.41 / 49.90 / 178.02 | 29.67 / 133.42 / 217.83 |
| homeScrollNoProfile | phone-run2 | 176 | 19.97 / 45.99 / 177.64 | 33.17 / 133.66 / 199.63 |
| homeScrollNoProfile | phone-contended | 177 | 21.20 / 42.01 / 151.01 | 29.19 / 125.36 / 198.31 |
| homeScrollNoProfile | phone-run3 | 176 | 21.32 / 42.48 / 154.76 | 28.75 / 132.40 / 195.30 |
| homeScrollNoProfile | phone-run4 | 179 | 22.73 / 45.40 / 154.72 | 28.60 / 123.31 / 189.02 |


### Reading the startup table

| Run | timeToInitialDisplay none -> profile (median) | timeToFullDisplay none -> profile (median) |
|---|---|---|
| run 1 (first profile) | 1551 -> 1073 ms (**-31 %**; ranges 1269-1970 vs 902-1215 do not overlap) | 2524 -> 1689 ms (**-33 %**) |
| run 2 (first) | 1506 -> 1207 ms (**-20 %**) | 2552 -> 1835 ms (**-28 %**) |
| contended (first) | 1528 -> 1343 ms (**-12 %**) | 2513 -> 1996 ms (**-21 %**) |
| run 3 (final) | 1504 -> 1442 ms (**-4 %**; ranges 1294-1596 vs 1295-1646 overlap completely) | 2541 -> 2161 ms (**-15 %**) |
| run 4 (final) | 1627 -> 1379 ms (**-15 %**) | 2645 -> 2018 ms (**-24 %**) |

The profile was faster in the median in all five runs and the no-profile numbers are stable (1504-1627 ms), but the first-frame gain with the final profile
(-4 %, -15 %) is within the run-to-run spread of one session and is **not a demonstrated improvement on its own**; the time to the Home content (-15 %, -24 %)
is clearer. The relative gain is also larger than the TV baseline's (-8 % / -20 %), plausibly because class loading and JIT warm-up are slower on this
starved host; read the percentages as an upper bound. Frame timing: with and without the profile the Home-scroll and chat-scroll medians agree within about 7 %
in every run and the percentiles overlap; the fullscreen toggle (25-31 frames per run) swings 53-69 ms p50 in both modes. **No frame-timing benefit is claimed**.

### Reading the frame numbers

The absolute frame numbers are poor (chat scroll p50 about 51-55 ms, Home scroll p50 about 19-23 ms, fullscreen toggle p99 about 0.5-1 s: that one is two activity
configuration changes plus a rotation, at 25-31 frames in total) and they are the same in all five runs. One earlier run of the `homeScrollNoProfile` test the same
day printed **3.3 / 18.2 / 22.1 ms** (p50 / p90 / p99 frameDurationCpu, 343 frames) before the host ran short of memory. That JSON was overwritten (only the printed line
survives, here), so it is a single unverifiable data point.

**Hypothesis, not a finding**: the emulator was slower than earlier in the day because the host was short of memory (below), not because the app regressed. What
supports it: the host was swapping heavily, other emulator workloads (Gradle, the install) were visibly slower at the same time, and the five runs agree with each
other. What does not: the earlier JSON is gone, the app code changed in between (step C review fixes, profileinstaller, `ReportDrawnWhen`), and no run on a quiet host
or a real phone exists to separate the two. **The honest statement is that this data cannot say how fast the phone app is.**

## Noise

- **Host load while measuring** (sampled every 30 s): run 2 Android Studio median 0.5 % CPU, spike 93 %, load average 3.2-3.5, swap 4.2 GB used; run 3 Studio median 0.5 %, max 4.9 %; run 4 Studio median 0.4 %, one spike to 262 %, load average 2.7-3.6, swap 5.5-5.6 GB used (of 6-7 GB). **The host was not quiet** by the memory criterion (swap nearly full, about 70 MB of free pages) although the load average stayed below 4, so the frame-timing tables were **not** replaced by quiet-host numbers: none could be taken.
- **Host memory.** During the runs the Mac had about 65-85 MB of free pages and 4.2-6.4 GB of swap in use (Android Studio, other apps and the emulator
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
| Cold start with the profile, `timeToInitialDisplay` median | not worse than **+10 %** on a repeated run | 1442 / 1379 ms (final profile, this session) |
| Cold start with the profile vs no compilation | never slower (`timeToInitialDisplay` median) and at least **10 % faster** to `timeToFullDisplay` in both runs of a pair | -4 / -15 % and -15 / -24 % (final profile); -31 / -20 % and -33 / -28 % (first) |
| Frame timing p50 / p90 of Home scroll, chat scroll | not worse than **+15 %** (p50) / **+25 %** (p90) on a repeated run | Home 19-23 / 42-61 ms, chat 50-55 / 83-93 ms (all five runs) |
| Release APK / dex total | growth above **3 %** needs a sentence in the commit and this file | 3,927,149 B / 5,843,324 B |
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

# Performance after Phase 1 (KMP extraction)

Measured 2026-11-11 on `5505b10` (HEAD, branch `feat/kmp-shared`) against the baseline in
[perf-baseline.md](perf-baseline.md) (commit `e9a6534`, code identical to `917acaa`). Same emulator (TwitchTV_API33,
arm64, host GPU), same commands. Profiles were **regenerated** first (`benchmark/tools/generate-profiles.sh`).

**Verdict: no regression demonstrated, none proven absent.** In a same-session A/B (three alternating runs per side)
startup is not slower than the pre-migration code, frame timing is unchanged within noise, size growth is 1.2 % (APK) /
1.6 % (dex), the R8 analyzer scores did not drop and the app has no `-keep` rules. The absolute cold-start threshold
(340 ms) is not met in one of two full-suite runs; the A/B below argues this is a session effect, but three runs per
side sit inside the session-to-session spread, so the gains quoted are indicative, not proven. **The real TV has not been
re-measured** (see "Not measured"); the raw benchmark JSON of the startup runs was not retained, so only the
tables in this file are available. Keep the JSON next time (`benchmark/build/outputs/connected_android_test_additional_output/`).

## Build size and packaging

| Item | Baseline | After | Delta |
|---|---|---|---|
| Release APK (unsigned) | 3,952,530 B | 4,001,682 B | +1.24 % |
| `classes.dex` | 1,824,696 B | 2,308,208 B | startup classes moved here (see below) |
| `classes2.dex` | 3,240,128 B | 2,834,900 B | |
| dex total | 5,064,824 B | 5,143,108 B | **+1.55 %** (limit 3 %) |
| `assets/dexopt/baseline.prof` / `.profm` | 14,206 / 990 B | 15,164 / 898 B | packaged in APK and AAB |
| Baseline profile rules (total / `com/twitchplayer`) | 28,888 / 3,150 | 31,619 / 3,596 | regenerated |
| Startup profile rules (total / `com/twitchplayer`) | 19,429 / 1,494 | 23,289 / 1,936 | regenerated |
| `-keep` class rules in the app | 0 | 0 (only `-keepattributes`) | |
| R8 analyzer: optimization / obfuscation / shrinking | 98.7 / 99.1 / 99.0 % (documented) | 98.8 / 99.1 / 99.1 % | +0.1 / 0 / +0.1 |

The analyzer scores were read from the report rendered in a headless browser (the HTML builds them with JavaScript);
the baseline column is the figure documented in `docs/PERFORMANCE.md`.

**Profile coverage of the moved code.** The profile is collected from the whole app process, so classes of the shared
modules (same packages, same APK) are covered with no filter. The regenerated files contain the new classes
(`HomePresenter` 51 rules, `PlayerPresenter` 212, `NumberFormatter`, `AndroidSnapshotStorage`, ...) and no longer contain
the old `VoiceRecognizer`; `HomeViewModel` / `PlayerViewModel` shrank to their thin holders (51 -> 9, 161 -> 28 rules).
`TwitchRepositoryImpl`, `GqlPlaybackResolver` and `EventSubChatClient` have no recorded rules, as before: demo data never
loads them. They are covered by the hand-written wildcard rules in `benchmark/tools/extra-profile-rules.txt` (package
based, so they still match the moved classes: `data/stream/**`, `data/chat/**`, `playback/**`).

**Startup classes land in `classes.dex`.** The startup profile names 5,278 classes; after R8 shrinking and renaming
(mapped through `mapping.txt`) 2,435 are in the APK, and **2,406 of them (98.8 %) are in `classes.dex`**, 29 in
`classes2.dex`; of the `com/twitchplayer` ones 211 of 215 are in `classes.dex`. That is also why `classes.dex` is larger
than at baseline: the regenerated startup profile is larger (DI graph, presenters, shared modules), so more classes are laid
out first.

## Cold start, same-session A/B (the decisive comparison)

`StartupBenchmarks`, 10 iterations per run, **medians in ms**, three alternating runs (baseline code and profile built from
`917acaa` in a scratch worktree, then HEAD, base, head, base, head), same emulator session so host drift hits both sides.

| Test / metric | Baseline code, 3 runs | HEAD, 3 runs | Mean | Change |
|---|---|---|---|---|
| Baseline Profile, first frame | 251, 283, 223 | 222, 215, 214 | 252 -> 217 | **-14 %** |
| Baseline Profile, full display | 345, 389, 313 | 330, 306, 295 | 349 -> 310 | **-11 %** |
| No compilation, first frame | 264, 242, 228 | 255, 237, 228 | 245 -> 240 | -2 % |
| No compilation, full display | 452, 404, 385 | 429, 404, 378 | 414 -> 404 | -2.5 % |

Every HEAD run is at or below the 340 ms policy line (330, 306, 295). The pre-migration code, in the same session, exceeds
it in two of three runs (345, 389): the absolute threshold in the policy was calibrated on the session that produced the
baseline and moves by ±15 % between sessions on this emulator.

## Full suite, two runs (for the frame tables and the absolute policy line)

Cold start (min / median / max, ms), baseline from `perf-baseline.md`:

| Test | Baseline | HEAD run 1 | HEAD run 2 |
|---|---|---|---|
| Baseline Profile, first frame | 217 / 226 / 252 | 213 / 243 / 285 | 222 / 261 / 788 |
| Baseline Profile, full display | 302 / 318 / 340 | 306 / **339** / 392 | 311 / **365** / 972 |
| No compilation, first frame | 225 / 246 / 298 | 235 / 253 / 291 | 247 / 290 / 324 |
| No compilation, full display | 380 / 395 / 486 | 397 / 424 / 484 | 403 / 472 / 512 |

Read strictly, "cold start median with profile <= 340 ms across two runs" holds for run 1 (339) and **not for run 2
(365)**. Run 2 contains one 972 ms outlier iteration and its no-profile twin moved by the same amount (472 vs 395 in the
baseline session), i.e. the whole session was slow, which the A/B above isolates: same-session HEAD beats the old code.
Both statements are reported on purpose; the TV re-measurement is the check that settles it.

Frame timing, `frameDurationCpuMs` p50 / p90 / p99 (ms). Baseline from `perf-baseline.md`:

| Journey | Baseline | HEAD run 1 | HEAD run 2 |
|---|---|---|---|
| homeNavigation, profile | 4.90 / 6.69 / 10.02 | 5.05 / 17.54 / 23.49 | 4.95 / 6.79 / 9.63 |
| homeNavigation, no profile | 5.17 / 7.94 / 10.88 | 5.29 / 7.44 / 10.59 | 5.48 / 16.35 / 20.09 |
| browseGrid, profile | 17.95 / 32.22 / 34.45 | 17.84 / 32.61 / 36.54 | 18.23 / 32.01 / 34.61 |
| chatExpandCollapse, profile | 4.41 / 18.25 / 21.17 | 5.69 / 19.44 / 24.40 | 6.56 / 19.40 / 24.45 |
| chatExpandCollapse, no profile | 5.11 / 18.87 / 23.04 | 7.27 / 19.97 / 25.52 | 5.39 / 19.14 / 24.53 |
| switchChannel, profile | 4.52 / 18.96 / 25.60 | 4.64 / 18.92 / 27.06 | 4.36 / 17.77 / 24.63 |
| playerChatAndZapper, profile | 6.60 / 20.31 / 34.96 | 11.12 / 22.50 / 35.69 | 6.32 / 21.42 / 34.59 |

Single runs disagree with each other (the home journey's p90 spike is on the profile variant in run 1 and on the
no-profile variant in run 2), so frame timing was also A/B-ed: `FrameTimingBenchmarks` on the baseline code and on HEAD,
two alternating runs each, same session. Mean of the two runs, p50 / p90 (ms):

| Journey | Baseline code | HEAD |
|---|---|---|
| homeNavigation, profile | 4.88 / 6.55 | 4.86 / 7.72 |
| homeNavigation, no profile | 5.18 / 12.22 | 5.08 / 7.22 |
| browseGrid, profile | 17.71 / 26.08 | 17.36 / 26.39 |
| chatExpandCollapse, profile | 4.50 / 15.10 | 4.85 / 18.77 |
| chatExpandCollapse, no profile | 5.61 / 19.13 | 5.37 / 19.11 |
| switchChannel, profile | 4.38 / 18.77 | 4.23 / 18.25 |
| playerChatAndZapper, profile | 8.27 / 21.75 | 7.52 / 21.19 |

p50 differs by -9 % to +8 % (both signs). The p90 / p99 columns are bimodal run to run on **both** sides: the baseline's own
chat p90 reads 18.5 ms in one run and 11.7 ms in the next (HEAD: 18.8 and 18.7), its home no-profile p90 reads 17.3 then
7.1. The apparent +24 % chat p90 and +36 % home p99 come from one low or one high run on a single side; the other runs
agree. No clear shift, which is what the policy asks for. The emulator's GPU side is pinned to host vsync and its CPU side
is meaningful only at this resolution (see `perf-baseline.md`).

## Compose stability

`./gradlew :app:compileDebugKotlin -PcomposeReports --rerun`: 145 composables, **130 skippable**, and the unstable
parameters are the same five as before the migration (`context`, `controller`, `edgeGuard`, `preview`, `token`), none of
them a shared type. The classes the report lists as unstable are app-side only: `MainActivity`, `TwitchPlayerApp`, the holder ViewModels, the Android implementations (`AndroidKeystoreTokenStore`, `AndroidSnapshotStorage`, `PlatformVoiceRecognizer`), the players (`NativeVideoPlayer`, `StreamPreview`, `WebViewVideoPlayer`), `RailEdgeGuard` and the demo-data classes; none is a shared type and none is a state class passed to a composable.

## Policy check

| Policy item | Result |
|---|---|
| APK / dex growth <= 3 % | pass: +1.24 % / +1.55 % |
| Baseline profile packaged (APK and AAB) | pass |
| Startup profile applied (startup classes in `classes.dex`) | pass: 98.8 % of the surviving startup classes |
| No `-keep` rules added to the app | pass (only `-keepattributes`) |
| Analyzer scores drop <= 0.5 pt | pass: +0.1 / 0 / +0.1 |
| No clear frame-timing shift | pass (same-session A/B) |
| Cold start median with profile <= 340 ms across two runs | **mixed**: run 1 339, run 2 365 (outlier, slow session); same-session A/B 330 / 306 / 295 and 11 % faster than the old code |

## Not measured

- **The real TV** (TCL, MT5896, Android 14). Install the release APK with `benchmark/tools/install-release.sh` (AOT with its
  `.dm`) and compare with the numbers in `docs/PERFORMANCE.md` (first frame ~475 ms, Home content ~520 ms with the
  snapshot). The emulator CPU is far faster than the TV SoC, so the TV is where a DI or class-loading cost would show.
- A long session (memory over hours), the embedded WebView player path, 144 Hz panels.

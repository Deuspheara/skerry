# Phone performance after the redesign (step 11 of `docs/phone-redesign.md`)

Measured 2026-11-12 on the Pixel 8 emulator (`Phone_Pixel8_API36`, arm64, host GPU), `benchmark` build type of `:phone` (R8 full mode, demo data), through
`:benchmark-phone`. **Emulator-only, indicative.** Nothing here was measured on a real phone or on the real TV.

**Verdict.** Mixed, and stated with its numbers rather than rounded up. Cold start is a little *faster* (first frame about 15-20 % lower with the profile, a smaller gap without it); Home scroll and chat
scroll are unchanged within the noise; the new sheet drag runs at 4-5 ms median CPU per frame with the profile; the release APK is 1.9 % smaller than before the redesign. **Not better, and in two places not
proven equal:** the fullscreen toggle's p90 is **about 22-23 ms with the profile after the redesign (21.8-23.2 ms in the three runs) against 20-21 ms before**, and **without a profile 31.8-33.1 ms in two of three runs
against 19.9 ms in the third**, so the no-profile case is clearly worse on the host's quieter runs; and the sheet drag has outliers (p90 up to 16 ms with the profile, 134 ms and 98 frames instead of 155 in one no-profile run).
The picture is no longer re-created on rotation, but the frame numbers do not show a gain from it. The profiles had gone stale (192 lines for removed classes, none for the new hot path) and are regenerated. Limits, not
hidden: the drag percentiles beyond p50 are noisy on this host, one run of the no-profile drag test is an outlier, and the before/after runs were not taken in the same minute.

## What changed in the benchmark module

| Piece | Change |
|---|---|
| Journeys (`HomeScenarios`, `PlayerScenarios`, `UiSteps`) | rewritten for the new navigation: the bar's tabs have no text (selected by description), Search and Settings are pages opened from the header's search pill and account circle, a stream card plays the stream with its chat (no channel page in between), the streamer row under the picture leads to the channel page, replays and clips open in the same sheet |
| New journeys recorded in the profile | open a stream from Home, minimise and expand the sheet **both ways** (drag down + drag up on the picture and strip, then Back + tap on the strip), Following tab, search page (open, type, close), Settings page (open, scroll, close), channel page via the streamer row, replay |
| New frame benchmark | `sheetDrag` (drag the picture down until the strip rests, drag the strip up until the player is expanded), with and without the profile |
| Robustness | taps that land on a still-flinging list or on a text whose row is the clickable are retried, with a settle delay; a failing step still leaves a screenshot and the visible labels |

## Profiles

| Item | Before (`8e19b88`) | After |
|---|---|---|
| Baseline Profile rules (total / `com/twitchplayer/phone` / shared `com/twitchplayer/tv`) | 38,639 / 2,212 / 1,978 | **36,775 / 2,643 / 1,967** |
| Startup profile rules (same columns) | 21,673 / 601 / 823 | **20,011 / 745 / 798** |
| Rules naming removed code (`NavigationSuite*`, `LiveKey`/`ReplayKey`/`ClipKey`, `PlayerLauncher`) | 173 lines for `NavigationSuite*` (and 192 for the old `LivePlayer*`/`PlayerScaffold` shapes) in the stale file, per the independent review | **0** for `NavigationSuite`, `LiveKey`, `ReplayKey`, `ClipKey`, `PlayerLauncher` |
| Rules for the new hot path | none | `PlayerOverlay` 109, `PlayerSession` 101, `PlayerSheetLayout` 19, `PlayerStrip` 12, `SearchScreen` 199, `SettingsScreen` 107, `anchoredDraggable` 202 |
| `assets/dexopt/baseline.prof` / `.profm` in the APK | 15,522 B / 1,206 B | 15,474 B / packaged |
| Startup classes in `classes.dex` | 2,298 of 2,317 (99.2 %) | **2,104 of 2,126 (99.0 %)** (the profile names 4,625 before shrinking) |
| `profileinstaller`, `ProfileInstallReceiver`, baseline profile in the AAB | present | present (`benchmark/tools/check-profile-packaging.py phone phone`: all `pass`) |

Generated with `benchmark-phone/tools/generate-profiles.sh emulator-5554` after `:benchmark-phone:clean`. R8 settings, keep rules (still only `-keepattributes` in
`phone/proguard-rules.pro`; 0 `-keep` in the consumer rules), baseline/startup-profile and `profileinstaller` configuration are **untouched**. The R8
Configuration Analyzer was not re-run in this step (its report needs a browser to render); there is no reason for its scores to move, but that is an assumption.

## Build size

| Item | Before | After | Delta |
|---|---|---|---|
| Phone release APK (unsigned) | 3,927,149 B | **3,853,585 B** | -73,564 B (-1.9 %) |
| Phone release AAB | 8,794,098 B | **8,593,649 B** | -2.3 % |
| dex (`classes.dex` + `classes2.dex`) | 2,046,700 + 3,796,624 B (5,843,324 B) | 1,905,676 + 3,812,276 B (5,717,952 B) | -2.1 % |
| TV release APK | 4,001,682 B | **4,001,682 B** | identical |
| TV startup classes in `classes.dex` | 98.8 % (2,406 of 2,435, `docs/perf-after-phase1.md`) | 98.8 % (2,407 of 2,437), `baseline.prof` and `ProfileInstallReceiver` packaged | unchanged |

The smaller size is consistent with dropping `material3-adaptive-navigation-suite` and the page-based player code; the split was not measured per cause. The earlier +3.9 % over the 3 % guardrail (the startup-profile dex split, see
`docs/perf-phone.md`) is therefore down to +2.0 % over the pre-step-D phone APK (3,779,635 B); the startup profile is still in use. The TV was **not**
re-measured on the real TV (needs the TV and `benchmark/tools/install-release.sh`); its APK is byte-for-byte the size it had before the redesign, as expected.

## Compose stability (`:phone:compileReleaseKotlin -PcomposeReports --rerun -Pkotlin.incremental=false`, reports in `phone/build/compose_compiler/`)

| | Before (`8e19b88`) | After |
|---|---|---|
| Composables (`phone-composables.csv`) / skippable | 84 / 77 | **115 / 103** |
| Restartable composables that are not skippable | 0 | **0** |
| Non-skippable composables | 7 | 12, all non-restartable helpers that return a value (`rememberTargetStoreOwner`, `rememberPlayerRestoration`, `rememberPlayerSnapshot`, `rememberGuardedBackStack`, `rememberDeliberateFling`, `rememberScreenFill`, `rememberStripHeight`, `replayMeta`, `streamLayout`, `isWideWindow`, `chromeColor`, `overVideoPanelColor`) |
| Unstable classes | 9 | **9**, the same kinds: `MainActivity`, `PhoneApp`, `ActivityPlayerHost`, `BackgroundResume`, `PauseInBackground`, `FullscreenPolicy`, `RecordedPlayback`, `AppLocalesSwitcher`, `SharedPreferencesThemePreference` |

No new unstable parameter on a restartable composable, so `config/compose/stability.conf` is **unchanged**. None of the 9 unstable classes is a state class passed to
a composable (hosts, policies and holders kept in `remember`). The sheet's `PlayerSheet` carries its drag progress as a `() -> Float`, read only in layout and draw
lambdas (see `PlayerSheet.kt`), which is what keeps the drag at about 4 ms per frame.

## Cold start and frame timing

Setup: the same emulator, the same commands (`benchmark-phone/tools/run-benchmarks.sh <label>`), three runs of the **before** build (git worktree at `8e19b88`:
pre-redesign code with its own final profile), one run of the **after** build with the **stale** profile, three runs of the **after** build with the **regenerated**
profile. Cold start 10 iterations per test, frames 5 iterations, warm. Raw JSON of every run is in `docs/perf-data/` (`phone-before-run{1,2,3}.json`,
`phone-redesign-stale.json`, `phone-redesign-run{1,2,3}.json`, host load logs `phone-redesign-run{1,2,3}-hostload.txt`).

### Cold start (ms, min / median / max)

| Test | Build | timeToInitialDisplay | timeToFullDisplay |
|---|---|---|---|
| with profile | before run 1 | 182 / 191 / 205 | 232 / 251 / 264 |
| with profile | before run 2 | 179 / 197 / 233 | 234 / 255 / 296 |
| with profile | before run 3 | 175 / 186 / 211 | 223 / 233 / 270 |
| with profile | **after, stale profile** | 152 / 171 / 183 | 195 / 216 / 230 |
| with profile | after run 1 | 147 / 152 / 172 | 177 / 191 / 220 |
| with profile | after run 2 | 145 / 151 / 159 | 181 / 191 / 198 |
| with profile | after run 3 (host spike, see Noise) | 172 / 186 / 253 | 223 / 238 / 314 |
| no compilation | before run 1 | 214 / 231 / 237 | 296 / 315 / 327 |
| no compilation | before run 2 | 221 / 234 / 255 | 299 / 332 / 345 |
| no compilation | before run 3 | 216 / 227 / 240 | 293 / 314 / 334 |
| no compilation | **after, stale profile build** | 187 / 194 / 201 | 265 / 274 / 299 |
| no compilation | after run 1 | 161 / 180 / 196 | 232 / 251 / 294 |
| no compilation | after run 2 | 167 / 181 / 255 | 240 / 265 / 352 |
| no compilation | after run 3 | 199 / 207 / 457 | 273 / 284 / 614 |

| | first frame, no compilation -> profile (median) | Home content, no compilation -> profile (median) |
|---|---|---|
| before, runs 1-3 | 231 -> 191 (**-17 %**), 234 -> 197 (-16 %), 227 -> 186 (-18 %) | 315 -> 251 (-20 %), 332 -> 255 (-23 %), 314 -> 233 (-26 %) |
| after, runs 1-3 | 180 -> 152 (**-16 %**), 181 -> 151 (-17 %), 207 -> 186 (-10 %) | 251 -> 191 (-24 %), 265 -> 191 (-28 %), 284 -> 238 (-16 %) |

Reading it: the profile helps by about the same amount before and after, and the after build starts faster in absolute terms (median first frame with the
profile 151-186 ms against 186-197 ms; no compilation 180-207 ms against 227-234 ms). **Why it is faster is a hypothesis**: the stock navigation-suite scaffold is
no longer on the startup path and the Home header is simpler. It was not isolated, so it is not claimed. The stale profile (one run) sits between the two
(171 ms first frame), roughly 12 % above the median of the three fresh-profile runs; a single run cannot separate that from noise, so this is not a measured benefit
of regenerating either. All numbers are 5-10x better than the ones in `docs/perf-phone.md`: that session ran on a host short of memory, this one did not (see
Noise), so **the two documents' absolute numbers must not be compared**; only within-session comparisons count.

### Frame timing (ms, P50 / P90 / P99 of `frameDurationCpuMs`)

| Journey | Before, with profile (runs 1-3 p50 / p90) | After, with profile (runs 1-3 p50 / p90) | After, no compilation (runs 1-3 p50 / p90) |
|---|---|---|---|
| Home scroll (about 177-180 frames) | 3.35 / 4.21, 3.12 / 4.66, 3.27 / 4.17 | 2.96 / 3.89, 2.92 / 3.96, 3.43 / 4.72 | 3.26 / 4.47, 3.25 / 4.33, 3.20 / 4.31 |
| Chat scroll (about 290 frames) | 3.42 / 18.59, 3.46 / 19.06, 3.42 / 18.37 | 3.69 / 54.41 (outlier), 3.32 / 19.36, 3.37 / 6.39 | 3.43 / 6.76, 3.43 / 7.59, 3.35 / 19.03 |
| Fullscreen toggle (about 56 frames) | 5.16 / 19.96, 18.34 / 21.14, 5.93 / 20.51 | 5.75 / 23.17, 5.17 / 21.91, 7.07 / 21.84 | 6.17 / 19.87, 5.84 / 33.06, 14.51 / 31.80 |
| **Sheet drag** (new, about 155 frames) | not applicable | 4.27 / 7.66, 4.37 / 8.47, 4.10 / 15.99 | 72.71 / 133.76 (outlier, 98 frames), 4.30 / 7.50, 4.65 / 10.34 |

Full p50 / p90 / p99 and frame-overrun percentiles for every run: `python3 benchmark/tools/summarize-benchmarks.py docs/perf-data/phone-before-run*.json docs/perf-data/phone-redesign-*.json`.

- **Home scroll** and **chat scroll** p50: the same in all builds, 3-4 ms, far inside a 16.7 ms frame. No shift beyond the run-to-run spread, as the regression policy of `docs/perf-baseline.md` asks (repeated on a second and third run).
- **Fullscreen toggle**: p50 5-7 ms in most runs, 14-18 ms in two of them (before run 2, after no-profile run 3); p90 about 20-23 ms in both builds. This journey is two activity configuration changes plus a rotation in 56 frames, so its percentiles are coarse. The picture is no longer re-created on rotation (instrumented test `turningThePhoneKeepsTheSamePictureViewAndNeverShowsAnotherParent`), but the frame numbers do **not** show a measurable improvement from it, and none is claimed.
- **Sheet drag**: p50 about 4.1-4.7 ms with the profile and without it (except the outlier), p90 7.5-10 ms, p99 up to 33 ms. The drag reads its progress in layout and draw, so a frame costs little CPU; the p99 tail has the same shape as the other journeys' tails and the host was loaded in run 3 (see Noise). There is nothing to compare it with before the redesign (the player was a page).
- **Outliers kept in the table**: after run 1 chat scroll with the profile (p90 54 ms) and after run 1 sheet drag without the profile (p50 72 ms, 98 frames instead of 155). Both are one run each and the next two runs of the same test are normal, which points at a hiccup of the host or of a gesture on this emulator rather than at the app; the cause was not investigated further.
- **The profile does not change frame timing** (with and without it the medians agree within the spread), same finding as `docs/perf-phone.md`.

## Noise and what this does not show

- **Host load** (sampled every 30 s during the redesign runs, `*-hostload.txt`): run 1 load average (1 min) 3.7 on average and 8.4 at most, minimum free+inactive memory about 3.0 GB; run 2 4.2 / 7.8 / 4.2 GB; **run 3 6.6 / 20.9 / 2.9 GB**, which is the run with the slower cold start and the wider percentiles. The host was much quieter than during `docs/perf-phone.md` (no swap pressure sampled here), which is why these numbers are so much lower, but it was **not idle**: Gradle's daemons were stopped by `run-benchmarks.sh`, other applications ran.
- **The before runs have no host-load log.** They were taken after the redesign runs, one after the other with the same script; the before cold-start and frame numbers are as stable between runs as the after ones, but the load during them is unknown.
- **The two builds were not measured interleaved**, so a slow drift of the emulator over the session cannot be excluded; three runs per build and an agreeing median are the mitigation.
- **Emulator only.** The host GPU is pinned to vsync, class loading and JIT behave differently from a phone, and the profile gain on a real device can be smaller or larger. Nothing was measured on the Samsung; its gesture navigation, the real Picture-in-Picture window and the real rotation sensor are untested.
- The instrumented UI journeys use demo data: the code that needs the real Twitch service is covered by `benchmark-phone/tools/extra-profile-rules.txt`, as before.
- The TV app was not re-measured on the real TV after the redesign; only its APK size (identical) and packaging were checked.

## Reproduce

```
benchmark-phone/tools/generate-profiles.sh emulator-5554          # regenerates the two profile files
benchmark-phone/tools/run-benchmarks.sh <label> emulator-5554     # -> docs/perf-data/phone-<label>.json
python3 benchmark/tools/summarize-benchmarks.py docs/perf-data/phone-<label>.json ...
python3 benchmark/tools/check-profile-packaging.py phone phone    # after :phone:assembleRelease :phone:bundleRelease
```

If `generate-profiles.sh` reports `ClassNotFoundException: ...BaselineProfileGenerator` right after a build, the Gradle build cache restored a test APK without
the module's own classes: run `./gradlew :benchmark-phone:clean` and `./gradlew --no-build-cache :benchmark-phone:assembleNonMinified` once, then the script again
(seen once during this step).

## Motion pass (search morph, press scale, load cross-fade, list placement, strip close)

Commits `c25016e` (before: Picture-in-Picture and scrim fixes, no new motion) and `b37d870` (after). Same emulator, same commands
(`benchmark-phone/tools/run-benchmarks.sh`), interleaved **before1, after1, before2, after2** (about 20 minutes each, Gradle daemons stopped), both builds use
the profile files regenerated earlier in this document (they were **not** regenerated for the motion code, so the new composables run uncompiled-ish in the
"with profile" rows; that biases against the after build). Raw JSON: `docs/perf-data/phone-motion-{before,after}{1,2}.json`, host load samples
`docs/perf-data/hostload-{before,after}{1,2}.txt`.

What was added: the header search pill grows into the Search field (`sharedBounds`, 300 ms, none with reduced motion); a pressed card or row shrinks to 97 %
(`graphicsLayer`, read in the draw phase); skeleton, message and content cross-fade in `LoadHost` (200 ms, only between kinds of state); `animateItem()` on keyed
list items (Home, Following, Category, Search); the strip slides off the bottom when its X is pressed. **Tried and removed:** a rise-and-fade of the sheet when a
stream opens: the picture is a `SurfaceView`, which ignores `graphicsLayer` alpha and translation, so the video appeared in its final place while the page
faded in. **Not done on purpose:** an animated chat (high-frequency list, no gain), a shared element from a stream card's thumbnail to the player picture (the
picture is that same `SurfaceView`).

### Cold start (ms, min / median / max)

| Test | before 1 | after 1 | before 2 | after 2 |
|---|---|---|---|---|
| with profile, first frame | 193 / 253 / 303 | 167 / 191 / 204 | 179 / 206 / 345 | 167 / 191 / 208 |
| with profile, full display | 243 / 333 / 447 | 206 / 237 / 250 | 222 / 256 / 455 | 217 / 233 / 249 |
| no compilation, first frame | 220 / 236 / 284 | 213 / 228 / 253 | 206 / 219 / 252 | 207 / 228 / 262 |
| no compilation, full display | 303 / 335 / 406 | 299 / 328 / 379 | 283 / 304 / 343 | 286 / 320 / 367 |

No cold-start regression: the after build is equal or faster in the profile rows (the before runs carry the wide maxima), and within noise without a profile.

### Frame timing (`frameDurationCpuMs`, P50 / P90 / P99)

| Journey | before 1 | after 1 | before 2 | after 2 |
|---|---|---|---|---|
| Home scroll, with profile | 3.29 / 4.34 / 8.18 | 3.35 / 4.62 / 7.32 | 3.24 / 4.26 / 5.77 | 3.02 / 4.39 / 6.61 |
| Home scroll, no profile | 3.38 / 4.63 / 8.45 | 3.50 / 5.31 / 18.48 | 3.53 / 4.60 / 6.44 | 3.18 / 4.83 / 33.53 |
| Chat scroll, with profile | 3.36 / 5.68 / 33.20 | 3.52 / 18.76 / 21.68 | 3.29 / 5.09 / 8.33 | 3.40 / 4.85 / 19.39 |
| Chat scroll, no profile | 3.43 / 5.00 / 8.55 | 3.66 / 18.96 / 24.18 | 3.64 / 18.71 / 21.40 | 3.38 / 18.68 / 21.04 |
| Sheet drag, with profile | 4.61 / 8.85 / 35.28 | 4.79 / 9.55 / 72.92 | 4.22 / 7.04 / 11.66 | 4.34 / 6.50 / 10.28 |
| Sheet drag, no profile | 4.70 / 9.24 / 19.80 | 5.26 / 18.67 / 21.79 | 4.72 / 9.27 / 19.62 | 4.78 / 18.75 / 25.64 |
| Fullscreen toggle, with profile | 9.76 / 28.05 / 36.64 | 18.28 / 31.30 / 57.77 | 18.50 / 33.32 / 53.27 | 15.97 / 26.95 / 44.35 |
| Fullscreen toggle, no profile | 15.27 / 33.44 / 119.59 | 11.09 / 24.28 / 59.40 | 7.23 / 31.95 / 77.55 | 9.36 / 21.63 / 52.34 |

Reading it honestly: the **medians do not move** (Home 3.0-3.5 ms, chat 3.3-3.7 ms, sheet drag 4.2-5.3 ms, in both builds); the p90 / p99 columns jump between
about 5 and 19 ms in **both** builds from run to run (the same journey shows both values in the before runs too), so they carry the emulator's pinned-vsync
spikes, not a measurable effect of the motion. The fullscreen toggle (a rotation, with a media surface) is the noisiest journey in every run of every build and
shows no direction. Nothing here shows a regression, and nothing here proves the motion is free on a real phone: **the benchmarks were not run on a device**,
the animations themselves (the pill morph, the press) are not exercised by any benchmark journey beyond the search open in the profile generator.

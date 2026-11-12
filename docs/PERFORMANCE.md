# Performance

Budget: main-thread work per frame under 5 ms (144 Hz = 6.9 ms), never above 16.7 ms (60 Hz).

## How it is kept

- **Recomposition**: ViewModels expose immutable state (`@Immutable`, `ImmutableList`); lazy lists use stable `key` + `contentType`; focus never recomposes: `TvFocusable` animates scale/ring in `graphicsLayer` / draw lambdas only (`FocusProgress` is read in the draw phase), `FocusMemory.lastKey` is a plain field.
- **Cheap visuals**: no `RenderEffect`/blur anywhere (zapping dims the chat with a flat veil; the voice dialog uses a flat scrim), edge fades are gradient overlays on the solid background, not offscreen layers.
- **Images**: Coil 3, decoded at the target size (`TvAsyncImage` sets the pixel size), 20 % memory cache, 64 MB disk cache, 160 ms crossfade (0 with reduced motion), stable placeholder behind every image.
- **Chat**: events are batched (250 ms) and mapped to immutable `ChatLine`s off the main thread; the buffer is the newest 200; a message is one `Text` with inline badge / emote placeholders (no row of child composables), `LazyColumn` with stable `key` = message id, so a batch recomposes only the new lines. Emote stills go through Coil's memory cache at the exact slot size; animations are requested only for the newest visible lines, up to the user's limit (default 8, 4 on low-RAM devices), never while scrolling, backgrounded, hidden or under memory pressure. Details in `ARCHITECTURE.md` (Chat rendering). The numbers that justified it are in "Chat measurements" below.
- **Release**: R8 full mode + resource shrinking with no app keep rules beyond what the libraries ship (R8 Configuration Analyzer, `./gradlew :app:analyzeReleaseR8Config`: optimization 98.7 %, obfuscation 99.1 %, shrinking 99.0 %; the rest is held by AGP and library defaults). Baseline Profile and startup profile in `app/src/main/baselineProfiles/` (the only place AGP reads a startup profile from; the old `src/main/startup-prof.txt` was silently ignored), recorded by `benchmark/tools/generate-profiles.sh`: a startup-only run (launch to Home content) becomes the startup profile, so R8 puts exactly the startup classes in `classes.dex`; startup plus every journey (Home, Follows, Search, Browse, player with chat and zapper) becomes the Baseline Profile.
- **AOT on sideloaded installs**: a plain `adb install` leaves the app interpreted (`run-from-apk`) until the background dexopt job runs, which on the TV never happened. `benchmark/tools/install-release.sh` installs the APK with its `.dm` profile so ART compiles it at install (`speed-profile`, reason `install-dm`).
- **Snapshots**: Home's last good data is kept on disk per account (`SnapshotCache`, cache dir, 24 h, cleared on sign-out) and shown at once on the next start while the fresh copy loads; the `Loader` keeps shown data during reloads and failures instead of a spinner (the spinner itself only appears after 300 ms). The fresh data animates in: row cards fade / slide (`animateItem`), and a changed thumbnail crossfades over the previous image (its memory-cache key is the placeholder). Live thumbnail URLs carry a 5-minute bucket: Coil 3 caches by URL without HTTP cache headers, so they otherwise never refreshed.
- **Player transitions**: a chat change resizes the video once, at its start, and only slides the chat (200 ms); the former cover fade + 300 ms settle (~0.8 s of covered video) is gone. The rows under the player (Following, the stream's category, For you) move with one `graphicsLayer` translation of the whole sheet; nothing is laid out per frame.
- **Startup path**: the session opens with the last validated account (stored next to the tokens) and validates in parallel with the first screen's requests instead of before them; parallel 401s share one token refresh; Home's "For you" request no longer waits for the follows; screen loads run on `Dispatchers.Default`, not the main thread. Home calls `ReportDrawnWhen` once its data is shown (logcat "Fully drawn", Macrobenchmark `timeToFullDisplay`).

## Measurements (emulator: Android TV API 33 arm64, 1920x1080 @ 320 dpi, host GPU, 60 Hz vsync)

Build: `:app:assembleBenchmark` (R8 + baseline profile, demo data). Method: `benchmark/tools/replay-keys.sh` (D-pad key replay, `dumpsys gfxinfo framestats`); numbers are per-frame milliseconds over the measured frames. `ui` = UI thread (input -> sync queued), `rt` = RenderThread (sync -> swap).

| Scenario | frames | ui p50 / p99 / max | rt p50 / p99 | ui > 5 ms |
|---|---|---|---|---|
| Home: rows + vertical anchor scroll | 79 | 1.27 / 2.46 / 2.68 | 0.72 / 3.63 | 0 |
| Browse: 7-column poster grid | 67 | 0.78 / 2.15 / 2.35 | 0.72 / 1.20 | 0 |
| Search: keyboard | 76 | 0.93 / 1.93 / 2.38 | 1.10 / 1.83 | 0 |
| Player: chat 1 msg/s + chat modes + zapper open/close (old chat, before the chat rebuild) | 69 | 1.13 / 11.06 / 15.28 | 1.01 / 2.04 | 4 (2 above 6.9 ms) |

- Frames past their deadline: 1 to 2 of ~70 per scenario (60 Hz emulator).
- Player outliers are the transitions that compose a new subtree (opening the chat column, opening the zapper with ~11 image cards); steady-state frames stay under 4 ms.
- Cold start (`am start -W`, 8 runs, emulator): ~196 ms without AOT profile vs ~181 ms with the baseline profile compiled (noisy, indicative).
- Not measured: a real TV SoC, a 144 Hz panel, the real embedded Twitch player (the demo data has no WebView). The emulator is fast on the CPU side and meaningless on the GPU side (`GpuCompleted` is pinned to vsync). These numbers say the app's own work is small; they do not prove 144 Hz on hardware.
- `:benchmark` Macrobenchmark (`FrameTimingBenchmarks`) runs on the emulator but `FrameTimingMetric` only reports `frameCount` there (no FrameTimeline); on a device/TV it reports `frameDurationCpuMs` and `frameOverrunMs`.

## Chat measurements (emulator, same setup as above)

Method: `benchmark/tools/chat-load.sh` (R8 `benchmark` build, demo data, `pm clear` before each run, 20 s warm-up, then 20 s measured; `framestats.py --watch` merges `dumpsys gfxinfo framestats` samples because the ring buffer only keeps ~2 s). "Heavy" demo chat = ~16 messages/s, 1-4 emotes per message (about half of the third-party ones animated, a few zero-width overlays), badges, mentions, bits. `ui` = UI-thread ms per frame (input, or animation start for frames without input, to sync queued). `rt` = RenderThread ms. The old chat is commit `84e7d47` with only its demo traffic patched to the same message rate and emote art (Twitch-native fragments, static: all it can show).

| Scenario (heavy traffic) | frames | ui p50 / p90 / p99 / max | ui > 5 ms | rt p50 / p99 |
|---|---|---|---|---|
| Old chat (2 runs) | 166 (8/s) | 2.9-3.5 / 11.9-12.5 / 16.7 / 20-22 | 79-80 of 166 (48 %) | 1.2 / 3.1-3.2 |
| New chat, animations off (2 runs) | 159-166 (8/s) | 2.3-2.5 / 4.3-4.5 / 6.3-7.4 / 6.8-20.9 | 5-10 (3-6 %) | 1.5 / 3.3-3.4 |
| New chat, 8 animated emotes (default; 2 runs) | 679-686 (33/s) | 4.2 / 4.8-5.3 / 8.0-10.4 / 12-31 | 40-87 (6-13 %) | 1.7 / 3.6-4.0 |
| New chat, limit 4 | 594 (28/s) | 4.2 / 5.5 / 8.4 / 12.3 | 141 | 1.6 / 3.2 |
| New chat, limit 16 | 855 (41/s) | 4.3 / 5.4 / 7.1 / 11.8 | 167 | 1.6 / 4.5 |
| New chat, holding Up / Down through history (limit 8) | 1057 (51/s) | 0.8 / 4.2 / 6.5 / 7.5 | 37 | 0.7 / 2.2 |
| New chat, chat toggled compact / large / hidden ~50 times in 20 s | 331 (17/s) | 1.5 / 3.8 / 5.1 / 6.2 | 4 | 1.2 / 2.6 |

A last run on the final build (host otherwise idle): 8 animated emotes 669 frames, ui 4.2 / 5.4 / 8.0 / 14.6 ms; animations off 164 frames, ui 1.0 / 1.5 / 2.0 / 2.4 ms. The animations-off rows above are higher than that run because the earlier runs shared the host with Gradle builds: on an emulator, treat differences under ~2x between sessions as noise; the old-vs-new gap (about 3x at p90) and the animated-vs-still gap were seen in every pair of runs.

What this says (and what it does not):

- Same content, same traffic: the rebuilt list costs a third of the UI-thread time per update at p90 and p99 (4.4 vs 12 ms, 7 vs 16.7 ms) and almost never goes past 5 ms. Why: a batch used to recompose and relayout every visible line (the list was rebuilt from a reversed copy and every line rebuilt its inline content); now only new lines compose, and building the text and resolving emotes happens off the main thread.
- Animation is a fixed per-frame cost, not a per-emote cost: p50 stays ~4.2 ms with 4, 8 or 16 animated emotes while the frame rate follows the animation timers (28 / 33 / 41 per second). An animation costs a frame (Compose frame clock + traversal + draw record), so the budget limits decode work and frame rate, not the cost of one frame. A dedicated `graphicsLayer` per animated emote was tried: identical numbers (p50 4.2 ms), so it was not kept.
- The ~35-50 % "janky frames" `gfxinfo` reports in every row, old chat included, are not a result: each frame takes 17-18 ms from vsync on this emulator because GPU completion is pinned to the host's vsync (see above), so every sparse frame is "late". UI and RenderThread columns are the CPU-side signal.
- Total CPU of all threads (one `top` sample, noisy, indicative only): old 9-13 %, new animations off 15-24 %, new with 8 animations 55-65 %, limit 16 68 %. The new chat draws more per message (badges, overlays) and decodes animations on a background thread; the animation numbers are the cost to weigh on a weak SoC.
- Real data parse cost: the largest provider payload (7TV, 966 emotes, 2.3 MB) decodes in ~110 ms on the JVM (host CPU); on a TV SoC expect a few hundred ms, off the main thread, once per channel per 5 min.

Memory over a long stream (`benchmark/tools/chat-memory.sh 5`: heavy traffic, 8 animated emotes, ~4,800 messages in 5 minutes, `dumpsys meminfo` every 30 s): total PSS 50.8-61.8 MB (start 52.2, end 60.1), Java heap 5.9-13.2 MB, native heap 26.7-31.0 MB, a GC sawtooth with no upward trend. The buffer is 200 lines, Coil's memory cache is 20 % of the app heap, and animated decodes are never kept in it. Five minutes shows no leak; it does not show hours (not run). (`Graphics` reads 0 on this emulator, so GPU memory is not measured.)

Not measured: a real TV SoC (the animation cost above is the number most likely to differ), real Twitch / provider traffic and images (everything here is the offline demo art: the real 7TV / BTTV / FFZ images were not rendered in a benchmark), the embedded Twitch player running beside the chat (the demo has no WebView, so competition for CPU / GPU / memory with video decode is untested), 144 Hz panels, reconnection under load (covered by unit tests of the feed and repository, not by a timing run), and TalkBack. Animated WebP / GIF decoding needs API 28+ (`AnimatedImageDecoder`); below that GIFs animate through `GifDecoder` and WebP emotes stay stills; API < 28 was not run.

## Startup and AOT on a real TV (TCL Smart TV Pro, MT5896, Android 14, 32-bit ART, release build, real account, 2026-11-07)

Cold start, `am start -W` after `force-stop` (median of 6-9 runs; first frame = `TotalTime`, content = logcat "Fully drawn", i.e. Home shows its data). Network-bound numbers move with Twitch API latency.

| Build / install | first frame | Home content |
|---|---|---|
| Before: plain `adb install`, not compiled (`run-from-apk`) | ~1,900 ms | not instrumented |
| Same APK compiled with its profile (`speed-profile`) | ~390 ms | ~1,410 ms |
| Now: `.dm` install, regenerated profiles, parallel session / Home requests, loads off the main thread | ~393 ms | ~1,180 ms |
| + Home snapshot shown at once (stale-while-revalidate), fresh data animates in | ~475 ms | **~520 ms** |

Emulator, demo data (`StartupBenchmarks`, cold, 10 runs, medians): no compilation 215 ms first frame / 386 ms full display; Baseline Profile 195 / 302 ms. The emulator runs fast arm64 host CPUs, so the TV gap above is far larger.

Home rows, D-pad sweep (`replay-keys.sh`, 120 frames), same APK, `cmd package compile -m verify` (no AOT) vs `-m speed-profile`: UI thread p50 / p90 / p99 2.12 / 7.26 / 15.45 ms vs 0.93 / 2.34 / 5.85 ms (frames over 5 ms: 16 vs 2); RenderThread p90 15.4 vs 6.4 ms. The earlier TV numbers in this file (chat transitions of 100-230 ms) were taken on an uncompiled install and should be measured again.

## Video playback on a real TV (TCL Smart TV Pro, MediaTek MT5896, Android 14, 2026-11-07)

Release build, real account, 1080p60 Twitch source (the panel only offers 60 Hz: a 50 fps stream is shown with
50-on-60 judder whatever the app does). Tools: `framestats.py --watch` and `dumpsys SurfaceFlinger --latency <layer>`.

- Hardware acceleration is on (app default) and decoding is in hardware: `dumpsys media.resource_manager` shows
  `c2.mtk.avc.decoder` held by the app process.
- **Cause of the lag: `setLayerType(LAYER_TYPE_HARDWARE)` on the WebView.** With it, Chromium cannot give the video its
  own SurfaceFlinger layer; every video frame redraws the whole app window: ~31 app frames/s (the video reached the
  screen at that rate), RenderThread p50 17 ms, 90 % missed deadlines, compact chat open.
- Without it (current code) the video sits in `Webview Overlay SurfaceControl` and presents at 58-60 fps
  (p50 16.7 ms). The app window redraws ~6 times/s with chat hidden; ~31/s with compact chat (new messages and
  animated emotes), which no longer holds the video back.
- Nothing else drawn over the video forces repaints: no blur / backdrop filter, the scrim and overlay only animate
  alpha/transform while shown; the uptime clock ticks only while the overlay is visible; chat is not collected while hidden.

## Native player and the side chat (TCL TV, 2026-11-07)

The Media3 player removed the WebView's per-video-frame redraw of the app window and two redundant full-screen fills;
chat toggling went from vsync->done p50/p99 40.6/154 ms to ~24/73-90 ms. Method, numbers, the remaining one-frame
composition spike and the layout decision (resize once, SurfaceView) are in `docs/NATIVE_PLAYER.md`.

Phone app: see [perf-phone.md](perf-phone.md) (startup with and without the Baseline Profile, frame timing, sizes, the phone's regression policy).

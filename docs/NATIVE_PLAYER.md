# Native player (Media3)

A second implementation of the video, next to the WebView embed, behind one interface (`feature/player/playback/VideoPlayer`).
The WebView path is unchanged (`WebViewVideoPlayer` is the old `PlayerController`, renamed).

## Choosing the engine

| Level | How | Effect |
|---|---|---|
| Build | `-PnativePlayer=false` | Media3 is compiled out (R8 removes it; release APK 3.76 -> 2.82 MB). The Settings tile is hidden. Default `true`. |
| Build | `-PplayerEngine=native\|webview` | Engine a fresh install starts with (`BuildConfig.PLAYER_ENGINE_DEFAULT`). Default `native`. |
| Runtime | Settings > Player tile | Flips Native / Web, saved in DataStore (`AppSettings.playerEngine`), applies on the next player session. |
| Automatic | per channel | If the native player cannot resolve or play a channel (offline, token refused, integrity check, network down after retries, fatal player error) the host switches that channel to the WebView player (logged: `PlayerHost: native player failed for ...`). Picking another channel tries native again. |

No product flavors: a flavor would double every variant the benchmark module and the demo source sets are wired to.

## Stream resolution (`feature/player/data/stream`)

`TwitchPlaybackResolver.resolve(channel): ResolvedStream` is the seam. `GqlPlaybackResolver` is bound in `AppModule`; a client of your own server only has to return the same `ResolvedStream` (master URL + variants) and be bound instead.

1. `POST https://gql.twitch.tv/gql`, raw query string (no persisted-query hash) `streamPlaybackAccessToken(channelName, params: {platform: "web", playerType: "site"})`, headers `Client-Id` (Twitch's public web player id) and a random `X-Device-Id`. Result: token `value` + `signature`.
2. `GET https://usher.ttvnw.net/api/channel/hls/<login>.m3u8?sig&token&allow_source=true&allow_audio_only=true&player_backend=mediaplayer&playlist_include_framerate=true&supported_codecs=avc1&p=<random>`.
3. `MasterPlaylistParser` reads the variants (`chunked` = source, `720p60`, `480p30`, `160p30`, `audio_only`).

Failure handling:
- Network errors, 5xx and 429 retry with exponential backoff (4 attempts, 400 ms doubling to 4 s; `RetryPolicy`). Nothing else retries.
- Offline channel (usher 404 / `transcode_does_not_exist`) -> `ChannelOfflineException`.
- Any refusal -> `StreamAccessException(stage, status, body, integrity)` and a **warn log with the exact HTTP status and body** (`PlaybackResolver` tag; warn level survives R8's log stripping). A GraphQL `failed integrity check` answer sets `integrity = true`: Twitch then wants a client-integrity token, which this app does not have. If that happens on your network the exact response is in logcat; the app falls back to the WebView player.

Default quality (`QualityPolicy`): 720p60, else 1080p30, else the best rendition not above 1080p, else whatever exists. The viewer's pick (quality dialog: Auto, each rendition, Audio only) is saved and re-applied to every stream. "Auto" plays the master playlist (ExoPlayer adaptive); a pinned quality plays that rendition's media playlist.

## Playback (`NativeVideoPlayer`)

- One `ExoPlayer` per player session (created when the player opens, released when it closes). A channel switch resolves and swaps the media source on the same instance; the last frame stays up (`keepContentOnPlayerReset`) until the next channel's first frame.
- `HlsMediaSource` (chunkless preparation), hardware `MediaCodec` only (extension renderers off, decoder fallback on), buffers 1 s to start / 5-15 s, live offset target 4 s (2.5-8 s), catch-up speed 0.97-1.04x. Twitch's own low-latency prefetch segments are not supported by ExoPlayer: about three 2 s segments is the floor, so expect 4-6 s behind the live edge, not the web player's ~2 s.
- Errors: `BehindLiveWindow` re-prepares at the live edge; other errors re-resolve (new token) up to 3 times with 0.8 / 1.6 / 3.2 s pauses, then hand over to the WebView player.
- Pauses when the activity stops, resumes at the live edge when it starts.

## Why the side chat no longer janks

Measured on the TCL TV (MT5896, release, speed-profile, real 1080p60 stream, chat cycled 6 times in 22 s):

| | WebView (before) | Native | Native + overdraw fix (final) |
|---|---|---|---|
| App frames drawn | 439 | 182 | 192 |
| RenderThread p50 / p90 / p99 | 12.9 / 22.0 / 60.3 ms | 7.2 / 12.8 / 21.7 ms | 5.4 / 15.9 / 32.5 ms |
| vsync -> done p50 / p90 / p99 | 40.6 / 60.7 / 154 ms | 23.4 / 39.8 / 73.3 ms | 24.0 / 41.3 / 90.2 ms |
| UI thread p50 / p90 / p99 | 3.7 / 9.2 / 40.4 ms | 1.9 / 9.2 / 40.8 ms | 2.6 / 9.2 / 46.6 ms |
| Missed deadlines | 16.6 % | 24.2 % | 22.4 % |

(`benchmark/tools/chat-toggle.sh`; one run each, different channels and chat traffic, so treat differences under ~20 % as noise. "Missed deadlines" went *up* because the denominator shrank: the app window is no longer redrawn on every video frame, and the frames left are the transition frames. Read vsync -> done.)

Macrobenchmark `FrameTimingBenchmarks.chatExpandCollapseBaselineProfile` (emulator API 33, demo clip through the Media3 player, 5 iterations x 12 chat toggles, medians): frame time P50 / P90 / P95 / P99 = 38 / 81 / 105 / 150 ms, janky 57 %. Those are **emulator numbers and not comparable with the TV table**: the host GPU pins every frame to vsync (see PERFORMANCE.md), and the demo build has no WebView page, so the WebView engine cannot be benchmarked there. They exist as a regression guard for the native path (run it before and after a change); the WebView-vs-native comparison is `chat-toggle.sh` on the TV.

Root cause, from a Perfetto trace of the toggles (`atrace` view + gfx on the TV):

1. **Every video frame went through the app window with the WebView** (and the WebView resize made Chromium re-layout the page). The RenderThread spent 13 ms per frame (p50) on a 16.7 ms budget, so every chat animation frame landed late. A `SurfaceView` is its own SurfaceFlinger layer fed by the decoder: video presents at 60.5/s (`dumpsys SurfaceFlinger --latency`, no gap over 20 ms) without touching the app's frames.
2. **Four stacked opaque full-screen fills** (window, app root, navigation marker entry, player root) under a hole-punched video: the GPU overdraw view was solid red (4x+) across the picture. Two redundant fills are gone for the native path: green (2x).
3. **One heavy main-thread frame per toggle** (26-75 ms: 24-39 ms recomposition + ~38 ms recording of the chat's first 12-14 lines, all new after the list was frozen while hidden). This one is *not* fixed: the slide waits for steady frames (`awaitSteadyFrames`) so it is not seen as a stutter of the animation, but the control bar and focus ring hitch for those frames. Candidate for later: keep the hidden list warm while the control bar is visible (moves the cost, does not remove it) or stagger the lines over two frames.

Chosen layout approach, **(b) resize once**: the video has to shrink next to the chat (the chat is an opaque column, not an overlay), so (a) "fixed size + translate the chat" would hide 24-28 % of the picture. The `PlayerView` (a `SurfaceView` inside Media3's `AspectRatioFrameLayout`, `RESIZE_MODE_FIT`) is given `width(videoWidth).fillMaxHeight()` where `videoWidth` depends only on the chat *mode*: one measure/layout at the start of a change, never per animation frame, and the PlayerView letterboxes the picture itself. The chat column only moves through `graphicsLayer` (transform + alpha, read in lambdas). The view is created once (`AndroidView` factory) and never touched by `update`.

SurfaceView vs TextureView: SurfaceView keeps the video out of the app's GL pass and on the SoC's hardware composition path; TextureView would copy each frame through the GPU into the window (the same cost class as the WebView problem) and is only worth it to animate the video's transform, which this screen does not do.

Chat list (already in place, verified): `LazyColumn` with `key = message id` and `contentType`, newest 200 kept, 250 ms batches mapped off the main thread, only new lines compose, no `notifyDataSetChanged` equivalent, frozen (not collected) while hidden, and it never touches the video's layout.

## R8 / profiles

- Release: `isMinifyEnabled` + `isShrinkResources`, no new app keep rules were needed: Media3 ships consumer rules, kotlinx.serialization's rules cover the `@Serializable` GraphQL models (called through their generated serializers), and Ktor/OkHttp are unchanged. Verified by playing a real stream on the R8 release APK on the TV.
- Baseline Profile (`app/src/main/baselineProfiles/`): regenerated with `benchmark/tools/generate-profiles.sh` (emulator). New journeys: open stream and play (demo clip through the real Media3 player), chat expand/collapse, channel switch. `benchmark/tools/extra-profile-rules.txt` adds hand-written rules for the resolver and player package, which the demo run cannot reach. Packaged: `assets/dexopt/baseline.prof` in the release APK, `baselineProfiles/0/*.dm` next to it.

## Running things

```
# Native (default) / WebView-only build
./gradlew :app:assembleRelease
./gradlew :app:assembleRelease -PnativePlayer=false
# Install on the TV with its profile (never `installDebug`: it hits every adb device)
ANDROID_SERIAL=192.168.1.23:5555 benchmark/tools/install-release.sh
# Switch engine: Settings > Player  (or fresh install with -PplayerEngine=webview)

# Chat expand/collapse frame timing on the real TV and a real stream (run once per engine)
ANDROID_SERIAL=192.168.1.23:5555 benchmark/tools/chat-toggle.sh 6

# Macrobenchmark on the emulator / a device (demo data, native player over the bundled clip)
ANDROID_SERIAL=emulator-5554 ./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.twitchplayer.tv.benchmark.FrameTimingBenchmarks
# Regenerate the profiles (emulator only)
benchmark/tools/generate-profiles.sh emulator-5554
```

## Testing ads, latency and stats on the emulator (HLS test server)

The demo build normally "streams" a looping MP4, which has no playlist, so the ad marker and the latency cannot be checked. `benchmark/tools/hls-server.py` serves a real sliding HLS live playlist (wall-clock `EXT-X-PROGRAM-DATE-TIME`, and a stitched-ad `EXT-X-DATERANGE` covering the first 20 s of every minute) over looping segments:

```
ffmpeg -y -stream_loop 1 -i demo-android/src/main/assets/demo_stream.mp4 -t 20 -c:v libx264 -g 60 -keyint_min 60 -sc_threshold 0 -c:a aac \
  -f hls -hls_time 2 -hls_list_size 0 -hls_flags independent_segments -hls_segment_filename 'SEG_DIR/s%d.ts' SEG_DIR/base.m3u8
python3 benchmark/tools/hls-server.py SEG_DIR 8099 $(( $(adb shell date +%s) - $(date +%s) ))   # last argument: device clock - host clock
adb shell settings put global twitchtv_demo_hls http://10.0.2.2:8099/live.m3u8                  # demo build only; `settings delete global twitchtv_demo_hls` to stop
adb shell settings put global twitchtv_demo_events 1    # demo chat sends a room mode, a deleted message, a timeout, a title change, a raid, "stream ended"; channel pages get clips and a schedule
```

The debug build allows cleartext HTTP to 10.0.2.2 only (`app/src/debug/res/xml/debug_network_security.xml`). Checked this way on 2026-11-07: the ad chip counts down from the real playlist tags, the latency matches `currentLiveOffset`, the raid banner appears and the follow-raids setting zaps after 6 s, and a typed message comes back through chat.

# iOS app

SwiftUI app on top of the shared Kotlin core (`shared/*`, bridged by `TwitchShared.xcframework`, see `docs/APPLE_PLAN.md` and `apple/README.md`).
It is the iOS counterpart of the Android phone app (`docs/phone-redesign.md`): same presenters, same data, a native look (Liquid Glass on iOS 26
and later, materials on iOS 18 to 25).

## Build and run

```sh
brew install xcodegen                       # once
cp apple/Config/Local.xcconfig.example apple/Config/Local.xcconfig   # then put the public Twitch client id in it (git-ignored)
apple/scripts/build-shared.sh debug         # the XCFramework, with the offline demo (about a minute once Kotlin/Native is downloaded)
apple/scripts/build-shared.sh release       # the XCFramework without any demo code (checked), for Release builds; needs -Xmx4g (the script sets it)
python3 apple/scripts/sync-strings.py       # only when the Android strings changed (the catalog is committed)
apple/scripts/ios-run.sh                    # builds TwitchIOS for a simulator, installs and launches it
apple/scripts/ios-run.sh -demo -screenshot /tmp/home.png   # offline fictional data, then a screenshot
(cd apple && xcodegen generate) && xcodebuild test -project apple/TwitchPlayerApple.xcodeproj -scheme TwitchIOS -destination 'platform=iOS Simulator,name=iPhone 17'
```

Debug launch arguments (compiled out of Release builds): `-demo` (offline fictional data: Kotlin `appleDemoModule`, artwork drawn by `DemoArt`, the
bundled test clip as the stream), `-demo-signed-out`, `-tab following|browse`, `-push search|settings|channel:<login>|category:<id>:<name>`,
`-open <login>` (opens the player), `-minimised`, `-fullscreen`, `-emotes`, `-sheet signin`.

## Architecture

```
apple/
  Shared/          code the three Apple apps will share (iOS today)
    Bridge/        Observation.swift (StateFlow -> main-actor closures), LoadState.swift (Kotlin Load<T> -> Swift enum)
    App/           AppModel (tabs, per-tab navigation stacks, session, launch args), Models.swift (one @Observable model per screen
                   around its shared presenter), PlayerSession (what is playing, where the sheet rests)
    Player/        PlayerController (AVPlayer, audio session, Now Playing, remote commands, PiP, sleep timer, quality, audio-only),
                   PlaybackModel (the live PlayerPresenter: chat, emotes, room, plus replay/clip loading and replay progress)
    Design/        Theme (palette, the one place that knows about Liquid Glass), RemoteImage (memory + 128 MB disk cache), DemoArt, Formatting
  iOS/             SwiftUI views: RootView (stacks + TabBar + PlayerSheet + sign-in sheet), HeaderBar, Screens (Home, Following, Browse,
                   Category), SearchScreen, ChannelScreen, SettingsScreen, SignInSheet, PlayerSheet, VideoStage, VideoSurface (the AVPlayerLayer
                   view), ChatViews, ChatComposer, EmoteViews, Orientation
  Resources/       Localizable.xcstrings (generated)
  Tests/           XCTest
```

- **State flows one way.** A view model creates its presenter with a `PresenterScope` (its lifetime), observes the `StateFlow` with
  `for await` (SKIE) and publishes Swift state; views only render it and call `onEvent`. Nothing in Swift holds business logic.
- **Liquid Glass without scattering `#available`.** `Theme.swift` exposes `glass(in:interactive:tint:)`, `glassCapsule` and `GlassGroup`; they use
  `glassEffect` / `GlassEffectContainer` on 26 and later and `.ultraThinMaterial` with a hairline below. Minimum is iOS 18.
- **One video view.** `VideoSurface` wraps one `AVPlayerLayer`; `PlayerSheet` only changes its frame (expanded, dragged, docked as the strip above
  the tab bar, landscape fullscreen). Rotation, the strip and Picture in Picture therefore share one layer, and the sheet never re-parents it.
  The scrim of the controls is full-bleed; only the controls respect the safe areas and the cutout.
- **Navigation.** One `NavigationStack` per tab (kept alive, so scroll position and pages survive tab switches). Search and Settings are pages pushed
  from the header; the search page opens with a native zoom transition from the header pill (`matchedTransitionSource`). A page already on the
  stack is returned to, never pushed twice. Tapping a stream opens the player with its chat; the channel page is one tap away on the streamer row.
- **Startup and refresh.** The shared presenters load from the on-disk snapshots first (stale-while-revalidate); `scenePhase == .active` calls the
  shared `ForegroundSignal`, so feeds older than their TTL refresh themselves (the same policy as Android and the TV). List changes animate
  (`animation(_:value:)` on stable ids), switched off by Reduce Motion and by the in-app animation setting.
- **Localization.** `apple/scripts/sync-strings.py` generates `Localizable.xcstrings` from the Android resources (the TV's 156 strings in 36 locales,
  the phone-only strings in English, French and Arabic) and from `apple/Resources/AppleStrings.json` (strings only the Apple apps have: en, fr, ar). The
  Android resource name is the catalog key; `%s` becomes `%@`; plurals (`<plurals>`) become catalog plural variations with `%lld` and are read with
  `plural(key, count)` (sleep timer minutes, the "new messages" pill); locales are mapped (`in` to `id`, `iw` to `he`, `zh-rCN` to `zh-Hans`...). CI fails when the
  catalog is stale, and `LocalizationTests` fails when a key used in the Swift sources is missing from it. **Which keys fall back to English:** 152 keys
  (the TV strings) exist in all 36 locales; the other 141 keys (phone-only strings, the Mac menu and Apple-only strings) are translated to English,
  French and Arabic only, so the other 33 locales show English for them. `python3 apple/scripts/sync-strings.py --report` prints the exact list.
- **Accessibility.** Dynamic Type everywhere (system text styles, no fixed sizes except the chat, which follows the in-app text size), VoiceOver labels
  and traits on every control (the icon-only tab bar, the strip, chat lines with a "reply" action), RTL (leading/trailing, mirrored chevrons), Reduce
  Motion respected.

## Feature parity with the Android phone app

| Feature | iOS | Notes |
|---|---|---|
| Tab bar (Home, Following when signed in, Browse), header search pill and account button | done | custom Liquid Glass capsule, no Search/Settings tabs |
| Sign-in with the device code | done | open twitch.tv/activate, copy (local-only, expiring clipboard item), countdown, closes on success |
| Home, Following (continue watching, offline channels with last seen), Browse with filters and sort, Category | done | snapshot-first, stale banner, animated list changes, avatars on rows |
| Search: recents, live now, filters, seamless pill transition | done | zoom transition; voice search not on Apple yet |
| Channel page (schedule, replays with progress, clips, similar) | done | follow opens twitch.tv, as on Android |
| Player: live, replay, clip over AVPlayer (native HLS) | done | quality menu, audio-only, speed, sleep timer, replay resume and progress saving |
| Chat: emotes (Twitch, 7TV, BTTV, FFZ), badges, replies, mentions, notices | done | animated emotes via ImageIO for the newest lines; zero-width overlays stack |
| Emote picker, `:` suggestions, compact input pill | done | the panel takes the keyboard's place |
| Draggable sheet, mini strip above the tab bar, streamer row opens the channel | done | drag down to minimise, up or tap to expand, deliberate pull or X to close |
| Fullscreen landscape with overlay chat, scrim edge to edge | done | the button asks the scene to rotate |
| Picture in Picture, background audio, Now Playing, remote commands | wired, not verified on hardware | `AVPictureInPictureController`, `UIBackgroundModes: audio` |
| Settings (account card, playback, chat, appearance, language, about) | done | language opens the iOS per-app language page |
| Discover vertical feed | not done | needs the two-player pool; the shared `FeedPresenter` is ready |
| Voice search | not done | `UnavailableVoiceInput` on Apple |
| Pull-to-refresh | not done | refresh is automatic (foreground signal) and via the stale banner |

## Real data and the docked chrome

- **Root cause of the floating strip and tab bar.** The sheet's geometry was computed from the size SwiftUI proposes to the view, and that size shrinks by the
  keyboard's safe area. With the chat field focused and then the sheet minimised, the field stays first responder under the strip, so the "keyboard" region stayed
  reserved: the strip's rest position (`container height - tab bar - strip`) and the tab bar (aligned to the bottom of a shrunk stack) both ended up about a
  keyboard's height too high, while the scroll views kept drawing to the real bottom. Now `SheetGeometry` (pure maths, `SheetGeometryTests`) takes the screen
  size without the keyboard, the keyboard only shortens the expanded sheet, the sheet and the tab bar ignore the keyboard safe area, and minimising or closing
  dismisses the keyboard. Verified on iOS 26.0 and 26.2 simulators (26.5 shares the code; its device was left alone, the user was watching it).
- **Real playback.** Launched without `-demo` and signed out, `-open <login>` plays real live channels (rainbow6, shadowkekw, blastpremier, chopperinho,
  kato_junichi0817) in the expanded sheet and in the strip: the shared resolver gets the usher URL with the public GQL client, `AVPlayer` plays the HLS master
  playlist, no ATS or header change was needed. `AVPlayerItem` error-log entries ("START-TIME is too close to live", "Segment exceeds specified bandwidth")
  appear in the log (`subsystem com.twitchplayer.player`) and are not failures. A strip that is black means no picture yet (address being resolved, stream
  loading) or a failure: it now shows the stream's thumbnail with a spinner, or a warning triangle when the stream failed or the channel is offline.
  Signed-in playback with the user's own account was not exercised here.

## The sheet's drag: root causes and numbers

The swipe-down to minimise was jittery with real data. What was measured, and how it was found:

**Real conditions.** `apple/scripts/measure-drag.sh <udid> [Debug|Profile]` runs an XCUITest (`apple/UITests`) that opens a real live channel (picked from Twitch's live list)
beside a fast fictional chat (`-real-fast-chat`: the real network and playback, the demo's session and about 16 messages a second) and drags the sheet down and up with
real touches (`press(forDuration:thenDragTo:withVelocity:)`, slow, default and fast, six drags each way), while `simctl io recordVideo` records the screen. The app logs the
main thread's frame cadence per phase (`FrameMonitor`, `-frame-monitor`), and `apple/scripts/analyze-recording.py` reads the recording itself: it follows the sheet's top
edge (a magenta bar drawn on the sheet's edge while measuring) frame by frame, i.e. what the render server really presented. `xctrace`'s Animation Hitches template does not
support the simulator ("Hitches is not supported on this platform"). A control (`analyze-scroll.py`: a plain list scrolled with the same touches) gives the recorder's own noise floor:
p95 frame interval 25.0 ms and 4.9 % of intervals above 25 ms.

**Root cause (the one that was jitter):** the pan's translation was measured in the gesture's *local* coordinate space. The view carrying the gesture moves (and scales) with the
sheet, so its own origin moves under the finger, the translation loses what the view has already moved, and the next sample compensates: the sheet zigzags between two positions
from one frame to the next (in the recordings, 40 % of the steps went against the movement, and every other frame showed the sheet at an earlier progress with the details at
full opacity). The main-thread timer saw nothing (0 hitches in every drag, in every version), which is why the first two rounds of measurements missed it. Fix:
`DragGesture(minimumDistance:coordinateSpace: .global)`.

**Bisect (same harness, real touches, recordings; demo clip and demo chat for the older commits).** Zigzag, as the share of steps against the movement: 0ce8bc6 (the first sheet)
42.5 %, fe1e320 (before the keyboard/strip fix) 40.5 %, b5b75d9 (the keyboard/strip fix) 40.9 %, 1d46d2c (the first drag rewrite) 42.6 %, then 0.0 % with the global space. So the
chat fixes did not introduce it: it was there from the first sheet and the commits in between neither added nor removed it (the user's report that it appeared with the chat fix is not
supported by the measurements; what changed meanwhile was the amount of chat and video on screen, which is what made it visible).

**Second cause (the 30 fps on the way down):** with the zigzag gone, the downward drags still ran at about 30 fps while the upward ones ran at 60. The cost was GPU work proportional
to the sheet's size, repainted per frame while the large sheet moved: Liquid Glass elements over the playing video and the moving sheet (the chat input's glass, the controls', the tab
bar's: each samples what is behind it every frame), an opacity group over the whole details subtree (chat lines, emotes, input), a rounded-corner mask over the whole sheet and the
shadow's blur of a full-screen shape. Now: glass is a flat translucent fill while the sheet moves (`flatChrome`, set for the whole sheet and the tab bar, until 260 ms after it has landed),
the details fade by a flat cover of their own colour instead of an opacity group, the controls go in one short fade at touch-down, corners and shadow only appear when the sheet is nearly
the strip, and the landing's work (the position the stage is rebuilt for, the glass coming back, the chat catching up) waits until the sheet has visibly stopped.

**Pure SwiftUI, one value.** `SheetProgress.value` is the only thing the drag and the settle drive; no view body reads it, only `SheetFrame`, `PictureTransform` and the cover/fade
modifiers do. The picture is laid out once at its own size and only scaled and moved (`scaleEffect`, `offset`) while the sheet moves, so the video layer is never resized mid-drag; the
chat is held (new batches kept aside, `holdChat`) until the sheet has landed; the settle is SwiftUI's own `interpolatingSpring` launched with the finger's velocity, and a new touch starts
from the value SwiftUI is showing (`PresentedProbe`), so the sheet can be caught in flight; the screen is measured with `onGeometryChange`, not a `GeometryReader`.

**Numbers** (iPhone 17 Pro, iOS 26.5 simulator, Debug, real touches, live video, fast chat; the 12 drags of one run; the recorder's own control in the last column):

| | 1d46d2c, Debug (before) | now, Debug | now, Profile (Release optimisation) | plain list scrolled (control) |
|---|---|---|---|---|
| steps against the movement (zigzag) | 42.6 % (81 of 190) | 0.0 % (0 of 666) | 0.0 % (0 of 704) | - |
| distinct positions presented | 188 | 661 | 699 | - |
| median interval between positions | 21.7 ms (46 fps) | 16.7 ms (60 fps) | 16.7 ms (60 fps) | 16.7 ms |
| p95 interval | 125 ms | 25.0 ms | 23.3 ms | 25.0 ms |
| intervals above 25 ms (fast part) | 46.3 % | 5.1 % | 3.6 % (11.4 % in a first run while the host was busier) | 4.9 % |
| speed discontinuity between frames (p50, 1 = a full speed change) | 1.99 | 0.17 | 0.15 | - |

Debug is not slower than Release for this gesture any more (they are both at the recorder's noise floor); before, Debug and the older commits all zigzagged alike, so the jitter was not
a Debug-only cost. `Profile` = Release's optimisation with the debug hooks (`apple/scripts/measure-drag.sh <udid> Profile`, with a framework built by
`./gradlew :shared:platform-apple:assembleTwitchSharedReleaseXCFramework -PappleDemo=true`, copied to `apple/Frameworks`). Building it also found a crash in the Kotlin logger
(`NSLog("%@", <Kotlin string>)` segfaulted in optimised builds): fixed by passing an `NSString`.

The macOS app and the Apple TV player have no drag on the sheet (the Mac's mini player is toggled by a button, the Apple TV's player is the system `AVPlayerViewController`),
so there is nothing to measure there. Not measured: a physical device (ProMotion at 120 Hz, the real GPU), a real finger.

## Lifecycle and system integration

- **Audio session.** Created inactive: it is activated when playback starts and deactivated (notifying other apps) when the player stops, so launching the app
  never silences the viewer's music. Interruptions (calls, Siri) pause and resume.
- **Observers and remote commands** are stored and removed in `PlayerController.tearDown()` and `deinit`; remote-command handlers hop to the main actor with a
  `Task` (they are called on the system's queue); a test checks that creating and tearing down controllers never stacks handlers.
- **Retry.** The failure flag is cleared before the reload, so a reload that fails again is reported again; a live stream restarts at its live edge, a replay
  resumes where it stopped. `setRate` always sets `defaultRate` (the rate used on every resume) and the current rate when playing.
- **Picture in Picture.** One `AVPictureInPictureController` per layer (reused); its "return to app" button calls `restoreUI`, which expands the sheet. Not
  verified on hardware.
- **Backgrounding** (`scenePhase == .background`) writes the replay position immediately (`AppModel.appWentToBackground`).
- **The demo** lives in its own Kotlin source directory and is left out of the release XCFramework (`-PappleDemo=false`, `build-shared.sh release` checks
  the binary for demo symbols); the Swift side calls it under `#if DEBUG` only. A Release build also has neither the demo clip nor the launch arguments.

## Screenshots

The PNGs in `docs/screenshots/{ios,macos,tvos}` are downscaled (iPhone captures at 50 %, tvOS at 1280 px wide) and quantised to 192 colours to keep the
repository small (about 3.3 MB for all three folders, from 30 MB). Details such as 1 px hairlines are therefore softer than on screen.

## Verified, and not

Verified on the iPhone 17 simulator (iOS 26.5) and the iPhone 16 simulator (iOS 18.3, material fallback), with screenshots in `docs/screenshots/ios/`, and by
Swift tests that run on all three Apple platforms from one `apple/Tests` folder: 41 on iOS, 43 on macOS and 43 on tvOS (the Mac and tvOS targets add their own).
They cover formatting, the shell's navigation rules, the player session, the `PlayerController` transitions (play, pause, stop, failure and retry, live
edge vs replay resume, quality and audio-only, sleep timer, rate, no stacked remote handlers, audio session only while playing), the Keychain round trip from
the signed app, localisation (every key used in Swift exists; plurals; fr and ar), and the real Kotlin presenters running on the demo data through the bridge.
Tests run in random order (`randomExecutionOrder`): they share one Kotlin graph per process (DataStore allows one instance per file, so it cannot be reset
between tests) and none may depend on another. Kotlin: the shared tests run on macOS, the iOS simulator and, with `-PtvosSimulatorTests=true`, the tvOS simulator
(storage in Caches, DataStore and snapshot files written and read back there). The release XCFramework (`build-shared.sh release`, 214 MB against 383 MB for the debug one) was built and checked to contain no demo symbol, and the iOS Release
configuration builds against it and contains neither the demo clip nor the launch arguments.

Strict concurrency: the projects build with `SWIFT_STRICT_CONCURRENCY=targeted`. With `complete` the iOS target reports 99 warning lines (about 50 distinct):
redundant casts of SKIE-typed arrays, `nonisolated(unsafe)` notes in `PlayerController`, and "sending `graph.playback` / `self` risks data races" where Kotlin
objects (not `Sendable`) cross into tasks. They are the list to clear before moving to Swift 6 language mode.

Not verified: a physical device, a real Twitch account (everything above ran on the offline demo data), real HLS playback of a live channel, Picture in
Picture and background audio on hardware (the "return to app" button included), AirPlay, rotation with a real device sensor, the Keychain fallback on a
sandboxed, ad-hoc signed Mac app without the entitlement (only the success path ran), VoiceOver passes, the GitHub workflow (never run; Xcode 26.4 on `macos-26` is an
assumption), and Xcode stable releases (Xcode 27 beta 4 only). Performance was not measured.

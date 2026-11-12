# iOS app

SwiftUI app on top of the shared Kotlin core (`shared/*`, bridged by `TwitchShared.xcframework`, see `docs/APPLE_PLAN.md` and `apple/README.md`).
It is the iOS counterpart of the Android phone app (`docs/phone-redesign.md`): same presenters, same data, a native look (Liquid Glass on iOS 26
and later, materials on iOS 18 to 25).

## Build and run

```sh
brew install xcodegen                       # once
cp apple/Config/Local.xcconfig.example apple/Config/Local.xcconfig   # then put the public Twitch client id in it (git-ignored)
apple/scripts/build-shared.sh debug         # the XCFramework (about a minute once Kotlin/Native is downloaded)
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
- **Localization.** `apple/scripts/sync-strings.py` generates `Localizable.xcstrings` from the Android resources (the TV's 156 strings in 36 locales
  and the phone-only strings in English, French and Arabic; the other locales fall back to English, as on Android). The Android resource name is
  the catalog key; `%s` becomes `%@`; locales are mapped (`in` to `id`, `iw` to `he`, `zh-rCN` to `zh-Hans`...). CI fails when the catalog is stale.
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

## Verified, and not

Verified on the iPhone 17 simulator (iOS 26.5) and the iPhone 16 simulator (iOS 18.3, material fallback), with screenshots in
`docs/screenshots/ios/`, and by 17 Swift tests (`apple/Tests`: formatting, the shell's navigation rules, the player session, and the real Kotlin presenters
running on the demo data through the bridge). The Release configuration builds and contains neither the demo clip nor the launch arguments.

Not verified: a physical device, a real Twitch account (everything above ran on the offline demo data), real HLS playback of a live channel, Picture in
Picture and background audio on hardware, AirPlay, rotation with a real device sensor, TalkBack-equivalent VoiceOver passes, and Xcode stable releases
(Xcode 27 beta 4 only). Performance was not measured.

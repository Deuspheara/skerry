# Apple plan (iOS, macOS, tvOS in SwiftUI)

Phase 2. The shared Kotlin core (`shared/*`) gets Apple targets; each Apple platform gets a native SwiftUI app on top of the
presenters. This file records the decisions taken by default (the owner asked for "iOS, macOS, tvOS in SwiftUI" and left the
open questions of the Phase 1 brief unanswered); each can be changed later without redoing the foundations.

## Decisions

| Topic | Decision | Why |
|---|---|---|
| Kotlin targets | `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `tvosArm64`, `tvosSimulatorArm64`. No x64 (AGENTS.md). | Apple silicon only; fewer slices, shorter builds. |
| Minimum OS | iOS 18, macOS 15, tvOS 18 | One release behind the Liquid Glass generation (26). Lets the apps use modern SwiftUI (`@Observable`, `NavigationStack`, `TabView` styles) and fall back to `Material` below 26. |
| Liquid Glass | `if #available(iOS 26, macOS 26, tvOS 26, *)` around `glassEffect`, `GlassEffectContainer`, `.buttonStyle(.glass)`; `.ultraThinMaterial` otherwise. One small `Glass` helper per app, never scattered `#available`. | The design language is the point of the native UIs, the fallback keeps the min OS low. |
| Playback | Native HLS with `AVPlayer`. The shared `:shared:stream` resolves the signed master playlist URL and quality variants; Swift hands the URL to `AVPlayer`. | No web or CORS problem on Apple (the Android WebView/embed fallback is not needed), hardware decode, PiP, AirPlay and Now Playing come with `AVKit`. |
| Chat | Same shared chat client (Ktor WebSocket, Darwin engine). | Already in `:shared:chat`. |
| Images | Left to Swift (`AsyncImage` plus a small `URLCache`-backed loader). Emote animation (WebP/GIF) is a step B concern. | Coil is not multiplatform-ready for our use; the system loaders are good enough. |
| Storage | Settings and VOD progress: the same `DataStore<Preferences>` (KMP) in the app support directory. Tokens: Keychain (`Security`). Screen snapshots: files in the Caches directory. | Same file format and keys as Android (`StorageNames`). |
| Bridge | Plain Kotlin/Native Objective-C interop plus **SKIE 0.10.16** if it works with Kotlin 2.4.21 and the Xcode beta (suspend to `async`, `StateFlow` to `AsyncSequence`, sealed classes to `enum`); otherwise thin Swift wrappers (`FlowObserver`) documented in `apple/README.md`. | SKIE removes most hand-written glue. |
| Framework | One static XCFramework `TwitchShared` (umbrella over core, stream, chat, player-api, presenters, platform-apple), built by `apple/scripts/build-shared.sh`. | One artifact for Xcode; static avoids embedding and signing steps. |
| Xcode project | **XcodeGen** (`apple/project.yml`, already installed through Homebrew). The generated `.xcodeproj` is git-ignored. | A reviewable text file instead of a merge-conflict-prone pbxproj. |
| Distribution | Out of scope: simulator and personal-team signing only. No provisioning profiles, certificates or team ids are committed. | Needs the owner's developer account. |
| Toolchain | Xcode 27.0 beta 4 (Swift 6.4, SDKs 27.0) is what this machine has. | Beta: expect breakage, CI must pin the Xcode version once a stable release exists. |

## Targets per platform

| Platform | Slice | Runs here |
|---|---|---|
| iOS | `iosArm64`, `iosSimulatorArm64` | simulator (iOS 26.x / 27.0 runtimes installed) |
| macOS | `macosArm64` | host |
| tvOS | `tvosArm64`, `tvosSimulatorArm64` | compiles and links; **no tvOS simulator runtime is installed** on this machine (the SDK is), so it cannot be run until the runtime is downloaded in Xcode |

## Steps

- **A. Foundations (this step):** Apple targets in the `kmp-library` convention, everything compiled for Native, shared tests on
  `iosSimulatorArm64` and `macosArm64`, Apple platform seams, `TwitchShared` XCFramework, XcodeGen skeleton and an iOS
  smoke app proving the bridge.
- **B. iOS app:** navigation (tabs, search, settings), Home/Browse/Category/Following, player with `AVPlayer`, chat, sign-in.
- **C. macOS and tvOS apps:** same presenters; sidebar and menus on macOS, focus engine and top shelf-like rows on tvOS.
- **D. Polish:** Liquid Glass pass, PiP/AirPlay/Now Playing, emotes, accessibility, localization (the same 36 locales).

## Constraints that carry over

`AGENTS.md` applies: no platform API in `commonMain` (`verifyCommonMain` stays in `check`), platform code behind interfaces
injected through Koin, presenters expose `StateFlow` plus `onEvent`, conventions in `build-logic/`, pinned versions, no
attribution in commits.

## Status (step A, done)

- All shared modules compile for the five Apple targets; their tests run on macOS (`macosArm64Test`) and the iOS simulator
  (`iosSimulatorArm64Test`) as part of `./gradlew check`: 362 tests plus 11 in `:shared:platform-apple` (snapshot files, number
  format, DataStore, Keychain round trip on macOS, and the full presenter graph resolved from the production module list).
  `tvosSimulatorArm64Test` is opt-in (no tvOS runtime installed here).
- `:shared:platform-apple` holds the Apple platform seams (`AppleLogger`, `AppleDeviceProfile`, `AppleNumberFormatter`,
  `AppleSnapshotStorage`, `KeychainTokenStore`, `AppleDataStores`, `AppleNetworkStatus` on Network.framework, Darwin engine,
  `UnavailableVoiceInput`), the Koin graph (`appleModules`) and the Swift entry point (`TwitchApp`, `TwitchGraph`, `PresenterScope`).
- `apple/scripts/build-shared.sh` produces the static `TwitchShared.xcframework` (iOS device and simulator, macOS, tvOS device and
  simulator) with SKIE. The XcodeGen project builds the smoke app for the iOS simulator and device, macOS and the tvOS simulator.
- Verified by running: the iOS smoke app on an iPhone 17 (iOS 26.5) simulator shows the settings presenter state, the Home presenter
  reaching `SignedOut`, and the snapshot and Keychain round trips; the macOS smoke app launches and stays up.

### Found on the way

- `Dispatchers.IO` is not available in common Kotlin/Native code: `ioDispatcher` is an `expect val` in `:shared:core`.
- The first run of the Swift smoke app showed Home loading forever: `TwitchApp.start` did not call `SessionRepository.start()`
  (Android does it in `Application.onCreate`). It does now.
- Keychain access needs a signed app: the unsigned Kotlin/Native test executable on the iOS simulator gets `errSecMissingEntitlement`, so
  that round trip is tested on macOS and by the (ad-hoc signed) smoke app.
- SKIE sends build analytics by default; the build disables it (`skie { analytics { ... } }`).
- Debug XCFramework size is about 380 MB (five static slices with debug info); release is far smaller and what apps link.

### Not verified

tvOS runtime (no simulator runtime installed), a physical device, the `Toggle animations` round trip through the DataStore (the button
was not tapped), Linux CI for the Android jobs now that Apple targets exist (the Kotlin plugin disables them off a Mac, but the CI was not
re-run), and Xcode stable releases (everything here ran on Xcode 27.0 beta 4).

## Status (step B, iOS app)

The iOS app exists: see `docs/IOS_APP.md` (architecture, parity table, what is verified). Steps C (macOS, tvOS) and D (polish) remain.

## Next: step B (iOS app), original notes

Needs from you: the Twitch client id in `apple/Config/Local.xcconfig`; a decision on the minimum OS versions (iOS 18 assumed); and, for a
real device, a development team id. Work: replace the smoke app by the real app (tab bar with Home, Browse, Following, Search, Settings,
sign-in with the device code, lists from the presenters, player with `AVPlayer` + chat), a `Glass` helper with `#available(iOS 26, *)`,
image loading, and the emote rendering.

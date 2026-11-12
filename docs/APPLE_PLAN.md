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

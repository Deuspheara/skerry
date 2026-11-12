# Apple apps

SwiftUI apps for iOS, macOS and tvOS on top of the shared Kotlin core. Plan and decisions: `docs/APPLE_PLAN.md`.

## What is here

| Path | What |
|---|---|
| `scripts/build-shared.sh` | Builds the `TwitchShared` XCFramework with Gradle and copies it to `Frameworks/` (git-ignored). |
| `project.yml` | XcodeGen spec. `xcodegen generate` writes `TwitchPlayerApple.xcodeproj` (git-ignored). |
| `Config/Base.xcconfig` | Build settings; the public Twitch client id goes in `Config/Local.xcconfig` (git-ignored, see the `.example`). |
| `SmokeApp/` | Smoke app (one SwiftUI source set, three targets: `SmokeIOS`, `SmokeMac`, `SmokeTV`) that proves the Kotlin bridge. |

## Build and run

```sh
brew install xcodegen                      # once
apple/scripts/build-shared.sh debug        # first run downloads Kotlin/Native and takes several minutes
(cd apple && xcodegen generate)
xcodebuild -project apple/TwitchPlayerApple.xcodeproj -scheme SmokeIOS \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Run it from Xcode, or `xcrun simctl install booted <SmokeIOS.app>` and `xcrun simctl launch booted com.twitchplayer.smoke`.
The smoke app shows the settings presenter's state (its `Toggle animations` button sends an event through the presenter into the
on-disk DataStore), the Home presenter's `Load` state, and one line per platform seam (snapshot file, Keychain, locale, number format).

## How Swift talks to Kotlin

`TwitchApp.shared.start(clientId:)` starts Koin and the stored session and returns a `TwitchGraph`; `graph.home(scope:)` and the other
factories build presenters. `PresenterScope` is the lifetime of one screen (call `cancel()` when it goes away).
`graph.playback` resolves a live channel, a replay or a clip to the signed HLS URLs `AVPlayer` opens.

The framework is built with **SKIE 0.10.16** (works with Kotlin 2.4.21 and Xcode 27 beta 4). What to know when writing Swift:

- `suspend` functions of **concrete classes** become `async throws` (`try await graph.playback.live(channel:)`). Suspend methods that are
  *requirements of a Kotlin interface* (`TokenStore`, `SnapshotStorage`) are not callable as `async` through the protocol: call them from
  Kotlin (see `AppleSelfTest`) or expose a concrete class.
- `StateFlow` properties come back as the raw Objective-C class; bridge them with `as SkieSwiftStateFlow<T>` and iterate with
  `for await`. Generic Kotlin types are erased (`Load.Ready.data` is `AnyObject?`): cast it (`as? HomeContent`).
- Sealed types become Swift enums through `onEnum(of:)` where SKIE generated one (`Load`, events, ...).
- Never build Kotlin state classes from Swift (`init()` is unavailable); keep them optional.
- The framework has **arm64 slices only** (AGENTS.md). The Xcode project sets `ARCHS = arm64`; without it the simulator also compiles an
  x86_64 variant, which cannot see the Swift half of the framework and fails with "cannot find type 'SkieSwiftStateFlow'".
- Swift 5 language mode (`SWIFT_VERSION: 5.0`): Kotlin types are not `Sendable`.

## Not done here

tvOS: the SDK is installed but no tvOS simulator runtime, so `SmokeTV` builds and is not run (and the Kotlin tests of
`tvosSimulatorArm64` are opt-in, `-PtvosSimulatorTests=true`). Signing, provisioning and distribution are out of scope (ad-hoc signing for
the simulator only; nothing secret is committed).

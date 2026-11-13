import AVFoundation
import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

/// The state transitions of the AVPlayer wrapper, on the bundled demo clip (no network). Each test tears its controller down, so no system hook
/// (remote command, observer) outlives it.
@MainActor
final class PlayerControllerTests: XCTestCase {
    private let demo = "demo://stream"
    private let badUrl = "ht tp://not a url"

    private func wait(timeout: Duration = .seconds(10), _ condition: () -> Bool) async throws {
        let deadline = ContinuousClock.now + timeout
        while !condition(), ContinuousClock.now < deadline { try await Task.sleep(for: .milliseconds(50)) }
    }

    private func make(live: Bool = true, autoplay: Bool = true, startAt: Double = 0) -> PlayerController {
        let controller = PlayerController()
        controller.load(masterUrl: demo, variants: [], live: live, title: "t", subtitle: "s", startAt: startAt, autoplay: autoplay)
        return controller
    }

    func testPlayingFollowsPlayAndPause() async throws {
        let controller = make()
        defer { controller.tearDown() }
        try await wait { controller.isPlaying }
        XCTAssertTrue(controller.isPlaying)
        controller.pause()
        try await wait { !controller.isPlaying }
        XCTAssertFalse(controller.isPlaying)
        controller.togglePlay()
        try await wait { controller.isPlaying }
        XCTAssertTrue(controller.isPlaying, "toggle resumes a paused player")
    }

    func testAPictureAppearsOnceTheClipIsLoaded() async throws {
        let controller = make()
        defer { controller.tearDown() }
        try await wait { controller.hasPicture }
        XCTAssertTrue(controller.hasPicture)
        XCTAssertGreaterThan(controller.videoSize.width, 0)
    }

    func testStopResetsEverythingTheUIReads() async throws {
        let controller = make()
        try await wait { controller.hasPicture }
        controller.stop()
        XCTAssertFalse(controller.isPlaying)
        XCTAssertFalse(controller.hasPicture)
        XCTAssertEqual(controller.position, 0)
        XCTAssertFalse(controller.failed)
        controller.tearDown()
    }

    func testAnUnplayableAddressFailsAndAFailedRetryFailsAgain() {
        let controller = PlayerController()
        defer { controller.tearDown() }
        controller.load(masterUrl: badUrl, variants: [], live: true, title: "", subtitle: "")
        XCTAssertTrue(controller.failed)
        controller.retry()
        XCTAssertTrue(controller.failed, "a reload that fails again must be reported again, not hidden by clearing the flag afterwards")
    }

    func testRetryAfterAFailureReloadsAndClearsTheFlag() async throws {
        let controller = make()
        defer { controller.tearDown() }
        controller.markFailed()
        XCTAssertTrue(controller.failed)
        controller.retry()
        XCTAssertFalse(controller.failed)
        try await wait { controller.isPlaying }
        XCTAssertTrue(controller.isPlaying)
    }

    func testALiveRetryStartsAtTheLiveEdgeAndAReplayRetryResumes() {
        let live = make(live: true, autoplay: false)
        defer { live.tearDown() }
        live.seek(to: 42)
        live.retry()
        XCTAssertEqual(live.lastOpen?.startAt, 0, "a live stream has no position to resume")
        let replay = make(live: false, autoplay: false)
        defer { replay.tearDown() }
        replay.seek(to: 42)
        replay.retry()
        XCTAssertEqual(replay.lastOpen?.startAt, 42, "a replay resumes where it stopped")
    }

    func testChoosingAQualityReloadsThatVariantAndAudioOnlyComesBack() {
        let controller = PlayerController()
        defer { controller.tearDown() }
        controller.load(masterUrl: demo, variants: [
            QualityOption(id: "720p60", name: "720p60", url: demo, height: 720, audioOnly: false),
            QualityOption(id: "audio_only", name: "audio", url: demo, height: 0, audioOnly: true),
        ], live: true, title: "", subtitle: "", autoplay: false)
        controller.select(quality: "720p60")
        XCTAssertEqual(controller.qualityId, "720p60")
        XCTAssertFalse(controller.audioOnly)
        controller.setAudioOnly(true, previous: "720p60")
        XCTAssertTrue(controller.audioOnly)
        controller.setAudioOnly(false, previous: "720p60")
        XCTAssertFalse(controller.audioOnly)
        XCTAssertEqual(controller.qualityId, "720p60")
        controller.select(quality: "does-not-exist")
        XCTAssertEqual(controller.qualityId, "720p60", "an unknown quality changes nothing")
    }

    func testTheSleepTimerIsSetAndCancelled() {
        let controller = make(autoplay: false)
        defer { controller.tearDown() }
        controller.setSleepTimer(minutes: 15)
        XCTAssertEqual(controller.sleepEndsAt?.timeIntervalSinceNow ?? 0, 900, accuracy: 5)
        controller.setSleepTimer(minutes: nil)
        XCTAssertNil(controller.sleepEndsAt)
    }

    func testTheRateAppliesNowAndOnResume() async throws {
        let controller = make()
        defer { controller.tearDown() }
        try await wait { controller.isPlaying }
        controller.setRate(1.5)
        XCTAssertEqual(controller.player.defaultRate, 1.5)
        XCTAssertEqual(controller.player.rate, 1.5)
        controller.pause()
        try await wait { !controller.isPlaying }
        controller.setRate(2)
        XCTAssertEqual(controller.player.defaultRate, 2, "a paused player keeps the rate for when it resumes")
    }

    func testTearDownRemovesTheSystemHooksAndCreatingControllersDoesNotStackThem() {
        for _ in 0..<5 {
            let controller = PlayerController()
            XCTAssertEqual(controller.remoteTargetCount, 5)
            controller.tearDown()
            XCTAssertEqual(controller.remoteTargetCount, 0)
        }
    }

    #if os(iOS) || os(tvOS)
    func testTheAudioSessionIsOnlyActiveWhilePlaying() async throws {
        let controller = PlayerController()
        XCTAssertFalse(controller.audioSessionActive, "creating the controller (launching the app) must not take the audio session from the viewer's music")
        controller.load(masterUrl: demo, variants: [], live: true, title: "", subtitle: "")
        XCTAssertTrue(controller.audioSessionActive)
        controller.stop()
        XCTAssertFalse(controller.audioSessionActive)
        controller.tearDown()
    }
    #endif
}

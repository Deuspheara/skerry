import XCTest
import TwitchShared
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

/// Regression: opening a replay, a clip or a channel that cannot be played used to END THE APP ("Uncaught Kotlin exception" through a suspend call that was not
/// `@Throws`). The demo resolvers fail on demand (`gone`, `subs`, `token`, `net` replays, a `gone` clip, the `offline` channel): each must come back as an error the
/// sheet shows, and the player must stay alive.
@MainActor
final class PlaybackFailureTests: XCTestCase {
    private func makeApp() -> AppModel {
        AppModel(launch: LaunchMode(demo: true, signedOut: false, tab: nil, push: nil, open: nil, sheet: nil))
    }

    private func wait(_ condition: () -> Bool) async throws {
        let deadline = ContinuousClock.now + .seconds(8)
        while !condition(), ContinuousClock.now < deadline { try await Task.sleep(for: .milliseconds(40)) }
    }

    func testAThrowingResolverCrossesTheBridgeAsAnErrorNotACrash() async {
        let app = makeApp()
        do {
            _ = try await app.graph.playback.replay(vodId: "gone")
            XCTFail("a removed replay must throw")
        } catch {
            XCTAssertEqual(PlaybackProblem.from(error), .gone)
        }
        do {
            _ = try await app.graph.playback.clip(slug: "gone")
            XCTFail("a clip that is gone must throw")
        } catch {
            XCTAssertEqual(PlaybackProblem.from(error), .network, "a plain failure is shown as a network one")
        }
        do {
            _ = try await app.graph.playback.live(channel: "offline")
            XCTFail("an offline channel must throw")
        } catch {
            XCTAssertEqual(PlaybackProblem.from(error), .offline)
        }
    }

    func testEachReplayFailureIsClassified() async {
        let app = makeApp()
        let expected: [(String, PlaybackProblem)] = [("gone", .gone), ("subs", .subscribersOnly), ("token", .unavailable), ("net", .network)]
        for (id, problem) in expected {
            do {
                _ = try await app.graph.playback.replay(vodId: id)
                XCTFail("\(id) must throw")
            } catch {
                XCTAssertEqual(PlaybackProblem.from(error), problem, id)
            }
        }
    }

    func testOpeningAFailingReplayShowsTheReasonAndKeepsTheAppAlive() async throws {
        let app = makeApp()
        for (id, problem) in [("gone", PlaybackProblem.gone), ("subs", .subscribersOnly)] {
            app.session.open(.replay(id: id, title: id, channelLogin: "x", channelName: "x", thumbnailUrl: ""))
            try await wait { app.session.playback.problem != nil }
            XCTAssertEqual(app.session.playback.problem, problem)
            XCTAssertTrue(app.session.playback.controller.failed)
            app.session.close()
        }
    }

    func testAFailingClipAndAnOfflineChannelAreHandledToo() async throws {
        let app = makeApp()
        app.session.open(.clip(slug: "gone", title: "c", channelLogin: "x", channelName: "x", thumbnailUrl: ""))
        try await wait { app.session.playback.problem != nil }
        XCTAssertEqual(app.session.playback.problem, .network)
        app.session.close()
        app.session.open(.live(login: "offline", name: "offline", title: "t", avatarUrl: nil, thumbnailUrl: ""))
        try await wait { app.session.playback.offline }
        XCTAssertTrue(app.session.playback.offline)
        app.session.close()
    }

    func testRetryResolvesAgainFromTheTarget() async throws {
        let app = makeApp()
        app.session.open(.replay(id: "gone", title: "g", channelLogin: "x", channelName: "x", thumbnailUrl: ""))
        try await wait { app.session.playback.problem != nil }
        app.session.playback.retry()
        XCTAssertNil(app.session.playback.problem, "the retry started again from the beginning")
        try await wait { app.session.playback.problem != nil }
        XCTAssertEqual(app.session.playback.problem, .gone)
        app.session.close()
    }

    func testReplayPositionCallsThrowSafely() async throws {
        let app = makeApp()
        let position = try await app.graph.replayProgress.position(vodId: "never-seen")
        XCTAssertEqual(position, 0)
    }
}

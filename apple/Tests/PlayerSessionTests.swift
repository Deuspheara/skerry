import XCTest
@testable import TwitchIOS

@MainActor
final class PlayerSessionTests: XCTestCase {
    private func makeApp() -> AppModel {
        AppModel(launch: LaunchMode(demo: true, signedOut: false, tab: nil, push: nil, open: nil, sheet: nil))
    }

    private let target = PlaybackTarget.live(login: "lunaria", name: "Lunaria", title: "Run", avatarUrl: nil, thumbnailUrl: "")

    func testOpeningShowsTheSheetExpanded() {
        let session = makeApp().session
        session.open(target)
        XCTAssertEqual(session.target, target)
        XCTAssertEqual(session.position, .expanded)
        session.close()
    }

    func testMinimiseAndExpandKeepTheSameSession() {
        let session = makeApp().session
        session.open(target)
        let generation = session.generation
        session.minimise()
        XCTAssertEqual(session.position, .collapsed)
        session.expand()
        XCTAssertEqual(session.position, .expanded)
        XCTAssertEqual(session.generation, generation)
        session.close()
    }

    func testOpeningTheSameTargetAgainExpandsWithoutReloading() {
        let session = makeApp().session
        session.open(target)
        let generation = session.generation
        session.minimise()
        session.open(target)
        XCTAssertEqual(session.position, .expanded)
        XCTAssertEqual(session.generation, generation, "the stream must not restart")
        session.close()
    }

    func testClosingEndsTheSession() {
        let session = makeApp().session
        session.open(target)
        session.close()
        XCTAssertNil(session.target)
        XCTAssertFalse(session.playback.isLive)
    }

    func testOpeningAnotherTargetReplacesThePreviousOne() {
        let session = makeApp().session
        session.open(target)
        let other = PlaybackTarget.live(login: "nova", name: "Nova", title: "Course", avatarUrl: nil, thumbnailUrl: "")
        session.open(other)
        XCTAssertEqual(session.target, other)
        XCTAssertEqual(session.generation, 2)
        session.close()
    }

    func testTheChannelPageIsOneTapAwayAndTheSheetMinimises() {
        let app = makeApp()
        app.session.open(target)
        app.openChannel("lunaria")
        XCTAssertEqual(app.session.position, .collapsed)
        XCTAssertEqual(app.paths[app.tab], [.channel(login: "lunaria")])
        app.session.close()
    }

    func testQualityOptionsPutAutoFirstAndAudioOnlyLast() {
        let controller = PlayerController()
        controller.load(
            masterUrl: "demo://stream",
            variants: [
                QualityOption(id: "480p30", name: "480p", url: "u1", height: 480, audioOnly: false),
                QualityOption(id: "audio_only", name: "audio", url: "u3", height: 0, audioOnly: true),
                QualityOption(id: "720p60", name: "720p60", url: "u2", height: 720, audioOnly: false),
            ],
            live: true, title: "t", subtitle: "s", autoplay: false,
        )
        XCTAssertEqual(controller.qualities.map(\.id), ["auto", "720p60", "480p30", "audio_only"])
        controller.stop()
    }
}

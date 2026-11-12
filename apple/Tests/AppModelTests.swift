import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

/// The shell's navigation rules, on the offline demo data (no network, no account). The Kotlin bridge itself is exercised too: the presenters
/// really run and the states really arrive in Swift.
@MainActor
final class AppModelTests: XCTestCase {
    private func makeApp(signedOut: Bool = false) -> AppModel {
        AppModel(launch: LaunchMode(demo: true, signedOut: signedOut, tab: nil, push: nil, open: nil, sheet: nil))
    }

    /// Polls until `condition` holds (the presenters run on the main dispatcher, so the test must yield to them).
    private func wait(timeout: Duration = .seconds(8), _ condition: () -> Bool) async throws {
        let deadline = ContinuousClock.now + timeout
        while !condition(), ContinuousClock.now < deadline { try await Task.sleep(for: .milliseconds(50)) }
    }

    func testAPageAlreadyOnTheStackIsReturnedToNotPushedTwice() {
        let app = makeApp()
        app.push(.channel(login: "lunaria"))
        app.push(.category(id: "racers", name: "Pixel Racers"))
        app.push(.channel(login: "lunaria"))
        XCTAssertEqual(app.paths[.home], [.channel(login: "lunaria")])
    }

    func testTappingTheSelectedTabPopsToItsRoot() {
        let app = makeApp()
        app.push(.settings)
        app.select(.home)
        XCTAssertEqual(app.paths[.home], [])
    }

    func testTabsKeepTheirOwnStacks() {
        let app = makeApp()
        app.push(.search)
        app.select(.browse)
        app.push(.settings)
        app.select(.home)
        XCTAssertEqual(app.paths[.home], [.search])
        XCTAssertEqual(app.paths[.browse], [.settings])
    }

    func testSigningOutLeavesTheFollowingTab() {
        let app = makeApp()
        app.select(.following)
        app.push(.settings)
        app.onSignedOut()
        XCTAssertEqual(app.tab, .home)
        XCTAssertEqual(app.paths[.following] ?? [], [])
    }

    func testHomeReceivesItsContentFromTheSharedPresenter() async throws {
        let app = makeApp()
        let model = HomeModel(graph: app.graph)
        try await wait { !model.state.isLoading }
        let content = try XCTUnwrap(model.state.content, "the demo Home never became ready")
        XCTAssertFalse((content.forYou as? [Any] ?? []).isEmpty)
        XCTAssertFalse((content.categories as? [Any] ?? []).isEmpty)
    }

    func testSearchAnswersThroughThePresenter() async throws {
        let app = makeApp()
        let model = SearchModel(graph: app.graph)
        model.setQuery("lun")
        try await wait { if let state = model.state { !state.searching && state.resultsFor == "lun" } else { false } }
        let state = try XCTUnwrap(model.state)
        XCTAssertEqual(state.resultsFor, "lun")
        XCTAssertFalse(state.failed)
    }
}

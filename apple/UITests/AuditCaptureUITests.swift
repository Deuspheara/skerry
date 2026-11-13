import XCTest

/// Screen audit helper (docs/screenshots/audit/apple): drives the iOS app through its screens and writes PNGs to `AUDIT_OUT`
/// (env `TEST_RUNNER_AUDIT_OUT` with xcodebuild), named `<AUDIT_TAG>-<name>.png`. Not part of any test plan: run it by name, see
/// docs/screenshots/audit/apple/INDEX.md. Labels are the French ones of the simulator language.
final class AuditCaptureUITests: XCTestCase {
    var app: XCUIApplication!
    let env = ProcessInfo.processInfo.environment
    var out: String { env["AUDIT_OUT"] ?? "/tmp/audit" }
    var tag: String { env["AUDIT_TAG"] ?? "ios" }
    var extra: [String] { (env["AUDIT_ARGS"] ?? "").split(separator: " ").map(String.init) }
    var channel: String { env["AUDIT_CHANNEL"] ?? "" }

    override func setUp() {
        continueAfterFailure = true
        try? FileManager.default.createDirectory(atPath: out, withIntermediateDirectories: true)
    }

    func launch(_ args: [String], wait: TimeInterval = 6) {
        if app != nil { app.terminate() }
        app = XCUIApplication()
        app.launchArguments = args + extra
        app.launch()
        Thread.sleep(forTimeInterval: wait)
    }

    func shot(_ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try? png.write(to: URL(fileURLWithPath: "\(out)/\(tag)-\(name).png"))
    }

    func swipeUp(times: Int = 1, dy: CGFloat = 0.55) {
        for _ in 0..<times {
            let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
            let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8 - dy))
            from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.3)
        }
        Thread.sleep(forTimeInterval: 1)
    }

    func swipeDown(times: Int = 1) {
        for _ in 0..<times {
            let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25))
            let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
            from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.3)
        }
        Thread.sleep(forTimeInterval: 1)
    }

    @discardableResult
    func tap(_ label: String, wait: TimeInterval = 1.2) -> Bool {
        let b = app.buttons[label].firstMatch
        guard b.waitForExistence(timeout: 3) else { return false }
        if b.isHittable { b.tap() } else { b.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap() }
        Thread.sleep(forTimeInterval: wait)
        return true
    }

    func tabBar(_ id: String) { app.buttons[id].firstMatch.tap(); Thread.sleep(forTimeInterval: 1.5) }

    // MARK: Demo (offline) runs

    func testDemoHomeFollowingBrowse() {
        launch(["-demo"])
        shot("home")
        swipeUp(times: 2); shot("home-scrolled")
        tabBar("tab-following"); shot("following")
        swipeUp(times: 2); shot("following-scrolled-offline")
        swipeUp(times: 2); shot("following-bottom")
        tabBar("tab-browse"); shot("browse")
        tap("Jeux", wait: 0.25); shot("browse-chip-games-mid")
        Thread.sleep(forTimeInterval: 1.5); shot("browse-chip-games")
        tap("IRL"); shot("browse-chip-irl")
        tap("Musique"); shot("browse-chip-music")
        tap("Tous"); swipeUp(times: 2); shot("browse-scrolled")
        swipeDown(times: 2)
        tap("Valmoor", wait: 3); shot("category")
        swipeUp(); shot("category-scrolled")
    }

    func testDemoSignedOutAndSignIn() {
        launch(["-demo", "-demo-signed-out"])
        shot("home-signed-out")
        tabBar("tab-browse"); shot("browse-signed-out")
        launch(["-demo", "-demo-signed-out", "-sheet", "signin"])
        shot("signin-sheet")
    }

    func testDemoSearch() {
        launch(["-demo", "-push", "search"])
        shot("search-empty")
        let field = app.textFields.firstMatch
        if field.waitForExistence(timeout: 3) {
            field.tap(); field.typeText("lun"); Thread.sleep(forTimeInterval: 0.3); shot("search-typing-loading")
            Thread.sleep(forTimeInterval: 2); shot("search-results")
            app.buttons["Chaînes"].firstMatch.tap(); Thread.sleep(forTimeInterval: 1); shot("search-filter-channels")
            app.buttons["Catégories"].firstMatch.tap(); Thread.sleep(forTimeInterval: 1); shot("search-filter-categories")
            field.tap()
            for _ in 0..<3 { field.typeText(XCUIKeyboardKey.delete.rawValue) }
            field.typeText("zzzzqq"); Thread.sleep(forTimeInterval: 2.5); shot("search-no-results")
        }
        launch(["-demo", "-push", "search"])
        let f2 = app.textFields.firstMatch
        if f2.waitForExistence(timeout: 3) {
            f2.tap(); f2.typeText("ondine\n"); Thread.sleep(forTimeInterval: 2)
            app.buttons.matching(identifier: "chevron.backward").firstMatch.tap()
        }
        launch(["-demo", "-push", "search"]); shot("search-recents")
    }

    func testDemoChannelAndSettings() {
        launch(["-demo", "-push", "channel:lunaria"])
        shot("channel-live")
        swipeUp(times: 2); shot("channel-live-scrolled")
        launch(["-demo", "-push", "channel:hibou"])
        shot("channel-offline")
        swipeUp(); shot("channel-offline-scrolled")
        launch(["-demo", "-push", "settings"])
        shot("settings-top")
        swipeUp(times: 1); shot("settings-2")
        swipeUp(times: 1); shot("settings-3")
        swipeUp(times: 2); shot("settings-bottom")
        swipeDown(times: 5)
        if app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Langue'")).firstMatch.exists {
            app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Langue'")).firstMatch.tap(); Thread.sleep(forTimeInterval: 1.5); shot("settings-language-picker")
        }
    }

    func testDemoPlayerLive() {
        launch(["-demo", "-open", "lunaria"], wait: 7)
        shot("player-live-portrait")
        app.buttons["Émotes"].firstMatch.tap(); Thread.sleep(forTimeInterval: 1.5); shot("player-emote-picker")
        let row = app.otherElements.matching(NSPredicate(format: "label CONTAINS 'Tom_Lefebvre'")).firstMatch
        if app.buttons["Clavier"].firstMatch.exists { app.buttons["Clavier"].firstMatch.tap() } else { app.buttons["Émotes"].firstMatch.tap() }
        Thread.sleep(forTimeInterval: 1)
        if row.exists { row.press(forDuration: 1.2); Thread.sleep(forTimeInterval: 1); shot("player-chat-row-longpress") }
        // Landscape fullscreen: default, then each chat mode.
        XCUIDevice.shared.orientation = .landscapeLeft; Thread.sleep(forTimeInterval: 2.5)
        shot("player-landscape-chat-default")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.5)).tap(); Thread.sleep(forTimeInterval: 1); shot("player-landscape-controls")
        for i in 1...3 {
            if app.buttons["Chat"].firstMatch.exists { app.buttons["Chat"].firstMatch.tap() } else {
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.5)).tap(); Thread.sleep(forTimeInterval: 0.8)
                app.buttons["Chat"].firstMatch.tap()
            }
            Thread.sleep(forTimeInterval: 1.5); shot("player-landscape-chat-mode\(i)")
        }
        XCUIDevice.shared.orientation = .portrait; Thread.sleep(forTimeInterval: 2.5)
        shot("player-back-portrait")
        app.buttons["Réduire le lecteur"].firstMatch.tap(); Thread.sleep(forTimeInterval: 2); shot("strip-over-home")
        tabBar("tab-browse"); shot("strip-over-browse")
        tabBar("tab-following"); shot("strip-over-following")
    }

    func testDemoReplayClipAndErrors() {
        launch(["-demo", "-open-vod", "demo"], wait: 7); shot("player-replay-portrait")
        launch(["-demo", "-open-clip", "demo"], wait: 7); shot("player-clip-portrait")
        launch(["-demo", "-open", "hibou"], wait: 7); shot("player-offline-channel")
    }

    /// The sheet while a finger holds it halfway between expanded and the strip (the screenshot is taken during the hold).
    func testDemoDragMidState() {
        launch(["-demo", "-open", "lunaria"], wait: 7)
        let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.6))
        let queue = DispatchQueue(label: "audit.shot")
        let name = "player-drag-mid"
        let exp = expectation(description: "shot")
        queue.asyncAfter(deadline: .now() + 2.2) {
            let png = XCUIScreen.main.screenshot().pngRepresentation
            try? png.write(to: URL(fileURLWithPath: "\(self.out)/\(self.tag)-\(name).png"))
            exp.fulfill()
        }
        from.press(forDuration: 0.1, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 3.5)
        wait(for: [exp], timeout: 10)
    }

    // MARK: Real (network) runs: AUDIT_CHANNEL is a live channel login

    func testRealScreens() {
        launch([], wait: 9)
        shot("real-home-signed-out")
        tabBar("tab-browse"); Thread.sleep(forTimeInterval: 3); shot("real-browse")
        swipeUp(); shot("real-browse-scrolled")
        tap("Jeux", wait: 3); shot("real-browse-chip-games")
        swipeDown(times: 2)
        // A coordinate tap on the first category card (an element query on frame sizes is far too slow on the real feed).
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.38)).tap(); Thread.sleep(forTimeInterval: 4); shot("real-category")
        launch(["-push", "search"]); 
        let field = app.textFields.firstMatch
        if field.waitForExistence(timeout: 3) {
            shot("real-search-empty")
            field.tap(); field.typeText("valorant"); Thread.sleep(forTimeInterval: 0.4); shot("real-search-typing")
            Thread.sleep(forTimeInterval: 3); shot("real-search-results")
        }
        if !channel.isEmpty {
            launch(["-push", "channel:\(channel)"], wait: 6); shot("real-channel"); swipeUp(); shot("real-channel-scrolled")
            launch(["-open", channel], wait: 14); shot("real-player-portrait")
            XCUIDevice.shared.orientation = .landscapeLeft; Thread.sleep(forTimeInterval: 3); shot("real-player-landscape")
            XCUIDevice.shared.orientation = .portrait; Thread.sleep(forTimeInterval: 2)
            app.buttons["Réduire le lecteur"].firstMatch.tap(); Thread.sleep(forTimeInterval: 2); shot("real-strip-over-home")
        }
        launch(["-push", "settings"]); shot("real-settings")
        launch(["-open-vod", "999999999"], wait: 8); shot("real-error-replay-unavailable")
        launch(["-push", "channel:thischanneldoesnotexist9"], wait: 6); shot("real-error-channel-unknown")
    }
}

import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

final class FormattingTests: XCTestCase {
    func testViewerCountsUseTheSameBreaksAsAndroid() {
        XCTAssertEqual(formatViewers(0), "0")
        XCTAssertEqual(formatViewers(999), "999")
        XCTAssertEqual(formatViewers(1_000), "1K")
        XCTAssertEqual(formatViewers(14_820), "15K")
        XCTAssertEqual(formatViewers(6_340), "6.3K")
        XCTAssertEqual(formatViewers(1_300_000), "1.3M")
    }

    func testDurations() {
        XCTAssertEqual(formatDuration(seconds: 59), "0:59")
        XCTAssertEqual(formatDuration(seconds: 3_725), "1:02:05")
    }

    func testUptimeNeedsAStartTime() {
        XCTAssertNil(uptime(since: 0))
        let now = Date(timeIntervalSince1970: 10_000)
        XCTAssertEqual(uptime(since: 10_000_000 - 3_725_000, now: now), "1:02:05")
        XCTAssertEqual(uptime(since: 20_000_000, now: now), "0:00", "a start in the future never shows a negative time")
    }

    func testDemoArtIsRecognisedAndRealImagesAreNot() {
        XCTAssertTrue(DemoArt.isDemo(URL(string: "demoart://x/101/2")!))
        XCTAssertTrue(DemoArt.isDemo(URL(string: "demoemote://x/heart/still")!))
        XCTAssertTrue(DemoArt.isDemo(URL(string: "https://static-cdn.jtvnw.net/emoticons/v2/demo-smile/static/dark/2.0")!))
        XCTAssertFalse(DemoArt.isDemo(URL(string: "https://static-cdn.jtvnw.net/emoticons/v2/25/static/dark/2.0")!))
        XCTAssertEqual(DemoArt.emoteName(URL(string: "demoemote://x/clap/anim")!), "clap")
    }
}

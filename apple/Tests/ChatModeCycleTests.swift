import TwitchShared
import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

final class ChatModeCycleTests: XCTestCase {
    func testPortraitToggles() {
        XCTAssertEqual(ChatMode.compact.cycled(landscape: false), .hidden)
        XCTAssertEqual(ChatMode.hidden.cycled(landscape: false), .compact)
    }

    func testLandscapeCyclesHiddenSideOverlay() {
        XCTAssertEqual(ChatMode.hidden.cycled(landscape: true), .compact)
        XCTAssertEqual(ChatMode.compact.cycled(landscape: true), .overlay)
        XCTAssertEqual(ChatMode.overlay.cycled(landscape: true), .hidden)
    }
}

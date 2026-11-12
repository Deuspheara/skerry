import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

#if os(macOS)
@MainActor
final class MacPlatformTests: XCTestCase {
    func testCmdFBumpsTheSearchFocusRequest() {
        let mac = MacModel()
        XCTAssertEqual(mac.focusSearchTick, 0)
        mac.focusSearch()
        mac.focusSearch()
        XCTAssertEqual(mac.focusSearchTick, 2, "the root view reacts to every request, not only the first")
    }

    func testTheChatColumnIsVisibleByDefault() {
        XCTAssertTrue(MacModel().showChat)
    }

    func testEverySidebarSectionHasASymbolAndATitleKey() {
        for tab in AppTab.allCases {
            XCTAssertFalse(tab.systemImage.isEmpty)
            XCTAssertEqual(tab.filledImage, tab.systemImage + ".fill")
            XCTAssertTrue(tab.titleKey.hasPrefix("nav_"))
        }
    }
}
#endif

#if os(tvOS)
final class TVPlatformTests: XCTestCase {
    func testTheSignInQRCodeIsGenerated() {
        let image = TVSignInScreen.qr(for: "https://www.twitch.tv/activate?device-code=ABCDEFGH")
        XCTAssertNotNil(image)
        XCTAssertGreaterThan(image?.size.width ?? 0, 100, "a code that is too small cannot be scanned from the couch")
    }

    func testTheCardsAreSizedForTheTenFootDistance() {
        XCTAssertGreaterThanOrEqual(TVMetrics.cardWidth, 380)
        XCTAssertEqual(TVMetrics.edge, 80, "the title-safe margin of the Apple TV")
    }
}
#endif

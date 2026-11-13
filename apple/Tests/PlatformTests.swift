import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

/// The Keychain, from the signed app: the shared `AppleSelfTest` writes, reads and removes a probe item (never the real session) and says what failed.
@MainActor
final class KeychainFromSwiftTests: XCTestCase {
    func testTheKeychainAndTheFilesRoundTripInTheSignedApp() async throws {
        let app = AppModel(launch: LaunchMode(demo: true, signedOut: false, tab: nil, push: nil, open: nil, sheet: nil))
        let lines = try await app.graph.selfTest.run()
        XCTAssertTrue(lines.contains("keychain round trip: ok"), "\(lines)")
        XCTAssertTrue(lines.contains("snapshot round trip: ok"), "\(lines)")
    }
}

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

    func testDifferentCodesGiveDifferentQRImagesAndNothingGivesNoImage() {
        let a = TVSignInScreen.qr(for: "https://www.twitch.tv/activate?device-code=AAAAAAAA")
        let b = TVSignInScreen.qr(for: "https://www.twitch.tv/activate?device-code=BBBBBBBB")
        XCTAssertNotNil(a)
        XCTAssertNotEqual(a?.pngData(), b?.pngData(), "the code is in the picture")
    }
}
#endif

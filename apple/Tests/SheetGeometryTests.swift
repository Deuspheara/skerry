import XCTest
#if canImport(TwitchIOS)
@testable import TwitchIOS
#elseif canImport(TwitchMac)
@testable import TwitchMac
#elseif canImport(TwitchTV)
@testable import TwitchTV
#endif

/// The player sheet's maths. The regression these guard: the strip and the tab bar floated ~300 pt above the bottom (in the middle of the screen)
/// because the keyboard's height was taken off the container the strip's position was computed from.
final class SheetGeometryTests: XCTestCase {
    /// An iPhone 17 in portrait: 402 x 874 pt, status bar area 62, home indicator 34.
    private func phone(keyboard: CGFloat = 0, tabBar: Bool = true, progress: CGFloat = 0) -> SheetGeometry {
        SheetGeometry(container: CGSize(width: 402, height: 874), safeTop: 62, safeBottom: 34, keyboardHeight: keyboard, tabBarVisible: tabBar, progress: progress)
    }

    func testTheStripRestsDirectlyAboveTheTabBarAtTheBottom() {
        let g = phone(progress: 1)
        XCTAssertEqual(g.collapsedY, 874 - 34 - 72 - 64)
        XCTAssertEqual(g.sheetY, g.collapsedY)
        XCTAssertEqual(g.sheetHeight, SheetGeometry.miniHeight)
        XCTAssertEqual(g.collapsedBottom, 874 - 34 - 72, "the strip's bottom edge touches the tab bar's top edge")
    }

    func testTheKeyboardNeverMovesTheStrip() {
        let without = phone(progress: 1)
        for keyboard in [0, 52, 291, 336] as [CGFloat] {
            let with = phone(keyboard: keyboard, progress: 1)
            XCTAssertEqual(with.sheetY, without.sheetY, "keyboard \(keyboard)")
            XCTAssertEqual(with.sheetHeight, without.sheetHeight)
            XCTAssertEqual(with.collapsedBottom, without.collapsedBottom)
        }
    }

    func testTheKeyboardOnlyShortensTheExpandedSheet() {
        XCTAssertEqual(phone().expandedHeight, 874)
        XCTAssertEqual(phone(keyboard: 336).expandedHeight, 874 - 336)
        XCTAssertEqual(phone(keyboard: 336).sheetY, 0)
        XCTAssertEqual(phone(keyboard: 336).sheetHeight, 874 - 336)
    }

    func testAKeyboardTallerThanTheScreenNeverMakesTheSheetShorterThanTheStrip() {
        XCTAssertEqual(phone(keyboard: 5_000).expandedHeight, SheetGeometry.miniHeight)
    }

    func testWithoutATabBarTheStripSitsOnTheBottomSafeArea() {
        let g = phone(tabBar: false, progress: 1)
        XCTAssertEqual(g.collapsedBottom, 874 - 34)
    }

    func testExpandedPortraitVideoIsSixteenByNineUnderTheStatusBar() {
        let g = phone()
        XCTAssertFalse(g.fullscreen)
        XCTAssertEqual(g.expandedVideoHeight, 402 * 9 / 16 + 62, accuracy: 0.001)
        XCTAssertEqual(g.videoWidth, 402)
    }

    func testLandscapeExpandedIsFullscreenAndTheStripIsStillTheStrip() {
        var g = SheetGeometry(container: CGSize(width: 874, height: 402), safeTop: 0, safeBottom: 21, tabBarVisible: true, progress: 0)
        XCTAssertTrue(g.landscape)
        XCTAssertTrue(g.fullscreen)
        XCTAssertEqual(g.expandedVideoHeight, 402)
        g.progress = 1
        XCTAssertFalse(g.fullscreen)
        XCTAssertEqual(g.sheetHeight, SheetGeometry.miniHeight)
        XCTAssertEqual(g.collapsedBottom, 402 - 21 - 72)
    }

    func testMidDragInterpolatesTheSheetAndThePicture() {
        let g = phone(progress: 0.5)
        XCTAssertEqual(g.sheetY, phone(progress: 1).collapsedY / 2, accuracy: 0.001)
        XCTAssertEqual(g.videoWidth, (402 + SheetGeometry.miniVideoWidth) / 2, accuracy: 0.001)
        XCTAssertEqual(g.sheetHeight, (874 + SheetGeometry.miniHeight) / 2, accuracy: 0.001)
    }

    func testDragProgressStartsFromTheRestingPositionAndIsClamped() {
        XCTAssertEqual(SheetGeometry.progress(collapsed: false, translation: 0, travel: 700), 0)
        XCTAssertEqual(SheetGeometry.progress(collapsed: false, translation: 350, travel: 700), 0.5, accuracy: 0.001)
        XCTAssertEqual(SheetGeometry.progress(collapsed: false, translation: 5_000, travel: 700), 1)
        XCTAssertEqual(SheetGeometry.progress(collapsed: true, translation: -350, travel: 700), 0.5, accuracy: 0.001)
        XCTAssertEqual(SheetGeometry.progress(collapsed: true, translation: 400, travel: 700), 1, "pulling the strip further down never goes past the dock")
        XCTAssertEqual(SheetGeometry.progress(collapsed: true, translation: -9_000, travel: 700), 0)
        XCTAssertEqual(SheetGeometry.progress(collapsed: false, translation: 10, travel: 0), 1, "a zero travel cannot divide by zero")
    }
}

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

/// The drag's maths beyond the rest positions, and the spring that settles the sheet.
final class SheetDragMathTests: XCTestCase {
    func testInsideTheRestPositionsTheFingerIsFollowedOneToOne() {
        for raw in stride(from: CGFloat(0), through: 1, by: 0.1) { XCTAssertEqual(SheetGeometry.rubberBanded(raw), raw, accuracy: 1e-9) }
    }

    func testBeyondTheRestPositionsTheBandResistsAndNeverPassesItsLimit() {
        var previous: CGFloat = 0
        for raw in stride(from: CGFloat(1.0), through: 6, by: 0.25) {
            let banded = SheetGeometry.rubberBanded(raw)
            XCTAssertGreaterThanOrEqual(banded, previous, "monotonic")
            XCTAssertLessThan(banded, 1.12 + 1e-9, "bounded by the limit")
            XCTAssertLessThanOrEqual(banded - 1, (raw - 1), "never faster than the finger")
            previous = banded
        }
        XCTAssertEqual(SheetGeometry.rubberBanded(-0.5), -SheetGeometry.rubberBanded(1.5) + 1, accuracy: 1e-9, "symmetric on both ends")
    }

    func testPulledPastTheExpandedPositionTheSheetStaysPutAndPastTheDockItKeepsGoingDown() {
        let base = SheetGeometry(container: CGSize(width: 402, height: 874), safeTop: 62, safeBottom: 34)
        XCTAssertEqual(base.at(-0.1).sheetY, 0)
        XCTAssertEqual(base.at(-0.1).sheetHeight, base.at(0).sheetHeight)
        XCTAssertGreaterThan(base.at(1.2).sheetY, base.at(1).sheetY)
        XCTAssertEqual(base.at(1.2).sheetHeight, SheetGeometry.miniHeight, "the strip never shrinks below its own height")
        XCTAssertEqual(base.at(1.2).videoWidth, SheetGeometry.miniVideoWidth)
    }

    func testTheSpringLandsOnItsTargetFromRestAndFromAFling() {
        let spring = SpringMath()
        for (start, velocity, target) in [(0.3, 0.0, 1.0), (0.4, 4.0, 1.0), (0.8, -6.0, 0.0), (1.1, 0.0, 1.0)] as [(CGFloat, CGFloat, CGFloat)] {
            var state = SpringMath.State(value: start, velocity: velocity)
            var frames = 0
            while !spring.isSettled(state, target: target), frames < 600 { state = spring.advance(state, target: target, dt: 1.0 / 60); frames += 1 }
            XCTAssertLessThan(frames, 60, "settles within a second: \(start) \(velocity) -> \(target)")
            XCTAssertEqual(state.value, target, accuracy: 0.001)
        }
    }

    func testAFlingContinuesInItsDirectionAndOvershootsLittle() {
        let spring = SpringMath()
        var state = SpringMath.State(value: 0.5, velocity: 3)
        var peak: CGFloat = 0
        for _ in 0..<120 { state = spring.advance(state, target: 1, dt: 1.0 / 60); peak = max(peak, state.value) }
        XCTAssertGreaterThan(peak, 0.5)
        XCTAssertLessThan(peak, 1.03, "a hint of overshoot at most")
    }

    func testTheSettleDoesNotDependOnTheFrameRate() {
        let spring = SpringMath()
        func run(dt: CGFloat) -> CGFloat {
            var state = SpringMath.State(value: 0, velocity: 2)
            var t: CGFloat = 0
            while t < 0.3 { state = spring.advance(state, target: 1, dt: dt); t += dt }
            return state.value
        }
        XCTAssertEqual(run(dt: 1.0 / 60), run(dt: 1.0 / 120), accuracy: 0.01)
    }

    func testAnInterruptedSettleContinuesFromWhereItWas() {
        let spring = SpringMath()
        var state = SpringMath.State(value: 0, velocity: 0)
        for _ in 0..<8 { state = spring.advance(state, target: 1, dt: 1.0 / 60) }
        let mid = state.value
        XCTAssertGreaterThan(mid, 0)
        XCTAssertLessThan(mid, 1)
        // A new target from the in-flight value and velocity: no jump.
        let next = spring.advance(state, target: 0, dt: 1.0 / 240)
        XCTAssertEqual(next.value, mid, accuracy: 0.01)
    }
}

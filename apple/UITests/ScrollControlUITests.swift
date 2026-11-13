import XCTest

/// A control for the recording analysis: a plain list scrolled with real touches (no player, no video, no chat). If the screen recording of THIS shows frames
/// alternating between two scroll positions, the zigzag is the recorder's, not the sheet's.
final class ScrollControlUITests: XCTestCase {
    func testScrollTheDemoHome() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-demo"]
        app.launch()
        Thread.sleep(forTimeInterval: 6)
        for _ in 0..<4 {
            let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
            let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25))
            from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .slow, thenHoldForDuration: 0.1)
            Thread.sleep(forTimeInterval: 1.2)
            to.press(forDuration: 0.05, thenDragTo: from, withVelocity: .slow, thenHoldForDuration: 0.1)
            Thread.sleep(forTimeInterval: 1.2)
        }
    }
}

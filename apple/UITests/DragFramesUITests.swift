import XCTest

/// A slow real-touch drag from the expanded sheet to the strip and back, for a screen recording (frames are cut from it by `scripts/drag-frames.sh`).
final class DragFramesUITests: XCTestCase {
    func testSlowDragDownAndUp() {
        let app = XCUIApplication()
        app.launchArguments = ["-demo", "-open", "lunaria"] + (ProcessInfo.processInfo.environment["AUDIT_ARGS"] ?? "").split(separator: " ").map(String.init)
        app.launch()
        Thread.sleep(forTimeInterval: 7)
        let from = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        let to = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9))
        from.press(forDuration: 0.1, thenDragTo: to, withVelocity: 120, thenHoldForDuration: 1.5)
        Thread.sleep(forTimeInterval: 2)
        let up = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.86))
        let top = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        up.press(forDuration: 0.1, thenDragTo: top, withVelocity: 120, thenHoldForDuration: 1.0)
        Thread.sleep(forTimeInterval: 2)
    }
}

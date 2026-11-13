import XCTest

/// Real touches on a real live stream with a busy chat: opens `PERF_CHANNEL`, then drags the player sheet down to the strip and up again with the synthesized
/// touch pipeline (the same `DragGesture` a finger drives), at a few speeds. The app, started with `-frame-monitor`, logs the frame cadence of each drag and settle
/// to `Caches/framemon.txt` (read it from the app container afterwards: `apple/scripts/measure-drag.sh` does).
final class MinimiseDragUITests: XCTestCase {
    func testMinimiseAndExpandWithLiveVideoAndFastChat() throws {
        let channel = ProcessInfo.processInfo.environment["PERF_CHANNEL"] ?? "rainbow6"
        let app = XCUIApplication()
        app.launchArguments = ["-open", channel, "-real-fast-chat", "-frame-monitor"]
        app.launch()
        // The picture and the chat settle.
        Thread.sleep(forTimeInterval: 14)
        let speeds: [XCUIGestureVelocity] = [.slow, .default, .fast]
        for round in 0..<2 {
            for speed in speeds {
                // Minimise: from the picture (top of the screen, under the status bar) down to the middle.
                let top = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.17))
                let middle = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.62))
                top.press(forDuration: 0.05, thenDragTo: middle, withVelocity: speed, thenHoldForDuration: 0.1)
                Thread.sleep(forTimeInterval: 1.6)
                // Expand: from the strip (above the tab bar) up to the top.
                let strip = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.86))
                let up = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25))
                strip.press(forDuration: 0.05, thenDragTo: up, withVelocity: speed, thenHoldForDuration: 0.1)
                Thread.sleep(forTimeInterval: 1.6)
                _ = round
            }
        }
    }
}

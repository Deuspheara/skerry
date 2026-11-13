import CoreGraphics

/// The geometry of the player sheet, as pure maths (no SwiftUI), so it can be tested for every state: no keyboard, keyboard up, strip, expanded,
/// landscape, mid-drag, tab bar hidden under a pushed page.
///
/// Coordinates are those of the whole screen (the sheet covers the status bar and the home indicator). `container` is the screen size **without**
/// the keyboard taken off: the keyboard only ever shortens the expanded sheet (so the chat input stays above it), it never moves the strip or
/// the tab bar, which are docked to the bottom safe area.
struct SheetGeometry: Equatable {
    var container: CGSize
    var safeTop: CGFloat = 0
    var safeBottom: CGFloat = 0
    /// The keyboard's height measured from the bottom edge of the screen (0 when hidden).
    var keyboardHeight: CGFloat = 0
    var tabBarVisible = true
    /// 0 expanded, 1 docked as the strip; in between while dragging.
    var progress: CGFloat = 0

    static let miniHeight: CGFloat = 64
    static let miniVideoWidth: CGFloat = 112
    static let tabBarHeight: CGFloat = 72

    var landscape: Bool { container.width > container.height }
    var fullscreen: Bool { landscape && progress == 0 }

    /// Space the tab bar takes above the bottom safe area.
    var bottomChrome: CGFloat { tabBarVisible ? Self.tabBarHeight : 0 }

    /// Top edge of the strip at rest: directly above the tab bar (or the bottom safe area when the tab bar is hidden).
    var collapsedY: CGFloat { container.height - safeBottom - bottomChrome - Self.miniHeight }

    /// Height of the expanded sheet: the whole screen, shortened by the keyboard.
    var expandedHeight: CGFloat { max(Self.miniHeight, container.height - keyboardHeight) }

    var sheetY: CGFloat { lerp(0, collapsedY, progress) }
    var sheetHeight: CGFloat { lerp(expandedHeight, Self.miniHeight, progress) }

    var expandedVideoHeight: CGFloat { fullscreen ? container.height : container.width * 9 / 16 + safeTop }
    var videoWidth: CGFloat { lerp(container.width, Self.miniVideoWidth, progress) }
    var videoHeight: CGFloat { lerp(expandedVideoHeight, Self.miniHeight, progress) }

    /// Bottom edge of the strip at rest, in screen coordinates.
    var collapsedBottom: CGFloat { collapsedY + Self.miniHeight }

    /// Progress for a drag of `translation` points from the position `collapsed`.
    static func progress(collapsed: Bool, translation: CGFloat, travel: CGFloat) -> CGFloat {
        min(1, max(0, (collapsed ? 1 : 0) + translation / max(1, travel)))
    }

    private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat { a + (b - a) * t }
}

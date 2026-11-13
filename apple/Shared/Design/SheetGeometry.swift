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
    /// 0 expanded, 1 docked as the strip; in between while dragging. It may leave [0, 1] a little while the finger pulls past a rest position (rubber band) or the
    /// strip is thrown off the bottom; sizes stay within the two rest sizes, only the vertical position follows.
    var progress: CGFloat = 0

    /// The progress the sizes use: clamped to [0, 1].
    var q: CGFloat { min(1, max(0, progress)) }

    func at(_ progress: CGFloat) -> SheetGeometry {
        var copy = self
        copy.progress = progress
        return copy
    }

    static let miniHeight: CGFloat = 64
    static let miniVideoWidth: CGFloat = 112
    static let tabBarHeight: CGFloat = 72

    var landscape: Bool { container.width > container.height }
    var fullscreen: Bool { landscape && q == 0 }

    /// Space the tab bar takes above the bottom safe area.
    var bottomChrome: CGFloat { tabBarVisible ? Self.tabBarHeight : 0 }

    /// Top edge of the strip at rest: directly above the tab bar (or the bottom safe area when the tab bar is hidden).
    var collapsedY: CGFloat { container.height - safeBottom - bottomChrome - Self.miniHeight }

    /// Height of the expanded sheet: the whole screen, shortened by the keyboard.
    var expandedHeight: CGFloat { max(Self.miniHeight, container.height - keyboardHeight) }

    /// Pulled past the expanded position the sheet stays put; thrown past the dock it keeps going down.
    var sheetY: CGFloat { progress < 0 ? 0 : lerp(0, collapsedY, progress) }
    var sheetHeight: CGFloat { lerp(expandedHeight, Self.miniHeight, q) }

    /// The picture's own size, fixed whatever the progress: the full container in landscape (fullscreen), 16:9 across the container in portrait. The picture view is laid out
    /// once at this size and only scaled and moved by transforms while the sheet moves, so the video layer is never resized during a drag.
    var pictureBase: CGSize { landscape ? container : CGSize(width: container.width, height: container.width * 9 / 16) }

    /// What sits above the picture when expanded: the status bar area in portrait (black), nothing in fullscreen.
    var pictureTopInset: CGFloat { landscape ? 0 : safeTop }

    var expandedVideoHeight: CGFloat { pictureBase.height + pictureTopInset }

    /// The box that holds the picture: it shrinks from the expanded picture (with its black status bar area) to the strip's 112 x 64 thumbnail.
    var videoWidth: CGFloat { lerp(container.width, Self.miniVideoWidth, q) }
    var videoHeight: CGFloat { lerp(expandedVideoHeight, Self.miniHeight, q) }

    /// One scale for both axes (the picture is never stretched): from 1 to the factor that fits the thumbnail.
    var pictureScale: CGFloat {
        let fit = min(Self.miniVideoWidth / pictureBase.width, Self.miniHeight / pictureBase.height)
        return lerp(1, fit, q)
    }

    /// Where the scaled picture's top-left corner sits in its box: under the status bar area when expanded, centred in the thumbnail when docked.
    var pictureOffset: CGSize {
        let fit = min(Self.miniVideoWidth / pictureBase.width, Self.miniHeight / pictureBase.height)
        let dockedX = (Self.miniVideoWidth - pictureBase.width * fit) / 2
        let dockedY = (Self.miniHeight - pictureBase.height * fit) / 2
        return CGSize(width: lerp(0, dockedX, q), height: lerp(pictureTopInset, dockedY, q))
    }

    /// Bottom edge of the strip at rest, in screen coordinates.
    var collapsedBottom: CGFloat { collapsedY + Self.miniHeight }

    /// Resistance past a rest position: inside [0, 1] the finger is followed 1:1; beyond, the sheet follows a fraction that tends to `limit` (in progress units),
    /// the way a scroll view's rubber band does.
    static func rubberBanded(_ raw: CGFloat, limit: CGFloat = 0.12) -> CGFloat {
        func damp(_ x: CGFloat) -> CGFloat { limit * (1 - 1 / (x / limit * 0.55 + 1)) }
        if raw < 0 { return -damp(-raw) }
        if raw > 1 { return 1 + damp(raw - 1) }
        return raw
    }

    /// Progress for a drag of `translation` points from the position `collapsed`.
    static func progress(collapsed: Bool, translation: CGFloat, travel: CGFloat) -> CGFloat {
        min(1, max(0, (collapsed ? 1 : 0) + translation / max(1, travel)))
    }

    private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat { a + (b - a) * t }
}

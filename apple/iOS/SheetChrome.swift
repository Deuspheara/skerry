import SwiftUI

/// Where the player sheet is between expanded (0) and docked as the strip (1), as one value. **No view body reads it**: only the small modifiers below do, so a
/// drag frame re-evaluates a handful of modifier bodies and moves the same views; the chat, the controls and the video view are not rebuilt.
@MainActor
@Observable
final class SheetProgress {
    var value: CGFloat = 0
    /// A finger is on the sheet (the chat is frozen and the glass is flat meanwhile). Changes twice per gesture, never per frame.
    var isDragging = false
    /// The value SwiftUI is showing right now, which differs from `value` while a spring is running (`value` already holds the target). A new touch starts from this.
    @ObservationIgnored var presented: CGFloat = 0
    @ObservationIgnored var dragBase: CGFloat = 0
    /// Identifies the latest settle: a replaced spring still reports completion, and must not land.
    @ObservationIgnored var settleID = 0
}

/// Reports the animated value back, frame by frame, without involving any view body: `animatableData` is set by SwiftUI's animation system as the spring runs,
/// so the sheet can be caught in flight and carried on from where it really is.
struct PresentedProbe: ViewModifier, Animatable {
    var value: CGFloat
    let sink: SheetProgress

    var animatableData: CGFloat {
        get { value }
        set {
            value = newValue
            sink.presented = newValue
        }
    }

    func body(content: Content) -> some View { content }
}

/// The sheet's frame, position and corners from the progress. The shadow belongs to a plain shape behind the content, not to the video.
struct SheetFrame: ViewModifier {
    let progress: SheetProgress
    let base: SheetGeometry

    func body(content: Content) -> some View {
        let g = base.at(progress.value)
        // Rounded corners clip the whole sheet (video and chat included) through a mask, and a blurred shadow of a big shape is a big blur: both are repainted every frame
        // while the sheet is large, which halved the frame rate on the way down. They appear only when the sheet is nearly the strip.
        let corner = 14 * min(1, max(0, (g.q - 0.7) / 0.3))
        content
            .frame(width: g.container.width - 16 * g.q, height: g.sheetHeight, alignment: .topLeading)
            .background { ZStack { Theme.background; Theme.surface.opacity(g.q) } }
            #if DEBUG
            // Measuring only (`-frame-monitor`): a magenta bar on the sheet's leading edge, so `analyze-recording.py` can follow the sheet's top edge exactly in a screen recording.
            .overlay(alignment: .leading) { if FrameMonitor.enabled { Rectangle().fill(Color(red: 1, green: 0, blue: 1)).frame(width: 5) } }
            #endif
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            .background {
                RoundedRectangle(cornerRadius: corner, style: .continuous).fill(Theme.surface)
                    .shadow(color: .black.opacity(0.25 * g.q), radius: 12, y: 2).opacity(g.q > 0.7 ? 1 : 0)
            }
            .offset(x: 8 * g.q, y: g.sheetY)
            .modifier(PresentedProbe(value: progress.value, sink: progress))
    }
}

/// The picture: a view laid out ONCE at its own size and only scaled and moved while the sheet moves (never resized, so the video layer is never re-composited
/// at a new size mid-drag). Its box shrinks around it and clips it.
struct PictureTransform<Picture: View>: View {
    let progress: SheetProgress
    let base: SheetGeometry
    @ViewBuilder let picture: () -> Picture

    var body: some View {
        let g = base.at(progress.value)
        let size = g.pictureBase
        picture()
            .frame(width: size.width, height: size.height)
            .scaleEffect(g.pictureScale, anchor: .topLeading)
            .offset(x: g.pictureOffset.width, y: g.pictureOffset.height)
            .frame(width: g.videoWidth, height: g.videoHeight, alignment: .topLeading)
            .background(Color.black)
            .clipped()
    }
}

/// Fades with the progress: the details and the controls out (gone by a third of the way), the strip's text and buttons in.
struct SheetFade: ViewModifier {
    enum Direction { case out, `in` }
    let progress: SheetProgress
    let direction: Direction

    func body(content: Content) -> some View {
        let q = min(1, max(0, progress.value))
        let opacity: Double = switch direction {
        case .out: Double(max(0, 1 - q * 3))
        case .in: Double(min(1, q * 1.4))
        }
        content.opacity(opacity).allowsHitTesting(direction == .out ? q < 0.2 : q > 0.5)
    }
}

/// The details under the picture "fade out" with a flat cover of their own background colour that fades in over them, not an opacity on the subtree: an opacity below 1
/// makes the whole subtree (chat lines, emotes, the input and its glass) render offscreen each frame, which on a full-screen sheet is what dropped frames.
struct SheetCover: ViewModifier {
    let progress: SheetProgress
    let color: Color

    func body(content: Content) -> some View {
        let q = min(1, max(0, progress.value))
        content
            .overlay { color.opacity(Double(min(1, q * 3))).allowsHitTesting(false) }
            .allowsHitTesting(q < 0.2)
    }
}

extension View {
    func sheetCover(_ progress: SheetProgress, color: Color) -> some View { modifier(SheetCover(progress: progress, color: color)) }

    func sheetFade(_ progress: SheetProgress, _ direction: SheetFade.Direction) -> some View { modifier(SheetFade(progress: progress, direction: direction)) }
}

import QuartzCore
import SwiftUI

/// Where the player sheet is between expanded (0) and docked as the strip (1), as one value. **No view body reads it**: only the small modifiers below do, so a
/// drag frame re-evaluates a handful of modifier bodies and moves the same views; the chat, the controls and the video view are not rebuilt.
@MainActor
@Observable
final class SheetProgress {
    var value: CGFloat = 0
    /// A finger is on the sheet (the chat is frozen meanwhile). Changes twice per gesture, never per frame.
    var isDragging = false
    @ObservationIgnored var dragBase: CGFloat = 0
}

/// Settles the sheet from where the finger let go, with its velocity, on a display link: the value is real at every frame (not a model value that a view
/// animation interpolates), so a new touch can catch the sheet in flight and carry on from exactly there.
@MainActor
final class SpringDriver: NSObject {
    private(set) var target: CGFloat = 0
    private var state = SpringMath.State(value: 0, velocity: 0)
    private var link: CADisplayLink?
    private var progress: SheetProgress?
    private var completion: (() -> Void)?
    private let math = SpringMath()

    var isRunning: Bool { link != nil }

    func animate(_ progress: SheetProgress, to target: CGFloat, velocity: CGFloat, reduceMotion: Bool, completion: (() -> Void)? = nil) {
        stop()
        self.progress = progress
        self.target = target
        self.completion = completion
        if reduceMotion {
            progress.value = target
            completion?()
            return
        }
        state = SpringMath.State(value: progress.value, velocity: velocity)
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.add(to: .main, forMode: .common)
        self.link = link
    }

    func stop() {
        link?.invalidate()
        link = nil
        completion = nil
    }

    @objc private func tick(_ link: CADisplayLink) {
        guard let progress else { return stop() }
        let dt = CGFloat(min(1.0 / 20, max(1.0 / 240, link.targetTimestamp - link.timestamp)))
        state = math.advance(state, target: target, dt: dt)
        progress.value = state.value
        if math.isSettled(state, target: target) {
            progress.value = target
            let done = completion
            stop()
            done?()
        }
    }
}

/// The sheet's frame, position, corners and shadow from the progress. The shadow belongs to a plain shape behind the content, not to the video.
struct SheetFrame: ViewModifier {
    let progress: SheetProgress
    let base: SheetGeometry

    func body(content: Content) -> some View {
        let g = base.at(progress.value)
        let corner = 14 * g.q
        content
            .frame(width: g.container.width - 16 * g.q, height: g.sheetHeight, alignment: .topLeading)
            .background { ZStack { Theme.background; Theme.surface.opacity(g.q) } }
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            .background {
                RoundedRectangle(cornerRadius: corner, style: .continuous).fill(Theme.surface)
                    .shadow(color: .black.opacity(0.25 * g.q), radius: 12, y: 2).opacity(g.q > 0.01 ? 1 : 0)
            }
            .offset(x: 8 * g.q, y: g.sheetY)
    }
}

/// The picture's size follows the progress; the view itself is never re-created or re-parented.
struct VideoFrame: ViewModifier {
    let progress: SheetProgress
    let base: SheetGeometry

    func body(content: Content) -> some View {
        let g = base.at(progress.value)
        content.frame(width: g.videoWidth, height: g.videoHeight)
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

extension View {
    func sheetFade(_ progress: SheetProgress, _ direction: SheetFade.Direction) -> some View { modifier(SheetFade(progress: progress, direction: direction)) }
}

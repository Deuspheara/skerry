import Observation
import SwiftUI
import UIKit

/// Fullscreen is landscape: the button asks the window scene to rotate (the system keeps honouring the viewer's rotation lock) and, after leaving
/// fullscreen, the scene is free to follow the device again.
enum Orientation {
    @MainActor static func request(_ mask: UIInterfaceOrientationMask) {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else { return }
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { _ in }
        scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
    }
}

/// The keyboard follows the player's state: minimising or closing the sheet hides it (the chat field is still first responder, hidden under the strip).
enum Keyboard {
    @MainActor static func dismiss() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

/// The on-screen keyboard's height, so the sheet (which covers the whole screen) can keep the chat input above it.
@MainActor
@Observable
final class KeyboardObserver {
    private(set) var height: CGFloat = 0
    private var tokens: [NSObjectProtocol] = []

    init() {
        #if DEBUG
        // `-fake-keyboard <points>`: lays the sheet out as if the keyboard were up (the simulator with a hardware keyboard shows none).
        if let fake = LaunchMode.argument("-fake-keyboard").flatMap(Double.init) { height = fake }
        #endif
        let center = NotificationCenter.default
        tokens.append(center.addObserver(forName: UIResponder.keyboardWillChangeFrameNotification, object: nil, queue: .main) { [weak self] note in
            let frame = (note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect) ?? .zero
            let screen = UIScreen.main.bounds
            let duration = (note.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
            MainActor.assumeIsolated {
                // Animated with the keyboard's own duration: the sheet's content rises and falls with it instead of jumping.
                withAnimation(.timingCurve(0.2, 0.0, 0.0, 1.0, duration: max(0.2, duration))) { self?.height = max(0, screen.height - frame.minY) }
            }
        })
        tokens.append(center.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { withAnimation(.timingCurve(0.2, 0.0, 0.0, 1.0, duration: 0.25)) { self?.height = 0 } }
        })
    }
}

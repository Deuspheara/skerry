import Observation
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
        let center = NotificationCenter.default
        tokens.append(center.addObserver(forName: UIResponder.keyboardWillChangeFrameNotification, object: nil, queue: .main) { [weak self] note in
            let frame = (note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect) ?? .zero
            let screen = UIScreen.main.bounds
            MainActor.assumeIsolated { self?.height = max(0, screen.height - frame.minY) }
        })
        tokens.append(center.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.height = 0 }
        })
    }
}

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

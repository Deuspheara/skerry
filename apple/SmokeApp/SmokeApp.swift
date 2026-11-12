import SwiftUI
import TwitchShared

/// Proves the Kotlin bridge end to end on every Apple platform: the shared presenters run, their state reaches SwiftUI through
/// SKIE's AsyncSequence, an event written from Swift goes through the settings presenter into the on-disk DataStore and
/// back, and the platform seams (locale, number formatting, snapshot storage, Keychain) answer.
@main
struct SmokeApp: App {
    @State private var model = SmokeModel()

    var body: some Scene {
        WindowGroup {
            SmokeView(model: model)
        }
    }
}

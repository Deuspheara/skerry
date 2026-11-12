import SwiftUI

@main
struct TwitchIOSApp: App {
    @State private var model = AppModel()
    @AppStorage(ThemeChoice.key) private var theme = ThemeChoice.system.rawValue
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .task { model.applyLaunchArguments() }
                .tint(Theme.accent)
                .preferredColorScheme((ThemeChoice(rawValue: theme) ?? .system).scheme)
        }
        .onChange(of: phase) { _, new in
            if new == .active { model.appCameToForeground() }
        }
    }
}

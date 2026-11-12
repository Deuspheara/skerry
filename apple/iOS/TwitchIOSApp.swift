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

/// The appearance the viewer picked in Settings (system by default).
enum ThemeChoice: String {
    case system, light, dark
    static let key = "themeChoice"
    static var current: ThemeChoice { ThemeChoice(rawValue: UserDefaults.standard.string(forKey: key) ?? "") ?? .system }
    var scheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

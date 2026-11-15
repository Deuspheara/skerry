import SwiftUI

/// The app's two models, created once (the scene and the debug snapshot runner share them: starting the Kotlin graph twice is not allowed).
@MainActor
enum MacRuntime {
    static let app = AppModel()
    static let mac = MacModel()
}

@main
struct TwitchMacApp: App {
    @State private var model = MacRuntime.app
    @State private var mac = MacRuntime.mac
    @AppStorage(ThemeChoice.key) private var theme = ThemeChoice.system.rawValue
    @Environment(\.scenePhase) private var phase

    init() {
        MacSnapshotRunner.startIfRequested()
    }

    var body: some Scene {
        // One window: the app model (session, navigation, player) is shared, and the window's size, tab and search are restored by the system.
        Window("Skerry", id: "main") {
            // A debug snapshot run (`MacSnapshotRunner`) renders the window itself: this scene stays empty so two views never drive the same navigation state.
            if MacSnapshotRunner.isActive {
                Color.clear
            } else {
                // Closing the window ends the player session: the stream stops, the replay position is saved, the chat disconnects (the app keeps
                // running in the Dock like any Mac app; reopening the window starts from the lists).
                content.onDisappear { model.session.close() }
            }
        }
        .defaultSize(width: 1220, height: 780)
        .windowResizability(.contentMinSize)
        .commands { MacCommands(app: model, mac: mac) }
        .onChange(of: phase) { _, new in
            if new == .active { model.appCameToForeground() } else if new == .background { model.appWentToBackground() }
        }

        // Cmd+, opens this window (the system adds the menu item).
        Settings {
            MacSettingsView()
            .environment(model)
                .tint(Theme.accent)
                .preferredColorScheme((ThemeChoice(rawValue: theme) ?? .system).scheme)
        }
    }

    private var content: some View {
        MacRoot()
            .environment(model)
            .environment(mac)
            .frame(minWidth: 940, minHeight: 600)
            .task {
                model.applyLaunchArguments()
                mac.applyLaunchArguments()
            }
            .tint(Theme.accent)
            .preferredColorScheme((ThemeChoice(rawValue: theme) ?? .system).scheme)
    }
}

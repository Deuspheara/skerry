import SwiftUI
import TwitchShared

enum TVSection: String, Hashable {
    case home, following, browse, search, settings
}

/// Opens a page on the stack of the section the viewer is in (the Search and Settings sections have stacks of their own, the others use the shared app model's).
private struct TVPushKey: EnvironmentKey {
    static let defaultValue: (Route) -> Void = { _ in }
}

extension EnvironmentValues {
    var tvPush: (Route) -> Void {
        get { self[TVPushKey.self] }
        set { self[TVPushKey.self] = newValue }
    }
}

/// The Apple TV shell: the system's top tab bar (Home, Following when signed in, Browse, Search, Settings), one navigation stack per section so the
/// focus and the pages survive a tab change, the sign-in sheet and the full-screen player on top.
struct TVRoot: View {
    @Environment(AppModel.self) private var app
    @State private var section = TVRoot.initialSection
    @State private var searchPath: [Route] = []
    @State private var settingsPath: [Route] = []

    var body: some View {
        @Bindable var app = app
        TabView(selection: $section) {
            Tab("nav_home", systemImage: "house", value: TVSection.home) { stack(.home) }
            if app.tabs.contains(.following) {
                Tab("nav_follows", systemImage: "heart", value: TVSection.following) { stack(.following) }
            }
            Tab("nav_browse", systemImage: "square.grid.2x2", value: TVSection.browse) { stack(.browse) }
            Tab("nav_search", systemImage: "magnifyingglass", value: TVSection.search) { searchStack }
            Tab("phone_nav_settings", systemImage: "gearshape", value: TVSection.settings) { settingsStack }
        }
        .onChange(of: section) { _, new in
            if let tab = AppTab(rawValue: new.rawValue) { app.tab = tab }
        }
        .fullScreenCover(isPresented: Binding(get: { app.session.target != nil }, set: { if !$0 { app.session.close() } })) {
            TVPlayerScreen()
        }
        .fullScreenCover(isPresented: $app.showSignIn) { TVSignInScreen() }
    }

    /// Debug launch argument `-section home|following|browse|search|settings` for screenshots.
    private static var initialSection: TVSection {
        #if DEBUG
        if let name = LaunchMode.argument("-section"), let next = TVSection(rawValue: name) { return next }
        #endif
        return .home
    }

    private func stack(_ tab: AppTab) -> some View {
        NavigationStack(path: app.path(for: tab)) {
            Group {
                switch tab {
                case .home: TVHomeScreen()
                case .following: TVFollowingScreen()
                case .browse: TVBrowseScreen()
                }
            }
            .navigationDestination(for: Route.self) { destination($0) }
        }
        .environment(\.tvPush, { route in app.tab = tab; app.push(route) })
    }

    private var searchStack: some View {
        NavigationStack(path: $searchPath) {
            TVSearchScreen().navigationDestination(for: Route.self) { destination($0) }
        }
        .environment(\.tvPush, { route in if !searchPath.contains(route) { searchPath.append(route) } })
    }

    private var settingsStack: some View {
        NavigationStack(path: $settingsPath) {
            TVSettingsScreen().navigationDestination(for: Route.self) { destination($0) }
        }
        .environment(\.tvPush, { route in if !settingsPath.contains(route) { settingsPath.append(route) } })
    }

    @ViewBuilder private func destination(_ route: Route) -> some View {
        switch route {
        case .channel(let login): TVChannelScreen(login: login)
        case .category(let id, let name): TVCategoryScreen(id: id, name: name)
        case .search, .settings: EmptyView()
        }
    }
}

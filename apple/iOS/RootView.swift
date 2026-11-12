import SwiftUI
import TwitchShared

/// The shell: one navigation stack per tab, the custom tab bar, the search/settings pages pushed from the header, and (above everything) the
/// player sheet and the sign-in sheet.
struct RootView: View {
    @Environment(AppModel.self) private var app
    @Namespace private var searchNamespace

    var body: some View {
        @Bindable var app = app
        ZStack(alignment: .bottom) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                TabStack(tab: tab, namespace: searchNamespace)
                    .opacity(app.tab == tab ? 1 : 0)
                    .allowsHitTesting(app.tab == tab)
                    .accessibilityHidden(app.tab != tab)
            }
            TabBar()
        }
        .sheet(isPresented: $app.showSignIn) { SignInSheet() }
        .background(Theme.background.ignoresSafeArea())
    }
}

/// One tab: its own stack so switching tabs keeps each tab's pages and scroll position.
private struct TabStack: View {
    @Environment(AppModel.self) private var app
    let tab: AppTab
    var namespace: Namespace.ID

    var body: some View {
        NavigationStack(path: app.path(for: tab)) {
            root.toolbar(.hidden, for: .navigationBar)
                .safeAreaInset(edge: .bottom, spacing: 0) { Color.clear.frame(height: 64) }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .channel(let login): ChannelScreen(login: login)
                    case .category(let id, let name): CategoryScreen(id: id, name: name)
                    case .search: SearchScreen(namespace: namespace)
                    case .settings: SettingsScreen()
                    }
                }
        }
    }

    @ViewBuilder private var root: some View {
        switch tab {
        case .home: HomeScreen(namespace: namespace)
        case .following: FollowingScreen(namespace: namespace)
        case .browse: BrowseScreen(namespace: namespace)
        }
    }
}

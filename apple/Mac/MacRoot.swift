import SwiftUI
import TwitchShared

/// The window: a sidebar (Home, Following, Browse), the page of the selected section in a navigation stack, the toolbar search field, and the
/// player above the page (a pane when expanded, a floating mini player when minimised).
struct MacRoot: View {
    @Environment(AppModel.self) private var app
    @Environment(MacModel.self) private var mac
    @SceneStorage("mac.section") private var storedSection = AppTab.home.rawValue
    @FocusState private var searchFocused: Bool

    var body: some View {
        @Bindable var app = app
        @Bindable var mac = mac
        NavigationSplitView {
            Sidebar()
                .navigationSplitViewColumnWidth(min: 180, ideal: 210, max: 280)
        } detail: {
            MacDetail()
        }
        .searchable(text: $mac.searchText, placement: .toolbar, prompt: Text("phone_search_hint"))
        .searchFocused($searchFocused)
        .onChange(of: mac.focusSearchTick) { _, _ in searchFocused = true }
        .toolbar { AccountMenu() }
        .sheet(isPresented: $app.showSignIn) { SignInSheet().frame(minWidth: 440, idealWidth: 460, minHeight: 520) }
        .background(Theme.background)
        .onAppear { if app.launch.tab == nil, let stored = AppTab(rawValue: storedSection), app.tabs.contains(stored) { app.tab = stored } }
        .onChange(of: app.tab) { _, tab in storedSection = tab.rawValue }
    }
}

private struct Sidebar: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        List(selection: Binding(get: { app.tab }, set: { if let next = $0 { app.select(next) } })) {
            Section {
                ForEach(app.tabs, id: \.self) { tab in
                    Label(LocalizedStringKey(tab.titleKey), systemImage: app.tab == tab ? tab.filledImage : tab.systemImage)
                        .tag(tab)
                        .accessibilityIdentifier("sidebar-\(tab.rawValue)")
                }
            }
        }
        .listStyle(.sidebar)
    }
}

/// The account button of the toolbar: the avatar glyph with a menu (sign out) or a sign-in button.
private struct AccountMenu: ToolbarContent {
    @Environment(AppModel.self) private var app

    var body: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            if app.isSignedIn {
                Menu {
                    Text(app.account ?? "")
                    Divider()
                    SettingsLink { Text("phone_nav_settings") }
                    Button("sign_out") { app.signOut() }
                } label: {
                    Label(app.account ?? "", systemImage: "person.crop.circle.fill")
                }
                .help(Text(app.account ?? ""))
            } else {
                Button { app.showSignIn = true } label: { Label("dialog_signin", systemImage: "person.crop.circle") }
                    .help(Text("dialog_signin"))
            }
        }
    }
}

/// The pages of the selected section, the search results while the search field is active, and the player on top.
struct MacDetail: View {
    @Environment(AppModel.self) private var app
    @Environment(MacModel.self) private var mac
    @Environment(\.isSearching) private var isSearching

    /// The search page shows while the toolbar field is active or holds text.
    private var searching: Bool { isSearching || !mac.searchText.isEmpty }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                MacTabStack(tab: tab)
                    .opacity(app.tab == tab && !searching ? 1 : 0)
                    .allowsHitTesting(app.tab == tab && !searching)
                    .accessibilityHidden(app.tab != tab || searching)
            }
            if searching { MacSearchView() }
            if app.session.target != nil { MacPlayerLayer() }
        }
        .background(Theme.background)
    }
}

/// One section: its own stack so switching sections keeps each one's pages and scroll position.
private struct MacTabStack: View {
    @Environment(AppModel.self) private var app
    let tab: AppTab

    var body: some View {
        NavigationStack(path: app.path(for: tab)) {
            root
                .navigationTitle(Text(LocalizedStringKey(tab.titleKey)))
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .channel(let login): MacChannelScreen(login: login)
                    case .category(let id, let name): MacCategoryScreen(id: id, name: name)
                    case .search, .settings: EmptyView()
                    }
                }
        }
    }

    @ViewBuilder private var root: some View {
        switch tab {
        case .home: MacHomeScreen()
        case .following: MacFollowingScreen()
        case .browse: MacBrowseScreen()
        }
    }
}

import Foundation
import Observation
import SwiftUI
import TwitchShared
#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

enum AppTab: String, CaseIterable, Hashable {
    case home, following, browse

    var systemImage: String {
        switch self {
        case .home: "house"
        case .following: "heart"
        case .browse: "square.grid.2x2"
        }
    }

    var filledImage: String { systemImage + ".fill" }

    /// The localisation key (shared with Android) of the tab's name.
    var titleKey: String {
        switch self {
        case .home: "nav_home"
        case .following: "nav_follows"
        case .browse: "nav_browse"
        }
    }
}

/// A page pushed on a tab's navigation stack. Search and Settings are pages opened from the header, not tabs.
enum Route: Hashable {
    case channel(login: String)
    case category(id: String, name: String)
    case search
    case settings
}

/// What the app was launched for: the real Twitch data or the offline fictional data used for screenshots and UI tests.
struct LaunchMode {
    let demo: Bool
    let signedOut: Bool
    /// Debug launch arguments for screenshots and UI tests: `-tab following`, `-push search|settings|channel:<login>|category:<id>:<name>`, `-open <login>`, `-sheet signin`.
    let tab: String?
    let push: String?
    let open: String?
    let sheet: String?

    static func argument(_ name: String) -> String? {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: name), index + 1 < args.count else { return nil }
        return args[index + 1]
    }

    #if DEBUG
    static let current = LaunchMode(
        demo: ProcessInfo.processInfo.arguments.contains("-demo"),
        signedOut: ProcessInfo.processInfo.arguments.contains("-demo-signed-out"),
        tab: argument("-tab"), push: argument("-push"), open: argument("-open"), sheet: argument("-sheet"),
    )
    #else
    static let current = LaunchMode(demo: false, signedOut: false, tab: nil, push: nil, open: nil, sheet: nil)
    #endif
}

@MainActor
@Observable
final class AppModel {
    let graph: TwitchGraph
    let launch: LaunchMode

    /// The signed-in account's login (nil when signed out or while the session is loading).
    private(set) var account: String?
    /// The stored session has not been read yet: `account == nil` does not mean signed out.
    private(set) var sessionLoading = true
    private(set) var animationsEnabled = true
    /// The latest settings, for the Settings page and anything that needs a preference (chat look, autoplay).
    private(set) var settingsState: SettingsUiState?

    var tab: AppTab = .home
    var paths: [AppTab: [Route]] = [:]
    var showSignIn = false
    let session: PlayerSession

    private let lifetime = Lifetime()
    private let settings: SettingsPresenter
    private static let hintKey = "signedInHint"

    init(launch: LaunchMode = .current) {
        self.launch = launch
        if launch.demo {
            graph = TwitchApp.shared.startDemo(signedIn: !launch.signedOut)
        } else {
            let id = (Bundle.main.object(forInfoDictionaryKey: "TwitchClientId") as? String).flatMap { $0.isEmpty ? nil : $0 } ?? ""
            graph = TwitchApp.shared.start(clientId: id)
        }
        session = PlayerSession(graph: graph)
        settings = graph.settings(scope: lifetime.scope)
        let flow = settings.state as SkieSwiftStateFlow<SettingsUiState>
        lifetime.keep(observe(flow) { [weak self] state in
            guard let self else { return }
            self.settingsState = state
            self.account = state.account
            self.sessionLoading = state.sessionLoading
            self.animationsEnabled = state.animationsEnabled
            if !state.sessionLoading { UserDefaults.standard.set(state.account != nil, forKey: Self.hintKey) }
        })
    }

    var isSignedIn: Bool { account != nil }

    /// Applies the debug launch arguments (never set in a release build).
    func applyLaunchArguments() {
        if let name = launch.tab, let tab = AppTab(rawValue: name) { self.tab = tab }
        if let push = launch.push {
            let parts = push.split(separator: ":", maxSplits: 2).map(String.init)
            switch parts.first {
            case "search": self.push(.search)
            case "settings": self.push(.settings)
            case "channel" where parts.count > 1: self.push(.channel(login: parts[1]))
            case "category" where parts.count > 2: self.push(.category(id: parts[1], name: parts[2]))
            default: break
            }
        }
        if launch.sheet == "signin" { showSignIn = true }
        if let login = launch.open {
            session.open(.live(login: login, name: login, title: login, avatarUrl: nil, thumbnailUrl: ""))
        }
    }

    /// The tabs of the bar. While the session loads the last known answer is used so the bar does not jump from two tabs to three.
    var tabs: [AppTab] {
        let signedIn = sessionLoading ? UserDefaults.standard.bool(forKey: Self.hintKey) : isSignedIn
        return signedIn ? [.home, .following, .browse] : [.home, .browse]
    }

    func path(for tab: AppTab) -> Binding<[Route]> {
        Binding(get: { self.paths[tab] ?? [] }, set: { self.paths[tab] = $0 })
    }

    func push(_ route: Route) {
        var stack = paths[tab] ?? []
        // A page already on the stack is returned to, not pushed twice.
        if let index = stack.firstIndex(of: route) {
            stack.removeSubrange((index + 1)...)
        } else {
            stack.append(route)
        }
        paths[tab] = stack
    }

    func select(_ next: AppTab) {
        if next == tab {
            paths[next] = []
        } else {
            tab = next
        }
    }

    /// Called when the scene becomes active: feeds older than their TTL refresh themselves (the shared policy).
    func appCameToForeground() {
        graph.foreground.appCameToForeground()
    }

    /// The animation list changes use: nil when the viewer turned animations off in the app or Reduce Motion is on.
    var motion: Animation? {
        #if canImport(UIKit)
        let reduce = UIAccessibility.isReduceMotionEnabled
        #else
        let reduce = NSWorkspace.shared.accessibilityDisplayShouldReduceMotion
        #endif
        return animationsEnabled && !reduce ? .smooth(duration: 0.3) : nil
    }

    func pop() {
        guard var stack = paths[tab], !stack.isEmpty else { return }
        stack.removeLast()
        paths[tab] = stack
    }

    func onSignedOut() {
        paths[.following] = []
        if tab == .following { tab = .home }
    }

    func signOut() {
        let auth = graph.auth(scope: lifetime.scope)
        auth.onEvent(event: AuthEventSignOut.shared)
        onSignedOut()
    }

    func onSettingsEvent(_ event: any SettingsEvent) {
        settings.onEvent(event: event)
    }

    var settingsPresenter: SettingsPresenter { settings }
}

extension AppModel {
    /// A tap on a stream card opens the player with its chat; the channel page is one tap away under the picture.
    func open(_ stream: LiveStream) {
        session.open(.live(login: stream.login, name: stream.name, title: stream.title, avatarUrl: stream.avatarUrl, thumbnailUrl: stream.thumbnailUrl))
    }

    func openReplay(_ item: ContinueWatchingItem) {
        let replay = item.replay.replay
        session.open(.replay(id: replay.id, title: replay.title, channelLogin: item.replay.channelLogin, channelName: item.replay.channelName, thumbnailUrl: replay.thumbnailUrl))
    }

    func openChannel(_ login: String) {
        session.minimise()
        push(.channel(login: login))
    }
}

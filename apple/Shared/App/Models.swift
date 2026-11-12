import Foundation
import Observation
import TwitchShared

/// The view models of the list screens: each owns its presenter's lifetime and publishes a Swift-friendly state.
@MainActor
@Observable
final class HomeModel {
    private(set) var state: LoadState<HomeContent> = .loading
    private let presenter: HomePresenter
    private let lifetime = Lifetime()

    init(graph: TwitchGraph) {
        presenter = graph.home(scope: lifetime.scope)
        let flow = presenter.state as SkieSwiftStateFlow<any Load>
        lifetime.keep(observe(flow) { [weak self] load in self?.state = LoadState(load, as: HomeContent.self) })
    }

    func retry() { presenter.onEvent(event: HomeEventRetry.shared) }
}

@MainActor
@Observable
final class FollowsModel {
    private(set) var state: LoadState<FollowsContent> = .loading
    private(set) var continueWatching: [ContinueWatchingItem] = []
    private let presenter: FollowsPresenter
    private let watching: ContinueWatchingPresenter
    private let lifetime = Lifetime()

    init(graph: TwitchGraph) {
        presenter = graph.follows(scope: lifetime.scope)
        watching = graph.continueWatching(scope: lifetime.scope)
        let flow = presenter.state as SkieSwiftStateFlow<any Load>
        lifetime.keep(observe(flow) { [weak self] load in self?.state = LoadState(load, as: FollowsContent.self) })
        let items = watching.items as SkieSwiftStateFlow<[ContinueWatchingItem]>
        lifetime.keep(observe(items) { [weak self] list in self?.continueWatching = list })
    }

    func retry() { presenter.onEvent(event: FollowsEventRetry.shared) }
}

@MainActor
@Observable
final class BrowseModel {
    private(set) var state: LoadState<BrowseUiState> = .loading
    private let presenter: BrowsePresenter
    private let lifetime = Lifetime()

    init(graph: TwitchGraph) {
        presenter = graph.browse(scope: lifetime.scope)
        let flow = presenter.state as SkieSwiftStateFlow<any Load>
        lifetime.keep(observe(flow) { [weak self] load in self?.state = LoadState(load, as: BrowseUiState.self) })
    }

    func select(_ filter: BrowseFilter) { presenter.onEvent(event: BrowseEventSelectFilter(filter: filter)) }
    func toggleSort() { presenter.onEvent(event: BrowseEventToggleSort.shared) }
    func retry() { presenter.onEvent(event: BrowseEventRetry.shared) }
}

@MainActor
@Observable
final class CategoryModel {
    private(set) var state: LoadState<CategoryUiState> = .loading
    private(set) var language = ""
    private(set) var languageOnly = false
    private let presenter: CategoryPresenter
    private let lifetime = Lifetime()

    init(graph: TwitchGraph, categoryId: String) {
        presenter = graph.category(categoryId: categoryId, scope: lifetime.scope)
        let flow = presenter.state as SkieSwiftStateFlow<CategoryScreenState>
        lifetime.keep(observe(flow) { [weak self] screen in
            self?.state = LoadState(screen.load, as: CategoryUiState.self)
            self?.language = screen.language
            self?.languageOnly = screen.languageOnly
        })
    }

    func toggleLanguage() { presenter.onEvent(event: CategoryEventToggleLanguage.shared) }
    func toggleFavorite() { presenter.onEvent(event: CategoryEventToggleFavorite.shared) }
    func retry() { presenter.onEvent(event: CategoryEventRetry.shared) }
}

/// Sign-in with the device code: the presenter asks Twitch for a code, shows it, and finishes when the viewer approves it on twitch.tv/activate.
@MainActor
@Observable
final class SignInModel {
    private(set) var code: DeviceCodeResponse?
    private(set) var result: SignInResult?
    /// When the code stops working.
    private(set) var expiresAt: Date?
    private let presenter: AuthPresenter
    private let lifetime = Lifetime()

    init(graph: TwitchGraph) {
        presenter = graph.auth(scope: lifetime.scope)
        let flow = presenter.state as SkieSwiftStateFlow<SignInUiState>
        lifetime.keep(observe(flow) { [weak self] state in
            guard let self else { return }
            if let code = state.code, self.code?.userCode != code.userCode { self.expiresAt = Date().addingTimeInterval(TimeInterval(code.expiresIn)) }
            self.code = state.code
            self.result = state.result
        })
        presenter.onEvent(event: AuthEventSignIn.shared)
    }

    func retry() { presenter.onEvent(event: AuthEventSignIn.shared) }
    func cancel() { presenter.onEvent(event: AuthEventCancel.shared) }
    func resultShown() { presenter.onEvent(event: AuthEventResultShown.shared) }
    func signOut() { presenter.onEvent(event: AuthEventSignOut.shared) }
}

@MainActor
@Observable
final class SearchModel {
    private(set) var state: SearchUiState?
    private let presenter: SearchPresenter
    private let lifetime = Lifetime()

    init(graph: TwitchGraph) {
        presenter = graph.search(scope: lifetime.scope)
        let flow = presenter.state as SkieSwiftStateFlow<SearchUiState>
        lifetime.keep(observe(flow) { [weak self] state in self?.state = state })
    }

    func setQuery(_ text: String) { presenter.onEvent(event: SearchEventSetQuery(value: text)) }
    func commit() { presenter.onEvent(event: SearchEventCommitQuery.shared) }
    func clear() { presenter.onEvent(event: SearchEventClear.shared) }
    func retry() { presenter.onEvent(event: SearchEventRetry.shared) }
    func removeRecent(_ query: String) { presenter.onEvent(event: SearchEventRemoveRecent(query: query)) }
    func clearRecents() { presenter.onEvent(event: SearchEventClearRecents.shared) }
}

@MainActor
@Observable
final class ChannelModel {
    private(set) var state: ChannelUiState?
    private let presenter: ChannelPresenter
    private let lifetime = Lifetime()

    init(graph: TwitchGraph, login: String) {
        presenter = graph.channel(login: login, scope: lifetime.scope)
        let flow = presenter.state as SkieSwiftStateFlow<ChannelUiState>
        lifetime.keep(observe(flow) { [weak self] state in self?.state = state })
    }

    func retry() { presenter.onEvent(event: ChannelEventRetry.shared) }
    func loadMoreReplays(channelId: String, firstCursor: String?) { presenter.onEvent(event: ChannelEventLoadMoreReplays(channelId: channelId, firstCursor: firstCursor)) }
}

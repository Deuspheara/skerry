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

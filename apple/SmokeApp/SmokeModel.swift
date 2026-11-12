import Foundation
import Observation
import TwitchShared

@MainActor
@Observable
final class SmokeModel {
    private(set) var settings: SettingsUiState?
    private(set) var home = "Loading"
    private(set) var checks: [String] = []

    private let graph: TwitchGraph
    private let scope = PresenterScope()
    private let settingsPresenter: SettingsPresenter
    private let homePresenter: HomePresenter

    init() {
        let clientId = (Bundle.main.object(forInfoDictionaryKey: "TwitchClientId") as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "smoke-test-client-id"
        graph = TwitchApp.shared.start(clientId: clientId)
        settingsPresenter = graph.settings(scope: scope)
        homePresenter = graph.home(scope: scope)
        Task { await observeSettings() }
        Task { await observeHome() }
        Task { await runSeamChecks() }
    }

    func toggleAnimations() {
        settingsPresenter.onEvent(event: SettingsEventToggleAnimations.shared)
    }

    private func observeSettings() async {
        let flow = settingsPresenter.state as SkieSwiftStateFlow<SettingsUiState>
        for await state in flow {
            settings = state
        }
    }

    private func observeHome() async {
        let flow = homePresenter.state as SkieSwiftStateFlow<any Load>
        for await state in flow {
            switch onEnum(of: state) {
            case .loading: home = "Loading"
            case .signedOut: home = "Signed out (no session): the bridge works, sign-in is a later step"
            case .failed: home = "Failed (no network or no client id)"
            case .ready(let ready):
                // Kotlin generics are erased in Objective-C: the payload comes back as AnyObject.
                if let content = ready.data as? HomeContent {
                    home = "Ready: \(content.forYou.count) streams, \(content.categories.count) categories"
                } else {
                    home = "Ready"
                }
            }
        }
    }

    /// The platform seams, exercised in Kotlin (Swift only sees concrete classes' async methods).
    private func runSeamChecks() async {
        do {
            checks = try await graph.selfTest.run()
        } catch {
            checks = ["self test failed: \(error.localizedDescription)"]
        }
    }
}

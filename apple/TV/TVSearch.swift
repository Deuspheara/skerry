import SwiftUI
import TwitchShared

/// Search on the Apple TV: the system search field (keyboard or dictation) on top, results as rows below. Recents and live channels fill the empty state.
struct TVSearchScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.tvPush) private var push
    @State private var model: SearchModel?
    @State private var text = ""

    var body: some View {
        Group {
            if let model, let state = model.state {
                TVPage {
                    if state.query.trimmingCharacters(in: .whitespaces).isEmpty {
                        emptyState(state, model)
                    } else if state.failed {
                        MessageState.failed(retry: { model.retry() }).frame(height: 400)
                    } else {
                        results(state)
                    }
                }
                .opacity(state.searching && !state.resultsFor.isEmpty ? 0.55 : 1)
                .animation(app.motion, value: state.searching)
            } else {
                Color.clear
            }
        }
        .searchable(text: $text, prompt: Text("phone_search_hint"))
        .onSubmit(of: .search) { model?.commit() }
        .task { if model == nil { model = SearchModel(graph: app.graph) } }
        .onChange(of: text) { _, new in model?.setQuery(new) }
    }

    @ViewBuilder private func emptyState(_ state: SearchUiState, _ model: SearchModel) -> some View {
        if !state.recents.isEmpty {
            TVRow(title: "phone_search_recent") {
                ForEach(state.recents, id: \.self) { recent in
                    Button { text = recent; model.commit() } label: { Label(recent, systemImage: "clock.arrow.circlepath").font(.callout).padding(.horizontal, 8) }
                        .buttonStyle(.bordered)
                }
                Button("phone_search_clear_recents") { model.clearRecents() }.buttonStyle(.bordered)
            }
        }
        if case .trends(let trends) = onEnum(of: state.results) {
            let channels = trends.channels as? [ChannelRef] ?? []
            if !channels.isEmpty { TVRow(title: "phone_search_live_now") { ForEach(channels, id: \.id) { c in TVChannelCard(channel: c) { open(c) } } } }
        }
    }

    @ViewBuilder private func results(_ state: SearchUiState) -> some View {
        if case .found(let found) = onEnum(of: state.results) {
            let channels = found.channels as? [ChannelRef] ?? []
            let categories = found.categories as? [TwitchShared.Category] ?? []
            if channels.isEmpty && categories.isEmpty { MessageState.noResults().frame(height: 400) }
            if !channels.isEmpty { TVRow(title: "sec_channels") { ForEach(channels, id: \.id) { c in TVChannelCard(channel: c) { open(c) } } } }
            if !categories.isEmpty { TVRow(title: "sec_categories") { ForEach(categories, id: \.id) { TVCategoryButton(category: $0) } } }
        } else if case .trends(let trends) = onEnum(of: state.results), trends.noResult {
            MessageState.noResults().frame(height: 400)
        }
    }

    private func open(_ channel: ChannelRef) {
        if channel.isLive {
            app.session.open(.live(login: channel.login, name: channel.name, title: channel.name, avatarUrl: channel.avatarUrl, thumbnailUrl: ""))
        } else {
            push(.channel(login: channel.login))
        }
    }
}

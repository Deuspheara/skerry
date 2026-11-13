import SwiftUI
import TwitchShared

private enum SearchFilter: CaseIterable {
    case all, channels, categories

    var key: LocalizedStringKey {
        switch self {
        case .all: "phone_search_filter_all"
        case .channels: "sec_channels"
        case .categories: "sec_categories"
        }
    }
}

/// Search while the toolbar field is active: recents and live channels when it is empty, grouped results (with a filter) when there is a query.
/// Results stay on screen, dimmed, while the next query loads (the same stale-while-revalidate as the other apps).
struct MacSearchView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacModel.self) private var mac
    @Environment(\.dismissSearch) private var dismissSearch
    @State private var model: SearchModel?
    @State private var filter = SearchFilter.all

    var body: some View {
        Group {
            if let model, let state = model.state {
                MacPage {
                    if !state.query.trimmingCharacters(in: .whitespaces).isEmpty, case .found = onEnum(of: state.results) {
                        Picker(selection: $filter) {
                            ForEach(SearchFilter.allCases, id: \.self) { Text($0.key).tag($0) }
                        } label: { Text("sec_channels") }
                        .pickerStyle(.segmented).labelsHidden().frame(maxWidth: 420)
                    }
                    if state.query.trimmingCharacters(in: .whitespaces).isEmpty {
                        emptyState(state, model)
                    } else if state.failed {
                        MessageState.failed(retry: { model.retry() }).frame(height: 300)
                    } else {
                        results(state)
                    }
                }
                .opacity(state.searching && !state.resultsFor.isEmpty ? 0.55 : 1)
                .animation(app.motion, value: state.searching)
                .overlay(alignment: .top) {
                    if state.searching && !state.resultsFor.isEmpty { ProgressView().progressViewStyle(.linear).tint(Theme.accent) }
                }
                .accessibilityLabel(state.searching ? Text("phone_search_updating") : Text(""))
            } else {
                Color.clear
            }
        }
        .background(Theme.background)
        .task(id: mac.searchText) {
            if model == nil { model = SearchModel(graph: app.graph) }
            model?.setQuery(mac.searchText)
        }
        .onSubmit(of: .search) { model?.commit() }
    }

    @ViewBuilder private func emptyState(_ state: SearchUiState, _ model: SearchModel) -> some View {
        if !state.recents.isEmpty {
            HStack {
                SectionTitle(key: "phone_search_recent")
                Button("phone_search_clear_recents") { model.clearRecents() }.buttonStyle(.borderless).foregroundStyle(Theme.accentText)
            }
            ForEach(state.recents, id: \.self) { recent in
                HStack {
                    Button { mac.searchText = recent; model.commit() } label: {
                        Label(recent, systemImage: "clock.arrow.circlepath").foregroundStyle(.primary).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button { model.removeRecent(recent) } label: { Image(systemName: "xmark").font(.footnote).foregroundStyle(Theme.textSecondary) }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text(String(format: String(localized: "phone_search_remove_recent"), recent)))
                }
            }
        }
        if case .trends(let trends) = onEnum(of: state.results) {
            let channels = trends.channels as? [ChannelRef] ?? []
            if !channels.isEmpty {
                SectionTitle(key: "phone_search_live_now")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 16)], spacing: 14) {
                    ForEach(channels, id: \.id) { channel in ChannelResultRow(channel: channel) }
                }
            }
        }
    }

    @ViewBuilder private func results(_ state: SearchUiState) -> some View {
        if case .found(let found) = onEnum(of: state.results) {
            let channels = found.channels as? [ChannelRef] ?? []
            let categories = found.categories as? [TwitchShared.Category] ?? []
            if channels.isEmpty && categories.isEmpty { MessageState.noResults().frame(height: 300) }
            if filter != .categories && !channels.isEmpty {
                if filter == .all { SectionTitle(key: "sec_channels") }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 16)], spacing: 14) {
                    ForEach(channels, id: \.id) { channel in ChannelResultRow(channel: channel) }
                }
            }
            if filter != .channels && !categories.isEmpty {
                if filter == .all { SectionTitle(key: "sec_categories") }
                LazyVGrid(columns: categoryGridColumns, spacing: 20) { ForEach(categories, id: \.id) { CategoryTile(category: $0) } }
            }
        } else if case .trends(let trends) = onEnum(of: state.results), trends.noResult {
            MessageState.noResults().frame(height: 300)
        }
    }
}

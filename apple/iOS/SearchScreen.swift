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

/// Search: the header pill zooms into this page (a native zoom transition from the pill), the field is the same glass capsule, results stay on screen
/// (dimmed, with a thin progress line) while the next query loads, and recents and live channels fill the empty state.
struct SearchScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: SearchModel?
    @State private var text = ""
    @State private var filter = SearchFilter.all
    @FocusState private var focused: Bool
    var namespace: Namespace.ID

    var body: some View {
        VStack(spacing: 0) {
            field
            filters
            content
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationTransition(.zoom(sourceID: SearchPill.id(app.tab), in: namespace))
        .task {
            if model == nil { model = SearchModel(graph: app.graph) }
            focused = true
        }
        .onChange(of: text) { _, new in model?.setQuery(new) }
    }

    private var field: some View {
        HStack(spacing: 10) {
            Button { app.pop() } label: {
                Image(systemName: "chevron.backward").font(.body.weight(.semibold)).frame(width: 44, height: 44).glass(in: Circle(), interactive: true)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("cd_back"))
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.textSecondary)
                TextField("phone_search_hint", text: $text)
                    .focused($focused)
                    .submitLabel(.search)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onSubmit { model?.commit() }
                    .accessibilityLabel(Text(text.isEmpty ? "phone_search_hint" : ""))
                if !text.isEmpty {
                    Button { text = ""; model?.clear() } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(Theme.textSecondary) }
                        .buttonStyle(.plain).accessibilityLabel(Text("phone_search_clear_field"))
                }
            }
            .padding(.horizontal, 14).frame(height: 44)
            .glassCapsule(interactive: true)
        }
        .padding(.horizontal, 16).padding(.vertical, 6)
    }

    @ViewBuilder private var filters: some View {
        if let state = model?.state, !state.query.isEmpty, case .found = onEnum(of: state.results) {
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(SearchFilter.allCases, id: \.self) { item in FilterChip(title: item.key, selected: filter == item) { filter = item } }
                }
                .padding(.horizontal, 16)
            }
            .scrollIndicators(.hidden).padding(.vertical, 6)
        }
    }

    @ViewBuilder private var content: some View {
        if let model, let state = model.state {
            let waiting = state.searching
            ZStack(alignment: .top) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        if state.query.trimmingCharacters(in: .whitespaces).isEmpty {
                            emptyState(state, model)
                        } else if state.failed {
                            MessageState.failed(retry: { model.retry() }).frame(height: 300)
                        } else {
                            results(state)
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 24)
                    .animation(app.motion, value: state.resultsFor)
                }
                .scrollIndicators(.hidden).scrollDismissesKeyboard(.interactively)
                .opacity(waiting && !state.resultsFor.isEmpty ? 0.55 : 1)
                .animation(app.motion, value: waiting)
                .accessibilityLabel(waiting ? Text("phone_search_updating") : Text(""))
                if waiting && !state.resultsFor.isEmpty { ProgressView().progressViewStyle(.linear).tint(Theme.accent).padding(.horizontal, 16) }
            }
        } else {
            Spacer()
        }
    }

    @ViewBuilder private func emptyState(_ state: SearchUiState, _ model: SearchModel) -> some View {
        if !state.recents.isEmpty {
            HStack {
                SectionTitle(key: "phone_search_recent")
                Button("phone_search_clear_recents") { model.clearRecents() }.font(.footnote.weight(.semibold)).foregroundStyle(Theme.accentText)
            }
            ForEach(state.recents, id: \.self) { recent in
                HStack {
                    Button { text = recent; model.commit() } label: {
                        Label(recent, systemImage: "clock.arrow.circlepath").foregroundStyle(.primary).frame(maxWidth: .infinity, alignment: .leading)
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
                ForEach(channels, id: \.id) { channel in ChannelResultRow(channel: channel) }
            }
        }
    }

    @ViewBuilder private func results(_ state: SearchUiState) -> some View {
        if case .found(let found) = onEnum(of: state.results) {
            let channels = found.channels as? [ChannelRef] ?? []
            let categories = found.categories as? [TwitchShared.Category] ?? []
            let scoped = filter == .categories ? categories.isEmpty : (filter == .channels ? channels.isEmpty : channels.isEmpty && categories.isEmpty)
            if scoped {
                MessageState.noResults(clear: { text = ""; model?.clear() }).frame(height: 300)
            }
            if filter != .categories && !channels.isEmpty {
                if filter == .all { SectionTitle(key: "sec_channels") }
                ForEach(channels, id: \.id) { channel in ChannelResultRow(channel: channel) }
            }
            if filter != .channels && !categories.isEmpty {
                if filter == .all { SectionTitle(key: "sec_categories") }
                ForEach(categories, id: \.id) { category in
                    Button { app.openCategory(category) } label: {
                        HStack(spacing: 12) {
                            RemoteImage(url: URL(string: category.artUrl)).frame(width: 40, height: 54).clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                            Text(category.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textSecondary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        } else if case .trends(let trends) = onEnum(of: state.results), trends.noResult {
            MessageState.noResults(clear: { text = ""; model?.clear() }).frame(height: 300)
        }
    }
}

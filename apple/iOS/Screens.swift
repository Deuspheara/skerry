import SwiftUI
import TwitchShared

/// The content width of the pages and the common scrolling container: header on top, content scrolls under it.
private let pageSpacing: CGFloat = 14

/// A banner shown when a refresh failed and what is on screen is a stored or older copy.
struct StaleBanner: View {
    let loadedAtMs: Int64
    var retry: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.arrow.triangle.2.circlepath")
            Text(loadedAtMs > 0 && Date().timeIntervalSince1970 * 1000 - Double(loadedAtMs) > 180_000
                ? String(format: String(localized: "phone_stale_ago"), relativeTime(fromMs: loadedAtMs))
                : String(localized: "phone_stale_failed"))
                .font(.footnote).lineLimit(2)
            Spacer(minLength: 0)
            Button("state_retry", action: retry).font(.footnote.weight(.semibold))
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .glass(in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
    }
}

/// Stable ids to drive list animations: the order of a list is what changes when a refresh lands.
private func ids(_ streams: [LiveStream]) -> [String] { streams.map(\.channelId) }

struct HomeScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: HomeModel?
    var namespace: Namespace.ID

    var body: some View {
        VStack(spacing: 0) {
            HeaderBar(namespace: namespace, tab: .home)
            content
        }
        .background(Theme.background.ignoresSafeArea())
        .task { if model == nil { model = HomeModel(graph: app.graph) } }
    }

    @ViewBuilder private var content: some View {
        switch model?.state ?? .loading {
        case .loading: SkeletonList()
        case .signedOut: SignedOutState()
        case .failed: MessageState(title: "state_error", action: (title: "state_retry", run: { model?.retry() }))
        case .ready(let home, let loadedAtMs, let refreshFailed):
            let following = home.following as? [LiveStream] ?? []
            let forYou = home.forYou as? [LiveStream] ?? []
            let categories = home.categories as? [TwitchShared.Category] ?? []
            if following.isEmpty && forYou.isEmpty {
                MessageState(title: "state_no_live", message: "phone_home_empty")
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: pageSpacing) {
                        if refreshFailed { StaleBanner(loadedAtMs: loadedAtMs) { model?.retry() } }
                        if !following.isEmpty {
                            SectionTitle(key: "phone_home_live_following")
                            ForEach(following, id: \.channelId) { stream in StreamRow(stream: stream) { app.open(stream) } }
                        }
                        if !forYou.isEmpty {
                            SectionTitle(key: "sec_for_you")
                            ForEach(forYou, id: \.channelId) { stream in StreamRow(stream: stream) { app.open(stream) } }
                        }
                        if !categories.isEmpty {
                            SectionTitle(key: "sec_categories")
                            CategoryStrip(categories: categories)
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 24)
                    .animation(app.motion, value: ids(following) + ids(forYou))
                }
                .scrollIndicators(.hidden)
            }
        }
    }
}

struct CategoryStrip: View {
    @Environment(AppModel.self) private var app
    let categories: [TwitchShared.Category]

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 12) {
                ForEach(categories, id: \.id) { category in
                    CategoryCard(category: category) { app.push(.category(id: category.id, name: category.name)) }.frame(width: 104)
                }
            }
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
    }
}

struct SkeletonList: View {
    var body: some View {
        VStack(spacing: pageSpacing) {
            ForEach(0..<7, id: \.self) { _ in StreamRowSkeleton() }
            Spacer()
        }
        .padding(16)
    }
}

struct SignedOutState: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        MessageState(title: "state_signin_title", message: "phone_signin_body", action: (title: "state_signin", run: { app.showSignIn = true }))
    }
}

struct FollowingScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: FollowsModel?
    var namespace: Namespace.ID

    var body: some View {
        VStack(spacing: 0) {
            HeaderBar(namespace: namespace, tab: .following)
            content
        }
        .background(Theme.background.ignoresSafeArea())
        .task { if model == nil { model = FollowsModel(graph: app.graph) } }
    }

    @ViewBuilder private var content: some View {
        switch model?.state ?? .loading {
        case .loading: SkeletonList()
        case .signedOut: SignedOutState()
        case .failed: MessageState(title: "state_error", action: (title: "state_retry", run: { model?.retry() }))
        case .ready(let follows, let loadedAtMs, let refreshFailed):
            let live = follows.live as? [LiveStream] ?? []
            let offline = follows.offline as? [ChannelRef] ?? []
            let resume = model?.continueWatching ?? []
            if live.isEmpty && offline.isEmpty && resume.isEmpty {
                MessageState(title: "state_no_live", message: "phone_following_empty")
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: pageSpacing) {
                        if refreshFailed { StaleBanner(loadedAtMs: loadedAtMs) { model?.retry() } }
                        if !resume.isEmpty {
                            SectionTitle(key: "phone_following_continue")
                            ContinueWatchingRow(items: resume)
                        }
                        if !live.isEmpty {
                            SectionTitle(key: "sec_following")
                            ForEach(live, id: \.channelId) { stream in StreamRow(stream: stream) { app.open(stream) } }
                        }
                        if !offline.isEmpty {
                            SectionTitle(key: "sec_offline")
                            ForEach(offline, id: \.id) { channel in OfflineRow(channel: channel) { app.push(.channel(login: channel.login)) } }
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 24)
                    .animation(app.motion, value: ids(live))
                }
                .scrollIndicators(.hidden)
            }
        }
    }
}

struct OfflineRow: View {
    let channel: ChannelRef
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Avatar(url: channel.avatarUrl, name: channel.name, size: 44).opacity(0.65)
                VStack(alignment: .leading, spacing: 2) {
                    Text(channel.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(channel.lastSeenLiveMs > 0
                        ? String(format: String(localized: "phone_following_last_live"), relativeTime(fromMs: channel.lastSeenLiveMs))
                        : String(localized: "phone_channel_offline"))
                        .font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.forward").font(.footnote.weight(.semibold)).foregroundStyle(Theme.textSecondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}

struct ContinueWatchingRow: View {
    @Environment(AppModel.self) private var app
    let items: [ContinueWatchingItem]

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 12) {
                ForEach(items, id: \.replay.replay.id) { item in
                    Button { app.openReplay(item) } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            ZStack(alignment: .bottom) {
                                Thumbnail(url: item.replay.replay.thumbnailUrl)
                                ProgressView(value: Double(item.progress.positionMs), total: max(1, Double(item.replay.replay.durationSeconds) * 1000))
                                    .tint(Theme.accent).padding(8)
                            }
                            .frame(width: 168)
                            Text(item.replay.replay.title).font(.footnote.weight(.semibold)).lineLimit(1).frame(width: 168, alignment: .leading)
                            Text(item.replay.channelName).font(.caption).foregroundStyle(Theme.textSecondary).lineLimit(1)
                        }
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
    }
}

struct BrowseScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: BrowseModel?
    var namespace: Namespace.ID

    var body: some View {
        VStack(spacing: 0) {
            HeaderBar(namespace: namespace, tab: .browse)
            content
        }
        .background(Theme.background.ignoresSafeArea())
        .task { if model == nil { model = BrowseModel(graph: app.graph) } }
    }

    private static let filters: [(BrowseFilter, String)] = [(.all, "filter_all"), (.games, "filter_games"), (.irl, "filter_irl"), (.music, "filter_music"), (.creative, "filter_creative")]

    @ViewBuilder private var content: some View {
        switch model?.state ?? .loading {
        case .loading: SkeletonList()
        case .signedOut: SignedOutState()
        case .failed: MessageState(title: "state_error", action: (title: "state_retry", run: { model?.retry() }))
        case .ready(let browse, let loadedAtMs, let refreshFailed):
            let categories = browse.categories as? [TwitchShared.Category] ?? []
            ScrollView {
                LazyVStack(alignment: .leading, spacing: pageSpacing) {
                    if refreshFailed { StaleBanner(loadedAtMs: loadedAtMs) { model?.retry() } }
                    HStack {
                        SectionTitle(key: "nav_browse")
                        Button { model?.toggleSort() } label: {
                            Label(browse.sort == .popularity ? "sort_popularity" : "sort_alpha", systemImage: "arrow.up.arrow.down").font(.footnote.weight(.semibold))
                        }
                        .buttonStyle(.plain).foregroundStyle(Theme.accent)
                    }
                    ScrollView(.horizontal) {
                        HStack(spacing: 8) {
                            ForEach(Self.filters, id: \.0) { filter, key in
                                FilterChip(title: LocalizedStringKey(key), selected: browse.filter == filter) { model?.select(filter) }
                            }
                        }
                    }
                    .scrollIndicators(.hidden).scrollClipDisabled()
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 12, alignment: .top)], spacing: 16) {
                        ForEach(categories, id: \.id) { category in
                            CategoryCard(category: category) { app.push(.category(id: category.id, name: category.name)) }
                        }
                    }
                    .animation(app.motion, value: categories.map(\.id))
                }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
        }
    }
}

struct FilterChip: View {
    let title: LocalizedStringKey
    let selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.subheadline.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 8)
                .foregroundStyle(selected ? Color.white : Color.primary)
                .background(selected ? AnyShapeStyle(Theme.accentDeep) : AnyShapeStyle(Theme.surface), in: Capsule())
        }
        .buttonStyle(PressableStyle())
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

struct CategoryScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: CategoryModel?
    let id: String
    let name: String

    var body: some View {
        VStack(spacing: 0) {
            DetailBar(title: name)
            content
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task { if model == nil { model = CategoryModel(graph: app.graph, categoryId: id) } }
    }

    @ViewBuilder private var content: some View {
        switch model?.state ?? .loading {
        case .loading: SkeletonList()
        case .signedOut: SignedOutState()
        case .failed: MessageState(title: "state_error", action: (title: "state_retry", run: { model?.retry() }))
        case .ready(let category, let loadedAtMs, let refreshFailed):
            let streams = category.streams as? [LiveStream] ?? []
            ScrollView {
                LazyVStack(alignment: .leading, spacing: pageSpacing) {
                    if refreshFailed { StaleBanner(loadedAtMs: loadedAtMs) { model?.retry() } }
                    if streams.isEmpty {
                        MessageState(title: "state_no_live")
                    }
                    ForEach(streams, id: \.channelId) { stream in StreamRow(stream: stream) { app.open(stream) } }
                }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 24)
                .animation(app.motion, value: ids(streams))
            }
            .scrollIndicators(.hidden)
        }
    }
}

/// The back row of the detail pages: a back button and a one-line title that scrolls slowly when it is too long.
struct DetailBar: View {
    @Environment(AppModel.self) private var app
    let title: String

    var body: some View {
        HStack(spacing: 10) {
            Button { app.pop() } label: {
                Image(systemName: "chevron.backward").font(.body.weight(.semibold)).frame(width: 38, height: 38).glass(in: Circle(), interactive: true)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("cd_back"))
            MarqueeText(text: title).font(.title3.weight(.bold))
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16).padding(.vertical, 6)
    }
}

/// One line with an ellipsis; if it does not fit it scrolls slowly (never with Reduce Motion).
struct MarqueeText: View {
    let text: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var textWidth: CGFloat = 0
    @State private var containerWidth: CGFloat = 0
    @State private var offset: CGFloat = 0

    var body: some View {
        let overflow = textWidth - containerWidth
        Text(text).lineLimit(1).fixedSize().hidden()
            .frame(maxWidth: .infinity, alignment: .leading)
            .overlay(alignment: .leading) {
                GeometryReader { proxy in
                    Text(text).lineLimit(1).fixedSize()
                        .background(GeometryReader { Color.clear.preference(key: WidthKey.self, value: $0.size.width) })
                        .offset(x: offset)
                        .frame(width: proxy.size.width, alignment: .leading)
                        .onAppear { containerWidth = proxy.size.width }
                        .onChange(of: proxy.size.width) { _, width in containerWidth = width }
                }
                .clipped()
            }
            .onPreferenceChange(WidthKey.self) { textWidth = $0 }
            .task(id: overflow) {
                offset = 0
                guard overflow > 1, !reduceMotion else { return }
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(1.2))
                    withAnimation(.linear(duration: Double(overflow) / 28)) { offset = -overflow }
                    try? await Task.sleep(for: .seconds(Double(overflow) / 28 + 1.2))
                    withAnimation(.easeOut(duration: 0.4)) { offset = 0 }
                    try? await Task.sleep(for: .seconds(0.6))
                }
            }
            .accessibilityLabel(text)
            .accessibilityAddTraits(.isHeader)
    }
}

private struct WidthKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

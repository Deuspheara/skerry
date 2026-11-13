import SwiftUI
import TwitchShared

/// The content width of the pages and the common scrolling container: header on top, content scrolls under it.
private let pageSpacing: CGFloat = 14

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
        case .signedOut: SignedOutState(context: .home)
        case .failed: MessageState.failed(retry: { model?.retry() })
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

/// Why a screen is empty without a session, what signing in gives, and the way to do it (instead of a bare "not connected").
struct SignedOutState: View {
    enum Context {
        case home, following, browse, category, channel

        var title: LocalizedStringKey {
            switch self {
            case .home: "signedout_home_title"
            case .following: "signedout_following_title"
            case .browse: "signedout_browse_title"
            case .category: "signedout_category_title"
            case .channel: "signedout_channel_title"
            }
        }

        var icon: String {
            switch self {
            case .home: "play.tv.fill"
            case .following: "heart.fill"
            case .browse: "square.grid.2x2.fill"
            case .category: "gamecontroller.fill"
            case .channel: "person.crop.circle.fill"
            }
        }
    }

    @Environment(AppModel.self) private var app
    var context: Context = .home

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Image(systemName: context.icon).font(.system(size: 30)).foregroundStyle(.white)
                    .frame(width: 72, height: 72).background(Theme.accentDeep.gradient, in: Circle())
                    .accessibilityHidden(true)
                Text(context.title).font(.title3.weight(.bold)).multilineTextAlignment(.center)
                Text("signedout_why").font(.subheadline).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
                VStack(alignment: .leading, spacing: 12) {
                    perk("dot.radiowaves.left.and.right", "signedout_perk_live")
                    perk("bubble.left.and.bubble.right.fill", "signedout_perk_chat")
                    perk("heart.fill", "signedout_perk_follow")
                }
                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                Button { app.showSignIn = true } label: {
                    Text("signedout_button").font(.body.weight(.semibold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 14)
                        .background(Theme.accentDeep, in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 24).padding(.top, 36).padding(.bottom, 120)
            .frame(maxWidth: 460)
            .frame(maxWidth: .infinity)
        }
    }

    private func perk(_ symbol: String, _ key: LocalizedStringKey) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).foregroundStyle(Theme.accentText).frame(width: 24)
            Text(key).font(.subheadline)
        }
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
        case .signedOut: SignedOutState(context: .following)
        case .failed: MessageState.failed(retry: { model?.retry() })
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
        case .signedOut: SignedOutState(context: .browse)
        case .failed: MessageState.failed(retry: { model?.retry() })
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
                        .buttonStyle(.plain).foregroundStyle(Theme.accentText)
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
        case .signedOut: SignedOutState(context: .category)
        case .failed: MessageState.failed(retry: { model?.retry() })
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

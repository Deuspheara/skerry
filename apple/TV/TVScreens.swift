import SwiftUI
import TwitchShared

/// The vertical page every list screen shares: rows stacked under the tab bar, a stale banner when a refresh failed.
struct TVPage<Content: View>: View {
    var banner: (loadedAtMs: Int64, retry: () -> Void)?
    @ViewBuilder var content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 34) {
                if let banner {
                    StaleBanner(loadedAtMs: banner.loadedAtMs, retry: banner.retry).padding(.horizontal, TVMetrics.edge).focusable(false)
                }
                content()
            }
            .padding(.vertical, 20)
        }
        .scrollClipDisabled()
    }
}

/// The states every list screen has besides content: the first load, signed out, failed.
private struct TVLoadStates<Content, Body: View>: View {
    @Environment(AppModel.self) private var app
    let state: LoadState<Content>
    let retry: () -> Void
    @ViewBuilder var ready: (Content, Int64, Bool) -> Body

    var body: some View {
        switch state {
        case .loading: TVSkeletonRow().frame(maxHeight: .infinity, alignment: .top).padding(.top, 60)
        case .signedOut: MessageState(title: "state_signin_title", message: "phone_signin_body", action: (title: "state_signin", run: { app.showSignIn = true }))
        case .failed: MessageState.failed(retry: retry)
        case .ready(let content, let loadedAtMs, let refreshFailed): ready(content, loadedAtMs, refreshFailed)
        }
    }
}

struct TVHomeScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: HomeModel?

    var body: some View {
        Group {
            if let model {
                TVLoadStates(state: model.state, retry: model.retry) { home, loadedAtMs, refreshFailed in
                    let following = home.following as? [LiveStream] ?? []
                    let forYou = home.forYou as? [LiveStream] ?? []
                    let categories = home.categories as? [TwitchShared.Category] ?? []
                    if following.isEmpty && forYou.isEmpty {
                        MessageState(title: "state_no_live", message: "phone_home_empty")
                    } else {
                        TVPage(banner: refreshFailed ? (loadedAtMs, { model.retry() }) : nil) {
                            if !following.isEmpty { TVRow(title: "phone_home_live_following") { ForEach(following, id: \.channelId) { s in TVStreamCard(stream: s) { app.open(s) } } } }
                            if !forYou.isEmpty { TVRow(title: "sec_for_you") { ForEach(forYou, id: \.channelId) { s in TVStreamCard(stream: s) { app.open(s) } } } }
                            if !categories.isEmpty { TVRow(title: "sec_categories") { ForEach(categories, id: \.id) { c in TVCategoryButton(category: c) } } }
                        }
                        .animation(app.motion, value: (following + forYou).map(\.channelId))
                    }
                }
            } else {
                TVSkeletonRow()
            }
        }
        .task { if model == nil { model = HomeModel(graph: app.graph) } }
    }
}

/// A category poster that opens the category page on the stack of the current section.
struct TVCategoryButton: View {
    @Environment(\.tvPush) private var push
    let category: TwitchShared.Category

    var body: some View { TVCategoryCard(category: category) { push(.category(id: category.id, name: category.name)) } }
}

struct TVFollowingScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.tvPush) private var push
    @State private var model: FollowsModel?

    var body: some View {
        Group {
            if let model {
                TVLoadStates(state: model.state, retry: model.retry) { follows, loadedAtMs, refreshFailed in
                    let live = follows.live as? [LiveStream] ?? []
                    let offline = follows.offline as? [ChannelRef] ?? []
                    let resume = model.continueWatching
                    if live.isEmpty && offline.isEmpty && resume.isEmpty {
                        MessageState(title: "state_no_live", message: "phone_following_empty")
                    } else {
                        TVPage(banner: refreshFailed ? (loadedAtMs, { model.retry() }) : nil) {
                            if !resume.isEmpty {
                                TVRow(title: "phone_following_continue") {
                                    ForEach(resume, id: \.replay.replay.id) { item in
                                        TVVideoCard(
                                            thumbnailUrl: item.replay.replay.thumbnailUrl, title: item.replay.replay.title, subtitle: item.replay.channelName,
                                            progress: Double(item.progress.positionMs) / max(1, Double(item.replay.replay.durationSeconds) * 1000),
                                        ) { app.openReplay(item) }
                                    }
                                }
                            }
                            if !live.isEmpty { TVRow(title: "sec_following") { ForEach(live, id: \.channelId) { s in TVStreamCard(stream: s) { app.open(s) } } } }
                            if !offline.isEmpty { TVRow(title: "sec_offline") { ForEach(offline, id: \.id) { c in TVChannelCard(channel: c) { push(.channel(login: c.login)) } } } }
                        }
                        .animation(app.motion, value: live.map(\.channelId))
                    }
                }
            } else {
                TVSkeletonRow()
            }
        }
        .task { if model == nil { model = FollowsModel(graph: app.graph) } }
    }
}

struct TVBrowseScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: BrowseModel?

    private static let filters: [(BrowseFilter, String)] = [(.all, "filter_all"), (.games, "filter_games"), (.irl, "filter_irl"), (.music, "filter_music"), (.creative, "filter_creative")]

    var body: some View {
        Group {
            if let model {
                TVLoadStates(state: model.state, retry: model.retry) { browse, loadedAtMs, refreshFailed in
                    let categories = browse.categories as? [TwitchShared.Category] ?? []
                    TVPage(banner: refreshFailed ? (loadedAtMs, { model.retry() }) : nil) {
                        VStack(alignment: .leading, spacing: 18) {
                            Text("nav_browse").font(.title3.weight(.bold)).accessibilityAddTraits(.isHeader)
                            ScrollView(.horizontal) {
                                HStack(spacing: 20) {
                                    ForEach(Self.filters, id: \.0) { filter, key in TVChip(title: LocalizedStringKey(key), selected: browse.filter == filter) { model.select(filter) } }
                                    Button { model.toggleSort() } label: {
                                        Label(browse.sort == .popularity ? "sort_popularity" : "sort_alpha", systemImage: "arrow.up.arrow.down").font(.callout.weight(.semibold))
                                    }
                                    .buttonStyle(.bordered)
                                }
                                .padding(.vertical, 12)
                            }
                            .scrollClipDisabled()
                        }
                        .padding(.horizontal, TVMetrics.edge).focusSection()
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: TVMetrics.posterWidth + 30), spacing: 36, alignment: .top)], spacing: 36) {
                            ForEach(categories, id: \.id) { TVCategoryButton(category: $0) }
                        }
                        .padding(.horizontal, TVMetrics.edge).focusSection()
                        .animation(app.motion, value: categories.map(\.id))
                    }
                }
            } else {
                TVSkeletonRow()
            }
        }
        .task { if model == nil { model = BrowseModel(graph: app.graph) } }
    }
}

struct TVCategoryScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: CategoryModel?
    let id: String
    let name: String

    var body: some View {
        Group {
            if let model {
                TVLoadStates(state: model.state, retry: model.retry) { category, loadedAtMs, refreshFailed in
                    let streams = category.streams as? [LiveStream] ?? []
                    TVPage(banner: refreshFailed ? (loadedAtMs, { model.retry() }) : nil) {
                        Text(name).font(.title.weight(.bold)).lineLimit(1).padding(.horizontal, TVMetrics.edge).accessibilityAddTraits(.isHeader)
                        if streams.isEmpty { MessageState(title: "state_no_live").frame(height: 300) }
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: TVMetrics.cardWidth + 20), spacing: 40, alignment: .top)], spacing: 40) {
                            ForEach(streams, id: \.channelId) { s in TVStreamCard(stream: s) { app.open(s) } }
                        }
                        .padding(.horizontal, TVMetrics.edge).focusSection()
                        .animation(app.motion, value: streams.map(\.channelId))
                    }
                }
            } else {
                TVSkeletonRow()
            }
        }
        .task { if model == nil { model = CategoryModel(graph: app.graph, categoryId: id) } }
    }
}

struct TVChannelScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.tvPush) private var push
    @State private var model: ChannelModel?
    let login: String

    var body: some View {
        Group {
            if let state = model?.state {
                switch LoadState(state.details, as: ChannelDetails.self) {
                case .loading: TVSkeletonRow()
                case .signedOut: MessageState(title: "state_signin_title", message: "phone_signin_body", action: (title: "state_signin", run: { app.showSignIn = true }))
                case .failed: MessageState.failed(retry: { model?.retry() })
                case .ready(let details, _, _): page(details, state)
                }
            } else {
                TVSkeletonRow()
            }
        }
        .task { if model == nil { model = ChannelModel(graph: app.graph, login: login) } }
    }

    private func page(_ details: ChannelDetails, _ state: ChannelUiState) -> some View {
        let replays = (details.replays as? [Replay] ?? []) + (state.tail.replays as? [Replay] ?? [])
        let clips = details.clips as? [ChannelClip] ?? []
        let similar = details.similar as? [ChannelRef] ?? []
        return TVPage {
            HStack(spacing: 28) {
                Avatar(url: details.channel.avatarUrl, name: details.channel.name, size: 130)
                    .overlay(Circle().stroke(Theme.live, lineWidth: details.stream != nil ? 5 : 0).padding(-6)).padding(6)
                VStack(alignment: .leading, spacing: 8) {
                    Text(details.channel.name).font(.largeTitle.weight(.bold)).lineLimit(1)
                    if details.stream == nil { Text("phone_channel_offline").font(.title3).foregroundStyle(.secondary) }
                }
            }
            .padding(.horizontal, TVMetrics.edge).accessibilityElement(children: .combine)
            if let stream = details.stream {
                TVRow(title: "home_live_now") { TVStreamCard(stream: stream) { app.open(stream) } }
            }
            if !replays.isEmpty {
                TVRow(title: "sec_replays") {
                    ForEach(replays, id: \.id) { replay in
                        TVVideoCard(
                            thumbnailUrl: replay.thumbnailUrl, title: replay.title, subtitle: replay.duration,
                            progress: state.progress[replay.id].map { Double($0.positionMs) / max(1, Double(replay.durationSeconds) * 1000) },
                        ) {
                            app.session.open(.replay(id: replay.id, title: replay.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: replay.thumbnailUrl))
                        }
                    }
                }
            }
            if !clips.isEmpty {
                TVRow(title: "sec_clips") {
                    ForEach(clips, id: \.slug) { clip in
                        TVVideoCard(thumbnailUrl: clip.thumbnailUrl, title: clip.title) {
                            app.session.open(.clip(slug: clip.slug, title: clip.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: clip.thumbnailUrl))
                        }
                    }
                }
            }
            if !similar.isEmpty {
                TVRow(title: "sec_similar") { ForEach(similar, id: \.id) { c in TVChannelCard(channel: c) { open(c) } } }
            }
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

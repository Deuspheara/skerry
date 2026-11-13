import SwiftUI
import TwitchShared

private func ids(_ streams: [LiveStream]) -> [String] { streams.map(\.channelId) }

/// What a list page shows besides its content: the first load, signed out, failed.
private struct LoadStates<Content, Body: View>: View {
    @Environment(AppModel.self) private var app
    let state: LoadState<Content>
    let retry: () -> Void
    @ViewBuilder var ready: (Content, Int64, Bool) -> Body

    var body: some View {
        switch state {
        case .loading: MacSkeletonGrid()
        case .signedOut: MessageState(title: "state_signin_title", message: "phone_signin_body", action: (title: "state_signin", run: { app.showSignIn = true }))
        case .failed: MessageState.failed(retry: retry)
        case .ready(let content, let loadedAtMs, let refreshFailed): ready(content, loadedAtMs, refreshFailed)
        }
    }
}

struct MacHomeScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: HomeModel?

    var body: some View {
        Group {
            if let model {
                LoadStates(state: model.state, retry: model.retry) { home, loadedAtMs, refreshFailed in
                    let following = home.following as? [LiveStream] ?? []
                    let forYou = home.forYou as? [LiveStream] ?? []
                    let categories = home.categories as? [TwitchShared.Category] ?? []
                    if following.isEmpty && forYou.isEmpty {
                        MessageState(title: "state_no_live", message: "phone_home_empty")
                    } else {
                        MacPage(banner: refreshFailed ? (loadedAtMs, { model.retry() }) : nil) {
                            if !following.isEmpty {
                                SectionTitle(key: "phone_home_live_following")
                                streams(following)
                            }
                            if !forYou.isEmpty {
                                SectionTitle(key: "sec_for_you")
                                streams(forYou)
                            }
                            if !categories.isEmpty {
                                SectionTitle(key: "sec_categories")
                                ScrollView(.horizontal) {
                                    LazyHStack(spacing: 16) {
                                        ForEach(categories, id: \.id) { CategoryTile(category: $0).frame(width: 140) }
                                    }
                                    .padding(.bottom, 6)
                                }
                                .scrollIndicators(.never).scrollClipDisabled()
                            }
                        }
                        .animation(app.motion, value: ids(following) + ids(forYou))
                    }
                }
            } else {
                MacSkeletonGrid()
            }
        }
        .task { if model == nil { model = HomeModel(graph: app.graph) } }
    }

    private func streams(_ list: [LiveStream]) -> some View {
        LazyVGrid(columns: streamGridColumns, spacing: 22) { ForEach(list, id: \.channelId) { StreamCard(stream: $0) } }
    }
}

struct MacFollowingScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: FollowsModel?

    var body: some View {
        Group {
            if let model {
                LoadStates(state: model.state, retry: model.retry) { follows, loadedAtMs, refreshFailed in
                    let live = follows.live as? [LiveStream] ?? []
                    let offline = follows.offline as? [ChannelRef] ?? []
                    let resume = model.continueWatching
                    if live.isEmpty && offline.isEmpty && resume.isEmpty {
                        MessageState(title: "state_no_live", message: "phone_following_empty")
                    } else {
                        MacPage(banner: refreshFailed ? (loadedAtMs, { model.retry() }) : nil) {
                            if !resume.isEmpty {
                                SectionTitle(key: "phone_following_continue")
                                ContinueWatchingRow(items: resume)
                            }
                            if !live.isEmpty {
                                SectionTitle(key: "sec_following")
                                LazyVGrid(columns: streamGridColumns, spacing: 22) { ForEach(live, id: \.channelId) { StreamCard(stream: $0) } }
                            }
                            if !offline.isEmpty {
                                SectionTitle(key: "sec_offline")
                                LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 16)], spacing: 14) {
                                    ForEach(offline, id: \.id) { channel in OfflineRow(channel: channel) { app.push(.channel(login: channel.login)) } }
                                }
                            }
                        }
                        .animation(app.motion, value: ids(live))
                    }
                }
            } else {
                MacSkeletonGrid()
            }
        }
        .task { if model == nil { model = FollowsModel(graph: app.graph) } }
    }
}

struct MacBrowseScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: BrowseModel?

    private static let filters: [(BrowseFilter, String)] = [(.all, "filter_all"), (.games, "filter_games"), (.irl, "filter_irl"), (.music, "filter_music"), (.creative, "filter_creative")]

    var body: some View {
        Group {
            if let model {
                LoadStates(state: model.state, retry: model.retry) { browse, loadedAtMs, refreshFailed in
                    let categories = browse.categories as? [TwitchShared.Category] ?? []
                    MacPage(banner: refreshFailed ? (loadedAtMs, { model.retry() }) : nil) {
                        HStack(spacing: 12) {
                            Picker(selection: Binding(get: { browse.filter }, set: { model.select($0) })) {
                                ForEach(Self.filters, id: \.0) { filter, key in Text(LocalizedStringKey(key)).tag(filter) }
                            } label: { Text("nav_browse") }
                            .pickerStyle(.segmented).labelsHidden().frame(maxWidth: 460)
                            Spacer()
                            Button { model.toggleSort() } label: {
                                Label(browse.sort == .popularity ? "sort_popularity" : "sort_alpha", systemImage: "arrow.up.arrow.down")
                            }
                            .buttonStyle(.borderless)
                        }
                        LazyVGrid(columns: categoryGridColumns, spacing: 20) { ForEach(categories, id: \.id) { CategoryTile(category: $0) } }
                            .animation(app.motion, value: categories.map(\.id))
                    }
                }
            } else {
                MacSkeletonGrid()
            }
        }
        .task { if model == nil { model = BrowseModel(graph: app.graph) } }
    }
}

struct MacCategoryScreen: View {
    @Environment(AppModel.self) private var app
    @State private var model: CategoryModel?
    let id: String
    let name: String

    var body: some View {
        Group {
            if let model {
                LoadStates(state: model.state, retry: model.retry) { category, loadedAtMs, refreshFailed in
                    let streams = category.streams as? [LiveStream] ?? []
                    MacPage(banner: refreshFailed ? (loadedAtMs, { model.retry() }) : nil) {
                        if streams.isEmpty { MessageState(title: "state_no_live").frame(height: 300) }
                        LazyVGrid(columns: streamGridColumns, spacing: 22) { ForEach(streams, id: \.channelId) { StreamCard(stream: $0) } }
                            .animation(app.motion, value: ids(streams))
                    }
                }
            } else {
                MacSkeletonGrid()
            }
        }
        .navigationTitle(name)
        .task { if model == nil { model = CategoryModel(graph: app.graph, categoryId: id) } }
    }
}

/// A channel page: avatar and live state, the stream, schedule, replays (with progress), clips and similar channels. Following is done on twitch.tv.
struct MacChannelScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.openURL) private var openURL
    @State private var model: ChannelModel?
    let login: String

    var body: some View {
        Group {
            if let state = model?.state {
                switch LoadState(state.details, as: ChannelDetails.self) {
                case .loading: MacSkeletonGrid()
                case .signedOut: MessageState(title: "state_signin_title", message: "phone_signin_body", action: (title: "state_signin", run: { app.showSignIn = true }))
                case .failed: MessageState.failed(retry: { model?.retry() })
                case .ready(let details, _, _): page(details, state)
                }
            } else {
                MacSkeletonGrid()
            }
        }
        .navigationTitle(login)
        .task { if model == nil { model = ChannelModel(graph: app.graph, login: login) } }
    }

    private func page(_ details: ChannelDetails, _ state: ChannelUiState) -> some View {
        let replays = (details.replays as? [Replay] ?? []) + (state.tail.replays as? [Replay] ?? [])
        let clips = details.clips as? [ChannelClip] ?? []
        let similar = details.similar as? [ChannelRef] ?? []
        let schedule = details.schedule as? [ScheduledStream] ?? []
        return MacPage {
            ChannelHeader(channel: details.channel, stream: details.stream, isFollowed: details.isFollowed, replays: replays.count, clips: clips.count) {
                if let url = URL(string: "https://www.twitch.tv/\(details.channel.login)") { openURL(url) }
            }
            if let stream = details.stream {
                StreamCard(stream: stream).frame(maxWidth: 420, alignment: .leading)
            }
            if !schedule.isEmpty {
                SectionTitle(key: "channel_schedule")
                ForEach(schedule, id: \.startMs) { item in
                    Label {
                        VStack(alignment: .leading) {
                            Text(item.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                            Text(Date(timeIntervalSince1970: Double(item.startMs) / 1000).formatted(date: .abbreviated, time: .shortened)).font(.footnote).foregroundStyle(Theme.textSecondary)
                        }
                    } icon: { Image(systemName: "calendar") }
                }
            }
            if !replays.isEmpty {
                SectionTitle(key: "sec_replays")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 380), spacing: 16)], spacing: 14) {
                    ForEach(replays, id: \.id) { replay in
                        ReplayRow(replay: replay, channel: details.channel, progress: state.progress[replay.id]) {
                            app.session.open(.replay(id: replay.id, title: replay.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: replay.thumbnailUrl))
                        }
                    }
                }
                if let cursor = details.replaysCursor, state.tail.cursor != nil || !state.tail.started {
                    Color.clear.frame(height: 1).onAppear { model?.loadMoreReplays(channelId: details.channel.id, firstCursor: cursor) }
                }
            }
            if !clips.isEmpty {
                SectionTitle(key: "sec_clips")
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 14) {
                        ForEach(clips, id: \.slug) { clip in
                            Button { app.session.open(.clip(slug: clip.slug, title: clip.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: clip.thumbnailUrl)) } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    Thumbnail(url: clip.thumbnailUrl).frame(width: 240)
                                    Text(clip.title).font(.footnote.weight(.semibold)).lineLimit(1).frame(width: 240, alignment: .leading)
                                }
                            }
                            .buttonStyle(PressableStyle())
                        }
                    }
                }
                .scrollIndicators(.never).scrollClipDisabled()
            }
            if !similar.isEmpty {
                SectionTitle(key: "sec_similar")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 16)], spacing: 14) {
                    ForEach(similar, id: \.id) { ChannelResultRow(channel: $0) }
                }
            }
        }
    }
}

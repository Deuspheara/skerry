import SwiftUI
import TwitchShared

/// A channel page: header with avatar and live state, schedule, replays (with progress), clips and similar channels. Twitch no longer lets a
/// third-party app follow or unfollow, so the follow button opens the channel on twitch.tv.
struct ChannelScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.openURL) private var openURL
    @State private var model: ChannelModel?
    let login: String

    var body: some View {
        VStack(spacing: 0) {
            DetailBar(title: "")
            content
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task { if model == nil { model = ChannelModel(graph: app.graph, login: login) } }
    }

    @ViewBuilder private var content: some View {
        if let state = model?.state {
            switch LoadState(state.details, as: ChannelDetails.self) {
            case .loading: SkeletonList()
            case .signedOut: SignedOutState(context: .channel)
            case .failed: MessageState.failed(retry: { model?.retry() })
            case .ready(let details, _, _): page(details, state)
            }
        } else {
            SkeletonList()
        }
    }

    private func page(_ details: ChannelDetails, _ state: ChannelUiState) -> some View {
        let replays = (details.replays as? [Replay] ?? []) + (state.tail.replays as? [Replay] ?? [])
        let clips = details.clips as? [ChannelClip] ?? []
        let similar = details.similar as? [ChannelRef] ?? []
        let schedule = details.schedule as? [ScheduledStream] ?? []
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                ChannelHeader(channel: details.channel, stream: details.stream, isFollowed: details.isFollowed, replays: replays.count, clips: clips.count) {
                    if let url = URL(string: "https://www.twitch.tv/\(details.channel.login)") { openURL(url) }
                }
                if let stream = details.stream {
                    StreamRow(stream: stream) { app.open(stream) }
                }
                if !schedule.isEmpty {
                    SectionTitle(key: "channel_schedule")
                    ForEach(schedule, id: \.startMs) { item in
                        HStack {
                            Image(systemName: "calendar")
                            VStack(alignment: .leading) {
                                Text(item.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                                Text(Date(timeIntervalSince1970: Double(item.startMs) / 1000).formatted(date: .abbreviated, time: .shortened)).font(.footnote).foregroundStyle(Theme.textSecondary)
                            }
                        }
                    }
                }
                if !replays.isEmpty {
                    SectionTitle(key: "sec_replays")
                    ForEach(replays, id: \.id) { replay in
                        ReplayRow(replay: replay, channel: details.channel, progress: state.progress[replay.id]) {
                            app.session.open(.replay(id: replay.id, title: replay.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: replay.thumbnailUrl), info: MediaInfo(views: Int(replay.views), durationSeconds: Int(replay.durationSeconds), dateMs: replay.publishedAtMs))
                        }
                    }
                    if let cursor = details.replaysCursor, state.tail.cursor != nil || !state.tail.started {
                        Color.clear.frame(height: 1).onAppear { model?.loadMoreReplays(channelId: details.channel.id, firstCursor: cursor) }
                    }
                }
                if !clips.isEmpty {
                    SectionTitle(key: "sec_clips")
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 12) {
                            ForEach(clips, id: \.slug) { clip in
                                Button { app.session.open(.clip(slug: clip.slug, title: clip.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: clip.thumbnailUrl), info: MediaInfo(views: Int(clip.views), durationSeconds: Int(clip.durationSeconds), dateMs: clip.createdAtMs)) } label: {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Thumbnail(url: clip.thumbnailUrl).frame(width: 168)
                                        Text(clip.title).font(.footnote.weight(.semibold)).lineLimit(1).frame(width: 168, alignment: .leading)
                                    }
                                }
                                .buttonStyle(PressableStyle())
                            }
                        }
                    }
                    .scrollIndicators(.hidden).scrollClipDisabled()
                }
                if !similar.isEmpty {
                    SectionTitle(key: "sec_similar")
                    ForEach(similar, id: \.id) { channel in ChannelResultRow(channel: channel) }
                }
            }
            .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
    }
}

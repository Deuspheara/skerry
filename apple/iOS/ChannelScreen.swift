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
            DetailBar(title: login)
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
            case .signedOut: SignedOutState()
            case .failed: MessageState(title: "state_error", action: (title: "state_retry", run: { model?.retry() }))
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
                HStack(spacing: 14) {
                    Avatar(url: details.channel.avatarUrl, name: details.channel.name, size: 72)
                        .overlay(Circle().stroke(Theme.live, lineWidth: details.stream != nil ? 3 : 0).padding(-4))
                        .padding(5)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(details.channel.name).font(.title2.weight(.bold)).lineLimit(1)
                        Button { if let url = URL(string: "https://www.twitch.tv/\(details.channel.login)") { openURL(url) } } label: {
                            Label(details.isFollowed ? "phone_follow_state_on" : "phone_follow", systemImage: details.isFollowed ? "heart.fill" : "heart")
                                .font(.footnote.weight(.semibold)).padding(.horizontal, 14).padding(.vertical, 7)
                        }
                        .glassCapsule(interactive: true).buttonStyle(.plain)
                        .accessibilityHint(Text("phone_follow_opens_twitch"))
                    }
                    Spacer(minLength: 0)
                }
                if let stream = details.stream {
                    StreamRow(stream: stream) { app.open(stream) }
                } else {
                    Text("phone_channel_offline").font(.subheadline).foregroundStyle(Theme.textSecondary)
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
                            app.session.open(.replay(id: replay.id, title: replay.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: replay.thumbnailUrl))
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
                                Button { app.session.open(.clip(slug: clip.slug, title: clip.title, channelLogin: details.channel.login, channelName: details.channel.name, thumbnailUrl: clip.thumbnailUrl)) } label: {
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

struct ReplayRow: View {
    let replay: Replay
    let channel: ChannelRef
    let progress: VodProgress?
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                ZStack(alignment: .bottom) {
                    Thumbnail(url: replay.thumbnailUrl)
                    if let progress, replay.durationSeconds > 0 {
                        ProgressView(value: min(1, Double(progress.positionMs) / (Double(replay.durationSeconds) * 1000))).tint(Theme.accent).padding(6)
                    }
                }
                .frame(width: 148)
                VStack(alignment: .leading, spacing: 4) {
                    Text(replay.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(replay.duration).font(.footnote).foregroundStyle(Theme.textSecondary)
                    Text(String(format: String(localized: "replay_views"), formatViewers(replay.views))).font(.caption).foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}
